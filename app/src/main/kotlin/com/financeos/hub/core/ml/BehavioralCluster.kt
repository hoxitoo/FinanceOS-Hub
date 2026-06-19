package com.financeos.hub.core.ml

import com.financeos.hub.core.database.entities.TransactionEntity
import com.financeos.hub.core.database.entities.TransactionType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Clusters the user's spending behaviour into one of 5 archetypes using K-means TFLite model.
 *
 * Features (7 dimensions):
 *   0: avg_hour_of_day     (normalised 0..1)
 *   1: weekend_ratio       (weekend/weekday spending ratio)
 *   2: avg_amount_rub      (normalised by 50_000₽)
 *   3: impulse_share       (fraction of impulse transactions)
 *   4: category_diversity  (unique categories / 13)
 *   5: income_expense_gap  (savings rate, clipped 0..1)
 *   6: txn_frequency       (transactions/day over period)
 *
 * Model spec:
 *   Input  : float[1][7]  — feature vector
 *   Output : float[1][5]  — cluster membership probabilities
 *
 * Falls back to rule-based archetype assignment when model is absent.
 */
@Singleton
class BehavioralCluster @Inject constructor(
    private val modelLoader: ModelLoader,
) {
    companion object {
        private const val MODEL_FILE    = "behavioral_cluster.tflite"
        private const val FEATURE_COUNT = 7

        val ARCHETYPES = listOf(
            "Плановик",       // 0 — high savings, low impulse, morning purchases
            "Импульсивный",   // 1 — night purchases, high impulse share
            "Гурман",         // 2 — high food/restaurant spend
            "Экономный",      // 3 — low spending, high savings
            "Путешественник", // 4 — high travel/entertainment, irregular patterns
        )
    }

    private val zone  = ZoneId.systemDefault()
    private val mutex = Mutex()

    private val interpreter: Interpreter? by lazy {
        try {
            modelLoader.load(MODEL_FILE)?.let { Interpreter(it) }
        } catch (e: Exception) {
            null
        }
    }

    data class ClusterResult(
        val archetype  : String,
        val clusterIdx : Int,
        val confidence : Float,
    )

    suspend fun classify(transactions: List<TransactionEntity>): ClusterResult {
        val features = extractFeatures(transactions)
        val interp   = interpreter ?: return ruleBased(features)

        return try {
            val inputBuf = ByteBuffer.allocateDirect(4 * FEATURE_COUNT)
                .order(ByteOrder.nativeOrder())
            features.forEach { inputBuf.putFloat(it) }
            inputBuf.rewind()

            val output = Array(1) { FloatArray(ARCHETYPES.size) }
            mutex.withLock { interp.run(inputBuf, output) }

            val probs  = output[0]
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
            ClusterResult(
                archetype  = ARCHETYPES[maxIdx],
                clusterIdx = maxIdx,
                confidence = probs[maxIdx],
            )
        } catch (e: Exception) {
            ruleBased(features)
        }
    }

    private fun extractFeatures(txs: List<TransactionEntity>): FloatArray {
        if (txs.isEmpty()) return FloatArray(FEATURE_COUNT)

        val expenses = txs.filter { it.type == TransactionType.EXPENSE }
        val incomes  = txs.filter { it.type == TransactionType.INCOME  }

        if (expenses.isEmpty()) return FloatArray(FEATURE_COUNT)

        val avgHour = expenses.map {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).hour
        }.average().toFloat() / 23f

        val byDay   = expenses.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek.value
        }
        val weekdaySum  = (1..5).mapNotNull { byDay[it] }.flatten().sumOf { abs(it.amountKopecks) }.toFloat()
        val weekendSum  = (6..7).mapNotNull { byDay[it] }.flatten().sumOf { abs(it.amountKopecks) }.toFloat()
        val weekendRatio= if (weekdaySum > 0f) (weekendSum / 2f) / (weekdaySum / 5f) else 1f

        val avgAmountRub = expenses.map { abs(it.amountKopecks) / 100f }.average().toFloat() / 50_000f

        val nightTx = expenses.count {
            val h = Instant.ofEpochMilli(it.timestamp).atZone(zone).hour
            h in 21..23 || h in 0..5
        }
        val impulseShare = if (expenses.isNotEmpty()) nightTx.toFloat() / expenses.size else 0f

        val catDiversity = expenses.mapNotNull { it.categoryId }.toSet().size.toFloat() / 13f

        val totalIncome  = incomes.sumOf { it.amountKopecks }.toFloat()
        val totalExpense = expenses.sumOf { abs(it.amountKopecks) }.toFloat()
        val savingsRate  = if (totalIncome > 0f) ((totalIncome - totalExpense) / totalIncome).coerceIn(0f, 1f) else 0f

        val days = ((txs.maxOf { it.timestamp } - txs.minOf { it.timestamp }) / 86_400_000f).coerceAtLeast(1f)
        val txFreq = (txs.size / days) / 10f  // normalise against ~10 tx/day

        return floatArrayOf(avgHour, weekendRatio.coerceIn(0f, 5f) / 5f, avgAmountRub.coerceIn(0f, 1f),
            impulseShare, catDiversity, savingsRate, txFreq.coerceIn(0f, 1f))
    }

    private fun ruleBased(features: FloatArray): ClusterResult {
        val impulseShare = features[3]
        val savingsRate  = features[5]
        val avgHour      = features[0]  // already normalised to 0..1

        val idx = when {
            savingsRate > 0.25f && impulseShare < 0.15f -> 0  // Плановик
            impulseShare > 0.35f                         -> 1  // Импульсивный
            savingsRate < 0.05f && impulseShare < 0.20f -> 3  // Экономный
            avgHour > 0.7f                               -> 4  // Путешественник (late activity)
            else                                         -> 2  // Гурман (default)
        }
        return ClusterResult(archetype = ARCHETYPES[idx], clusterIdx = idx, confidence = 0.6f)
    }
}

package com.financeos.hub.core.ml

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LSTM-based end-of-month spending predictor.
 *
 * Model spec:
 *   Input  : float[1][30][1]  — last 30 days of daily expense (normalised, kopecks/max)
 *   Output : float[1][1]      — predicted total for remaining days of month (normalised)
 *
 * Falls back to linear extrapolation when the model is absent.
 */
@Singleton
class SpendingPredictor @Inject constructor(
    private val modelLoader: ModelLoader,
) {
    companion object {
        private const val MODEL_FILE = "spending_predictor.tflite"
        private const val SEQ_LEN    = 30
    }

    private val mutex = Mutex()

    private val interpreter: Interpreter? by lazy {
        try {
            modelLoader.load(MODEL_FILE)?.let { Interpreter(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Predicts total end-of-month spending in kopecks.
     *
     * @param dailyExpenses  List of (dayEpochMs, kopecks) for the current month, sorted ascending
     * @param daysRemaining  How many days are left in the month
     */
    suspend fun predict(dailyExpenses: List<Pair<Long, Long>>, daysRemaining: Int): Long {
        if (dailyExpenses.isEmpty() || daysRemaining <= 0) return 0L

        val interp = interpreter ?: return linearFallback(dailyExpenses, daysRemaining)

        return try {
            val values = dailyExpenses.map { it.second }.takeLast(SEQ_LEN)
            val maxVal = values.maxOrNull()?.takeIf { it > 0 } ?: return 0L
            val padded = FloatArray(SEQ_LEN)
            values.forEachIndexed { i, v ->
                padded[SEQ_LEN - values.size + i] = v.toFloat() / maxVal
            }

            val inputBuf = ByteBuffer.allocateDirect(4 * SEQ_LEN)
                .order(ByteOrder.nativeOrder())
            padded.forEach { inputBuf.putFloat(it) }
            inputBuf.rewind()

            val output = Array(1) { FloatArray(1) }
            mutex.withLock { interp.run(inputBuf, output) }

            val normalizedPrediction = output[0][0].coerceIn(0f, 10f)
            (normalizedPrediction.toDouble() * maxVal.toDouble() * daysRemaining)
                .coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
        } catch (e: Exception) {
            linearFallback(dailyExpenses, daysRemaining)
        }
    }

    private fun linearFallback(dailyExpenses: List<Pair<Long, Long>>, daysRemaining: Int): Long {
        if (dailyExpenses.isEmpty()) return 0L
        val avgPerDay = dailyExpenses.sumOf { it.second } / dailyExpenses.size.coerceAtLeast(1)
        return avgPerDay * daysRemaining
    }
}

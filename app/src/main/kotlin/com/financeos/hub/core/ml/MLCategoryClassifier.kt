package com.financeos.hub.core.ml

import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.classifier.DictionaryClassifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TFLite-based merchant category classifier.
 *
 * Model spec:
 *   Input  : float[1][256]  — text feature vector from TextFeatureExtractor
 *   Output : float[1][13]   — softmax probabilities over 13 categories
 *
 * [DictionaryClassifier] runs FIRST and wins whenever it matches; the model only answers for
 * merchants no rule knows. It also covers the model being absent or throwing.
 */
@Singleton
class MLCategoryClassifier @Inject constructor(
    private val modelLoader        : ModelLoader,
    private val featureExtractor   : TextFeatureExtractor,
    private val dictionaryClassifier: DictionaryClassifier,
) : CategoryClassifier {

    companion object {
        private const val MODEL_FILE = "merchant_classifier.tflite"

        // Must match the order used during model training
        private val CATEGORY_IDS = listOf(
            "cat_food", "cat_grocery", "cat_transport", "cat_housing", "cat_health",
            "cat_shopping", "cat_telecom", "cat_entertain", "cat_education",
            "cat_travel", "cat_beauty", "cat_pets", "cat_other",
        )
    }

    private val mutex = Mutex()

    private val interpreter: Interpreter? by lazy {
        try {
            modelLoader.load(MODEL_FILE)?.let { Interpreter(it) }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun classify(merchant: String?, description: String?): String? {
        val text = listOfNotNull(merchant, description).joinToString(" ")
        if (text.isBlank()) return null

        // Явное правило бьёт модель. Модель заморожена на 13 метках (см. CATEGORY_IDS), и категории,
        // добавленные позже — «Букмекер», «Подписки» — она физически не может назвать. При включённой
        // «ИИ классификации» Netflix уходил бы в «Развлечения», а новая категория оставалась бы
        // навсегда пустой: со стороны это выглядит как сломанная функция, а не как ограничение модели.
        // Модель по-прежнему делает всю работу там, где правила молчат — то есть ровно то, ради чего
        // она и нужна.
        dictionaryClassifier.classify(merchant, description)?.let { return it }

        val interp = interpreter ?: return null

        return try {
            val features = featureExtractor.extract(text)

            val inputBuf = ByteBuffer.allocateDirect(4 * features.size)
                .order(ByteOrder.nativeOrder())
            features.forEach { inputBuf.putFloat(it) }
            inputBuf.rewind()

            val output = Array(1) { FloatArray(CATEGORY_IDS.size) }
            mutex.withLock { interp.run(inputBuf, output) }

            val probabilities = output[0]
            val maxIdx        = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

            // Require at least 40% confidence. Below it the dictionary has already had its turn
            // and said nothing, so the honest answer is "не знаю" — an under-confident guess is
            // worse than no category at all, because it looks decided.
            if (maxIdx >= 0 && probabilities[maxIdx] >= 0.40f) CATEGORY_IDS.getOrNull(maxIdx) else null
        } catch (e: Exception) {
            null
        }
    }
}

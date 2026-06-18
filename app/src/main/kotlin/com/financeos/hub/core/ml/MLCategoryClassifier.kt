package com.financeos.hub.core.ml

import com.financeos.hub.core.classifier.CategoryClassifier
import com.financeos.hub.core.classifier.DictionaryClassifier
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
 * Falls back to [DictionaryClassifier] if the model file is absent or inference fails.
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

        val interp = interpreter
            ?: return dictionaryClassifier.classify(merchant, description)

        return try {
            val features = featureExtractor.extract(text)

            val inputBuf = ByteBuffer.allocateDirect(4 * features.size)
                .order(ByteOrder.nativeOrder())
            features.forEach { inputBuf.putFloat(it) }
            inputBuf.rewind()

            val output = Array(1) { FloatArray(CATEGORY_IDS.size) }
            interp.run(inputBuf, output)

            val probabilities = output[0]
            val maxIdx        = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

            // Require at least 40% confidence, otherwise defer to dictionary
            if (maxIdx >= 0 && probabilities[maxIdx] >= 0.40f) {
                CATEGORY_IDS.getOrNull(maxIdx)
            } else {
                dictionaryClassifier.classify(merchant, description)
            }
        } catch (e: Exception) {
            dictionaryClassifier.classify(merchant, description)
        }
    }
}

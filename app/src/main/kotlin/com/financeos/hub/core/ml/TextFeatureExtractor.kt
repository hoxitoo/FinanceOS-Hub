package com.financeos.hub.core.ml

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts merchant name strings into fixed-size float feature vectors for TFLite inference.
 *
 * Strategy: character-level bag-of-n-grams (unigram + bigram) hashed into a 256-dim vector.
 * Fully offline, no vocabulary file needed.
 */
@Singleton
class TextFeatureExtractor @Inject constructor() {

    companion object {
        const val VECTOR_SIZE = 256
    }

    fun extract(text: String): FloatArray {
        val vector    = FloatArray(VECTOR_SIZE)
        val normalized = text.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }

        // Unigrams
        for (ch in normalized) {
            val idx = (ch.code * 2_654_435_761L and 0xFF).toInt()
            vector[idx] += 1f
        }

        // Bigrams
        for (i in 0 until normalized.length - 1) {
            val bigram = normalized[i].code * 31 + normalized[i + 1].code
            val idx    = (bigram * 2_654_435_761L and 0xFF).toInt()
            vector[idx] += 0.5f
        }

        // L2 normalise
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }

        return vector
    }
}

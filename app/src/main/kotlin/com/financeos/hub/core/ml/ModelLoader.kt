package com.financeos.hub.core.ml

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads TFLite model files from `assets/models/` as memory-mapped ByteBuffers.
 * Returns null if the file is not present — callers must handle missing models.
 */
@Singleton
class ModelLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(modelFileName: String): MappedByteBuffer? {
        return try {
            val assetManager = context.assets
            val fd           = assetManager.openFd("models/$modelFileName")
            val inputStream  = FileInputStream(fd.fileDescriptor)
            val channel      = inputStream.channel
            channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        } catch (e: Exception) {
            null  // Model not found or failed to load — callers fall back gracefully
        }
    }
}

package com.financeos.hub.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts plain text from a PDF file obtained via the Storage Access Framework.
 * No READ_EXTERNAL_STORAGE permission is required — the system grants temporary URI access.
 */
@Singleton
class PdfImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun extractText(uri: Uri): String = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                // sortByPosition keeps a tabular statement in visual reading order
                // (date → code → description → amount), which the row parser relies on.
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                stripper.getText(doc)
            }
        } ?: ""
    } catch (e: Exception) {
        ""
    }
}

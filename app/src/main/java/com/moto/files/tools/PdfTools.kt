package com.moto.files.tools

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting

/** SAF output; never modifies original PDFs. The provider may reject protected PDFs. */
class PdfTools(private val context: Context) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }
    private val resolver get() = context.contentResolver
    private fun parent(folder: Uri) = if (DocumentsContract.isDocumentUri(context, folder)) folder else
        DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
    fun merge(files: List<Uri>, folder: Uri): Uri {
        require(files.size >= 2) { "Select at least two PDFs" }
        val target = DocumentsContract.createDocument(resolver, parent(folder), "application/pdf", "Merged.pdf") ?: error("Cannot create PDF")
        try {
            val inputs = files.map { resolver.openInputStream(it) ?: error("Cannot read PDF") }
            try {
                resolver.openOutputStream(target, "w")?.use { output ->
                    val merger = PDFMergerUtility()
                    inputs.forEach(merger::addSource)
                    merger.destinationStream = output
                    merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
                } ?: error("Cannot write PDF")
            } finally { inputs.forEach { it.close() } }
            return target
        } catch (failure: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, target) }; throw failure }
    }
    fun split(file: Uri, folder: Uri): Int {
        resolver.openInputStream(file)?.use { input -> PDDocument.load(input).use { source ->
            val pages = Splitter().split(source)
            require(pages.size <= 200) { "More than 200 pages; split in smaller batches" }
            pages.forEachIndexed { index, page ->
                page.use {
                    val target = DocumentsContract.createDocument(resolver, parent(folder), "application/pdf", "Page ${index + 1}.pdf")
                        ?: error("Cannot create page ${index + 1}")
                    try { resolver.openOutputStream(target, "w")?.use { output -> page.save(output) } ?: error("Cannot write page") }
                    catch (failure: Exception) { runCatching { DocumentsContract.deleteDocument(resolver, target) }; throw failure }
                }
            }
            return pages.size
        } } ?: error("Cannot read PDF")
    }
}

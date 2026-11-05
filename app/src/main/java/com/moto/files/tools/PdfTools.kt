package com.moto.files.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF merge & split via framework PdfDocument/PdfRenderer.
 * Note: pages are re-rendered as images, so the output PDF has no selectable text layer.
 */
object PdfTools {

    suspend fun merge(files: List<File>, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = PdfDocument()
            files.forEach { renderInto(it, doc, 0, Int.MAX_VALUE) }
            dest.outputStream().use { doc.writeTo(it) }
            doc.close()
            true
        } catch (e: Exception) { false }
    }

    /** Pages [from]..[to], 1-based inclusive. */
    suspend fun split(src: File, from: Int, to: Int, dest: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val doc = PdfDocument()
                renderInto(src, doc, from - 1, to - 1)
                dest.outputStream().use { doc.writeTo(it) }
                doc.close()
                true
            } catch (e: Exception) { false }
        }

    private fun renderInto(src: File, doc: PdfDocument, from: Int, to: Int) {
        var pageNum = 0
        ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in from until minOf(to + 1, renderer.pageCount)) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(
                            page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val info = PdfDocument.PageInfo.Builder(
                            page.width, page.height, ++pageNum).create()
                        val p = doc.startPage(info)
                        p.canvas.drawBitmap(bmp, 0f, 0f, null)
                        doc.finishPage(p)
                        bmp.recycle()
                    }
                }
            }
        }
    }
}

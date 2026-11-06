package com.moto.files.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypesTest {
    @Test fun commonFormatsRouteToPreview() {
        assertEquals(ContentKind.IMAGE, FileTypes.kind("holiday.HEIC", "image/heic"))
        assertEquals(ContentKind.VIDEO, FileTypes.kind("clip.mkv", "video/x-matroska"))
        assertEquals(ContentKind.AUDIO, FileTypes.kind("recording.flac", "audio/flac"))
        assertEquals(ContentKind.PDF, FileTypes.kind("paper.pdf", "application/pdf"))
        assertEquals(ContentKind.TEXT, FileTypes.kind("notes.md", "text/plain"))
    }
    @Test fun unsupportedOfficeFormatUsesInstalledApps() {
        assertEquals(ContentKind.EXTERNAL, FileTypes.kind("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }
    @Test fun extensionIsCaseInsensitive() {
        assertEquals("webp", FileTypes.extension("PHOTO.WEBP"))
    }
}

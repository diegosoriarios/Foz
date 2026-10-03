package com.example.foz

import com.example.foz.model.ModelDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelDownloaderTest {

    @Test
    fun `content range with full range parses total`() {
        assertEquals(
            530_000_000L,
            ModelDownloader.contentRangeTotal("bytes 100-199/530000000")
        )
    }

    @Test
    fun `content range with unsatisfied range parses total`() {
        assertEquals(530_000_000L, ModelDownloader.contentRangeTotal("bytes */530000000"))
    }

    @Test
    fun `content range without total returns null`() {
        assertNull(ModelDownloader.contentRangeTotal("bytes 100-199/*"))
    }

    @Test
    fun `null or garbage header returns null`() {
        assertNull(ModelDownloader.contentRangeTotal(null))
        assertNull(ModelDownloader.contentRangeTotal(""))
        assertNull(ModelDownloader.contentRangeTotal("bytes 100-199"))
        assertNull(ModelDownloader.contentRangeTotal("not a range"))
    }
}

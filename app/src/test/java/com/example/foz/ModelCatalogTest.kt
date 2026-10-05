package com.example.foz

import com.example.foz.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    private val GB = 1024L * 1024 * 1024

    @Test
    fun `catalog ids and file names are unique`() {
        assertEquals(ModelCatalog.ALL.size, ModelCatalog.ALL.map { it.id }.distinct().size)
        assertEquals(ModelCatalog.ALL.size, ModelCatalog.ALL.map { it.fileName }.distinct().size)
    }

    @Test
    fun `all specs use https huggingface urls and positive limits`() {
        ModelCatalog.ALL.forEach { spec ->
            assertTrue(spec.url.startsWith("https://huggingface.co/"))
            assertTrue(spec.approxBytes > 0)
            assertTrue(spec.minTotalRamBytes > 0)
            assertTrue(spec.maxTokens >= 1280)
        }
    }

    @Test
    fun `byId falls back to default for unknown or null`() {
        assertEquals(ModelCatalog.DEFAULT, ModelCatalog.byId("nope"))
        assertEquals(ModelCatalog.DEFAULT, ModelCatalog.byId(null))
        assertEquals(
            ModelCatalog.GEMMA_1B_CLASSIC,
            ModelCatalog.byId("gemma-1b")
        )
    }

    @Test
    fun `byFileName maps known and legacy names`() {
        assertEquals(
            ModelCatalog.GEMMA_1B,
            ModelCatalog.byFileName("Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task")
        )
        assertEquals(
            ModelCatalog.GEMMA_1B_CLASSIC,
            ModelCatalog.byFileName("gemma3-1b-it-int4.task")
        )
        // Name our own v1.1 setup instructions used before the catalog existed.
        assertEquals(
            ModelCatalog.GEMMA_1B_CLASSIC,
            ModelCatalog.byFileName("gemma-3-1b-it-int4.task")
        )
        assertEquals(
            ModelCatalog.QWEN_15B,
            ModelCatalog.byFileName("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task")
        )
        assertEquals(
            ModelCatalog.QWEN_05B,
            ModelCatalog.byFileName("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task")
        )
        assertNull(ModelCatalog.byFileName(null))
        assertNull(ModelCatalog.byFileName("random-model.task"))
    }

    @Test
    fun `ram warning only for under-provisioned devices`() {
        assertNull(
            ModelCatalog.ramWarning(ModelCatalog.GEMMA_1B, 8L * GB)
        )
        val warning = ModelCatalog.ramWarning(ModelCatalog.GEMMA_1B, 3L * GB)
        assertFalse(warning.isNullOrEmpty())
        assertTrue(ModelCatalog.ramWarning(ModelCatalog.QWEN_05B, 2L * GB) == null)
        assertTrue(ModelCatalog.ramWarning(ModelCatalog.QWEN_15B, 4L * GB) == null)
        assertFalse(ModelCatalog.ramWarning(ModelCatalog.QWEN_15B, 3L * GB).isNullOrEmpty())
    }

    @Test
    fun `qwen models are token-free`() {
        assertTrue(ModelCatalog.QWEN_05B.needsToken.not())
        assertTrue(ModelCatalog.QWEN_15B.needsToken.not())
        assertTrue(ModelCatalog.GEMMA_1B.needsToken)
        assertTrue(ModelCatalog.GEMMA_1B_CLASSIC.needsToken)
    }

    @Test
    fun `litertlm specs are present ungated and distinct from mediapipe`() {
        val litertLm = ModelCatalog.ALL.filter {
            it.runtime == com.example.foz.model.ModelRuntime.LITERT_LM
        }
        assertEquals(3, litertLm.size)
        litertLm.forEach { spec ->
            assertTrue(spec.needsToken.not())
            assertTrue(spec.fileName.endsWith(".litertlm"))
            assertTrue(spec.url.endsWith(".litertlm"))
        }
        ModelCatalog.ALL.filter { it.runtime == com.example.foz.model.ModelRuntime.MEDIAPIPE_TASK }
            .forEach { spec -> assertTrue(spec.fileName.endsWith(".task")) }
    }

    @Test
    fun `byFileName maps litertlm files`() {
        assertEquals(
            ModelCatalog.QWEN3_06B,
            ModelCatalog.byFileName("Qwen3-0.6B.litertlm")
        )
        assertEquals(
            ModelCatalog.LFM25_12B,
            ModelCatalog.byFileName("LFM2.5-1.2B-Instruct_int4.litertlm")
        )
        assertEquals(
            ModelCatalog.GEMMA4_E2B,
            ModelCatalog.byFileName("gemma-4-E2B-it.litertlm")
        )
    }
}

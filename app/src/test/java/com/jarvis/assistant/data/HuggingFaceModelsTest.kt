package com.jarvis.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceModelsTest {
    @Test
    fun presetsAreUniqueAndMobileSizedFamilies() {
        val presets = HuggingFaceModels.presets
        assertEquals(5, presets.size)
        assertEquals(5, presets.map { it.id }.distinct().size)
        assertTrue(presets.all { it.fileName == "model.safetensors" })
        assertTrue(presets.any { it.id.startsWith("Qwen/") })
    }

    @Test
    fun customCatalogRejectsLargeModels() {
        assertTrue(HuggingFaceModels.isMobileCandidate("Qwen/Qwen3-0.6B"))
        assertTrue(HuggingFaceModels.isMobileCandidate("org/mobile-assistant"))
        assertTrue(!HuggingFaceModels.isMobileCandidate("org/assistant-7B"))
    }
}

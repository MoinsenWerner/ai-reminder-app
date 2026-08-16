package com.jarvis.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisLoggerTest {
    @Test
    fun summaryReturnsOnlyRecentNonEmptyLines() {
        val lines = listOf("first", "", "second", "third", "fourth")
        assertEquals("second\nthird\nfourth", JarvisLogger.summarize(lines, 3))
    }

    @Test
    fun summaryHandlesNonPositiveLimit() {
        assertEquals("", JarvisLogger.summarize(listOf("entry"), 0))
    }

    @Test
    fun contextFreeSummaryIsSafeBeforeInitialization() {
        assertEquals("", JarvisLogger.recentSummary())
    }

    @Test
    fun externalLogFlushIntervalIsThreeSeconds() {
        assertEquals(3L, JarvisLogger.EXTERNAL_FLUSH_INTERVAL_SECONDS)
    }
}

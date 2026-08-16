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
}

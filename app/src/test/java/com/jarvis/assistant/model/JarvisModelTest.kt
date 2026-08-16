package com.jarvis.assistant.model

import org.junit.Assert.*
import org.junit.Test

class JarvisModelTest {
    @Test fun detectsCalendarIntent() { assertEquals("calendar_event", JarvisModel().infer("WhatsApp: Treffen morgen 18 Uhr im Park").type) }
    @Test fun detectsWakeCommand() { assertEquals("voice_command", JarvisModel().infer("hey jarvis öffne chat").type) }
    @Test fun detectsAlarmCommand() { assertEquals("alarm", JarvisModel().infer("Stelle einen Wecker um 6:30 Uhr").type) }
    @Test fun parsesGermanTime() { assertEquals(6 to 30, AssistantEngine.parseTime("wecke mich um 6:30 Uhr")) }
    @Test fun huggingFacePromptDefinesActions() {
        assertTrue(AssistantEngine.HUGGING_FACE_SYSTEM_INSTRUCTION.contains("ACTION: alarm"))
        assertTrue(AssistantEngine.HUGGING_FACE_SYSTEM_INSTRUCTION.contains("Android-Assistent"))
    }
}

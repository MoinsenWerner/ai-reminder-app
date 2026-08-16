package com.jarvis.assistant.model

import org.junit.Assert.*
import org.junit.Test

class JarvisModelTest {
    @Test fun detectsCalendarIntent() { assertEquals("calendar_event", JarvisModel().infer("WhatsApp: Treffen morgen 18 Uhr im Park").type) }
    @Test fun detectsWakeCommand() { assertEquals("voice_command", JarvisModel().infer("hey jarvis öffne chat").type) }
}

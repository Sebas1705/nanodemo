package es.sebas1705.nanodemo.domain.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {
    private fun conversation(title: String) =
        Conversation(1, InferenceTier.CLOUD_GEMINI, title, createdAtMs = 0, updatedAtMs = 0, messageCount = 2)

    @Test
    fun `un titulo vacio es sin titulo`() {
        assertTrue(conversation("").isUntitled)
        assertTrue(conversation("   ").isUntitled)
    }

    @Test
    fun `el titulo por defecto viejo tambien es sin titulo`() {
        assertTrue(conversation("Conversación").isUntitled)
    }

    @Test
    fun `un titulo real se respeta`() {
        assertFalse(conversation("Conversación sobre el clima").isUntitled)
        assertFalse(conversation("Conversation").isUntitled)
    }
}

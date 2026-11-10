package es.sebas1705.nanodemo.feature.lab.trace

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitRetryTest {

    private val perMinute = """HTTP 429: {"error":{"code":429,"message":"You exceeded your current quota ... Quota exceeded for metric: generate_content_free_tier_requests, limit: 5 ... Please retry in 47.133731087s.","status":"RESOURCE_EXHAUSTED"}}"""
    private val perDay = """HTTP 429: {"error":{"message":"Quota exceeded for metric: generate_content_free_tier_requests, quotaId: GenerateRequestsPerDayPerProjectPerModel-FreeTier"}}"""
    private val question = listOf(ChatMessage(Role.USER, "hi"))

    @Test
    fun `a per-minute 429 is retryable and a daily one is not`() {
        assertTrue(isRetryableRateLimit(perMinute))
        assertFalse(isRetryableRateLimit(perDay))
        assertFalse(isRetryableRateLimit("HTTP 500: boom"))
        assertFalse(isRetryableRateLimit(null))
        assertFalse(isRetryableRateLimit("Límite gratis alcanzado: GEMINI_CLOUD"))
    }

    @Test
    fun `the wait the server asks for is read with a margin`() {
        assertEquals(48_133L, parseRetryDelayMs(perMinute))
        assertEquals(31_000L, parseRetryDelayMs("""... "retryDelay": "30s" ..."""))
        assertNull(parseRetryDelayMs("HTTP 429 with no hint"))
    }

    @Test
    fun `a rate limit is waited out and the retry result is returned`() = runBlocking {
        val waits = mutableListOf<Long>()
        val partials = mutableListOf<String>()
        var calls = 0
        val executor = RateLimitRetryExecutor(
            inner = { _, _, _, _ -> if (calls++ == 0) StepResult("", null, perMinute) else StepResult("ok", null) },
            wait = { waits += it },
        )

        val result = executor.run(InferenceTier.CLOUD_GEMINI, question, GenerationConfig()) { partials += it }

        assertTrue(result.ok)
        assertEquals("ok", result.text)
        assertEquals(2, calls)
        assertEquals(listOf(48_133L), waits)
        assertTrue(partials.single().contains("49 s"))
    }

    @Test
    fun `the wait is capped and retries are limited`() = runBlocking {
        val waits = mutableListOf<Long>()
        var calls = 0
        val executor = RateLimitRetryExecutor(
            inner = { _, _, _, _ -> calls++; StepResult("", null, "HTTP 429: retry in 600s") },
            maxRetries = 2,
            wait = { waits += it },
        )

        val result = executor.run(InferenceTier.CLOUD_GEMINI, question, GenerationConfig()) {}

        assertFalse(result.ok)
        assertEquals(3, calls) // first attempt + 2 retries
        assertEquals(listOf(MAX_RATE_LIMIT_WAIT_MS, MAX_RATE_LIMIT_WAIT_MS), waits)
    }

    @Test
    fun `without a hint the fallback wait is used`() = runBlocking {
        val waits = mutableListOf<Long>()
        var calls = 0
        val executor = RateLimitRetryExecutor(
            inner = { _, _, _, _ -> if (calls++ == 0) StepResult("", null, "HTTP 429 Too Many Requests") else StepResult("ok", null) },
            fallbackWaitMs = 7_000,
            wait = { waits += it },
        )
        executor.run(InferenceTier.CLOUD_GEMINI, question, GenerationConfig()) {}
        assertEquals(listOf(7_000L), waits)
    }

    @Test
    fun `other errors and a daily quota are returned without waiting`() = runBlocking {
        val waits = mutableListOf<Long>()
        for (error in listOf("HTTP 500: boom", perDay)) {
            var calls = 0
            val executor = RateLimitRetryExecutor(inner = { _, _, _, _ -> calls++; StepResult("", null, error) }, wait = { waits += it })
            val result = executor.run(InferenceTier.CLOUD_GEMINI, question, GenerationConfig()) {}
            assertFalse(result.ok)
            assertEquals(1, calls)
        }
        assertTrue(waits.isEmpty())
    }

    @Test
    fun `a success is returned untouched`() = runBlocking {
        val executor = RateLimitRetryExecutor(inner = { _, _, _, _ -> StepResult("fine", null) }, wait = { error("must not wait") })
        assertEquals("fine", executor.run(InferenceTier.ML_KIT_NANO, question, GenerationConfig()) {}.text)
    }
}

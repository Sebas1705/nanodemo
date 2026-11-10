package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.domain.models.ChatMessage
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.GenerationMetric
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.InferenceTier.CLOUD_GEMINI
import es.sebas1705.nanodemo.domain.models.InferenceTier.LITERT_LM_LOCAL
import es.sebas1705.nanodemo.domain.models.InferenceTier.ML_KIT_NANO
import es.sebas1705.nanodemo.domain.models.Role
import es.sebas1705.nanodemo.domain.models.StepResult
import es.sebas1705.nanodemo.feature.lab.trace.StepExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyComparisonTest {

    private fun run(
        policy: RoutingPolicy = RoutingPolicy.LOCAL_FIRST,
        prompt: String = "p",
        answeredBy: InferenceTier? = ML_KIT_NANO,
        blocked: Boolean = false,
        failed: Boolean = false,
        accepted: Boolean = true,
        escalations: Int = 0,
        latencyMs: Long = 1_000,
        cost: Double = 0.0,
        calls: Int = 0,
        pii: Int = 0,
        leaked: Int = 0,
        cloudOnly: Double = 0.001,
    ) = PolicyRun(prompt, policy, answeredBy, blocked, failed, accepted, escalations, latencyMs, cost, calls, pii, leaked, cloudOnly)

    // ---- aggregation --------------------------------------------------------------------------------------

    @Test
    fun `local share latency escalations and cost are aggregated per policy`() {
        val runs = listOf(
            run(answeredBy = ML_KIT_NANO, latencyMs = 1_000),
            run(answeredBy = LITERT_LM_LOCAL, latencyMs = 3_000, escalations = 1),
            run(answeredBy = CLOUD_GEMINI, latencyMs = 5_000, escalations = 2, cost = 0.002, calls = 1),
            run(answeredBy = CLOUD_GEMINI, latencyMs = 3_000, escalations = 1, cost = 0.001, calls = 1),
        )
        val s = PolicyAggregator.summarizeOne(RoutingPolicy.LOCAL_FIRST, runs)

        assertEquals(4, s.prompts)
        assertEquals(4, s.answered)
        assertEquals(0.5, s.localShare, 1e-9)
        assertEquals(3_000.0, s.avgLatencyMs, 1e-9)
        assertEquals(4, s.totalEscalations)
        assertEquals(1.0, s.avgEscalations, 1e-9)
        assertEquals(2, s.cloudCalls)
        assertEquals(0.003, s.cloudCostUsd, 1e-12)
        assertEquals(0.004, s.cloudOnlyCostUsd, 1e-12)
        assertEquals(0.75, s.costVsCloudOnly, 1e-9)
    }

    @Test
    fun `blocked and failed runs are counted but do not distort the averages`() {
        val runs = listOf(
            run(answeredBy = ML_KIT_NANO, latencyMs = 2_000),
            run(answeredBy = null, blocked = true, latencyMs = 0),
            run(answeredBy = null, failed = true, latencyMs = 9_000),
        )
        val s = PolicyAggregator.summarizeOne(RoutingPolicy.PRIVACY_FIRST, runs)

        assertEquals(3, s.prompts)
        assertEquals(1, s.answered)
        assertEquals(1, s.blocked)
        assertEquals(1, s.failed)
        assertEquals(2_000.0, s.avgLatencyMs, 1e-9)
        assertEquals(1.0, s.localShare, 1e-9)
    }

    @Test
    fun `pii prevented counts data that never reached the cloud in clear`() {
        val runs = listOf(
            run(pii = 3, leaked = 0), // kept local or masked
            run(pii = 2, leaked = 2, answeredBy = CLOUD_GEMINI, calls = 1), // gateway off: leaked
            run(pii = 0),
            run(pii = 1, leaked = 0, answeredBy = CLOUD_GEMINI, calls = 1), // masked by the gateway
        )
        val s = PolicyAggregator.summarizeOne(RoutingPolicy.CLOUD_FIRST, runs)
        assertEquals(4, s.piiLeaksPrevented)
        assertEquals(2, s.piiLeaked)
    }

    @Test
    fun `accepted share counts answers that passed the gate`() {
        val runs = listOf(run(accepted = true), run(accepted = false), run(accepted = true), run(accepted = true))
        assertEquals(0.75, PolicyAggregator.summarizeOne(RoutingPolicy.LOCAL_FIRST, runs).acceptedShare, 1e-9)
    }

    @Test
    fun `an empty policy has zeroed rates and no division by zero`() {
        val s = PolicyAggregator.summarizeOne(RoutingPolicy.LOCAL_FIRST, listOf(run(answeredBy = null, failed = true)))
        assertEquals(0.0, s.localShare, 0.0)
        assertEquals(0.0, s.avgLatencyMs, 0.0)
        assertEquals(0.0, s.acceptedShare, 0.0)
        assertEquals(0.0, PolicyAggregator.summarizeOne(RoutingPolicy.LOCAL_FIRST, listOf(run(cloudOnly = 0.0))).costVsCloudOnly, 0.0)
    }

    @Test
    fun `summaries come back in policy order and only for policies that ran`() {
        val runs = listOf(
            run(policy = RoutingPolicy.LATENCY_AWARE), run(policy = RoutingPolicy.LOCAL_FIRST), run(policy = RoutingPolicy.COST_AWARE),
        )
        assertEquals(
            listOf(RoutingPolicy.LOCAL_FIRST, RoutingPolicy.COST_AWARE, RoutingPolicy.LATENCY_AWARE),
            PolicyAggregator.summarize(runs).map { it.policy },
        )
    }

    // ---- cost maths ---------------------------------------------------------------------------------------

    @Test
    fun `cloud pricing matches the stats assumption`() {
        // 1M input tokens at $0.75 and 1M output tokens at $3.75.
        assertEquals(0.75, CloudPricing.usd(1_000_000, 0), 1e-12)
        assertEquals(3.75, CloudPricing.usd(0, 1_000_000), 1e-12)
        assertEquals(4.5, CloudPricing.usd(1_000_000, 1_000_000), 1e-12)
        assertEquals(0.0, CloudPricing.usd(0, 0), 0.0)
    }

    @Test
    fun `tokens are estimated at four characters per token rounding up`() {
        assertEquals(0, CloudPricing.tokensFromChars(0))
        assertEquals(1, CloudPricing.tokensFromChars(1))
        assertEquals(1, CloudPricing.tokensFromChars(4))
        assertEquals(2, CloudPricing.tokensFromChars(5))
        assertEquals(CloudPricing.usd(1_000, 500), CloudPricing.usdForChars(4_000, 2_000), 1e-12)
    }

    @Test
    fun `latency book smooths observations`() {
        val book = LatencyBook(alpha = 0.5)
        book.record(ML_KIT_NANO, 1_000)
        book.record(ML_KIT_NANO, 3_000)
        book.record(ML_KIT_NANO, 0) // ignored
        assertEquals(2_000L, book.snapshot()[ML_KIT_NANO])
        assertTrue(CLOUD_GEMINI !in book.snapshot())
    }

    // ---- the benchmark set --------------------------------------------------------------------------------

    @Test
    fun `the benchmark set covers the intended kinds of prompt`() {
        val set = RouterBenchSet.prompts
        assertEquals(10, set.size)
        assertEquals(set.size, set.map { it.id }.toSet().size)
        assertTrue(set.count { PromptClassifier.classify(it.text).sensitive } >= 2)
        assertTrue(set.any { PromptClassifier.classify(it.text).complexity == Complexity.TRIVIAL })
        assertTrue(set.any { PromptClassifier.classify(it.text).complexity == Complexity.COMPLEX })
        assertTrue(set.any { PromptClassifier.classify(it.text).needsFreshFacts })
        assertTrue(set.any { PromptClassifier.classify(it.text).features.hasCode })
        assertTrue(set.any { PromptClassifier.classify(it.text).features.hasMath })
        assertTrue(set.any { it.expectJson })
        assertTrue(set.any { it.text.length > 600 })
        assertTrue(RouterBenchSet.quick.size in 3..6)
    }

    @Test
    fun `the personal data in the benchmark prompts is really detected`() {
        val contact = RouterBenchSet.prompts.first { it.id == "pii-contact" }.text
        assertEquals(setOf(PiiKind.EMAIL, PiiKind.PHONE, PiiKind.DNI), PiiDetector.detect(contact).map { it.kind }.toSet())
        val bank = RouterBenchSet.prompts.first { it.id == "pii-bank" }.text
        assertEquals(setOf(PiiKind.IBAN, PiiKind.CARD), PiiDetector.detect(bank).map { it.kind }.toSet())
    }

    // ---- end to end with a fake executor ------------------------------------------------------------------

    private fun metric(tier: InferenceTier, totalMs: Long, promptChars: Int = 200, tokens: Int = 40) = GenerationMetric(
        tier = tier, startedAtMs = 0, firstChunkLatencyMs = 50, totalDurationMs = totalMs, promptChars = promptChars,
        responseChars = tokens * 4, success = true, responseTokens = tokens, responseTokensExact = true, memoryDeltaBytes = 0,
    )

    /** Locals answer only the trivial prompt well; everything else needs the cloud. */
    private class World(val usable: List<InferenceTier>) : StepExecutor {
        var calls = 0
        val cloudTexts = mutableListOf<String>()
        override suspend fun run(tier: InferenceTier, messages: List<ChatMessage>, config: GenerationConfig, onPartial: (String) -> Unit): StepResult {
            calls++
            val text = messages.last().text
            return if (tier.isCloud) {
                cloudTexts += text
                StepResult("Respuesta completa de la nube con bastante detalle para pasar el control de calidad.", GenerationMetric(
                    tier = tier, startedAtMs = 0, firstChunkLatencyMs = 50, totalDurationMs = 1_500, promptChars = text.length,
                    responseChars = 90, success = true, responseTokens = 23, responseTokensExact = true, memoryDeltaBytes = 0,
                ))
            } else {
                val reply = if (text.contains("capital")) "París" else ""
                StepResult(reply, GenerationMetric(
                    tier = tier, startedAtMs = 0, firstChunkLatencyMs = 50, totalDurationMs = 600, promptChars = text.length,
                    responseChars = reply.length, success = true, responseTokens = 2, responseTokensExact = true, memoryDeltaBytes = 0,
                ))
            }
        }
    }

    private fun compare(
        world: World,
        prompts: List<BenchPrompt>,
        settings: RouterSettings = RouterSettings(),
    ): List<PolicySummary> = runBlocking {
        val runs = PolicyComparison(world, testTexts).run(prompts, COMPARED_POLICIES, world.usable, settings)
        PolicyAggregator.summarize(runs)
    }

    private val trivial = RouterBenchSet.prompts.first { it.id == "trivial" }
    private val contact = RouterBenchSet.prompts.first { it.id == "pii-contact" }

    @Test
    fun `end to end the policies behave as designed on a trivial and a sensitive prompt`() {
        val world = World(listOf(ML_KIT_NANO, LITERT_LM_LOCAL, CLOUD_GEMINI))
        val summaries = compare(world, listOf(trivial, contact)).associateBy { it.policy }

        // Cloud first sends both prompts to the cloud; its cost is the highest.
        val cloudFirst = summaries.getValue(RoutingPolicy.CLOUD_FIRST)
        assertEquals(0.0, cloudFirst.localShare, 1e-9)
        assertEquals(2, cloudFirst.cloudCalls)
        assertTrue(cloudFirst.cloudCostUsd > 0.0)

        // Local first answers the trivial prompt on the device and escalates the sensitive one (masked).
        val localFirst = summaries.getValue(RoutingPolicy.LOCAL_FIRST)
        assertEquals(0.5, localFirst.localShare, 1e-9)
        assertEquals(1, localFirst.cloudCalls)
        assertTrue(localFirst.cloudCostUsd < cloudFirst.cloudCostUsd)

        // Privacy first: the sensitive prompt stays on the device and fails there; nothing sensitive is sent.
        val privacy = summaries.getValue(RoutingPolicy.PRIVACY_FIRST)
        assertEquals(0, privacy.cloudCalls)
        assertEquals(1, privacy.failed)
        assertEquals(0, privacy.piiLeaked)
        assertEquals(3, privacy.piiLeaksPrevented)

        // Cost aware never pays for the trivial prompt.
        val cost = summaries.getValue(RoutingPolicy.COST_AWARE)
        assertTrue(cost.cloudCostUsd <= localFirst.cloudCostUsd)

        // The gateway kept personal data off the cloud for every policy.
        summaries.values.forEach { assertEquals(0, it.piiLeaked) }
        assertTrue(summaries.getValue(RoutingPolicy.CLOUD_FIRST).piiLeaksPrevented >= 3)
        assertTrue(world.cloudTexts.none { it.contains("ana.garcia@example.com") || it.contains("12345678Z") })
    }

    @Test
    fun `with the gateway off the leaks show up in the cloud first row`() {
        val world = World(listOf(ML_KIT_NANO, CLOUD_GEMINI))
        val summaries = compare(world, listOf(contact), RouterSettings(gatewayEnabled = false)).associateBy { it.policy }
        assertTrue(summaries.getValue(RoutingPolicy.CLOUD_FIRST).piiLeaked >= 3)
        assertEquals(0, summaries.getValue(RoutingPolicy.PRIVACY_FIRST).piiLeaked)
    }

    @Test
    fun `engines are only called once per distinct request across policies`() {
        val world = World(listOf(ML_KIT_NANO, LITERT_LM_LOCAL, CLOUD_GEMINI))
        compare(world, listOf(trivial))
        // 5 policies, but Nano/LiteRT-LM/Cloud are each asked the same text at most once.
        assertTrue("calls=${world.calls}", world.calls <= 3)
    }

    @Test
    fun `no cloud engine means zero cloud spend for every policy`() {
        val world = World(listOf(ML_KIT_NANO, LITERT_LM_LOCAL))
        val summaries = compare(world, RouterBenchSet.quick)
        assertTrue(summaries.all { it.cloudCalls == 0 && it.cloudCostUsd == 0.0 })
    }

    @Test
    fun `no local engine means nothing is answered locally and privacy first blocks sensitive prompts`() {
        val world = World(listOf(CLOUD_GEMINI))
        val summaries = compare(world, listOf(trivial, contact)).associateBy { it.policy }
        assertTrue(summaries.values.all { it.localShare == 0.0 })
        assertEquals(1, summaries.getValue(RoutingPolicy.PRIVACY_FIRST).blocked)
        assertEquals(0, summaries.getValue(RoutingPolicy.PRIVACY_FIRST).piiLeaked)
    }

    @Test
    fun `progress is reported for every run`() = runBlocking {
        val world = World(listOf(ML_KIT_NANO))
        val seen = mutableListOf<Pair<Int, Int>>()
        PolicyComparison(world, testTexts).run(listOf(trivial), COMPARED_POLICIES, world.usable) { done, total, _ -> seen += done to total }
        assertEquals(COMPARED_POLICIES.size + 1, seen.size)
        assertEquals(COMPARED_POLICIES.size to COMPARED_POLICIES.size, seen.last())
    }

    @Test
    fun `the memoizing executor shares answers and does not cache failures`() = runBlocking {
        var n = 0
        val flaky = StepExecutor { _, _, _, _ -> n++; if (n == 1) StepResult("", null, "boom") else StepResult("ok", null) }
        val memo = MemoizingExecutor(flaky)
        val msg = listOf(ChatMessage(Role.USER, "hi"))
        val cfg = GenerationConfig()

        assertFalse(memo.run(ML_KIT_NANO, msg, cfg) {}.ok)
        assertEquals("ok", memo.run(ML_KIT_NANO, msg, cfg) {}.text)
        assertEquals("ok", memo.run(ML_KIT_NANO, msg, cfg) {}.text)
        assertEquals("ok", memo.run(ML_KIT_NANO, msg, cfg.copy(jsonMode = true)) {}.text)
        assertEquals(1, memo.hits)
        assertEquals(3, n) // the failure plus two distinct configs; the repeat was a hit
    }
}

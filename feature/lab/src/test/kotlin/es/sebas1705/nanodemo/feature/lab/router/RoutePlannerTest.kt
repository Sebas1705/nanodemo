package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.InferenceTier.CLOUD_GEMINI
import es.sebas1705.nanodemo.domain.models.InferenceTier.FIREBASE_AI
import es.sebas1705.nanodemo.domain.models.InferenceTier.LITERT_LM_LOCAL
import es.sebas1705.nanodemo.domain.models.InferenceTier.MEDIAPIPE_LOCAL
import es.sebas1705.nanodemo.domain.models.InferenceTier.ML_KIT_NANO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {

    private val all = InferenceTier.entries
    private val trivial = PromptClassifier.classify("¿Cuál es la capital de Francia?")
    private val complexPrompt = PromptClassifier.classify(
        "Analiza paso a paso y compara estas dos arquitecturas, justifica cada decisión y escribe el código en Kotlin. ".repeat(3),
    )
    private val pii = PromptClassifier.classify("Escribe a ana@example.com")
    private val fresh = PromptClassifier.classify("¿Cuál es el precio actual del bitcoin?")

    private fun tiers(plan: RoutePlan) = plan.engines.map { it.tier }
    private fun plan(
        policy: RoutingPolicy,
        c: Classification = trivial,
        usable: List<InferenceTier> = all,
        settings: RouterSettings = RouterSettings(),
        manual: InferenceTier? = null,
        observed: Map<InferenceTier, Long> = emptyMap(),
    ) = RoutePlanner.plan(policy, c, usable, settings, manual, observed)

    // ---- local first / cloud first ------------------------------------------------------------------------

    @Test
    fun `local first tries the best locals then one cloud engine`() {
        assertEquals(listOf(ML_KIT_NANO, LITERT_LM_LOCAL, CLOUD_GEMINI), tiers(plan(RoutingPolicy.LOCAL_FIRST)))
    }

    @Test
    fun `local first honours the local attempt cap`() {
        val p = plan(RoutingPolicy.LOCAL_FIRST, settings = RouterSettings(maxLocalAttempts = 1))
        assertEquals(listOf(ML_KIT_NANO, CLOUD_GEMINI), tiers(p))
    }

    @Test
    fun `cloud first puts the cloud engines in front and keeps locals as fallback`() {
        assertEquals(listOf(CLOUD_GEMINI, FIREBASE_AI, ML_KIT_NANO, LITERT_LM_LOCAL), tiers(plan(RoutingPolicy.CLOUD_FIRST)))
    }

    @Test
    fun `only cloud engines usable`() {
        val usable = listOf(CLOUD_GEMINI, FIREBASE_AI)
        val local = plan(RoutingPolicy.LOCAL_FIRST, usable = usable)
        assertEquals(listOf(CLOUD_GEMINI), tiers(local))
        assertTrue(local.notes.any { it.contains("local") })
        assertEquals(listOf(CLOUD_GEMINI, FIREBASE_AI), tiers(plan(RoutingPolicy.CLOUD_FIRST, usable = usable)))
    }

    @Test
    fun `only local engines usable`() {
        val usable = listOf(MEDIAPIPE_LOCAL, ML_KIT_NANO)
        val p = plan(RoutingPolicy.CLOUD_FIRST, usable = usable)
        assertEquals(listOf(ML_KIT_NANO, MEDIAPIPE_LOCAL), tiers(p))
        assertTrue(p.notes.any { it.contains("nube") })
        assertEquals(listOf(ML_KIT_NANO, MEDIAPIPE_LOCAL), tiers(plan(RoutingPolicy.LOCAL_FIRST, usable = usable)))
    }

    @Test
    fun `no engine at all is a blocked plan for every policy`() {
        RoutingPolicy.entries.forEach { policy ->
            val p = plan(policy, usable = emptyList(), manual = ML_KIT_NANO)
            assertTrue("$policy", p.blocked)
            assertTrue(p.engines.isEmpty())
        }
    }

    @Test
    fun `the gateway flag is set on cloud entries only and follows the setting`() {
        val on = plan(RoutingPolicy.LOCAL_FIRST)
        assertTrue(on.engines.filter { it.tier.isCloud }.all { it.viaGateway })
        assertTrue(on.engines.filter { !it.tier.isCloud }.none { it.viaGateway })
        val off = plan(RoutingPolicy.LOCAL_FIRST, settings = RouterSettings(gatewayEnabled = false))
        assertTrue(off.engines.none { it.viaGateway })
    }

    // ---- privacy ------------------------------------------------------------------------------------------

    @Test
    fun `privacy first keeps a prompt with pii off every cloud engine`() {
        val p = plan(RoutingPolicy.PRIVACY_FIRST, pii)
        assertEquals(listOf(ML_KIT_NANO, LITERT_LM_LOCAL), tiers(p))
        assertTrue(p.engines.none { it.tier.isCloud })
    }

    @Test
    fun `privacy first blocks a sensitive prompt when there is no local engine`() {
        val p = plan(RoutingPolicy.PRIVACY_FIRST, pii, usable = listOf(CLOUD_GEMINI, FIREBASE_AI))
        assertTrue(p.blocked)
        assertTrue(p.engines.isEmpty())
    }

    @Test
    fun `privacy first blocks a sensitive prompt too long for the small local engines`() {
        val long = PromptClassifier.classify("ana@example.com " + "texto ".repeat(1000))
        val p = plan(RoutingPolicy.PRIVACY_FIRST, long, usable = listOf(MEDIAPIPE_LOCAL, CLOUD_GEMINI))
        assertTrue(p.blocked)
        assertTrue(p.notes.any { it.contains("no cabe") })
    }

    @Test
    fun `privacy first behaves like local first for a prompt without sensitive data`() {
        assertEquals(tiers(plan(RoutingPolicy.LOCAL_FIRST)), tiers(plan(RoutingPolicy.PRIVACY_FIRST)))
    }

    @Test
    fun `a prompt marked sensitive is private even without detectable data`() {
        val marked = PromptClassifier.classify("Resume mi diario", markedSensitive = true)
        assertTrue(plan(RoutingPolicy.PRIVACY_FIRST, marked).engines.none { it.tier.isCloud })
    }

    // ---- cost ---------------------------------------------------------------------------------------------

    @Test
    fun `cost aware never pays the cloud for a trivial prompt that has a local engine`() {
        val p = plan(RoutingPolicy.COST_AWARE, trivial)
        assertTrue(p.engines.none { it.tier.isCloud })
        assertTrue(p.notes.any { it.contains("simple") })
    }

    @Test
    fun `cost aware escalates to the cloud for a harder prompt inside the budget`() {
        val p = plan(RoutingPolicy.COST_AWARE, complexPrompt)
        assertEquals(CLOUD_GEMINI, p.engines.last().tier)
    }

    @Test
    fun `cost aware drops the cloud when the estimate exceeds the budget`() {
        val p = plan(RoutingPolicy.COST_AWARE, complexPrompt, settings = RouterSettings(cloudBudgetUsd = 0.0000001))
        assertTrue(p.engines.none { it.tier.isCloud })
        assertTrue(p.notes.any { it.contains("presupuesto") })
    }

    @Test
    fun `cost aware still uses the cloud when it is the only engine`() {
        val p = plan(RoutingPolicy.COST_AWARE, trivial, usable = listOf(CLOUD_GEMINI))
        assertEquals(listOf(CLOUD_GEMINI), tiers(p))
    }

    @Test
    fun `estimated cost uses the shared pricing`() {
        val c = trivial
        val expected = CloudPricing.usd(c.features.estimatedTokens, RoutePlanner.expectedOutputTokens(c.complexity, RouterSettings()))
        assertEquals(expected, RoutePlanner.estimatedCloudCostUsd(c, RouterSettings()), 1e-12)
    }

    // ---- latency ------------------------------------------------------------------------------------------

    @Test
    fun `latency aware puts the fast local engine first for a short answer`() {
        val p = plan(RoutingPolicy.LATENCY_AWARE, trivial)
        assertEquals(ML_KIT_NANO, p.engines.first().tier)
    }

    @Test
    fun `latency aware prefers the cloud for a long answer on slow local engines`() {
        val p = plan(RoutingPolicy.LATENCY_AWARE, complexPrompt, usable = listOf(MEDIAPIPE_LOCAL, LITERT_LM_LOCAL, CLOUD_GEMINI))
        assertEquals(CLOUD_GEMINI, p.engines.first().tier)
    }

    @Test
    fun `observed latency overrides the static priors`() {
        val p = plan(RoutingPolicy.LATENCY_AWARE, trivial, observed = mapOf(ML_KIT_NANO to 9_000L, CLOUD_GEMINI to 300L))
        assertEquals(CLOUD_GEMINI, p.engines.first().tier)
        assertTrue(p.engines.first().reason.contains("observada"))
    }

    // ---- manual -------------------------------------------------------------------------------------------

    @Test
    fun `manual runs exactly the chosen engine`() {
        val p = plan(RoutingPolicy.MANUAL, manual = FIREBASE_AI)
        assertEquals(listOf(FIREBASE_AI), tiers(p))
        assertTrue(p.engines.single().viaGateway)
    }

    @Test
    fun `manual with an unavailable or missing engine is blocked`() {
        assertTrue(plan(RoutingPolicy.MANUAL, usable = listOf(ML_KIT_NANO), manual = CLOUD_GEMINI).blocked)
        assertTrue(plan(RoutingPolicy.MANUAL, manual = null).blocked)
    }

    // ---- capability gaps ----------------------------------------------------------------------------------

    @Test
    fun `fresh facts move the cloud ahead of the local engines`() {
        val p = plan(RoutingPolicy.LOCAL_FIRST, fresh)
        assertEquals(CLOUD_GEMINI, p.engines.first().tier)
        assertTrue(p.notes.any { it.contains("recientes") })
    }

    @Test
    fun `fresh facts never push a sensitive prompt to the cloud under the privacy policy`() {
        val c = PromptClassifier.classify("¿Cuál es el precio actual del bitcoin? Mi correo es ana@example.com")
        assertTrue(c.needsFreshFacts && c.sensitive)
        assertTrue(plan(RoutingPolicy.PRIVACY_FIRST, c).engines.none { it.tier.isCloud })
    }

    @Test
    fun `a prompt too long for the 1k engine skips it but keeps the others`() {
        val long = PromptClassifier.classify("texto ".repeat(900))
        val p = plan(RoutingPolicy.LOCAL_FIRST, long, usable = listOf(MEDIAPIPE_LOCAL, ML_KIT_NANO, CLOUD_GEMINI))
        assertFalse(MEDIAPIPE_LOCAL in tiers(p))
        assertEquals(ML_KIT_NANO, p.engines.first().tier)
        assertTrue(p.notes.any { it.contains("MEDIAPIPE_LOCAL") })
    }

    @Test
    fun `describe lists the route or the block reason`() {
        assertEquals("ML_KIT_NANO → LITERT_LM_LOCAL → CLOUD_GEMINI", plan(RoutingPolicy.LOCAL_FIRST).describe())
        assertTrue(plan(RoutingPolicy.PRIVACY_FIRST, pii, usable = listOf(CLOUD_GEMINI)).describe().startsWith("bloqueada"))
    }

    @Test
    fun `block reason is null for a runnable plan`() {
        assertNull(plan(RoutingPolicy.LOCAL_FIRST).blockedReason)
    }
}

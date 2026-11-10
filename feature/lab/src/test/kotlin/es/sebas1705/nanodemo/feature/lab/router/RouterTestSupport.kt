package es.sebas1705.nanodemo.feature.lab.router

import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.feature.lab.rag.XmlTexts

/**
 * The texts the router tests run with: English, read from `values/strings_router.xml`.
 *
 * The extensions below let a test call the pure logic the way it did before the texts were injected; the member
 * functions need a [RouterTexts], so these only apply to calls that do not pass one.
 */
val testTexts: RouterTexts = XmlTexts.english

fun PromptClassifier.classify(text: String, markedSensitive: Boolean = false): Classification =
    classify(text, markedSensitive, testTexts)

fun PromptClassifier.merge(heuristic: Classification, llm: LlmClassification): Classification =
    merge(heuristic, llm, testTexts)

fun Repetition.describe(text: String): String? = describe(text, testTexts)

fun Classification.summary(): String = summary(testTexts)

fun RoutePlan.describe(): String = describe(testTexts)

fun RoutePlanner.plan(
    policy: RoutingPolicy,
    classification: Classification,
    usable: List<InferenceTier>,
    settings: RouterSettings = RouterSettings(),
    manualTier: InferenceTier? = null,
    observedLatencyMs: Map<InferenceTier, Long> = emptyMap(),
): RoutePlan = plan(policy, classification, usable, testTexts, settings, manualTier, observedLatencyMs)

fun GateResult.summary(): String = summary(testTexts)

package es.sebas1705.nanodemo.data.repositories.repos

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.domain.models.GenerationConfig
import es.sebas1705.nanodemo.domain.models.InferenceTier
import es.sebas1705.nanodemo.domain.models.LiteRtLmAccelerator
import es.sebas1705.nanodemo.domain.models.VoteTally
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS = "app_settings"
private const val PREFERRED_TIER_KEY = "preferred_tier"
private const val LITERTLM_ACCELERATOR_KEY = "litertlm_accelerator"

/**
 * Traduce un [GenerationConfig] a pares clave/valor planos (y de vuelta), para guardarlo en
 * `SharedPreferences` sin depender de `org.json` (que en tests unitarios JVM es solo un stub). Un campo
 * `null` simplemente no se escribe: "sin valor" y "valor por defecto" son cosas distintas (p.ej.
 * `topK = null` significa "este tier no lo usa", no "40"), así que no se puede reconstruir desde los defaults.
 */
internal object GenerationConfigCodec {
    private const val TEMPERATURE = "temperature"
    private const val TOP_K = "topK"
    private const val TOP_P = "topP"
    private const val MAX_OUTPUT_TOKENS = "maxOutputTokens"
    private const val SYSTEM_PROMPT = "systemPrompt"
    private const val JSON_MODE = "jsonMode"
    private const val FUNCTION_CALLING = "functionCalling"
    private const val WEB_SEARCH = "webSearch"

    fun toMap(config: GenerationConfig): Map<String, Any> = buildMap {
        config.temperature?.let { put(TEMPERATURE, it) }
        config.topK?.let { put(TOP_K, it) }
        config.topP?.let { put(TOP_P, it) }
        config.maxOutputTokens?.let { put(MAX_OUTPUT_TOKENS, it) }
        config.systemPrompt?.let { put(SYSTEM_PROMPT, it) }
        put(JSON_MODE, config.jsonMode)
        put(FUNCTION_CALLING, config.functionCalling)
        put(WEB_SEARCH, config.webSearch)
    }

    /** Un tipo inesperado en un campo (preferencias corruptas o de otra versión) se trata como ausente. */
    fun fromMap(map: Map<String, *>): GenerationConfig = GenerationConfig(
        temperature = map[TEMPERATURE] as? Float,
        topK = map[TOP_K] as? Int,
        topP = map[TOP_P] as? Float,
        maxOutputTokens = map[MAX_OUTPUT_TOKENS] as? Int,
        systemPrompt = map[SYSTEM_PROMPT] as? String,
        jsonMode = map[JSON_MODE] as? Boolean ?: false,
        functionCalling = map[FUNCTION_CALLING] as? Boolean ?: false,
        webSearch = map[WEB_SEARCH] as? Boolean ?: false,
    )
}

/**
 * Ajustes que sobreviven al cierre de la app: la configuración de cada tier y el último tier que el
 * usuario eligió a mano. Antes todo vivía en memoria (`InferenceRepository`) y se reseteaba en cada arranque.
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun prefix(tier: InferenceTier) = "config.${tier.name}."

    /** Un tier sin nada guardado arranca con los valores por defecto. */
    fun loadConfigs(): Map<InferenceTier, GenerationConfig> {
        val all = prefs.all
        return InferenceTier.entries.associateWith { tier ->
            val saved = all.filterKeys { it.startsWith(prefix(tier)) }.mapKeys { it.key.removePrefix(prefix(tier)) }
            if (saved.isEmpty()) GenerationConfig() else GenerationConfigCodec.fromMap(saved)
        }
    }

    fun saveConfig(tier: InferenceTier, config: GenerationConfig) {
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(prefix(tier)) }.forEach(::remove)
            GenerationConfigCodec.toMap(config).forEach { (key, value) ->
                val full = prefix(tier) + key
                when (value) {
                    is Float -> putFloat(full, value)
                    is Int -> putInt(full, value)
                    is Boolean -> putBoolean(full, value)
                    is String -> putString(full, value)
                }
            }
        }.apply()
    }

    /** `null` si el usuario nunca eligió uno a mano (o el guardado ya no existe como tier). */
    fun loadPreferredTier(): InferenceTier? =
        prefs.getString(PREFERRED_TIER_KEY, null)?.let { name -> InferenceTier.entries.firstOrNull { it.name == name } }

    fun loadVotes(): Map<InferenceTier, VoteTally> = prefs.loadVotes()

    fun saveVotes(votes: Map<InferenceTier, VoteTally>) {
        prefs.edit().apply {
            votes.forEach { (tier, tally) ->
                putInt("${VOTES_PREFIX}${tier.name}.wins", tally.wins)
                putInt("${VOTES_PREFIX}${tier.name}.appearances", tally.appearances)
            }
        }.apply()
    }

    fun clearVotes() {
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith(VOTES_PREFIX) }.forEach(::remove) }.apply()
    }

    fun loadLiteRtLmAccelerator(): LiteRtLmAccelerator =
        prefs.getString(LITERTLM_ACCELERATOR_KEY, null)
            ?.let { name -> LiteRtLmAccelerator.entries.firstOrNull { it.name == name } }
            ?: LiteRtLmAccelerator.CPU

    fun saveLiteRtLmAccelerator(option: LiteRtLmAccelerator) {
        prefs.edit().putString(LITERTLM_ACCELERATOR_KEY, option.name).apply()
    }

    fun savePreferredTier(tier: InferenceTier) {
        prefs.edit().putString(PREFERRED_TIER_KEY, tier.name).apply()
    }
}

/**
 * Orden en que el arranque prueba los tiers: primero el que el usuario eligió a mano la última vez (si
 * sigue disponible se queda ahí, sin esperar a los de antes), y después el orden de siempre A → E como
 * respaldo si ese ya no anda (modelo borrado, sin red, etc.).
 */
internal fun tierProbeOrder(preferred: InferenceTier?): List<InferenceTier> =
    if (preferred == null) InferenceTier.entries else listOf(preferred) + InferenceTier.entries.filter { it != preferred }

private const val VOTES_PREFIX = "votes."

/** Votos de Comparar (ver [VoteTally]): contadores simples por motor, `votes.<TIER>.wins` / `.appearances`. */
internal fun SharedPreferences.loadVotes(): Map<InferenceTier, VoteTally> =
    InferenceTier.entries.associateWith { tier ->
        VoteTally(
            wins = getInt("$VOTES_PREFIX${tier.name}.wins", 0),
            appearances = getInt("$VOTES_PREFIX${tier.name}.appearances", 0),
        )
    }

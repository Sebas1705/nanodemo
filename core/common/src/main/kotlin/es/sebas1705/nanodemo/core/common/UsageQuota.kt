package es.sebas1705.nanodemo.core.common

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class QuotaPeriod { DAY, MONTH }

/**
 * Servicios con cuota que la app puede gastar sin querer (los tiers locales no tienen límite).
 *
 * Los `defaultLimit` son un punto de partida, NO un dato garantizado: Google no publica el tope exacto
 * del plan gratis de Gemini (se ve en https://aistudio.google.com/rate-limit y cambia por modelo/cuenta;
 * a 09/2026 fuentes externas hablan de ~20 pedidos/día para Gemini 3.5 Flash) y Brave da $5 de crédito
 * mensual (~1000 búsquedas, verificado 09/2026). Por eso el límite es editable en Ajustes.
 */
enum class QuotaService(val period: QuotaPeriod, val defaultLimit: Int) {
    GEMINI_CLOUD(QuotaPeriod.DAY, 20),
    GEMINI_FIREBASE(QuotaPeriod.DAY, 20),
    BRAVE_SEARCH(QuotaPeriod.MONTH, 1000),
}

data class QuotaState(
    val service: QuotaService,
    val used: Int,
    val limit: Int,
    /** `true` = al llegar al límite se frena el servicio (en vez de solo avisar). */
    val enforced: Boolean,
) {
    val fraction: Float get() = if (limit <= 0) 1f else (used.toFloat() / limit).coerceIn(0f, 1f)
    val reached: Boolean get() = used >= limit
    val nearLimit: Boolean get() = fraction >= 0.8f
}

/**
 * Contador local de uso contra los límites gratis. Se persiste en SharedPreferences y se reinicia solo al
 * cambiar el día/mes (el día de Gemini se mide en hora del Pacífico, como reinicia Google sus cuotas; el mes,
 * en calendario local). Es solo del lado de la app — no sabe lo que gastaste desde otro dispositivo o
 * key, ni reemplaza los topes de gasto de las consolas.
 *
 * Es un `object` (no un servicio inyectado) porque lo consultan módulos independientes que no comparten
 * grafo de Hilt: los backends de `data:*` y `WebSearchTool`. Antes de [init] todo está permitido.
 */
object UsageQuota {
    private const val PREFS = "usage_quota"
    private val DAY_KEY = DateTimeFormatter.ISO_LOCAL_DATE
    private val MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM")
    private val PACIFIC: ZoneId = ZoneId.of("America/Los_Angeles")

    private var prefs: SharedPreferences? = null
    private val _states = MutableStateFlow<Map<QuotaService, QuotaState>>(emptyMap())
    val states: StateFlow<Map<QuotaService, QuotaState>> = _states.asStateFlow()

    fun init(context: Context) {
        synchronized(this) {
            if (prefs != null) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            publish()
        }
    }

    private fun periodKey(service: QuotaService): String {
        // Las cuotas diarias de Gemini se reinician a medianoche del Pacífico (no a la del dispositivo).
        val today = LocalDate.now(if (service.period == QuotaPeriod.DAY) PACIFIC else ZoneId.systemDefault())
        return when (service.period) {
            QuotaPeriod.DAY -> today.format(DAY_KEY)
            QuotaPeriod.MONTH -> today.format(MONTH_KEY)
        }
    }

    private fun stateOf(service: QuotaService): QuotaState {
        val p = prefs ?: return QuotaState(service, 0, service.defaultLimit, enforced = false)
        val sameEra = p.getString("${service.name}.era", null) == periodKey(service)
        return QuotaState(
            service = service,
            used = if (sameEra) p.getInt("${service.name}.count", 0) else 0,
            limit = p.getInt("${service.name}.limit", service.defaultLimit),
            enforced = p.getBoolean("${service.name}.enforced", true),
        )
    }

    private fun publish() {
        _states.value = QuotaService.entries.associateWith(::stateOf)
    }

    /** Vuelve a leer todo (p.ej. si cruzó medianoche con la app abierta). */
    fun refresh() = synchronized(this) { publish() }

    /** `true` si se puede usar el servicio: no hay límite alcanzado, o el bloqueo está desactivado. */
    fun canUse(service: QuotaService): Boolean = synchronized(this) {
        val state = stateOf(service)
        !(state.enforced && state.reached)
    }

    fun record(service: QuotaService, count: Int = 1) {
        synchronized(this) {
            val p = prefs ?: return
            val current = stateOf(service)
            p.edit()
                .putString("${service.name}.era", periodKey(service))
                .putInt("${service.name}.count", current.used + count)
                .apply()
            publish()
        }
    }

    fun setLimit(service: QuotaService, limit: Int) = synchronized(this) {
        prefs?.edit()?.putInt("${service.name}.limit", limit.coerceAtLeast(1))?.apply()
        publish()
    }

    fun setEnforced(service: QuotaService, enforced: Boolean) = synchronized(this) {
        prefs?.edit()?.putBoolean("${service.name}.enforced", enforced)?.apply()
        publish()
    }

    fun reset(service: QuotaService) = synchronized(this) {
        prefs?.edit()
            ?.putString("${service.name}.era", periodKey(service))
            ?.putInt("${service.name}.count", 0)
            ?.apply()
        publish()
    }

    /** Mensaje para mostrarle al usuario (o al modelo, en una herramienta) cuando se frenó un servicio. */
    fun blockedMessage(service: QuotaService, texts: AppTexts): String {
        val state = stateOf(service)
        return texts.quotaBlocked(service, state.used, state.limit)
    }
}

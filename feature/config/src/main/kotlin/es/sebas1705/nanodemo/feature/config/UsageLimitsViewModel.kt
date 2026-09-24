package es.sebas1705.nanodemo.feature.config

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import es.sebas1705.nanodemo.core.common.QuotaService
import es.sebas1705.nanodemo.core.common.QuotaState
import es.sebas1705.nanodemo.core.common.UsageQuota
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class UsageLimitsViewModel @Inject constructor() : ViewModel() {
    val states: StateFlow<Map<QuotaService, QuotaState>> = UsageQuota.states

    /** Por si cruzó medianoche (o fin de mes) con la app abierta: el contador ya debería estar en 0. */
    fun refresh() = UsageQuota.refresh()
    fun setLimit(service: QuotaService, limit: Int) = UsageQuota.setLimit(service, limit)
    fun setEnforced(service: QuotaService, enforced: Boolean) = UsageQuota.setEnforced(service, enforced)
    fun reset(service: QuotaService) = UsageQuota.reset(service)
}

package es.sebas1705.nanodemo.feature.lab.common

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import es.sebas1705.nanodemo.feature.lab.R
import es.sebas1705.nanodemo.feature.lab.bench.BenchTexts
import es.sebas1705.nanodemo.feature.lab.trace.RetryTexts
import javax.inject.Inject

/**
 * String resource for an Android thermal status (`PowerManager.THERMAL_STATUS_*`), mirroring
 * `core:common`'s Spanish-only `thermalStatusLabel` but following the app language.
 */
@StringRes
fun thermalStatusLabelRes(status: Int?): Int = when (status) {
    0 -> R.string.lab_common_thermal_normal
    1 -> R.string.lab_common_thermal_light
    2 -> R.string.lab_common_thermal_moderate
    3 -> R.string.lab_common_thermal_severe
    4 -> R.string.lab_common_thermal_critical
    5 -> R.string.lab_common_thermal_emergency
    6 -> R.string.lab_common_thermal_shutdown
    else -> R.string.lab_common_thermal_unknown
}

/**
 * The Android implementation of the Lab's non-composable texts: logic that runs outside Compose (the rate-limit
 * retry, the benchmark runner, view model messages) cannot call `stringResource`, so it asks the small interfaces
 * it owns ([RetryTexts], [BenchTexts]) and this class answers them from string resources. Resolved on every call
 * so a language change with the app open is followed. Tests use the English defaults of those interfaces.
 */
class LabTexts @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RetryTexts, BenchTexts {

    override fun rateLimitWait(seconds: Long, attempt: Int, maxAttempts: Int): String =
        context.getString(R.string.trace_rate_limit_wait, seconds, attempt, maxAttempts)

    override fun timeout(seconds: Long): String = context.getString(R.string.bench_error_timeout, seconds)

    override fun unknownError(): String = context.getString(R.string.bench_error_unknown)

    override fun defaultSessionLabel(date: String): String = context.getString(R.string.bench_session_default_label, date)

    /** Title of the sandbox's single trace step. */
    fun sandboxAnswerTitle(): String = context.getString(R.string.sandbox_step_answer)
}

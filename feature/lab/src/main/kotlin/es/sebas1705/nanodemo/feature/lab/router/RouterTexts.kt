package es.sebas1705.nanodemo.feature.lab.router

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * The user-visible texts the router logic produces outside composables (classification reasons, route notes,
 * quality-check details, trace steps, errors). The logic stays pure: it asks this interface for a text by
 * resource id instead of owning a language. The Android implementation is [AndroidRouterTexts]; the unit tests
 * plug in an implementation that reads the English `values/strings_router.xml`, so they assert on the same text
 * the app shows.
 *
 * Numbers that must keep a fixed format (decimals, percentages, money) are formatted by the caller and passed as `%s`.
 */
interface RouterTexts {
    fun string(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any): String
}

/** [RouterTexts] backed by Android resources, so the texts follow the device language. */
class AndroidRouterTexts(private val resources: Resources) : RouterTexts {
    override fun string(id: Int, vararg args: Any): String = resources.getString(id, *args)
    override fun plural(id: Int, quantity: Int, vararg args: Any): String = resources.getQuantityString(id, quantity, *args)
}

/** The texts for composables that need a computed string from the logic layer; rebuilt when the configuration changes. */
@Composable
internal fun rememberRouterTexts(): RouterTexts {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AndroidRouterTexts(context.resources) }
}

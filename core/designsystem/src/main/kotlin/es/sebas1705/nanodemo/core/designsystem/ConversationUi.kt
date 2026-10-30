package es.sebas1705.nanodemo.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import es.sebas1705.nanodemo.domain.models.Conversation

/** El título guardado o, si no tiene uno propio, el título por defecto en el idioma de la UI. */
@Composable
fun Conversation.displayTitle(): String =
    if (isUntitled) stringResource(R.string.conversation_untitled) else title

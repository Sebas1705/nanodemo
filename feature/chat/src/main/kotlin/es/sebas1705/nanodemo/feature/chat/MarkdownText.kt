package es.sebas1705.nanodemo.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import es.sebas1705.nanodemo.core.ui.DemoColors

/** Bloques que reconoce el renderer — un subconjunto de Markdown, el que suelen producir los LLMs en chat. */
internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
}

private val HEADING = Regex("""^(#{1,3})\s+(.*)$""")
private val BULLET = Regex("""^\s*[-*•]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")

/**
 * Parseo línea a línea, tolerante a texto a medio escribir: un bloque ``` sin cerrar (el modelo sigue
 * generando) se emite igual como código hasta donde llegó, en vez de esperar al cierre.
 */
internal fun parseMarkdown(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    var codeLanguage: String? = null
    val code = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
        paragraph.clear()
    }

    for (line in text.lines()) {
        val trimmed = line.trim()
        if (codeLanguage != null) {
            if (trimmed.startsWith("```")) {
                blocks += MdBlock.Code(codeLanguage, code.joinToString("\n"))
                code.clear()
                codeLanguage = null
            } else {
                code += line
            }
            continue
        }
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                codeLanguage = trimmed.removePrefix("```").trim()
            }
            trimmed.isEmpty() -> flushParagraph()
            else -> {
                val heading = HEADING.matchEntire(trimmed)
                val bullet = BULLET.matchEntire(line)
                val numbered = NUMBERED.matchEntire(line)
                when {
                    heading != null -> {
                        flushParagraph()
                        blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
                    }
                    bullet != null -> {
                        flushParagraph()
                        blocks += MdBlock.ListItem("•", bullet.groupValues[1])
                    }
                    numbered != null -> {
                        flushParagraph()
                        blocks += MdBlock.ListItem("${numbered.groupValues[1]}.", numbered.groupValues[2])
                    }
                    else -> paragraph += line
                }
            }
        }
    }
    codeLanguage?.let { blocks += MdBlock.Code(it, code.joinToString("\n")) }
    flushParagraph()
    return blocks
}

private val INLINE = Regex(
    """\*\*(.+?)\*\*|`([^`\n]+)`|(?<![*\w])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![*\w])|\[([^\]\n]+)]\(([^)\s]+)\)""",
)

/** Negrita, cursiva, código en línea y links (solo estilo: no son clickeables). */
internal fun inlineMarkdown(text: String, linkColor: Color = Color(0xFF5CC7F2)): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        for (match in INLINE.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            val (bold, code, italic, linkLabel) = match.destructured
            when {
                bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                code.isNotEmpty() -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.White.copy(alpha = 0.10f),
                        fontSize = 0.92f.em,
                    ),
                ) { append(code) }
                italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                linkLabel.isNotEmpty() -> withStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ) { append(linkLabel) }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }

@Composable
internal fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = DemoColors.Ink) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    inlineMarkdown(block.text),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    color = color,
                )
                is MdBlock.Heading -> Text(
                    inlineMarkdown(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Default)
                    }.copy(fontWeight = FontWeight.Bold),
                    color = color,
                )
                is MdBlock.ListItem -> Row {
                    Text(
                        block.marker,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DemoColors.Muted,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdBlock.Code -> CodeBlock(block)
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090D0B), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                block.language.ifBlank { "código" },
                style = MaterialTheme.typography.labelSmall,
                color = DemoColors.Muted,
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copiar código",
                    tint = DemoColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            block.code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            color = DemoColors.Ink,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}

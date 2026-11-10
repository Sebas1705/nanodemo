package es.sebas1705.nanodemo.feature.lab.rag

import java.util.UUID

/** Pure helpers for turning pasted or file text into a document. */
object TextImport {

    /** Longest text kept per document; more would make indexing slow and the JSON file huge. */
    const val MAX_CHARS = 200_000

    /** Result of cleaning raw text: the text to index and whether it was cut. */
    class Cleaned(val text: String, val truncated: Boolean)

    /**
     * Normalises line endings, drops a BOM and trims. Returns null when nothing usable is left or the content
     * looks binary (a NUL character), so a picked PDF or image is rejected instead of indexed as garbage.
     */
    fun clean(raw: String): Cleaned? {
        if (raw.indexOf('\u0000') >= 0) return null
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return null
        return if (text.length > MAX_CHARS) Cleaned(text.take(MAX_CHARS), truncated = true) else Cleaned(text, truncated = false)
    }

    /** `notes.v2.md` -> `notes.v2`; falls back to `fallback` for an empty name. */
    fun titleFromFileName(name: String?, fallback: String = "Documento"): String {
        val trimmed = name?.trim().orEmpty()
        val base = trimmed.substringBeforeLast('.', missingDelimiterValue = trimmed)
        return base.ifBlank { fallback }
    }

    fun newDocumentId(): String = "doc-" + UUID.randomUUID().toString().take(8)

    fun newDocument(title: String, text: String, origin: DocumentOrigin, now: Long): RagDocument =
        RagDocument(id = newDocumentId(), title = title.trim().ifBlank { "Documento" }, text = text, origin = origin, addedAtMs = now)
}

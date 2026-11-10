package es.sebas1705.nanodemo.feature.lab.rag

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

/** Reads a file picked with the Storage Access Framework. Android-only, so not covered by JVM tests. */
class DocumentFileReader(private val resolver: ContentResolver) {

    class Picked(val title: String, val cleaned: TextImport.Cleaned)

    /** Null when the file cannot be read or does not look like text. */
    fun read(uri: Uri): Picked? {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val raw = resolver.openInputStream(uri)?.use { input ->
            // Read a little past the limit (UTF-8 is up to 4 bytes per char) so `clean` can tell the file was cut,
            // without loading a huge file whole.
            val limit = TextImport.MAX_CHARS * 4 + 4
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (total < limit) {
                val read = input.read(buffer, 0, minOf(buffer.size, limit - total))
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
            }
            String(out.toByteArray(), Charsets.UTF_8)
        } ?: return null
        val cleaned = TextImport.clean(raw) ?: return null
        return Picked(TextImport.titleFromFileName(name), cleaned)
    }
}

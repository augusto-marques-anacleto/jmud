package br.com.augusto.jmud.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class ShareManager(private val context: Context) {

    fun buildShareIntent(baseName: String, content: String, subject: String, description: String): Intent? {
        val uri = writeShareFile(baseName, content) ?: return null
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = ShareFormat.MIME_TYPE
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TITLE, subject)
        intent.putExtra(Intent.EXTRA_TEXT, description)
        intent.clipData = ClipData.newRawUri(subject, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MAX_IMPORT_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            if (total > MAX_IMPORT_BYTES) null else String(buffer, 0, total, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private fun writeShareFile(baseName: String, content: String): Uri? = try {
        val dir = File(context.cacheDir, SHARE_DIR)
        dir.mkdirs()
        cleanupOldFiles(dir)
        val file = File(dir, sanitizeFileName(baseName) + "." + ShareFormat.FILE_EXTENSION)
        file.writeText(content, Charsets.UTF_8)
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    } catch (e: Exception) {
        null
    }

    private fun cleanupOldFiles(dir: File) {
        val limit = System.currentTimeMillis() - MAX_FILE_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < limit) {
                file.delete()
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
        val limited = if (cleaned.length > MAX_NAME_LENGTH) cleaned.substring(0, MAX_NAME_LENGTH) else cleaned
        return limited.ifBlank { "jmud" }
    }

    private companion object {
        const val SHARE_DIR = "share"
        const val MAX_IMPORT_BYTES = 4 * 1024 * 1024
        const val MAX_FILE_AGE_MS = 60L * 60L * 1000L
        const val MAX_NAME_LENGTH = 48
    }
}

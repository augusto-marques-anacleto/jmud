package br.com.augusto.jmud.util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Collections

enum class SoundPackStep { DOWNLOADING, EXTRACTING }

data class ExtractResult(val keptExisting: Int)

data class SoundPackProgress(
    val step: SoundPackStep,
    val percent: Int,
    val totalKnown: Boolean = true,
    val downloadedMb: Float = 0f,
    val speedMbps: Float = 0f,
    val etaSeconds: Int = 0,
    val currentFile: Int = 0,
    val totalFiles: Int = 0
)

class SoundPackInstaller {

    init {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(CookieManager())
        }
    }

    fun cleanUrl(text: String): String {
        val start = text.indexOf("http")
        if (start == -1) return ""
        val sb = StringBuilder()
        for (c in text.substring(start)) {
            if (c == ' ' || c == '<' || c == '>' || c == '"' || c == '\'') break
            if (c != '\n' && c != '\r' && c != '\t') sb.append(c)
        }
        return sb.toString()
    }

    fun extractDriveId(url: String): String? {
        Regex("/file/d/([a-zA-Z0-9_-]+)").find(url)?.let { return it.groupValues[1] }
        Regex("[?&]id=([a-zA-Z0-9_-]+)").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        return conn
    }

    private fun isHtml(conn: HttpURLConnection): Boolean =
        conn.contentType?.contains("text/html") == true

    private fun resolveConnection(rawUrl: String): HttpURLConnection? {
        var url = cleanUrl(rawUrl)
        if (url.isEmpty()) return null

        if (url.contains("drive.usercontent.google.com") || url.contains("confirm=")) {
            return open(url)
        }

        val driveId = extractDriveId(url)
        if (driveId != null) {
            val downloadUrl = "https://drive.google.com/uc?export=download&id=$driveId"
            var conn = open(downloadUrl)
            if (isHtml(conn)) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val formMatch = Regex(
                    "(<form[^>]*id=[\"']download-form[\"'][\\s\\S]*?</form>)",
                    RegexOption.IGNORE_CASE
                ).find(body)
                conn = if (formMatch != null) {
                    val form = formMatch.groupValues[1]
                    var action = Regex("action=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                        .find(form)?.groupValues?.get(1) ?: downloadUrl
                    if (action.startsWith("/")) {
                        action = "https://drive.google.com$action"
                    }
                    val query = Regex("<input[^>]+>", RegexOption.IGNORE_CASE)
                        .findAll(form)
                        .mapNotNull { tag ->
                            val name = Regex("name=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                                .find(tag.value)?.groupValues?.get(1) ?: return@mapNotNull null
                            val value = Regex("value=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                                .find(tag.value)?.groupValues?.get(1) ?: ""
                            URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
                        }
                        .joinToString("&")
                    val separator = if (action.contains("?")) "&" else "?"
                    open(if (query.isEmpty()) action else action + separator + query)
                } else {
                    val token = Regex("confirm=([a-zA-Z0-9_-]+)").find(body)?.groupValues?.get(1)
                    if (token != null) {
                        open("$downloadUrl&confirm=$token")
                    } else {
                        open(downloadUrl)
                    }
                }
            }
            return conn
        }

        if (url.contains("dropbox.com")) {
            url = url.replace("dl=0", "dl=1")
        }
        return open(url)
    }

    suspend fun download(
        rawUrl: String,
        destination: File,
        onProgress: (SoundPackProgress) -> Unit
    ): Boolean {
        val conn = resolveConnection(rawUrl) ?: return false
        try {
            if (conn.responseCode >= 400) {
                throw IOException("HTTP " + conn.responseCode)
            }
            if (isHtml(conn)) {
                throw IOException("HTML")
            }

            val total = conn.contentLengthLong
            var downloaded = 0L
            val start = System.currentTimeMillis()
            var lastUi = 0L

            conn.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastUi >= 1000 || (total > 0 && downloaded == total)) {
                            lastUi = now
                            val elapsed = (now - start) / 1000f
                            val speedMbps = if (elapsed > 0) {
                                downloaded / elapsed / (1024f * 1024f)
                            } else {
                                0f
                            }
                            if (total > 0) {
                                val remaining = total - downloaded
                                val etaSeconds = if (speedMbps > 0f) {
                                    (remaining / (speedMbps * 1024f * 1024f)).toInt()
                                } else {
                                    0
                                }
                                onProgress(
                                    SoundPackProgress(
                                        step = SoundPackStep.DOWNLOADING,
                                        percent = (downloaded * 100 / total).toInt(),
                                        totalKnown = true,
                                        downloadedMb = downloaded / 1048576f,
                                        speedMbps = speedMbps,
                                        etaSeconds = etaSeconds
                                    )
                                )
                            } else {
                                onProgress(
                                    SoundPackProgress(
                                        step = SoundPackStep.DOWNLOADING,
                                        percent = 0,
                                        totalKnown = false,
                                        downloadedMb = downloaded / 1048576f,
                                        speedMbps = speedMbps
                                    )
                                )
                            }
                        }
                    }
                }
            }
            return true
        } finally {
            conn.disconnect()
        }
    }

    suspend fun extractAndCopy(
        zip: File,
        targetDir: File,
        onProgress: (SoundPackProgress) -> Unit
    ): ExtractResult? {
        val encoding = if (Charset.isSupported("CP437")) "CP437" else "ISO-8859-1"
        var kept = 0
        ZipFile(zip, encoding).use { zipFile ->
            val entries = Collections.list(zipFile.entries).filter { !it.isDirectory }
            val total = entries.size
            if (total == 0) return null

            val names = entries.map { fixEntryName(it).replace('\\', '/') }
            val rootPrefix = commonRootPrefix(names)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val buffer = ByteArray(256 * 1024)
            var lastUi = 0L
            entries.forEachIndexed { index, entry ->
                currentCoroutineContext().ensureActive()
                val name = names[index].removePrefix(rootPrefix)
                if (name.isNotBlank() && name.split('/').none { it == ".." }) {
                    val outFile = File(targetDir, name)
                    outFile.parentFile?.mkdirs()
                    if (!writeEntry(zipFile, entry, outFile, buffer)) {
                        kept++
                    }
                }
                val now = System.currentTimeMillis()
                if (now - lastUi >= 500 || index == total - 1) {
                    lastUi = now
                    onProgress(
                        SoundPackProgress(
                            step = SoundPackStep.EXTRACTING,
                            percent = (index + 1) * 100 / total,
                            currentFile = index + 1,
                            totalFiles = total
                        )
                    )
                }
            }
        }
        return ExtractResult(kept)
    }

    private fun writeEntry(
        zipFile: ZipFile,
        entry: ZipArchiveEntry,
        outFile: File,
        buffer: ByteArray
    ): Boolean {
        repeat(2) { attempt ->
            try {
                zipFile.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                return true
            } catch (e: IOException) {
                if (!outFile.exists()) throw e
                if (attempt == 0 && outFile.delete()) return@repeat
                return false
            }
        }
        return false
    }

    private fun commonRootPrefix(names: List<String>): String {
        val firstSegments = names.map { it.substringBefore('/', "") }.distinct()
        return if (firstSegments.size == 1 && firstSegments[0].isNotEmpty()) {
            firstSegments[0] + "/"
        } else {
            ""
        }
    }

    private fun fixEntryName(entry: ZipArchiveEntry): String {
        if (entry.generalPurposeBit.usesUTF8ForNames()) {
            return entry.name
        }
        val raw = entry.rawName ?: return entry.name
        decodeStrict(raw, Charsets.UTF_8)?.let { return it }
        if (Charset.isSupported("CP850")) {
            decodeStrict(raw, Charset.forName("CP850"))?.let { return it }
        }
        return entry.name
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: Exception) {
        null
    }
}

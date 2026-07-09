package br.com.augusto.jmud.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import br.com.augusto.jmud.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val changelog: String
)

class UpdateManager(private val context: Context) {

    fun checkForUpdate(): UpdateInfo? {
        return try {
            val conn = open(UPDATE_JSON_URL)
            try {
                if (conn.responseCode >= 400) return null
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.getString("versionName"),
                    url = json.getString("url"),
                    changelog = json.optString("changelog", "")
                )
                if (info.versionCode > BuildConfig.VERSION_CODE && info.url.isNotBlank()) {
                    info
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit): File? {
        val destination = File(context.getExternalFilesDir(null) ?: context.filesDir, "update.apk")
        return try {
            val conn = open(url)
            try {
                if (conn.responseCode >= 400) return null
                val total = conn.contentLengthLong
                var downloaded = 0L
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
                            if (total > 0 && (now - lastUi >= 1000 || downloaded == total)) {
                                lastUi = now
                                onProgress((downloaded * 100 / total).toInt())
                            }
                        }
                    }
                }
                destination
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            destination.delete()
            null
        }
    }

    fun installApk(file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json, */*")
        conn.setRequestProperty("User-Agent", "jMud/${BuildConfig.VERSION_NAME}")
        return conn
    }

    companion object {
        const val UPDATE_JSON_URL =
            "https://raw.githubusercontent.com/augusto-marques-anacleto/jmud/main/update.json"
    }
}

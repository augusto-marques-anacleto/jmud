package br.com.augusto.jmud.util

import android.content.Context
import br.com.augusto.jmud.R
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class LogManager(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val sessionFormat = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var writer: BufferedWriter? = null

    @Volatile
    private var sessionActive = false

    fun startSession(characterName: String, host: String = "") {
        val baseName = characterName.trim().ifBlank { context.getString(R.string.log_default_session_name) }
        val hostPart = host.trim()
        val folderName = if (hostPart.isBlank()) baseName else "$baseName ($hostPart)"
        val safeName = folderName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val startedMarker = context.getString(R.string.log_session_started)
        sessionActive = true
        executor.execute {
            closeWriter()
            try {
                val characterDir = File(AppStorage.logsDir(context), safeName)
                if (!characterDir.exists()) {
                    characterDir.mkdirs()
                }
                val file = File(characterDir, "$safeName ${sessionFormat.format(Date())}.txt")
                writer = BufferedWriter(FileWriter(file, true))
            } catch (e: Exception) {
                writer = null
            }
            write(startedMarker)
        }
    }

    fun endSession() {
        if (!sessionActive) return
        sessionActive = false
        val endedMarker = context.getString(R.string.log_session_ended)
        executor.execute {
            write(endedMarker)
            closeWriter()
        }
    }

    fun logIncoming(text: String) {
        append(text)
    }

    fun logOutgoing(command: String) {
        append(command)
    }

    private fun append(line: String) {
        if (!sessionActive) return
        executor.execute {
            write(line)
        }
    }

    private fun write(line: String) {
        val currentWriter = writer ?: return
        try {
            currentWriter.write("[${timeFormat.format(Date())}] $line")
            currentWriter.newLine()
            currentWriter.flush()
        } catch (e: Exception) {
        }
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (e: Exception) {
        } finally {
            writer = null
        }
    }

    fun cleanupOldLogs(retentionDays: Int) {
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        executor.execute {
            try {
                AppStorage.logsDir(context).listFiles()?.forEach { entry ->
                    if (entry.isDirectory) {
                        entry.listFiles()?.forEach { file ->
                            if (file.isFile && file.lastModified() < cutoff) {
                                file.delete()
                            }
                        }
                        if (entry.listFiles().isNullOrEmpty()) {
                            entry.delete()
                        }
                    } else if (entry.isFile && entry.lastModified() < cutoff) {
                        entry.delete()
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun shutdown() {
        executor.execute {
            closeWriter()
        }
        executor.shutdown()
    }
}

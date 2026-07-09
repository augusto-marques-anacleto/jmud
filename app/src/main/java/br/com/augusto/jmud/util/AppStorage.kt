package br.com.augusto.jmud.util

import android.content.Context
import android.os.Environment
import br.com.augusto.jmud.R
import java.io.File

object AppStorage {

    fun baseDir(context: Context): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(documentsDir, context.getString(R.string.app_name))
        if (!dir.exists()) {
            migrateLegacyDir(documentsDir, dir)
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun logsDir(context: Context): File {
        val dir = File(baseDir(context), "Logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun migrateLegacyDir(documentsDir: File, target: File) {
        for (legacyName in listOf("CMUD", "cmud", "Cmud")) {
            val legacy = File(documentsDir, legacyName)
            if (legacy.isDirectory && legacy.renameTo(target)) {
                return
            }
        }
    }
}

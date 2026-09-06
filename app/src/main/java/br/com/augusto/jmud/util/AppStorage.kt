package br.com.augusto.jmud.util

import android.content.Context
import android.os.Environment
import br.com.augusto.jmud.R
import java.io.File

object AppStorage {

    const val TEMP_DIR_NAME = ".temp"
    const val LOGS_DIR_NAME = "Logs"

    fun isReservedFolder(name: String): Boolean =
        name.startsWith(".") || name.equals(LOGS_DIR_NAME, ignoreCase = true)

    fun soundFolders(context: Context): List<String> =
        baseDir(context).listFiles()
            ?.filter { it.isDirectory && !isReservedFolder(it.name) }
            ?.map { it.name }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()

    private const val PREFS_NAME = "cmud_data"
    private const val KEY_LOCATION_ID = "storage_location_id"
    private const val KEY_LOCATION_PATH = "storage_location_path"
    private const val KEY_LOCATION_CHOSEN = "storage_location_chosen"
    private const val WRITE_TEST_FILE = ".jmud_write_test"

    @Volatile
    private var cachedBase: File? = null

    fun baseDir(context: Context): File {
        cachedBase?.let { cached ->
            if (cached.isDirectory) return cached
        }
        synchronized(this) {
            cachedBase?.let { cached ->
                if (cached.isDirectory) return cached
            }
            val resolved = resolveBaseDir(context)
            ensureUsable(resolved)
            cachedBase = resolved
            return resolved
        }
    }

    fun logsDir(context: Context): File {
        val dir = File(baseDir(context), "Logs")
        ensureUsable(dir)
        return dir
    }

    fun tempDir(context: Context): File {
        val dir = File(baseDir(context), TEMP_DIR_NAME)
        if (ensureUsable(dir)) {
            File(dir, ".nomedia").let { marker ->
                if (!marker.exists()) {
                    runCatching { marker.createNewFile() }
                }
            }
            return dir
        }
        return context.externalCacheDir ?: context.cacheDir
    }

    fun options(context: Context): List<StorageOption> {
        val options = mutableListOf<StorageOption>()
        val appName = context.getString(R.string.app_name)

        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documents != null) {
                options.add(toOption(StorageLocations.ID_DOCUMENTS, StorageType.DOCUMENTS, File(documents, appName)))
            }
        }

        val appDirs = context.getExternalFilesDirs(null).filterNotNull()
        appDirs.firstOrNull()?.let { primary ->
            options.add(toOption(StorageLocations.ID_APP_INTERNAL, StorageType.APP_INTERNAL, File(primary, appName)))
        }

        for (dir in appDirs.drop(1)) {
            if (Environment.getExternalStorageState(dir) != Environment.MEDIA_MOUNTED) continue
            val base = File(dir, appName)
            options.add(
                toOption(StorageLocations.removableId(dir.absolutePath), StorageType.REMOVABLE, base)
            )
        }

        return options
    }

    fun currentOption(context: Context): StorageOption {
        val available = options(context)
        val prefs = prefs(context)
        val saved = StorageLocations.pick(
            prefs.getString(KEY_LOCATION_ID, "") ?: "",
            prefs.getString(KEY_LOCATION_PATH, "") ?: "",
            available
        )
        if (saved != null) return saved

        val fallback = StorageLocations.fallback(available)
        if (fallback != null) return fallback

        val dir = File(context.filesDir, context.getString(R.string.app_name))
        return StorageOption(StorageLocations.ID_APP_INTERNAL, StorageType.APP_INTERNAL, dir)
    }

    fun select(context: Context, option: StorageOption) {
        prefs(context).edit()
            .putString(KEY_LOCATION_ID, option.id)
            .putString(KEY_LOCATION_PATH, option.baseDir.absolutePath)
            .putBoolean(KEY_LOCATION_CHOSEN, true)
            .apply()
        cachedBase = null
        ensureUsable(option.baseDir)
    }

    fun hasChosenLocation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCATION_CHOSEN, false)

    fun markLocationChosen(context: Context) {
        prefs(context).edit().putBoolean(KEY_LOCATION_CHOSEN, true).apply()
    }

    fun needsLocationChoice(context: Context): Boolean {
        if (hasChosenLocation(context)) return false
        val existing = options(context).firstOrNull { StorageLocations.hasContent(it.baseDir) }
        if (existing != null) {
            select(context, existing)
            return false
        }
        return options(context).size > 1
    }

    fun ensureUsable(dir: File): Boolean {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return isUsable(dir)
    }

    fun isUsable(dir: File): Boolean {
        if (!dir.exists() && !dir.mkdirs()) return false
        if (!dir.isDirectory) return false
        return try {
            val probe = File(dir, WRITE_TEST_FILE)
            probe.writeBytes(ByteArray(1))
            val ok = probe.exists() && probe.length() > 0
            probe.delete()
            ok
        } catch (e: Exception) {
            false
        }
    }

    fun invalidateCache() {
        cachedBase = null
    }

    private fun resolveBaseDir(context: Context): File {
        val option = currentOption(context)
        migrateLegacyDir(context, option)
        if (ensureUsable(option.baseDir)) return option.baseDir

        for (candidate in options(context)) {
            if (candidate.baseDir.absolutePath == option.baseDir.absolutePath) continue
            if (ensureUsable(candidate.baseDir)) {
                prefs(context).edit()
                    .putString(KEY_LOCATION_ID, candidate.id)
                    .putString(KEY_LOCATION_PATH, candidate.baseDir.absolutePath)
                    .apply()
                return candidate.baseDir
            }
        }

        val internal = File(context.filesDir, context.getString(R.string.app_name))
        internal.mkdirs()
        return internal
    }

    private fun toOption(id: String, type: StorageType, base: File): StorageOption {
        val reference = if (base.exists()) base else base.parentFile ?: base
        return StorageOption(
            id = id,
            type = type,
            baseDir = base,
            freeBytes = runCatching { reference.usableSpace }.getOrDefault(0L),
            totalBytes = runCatching { reference.totalSpace }.getOrDefault(0L)
        )
    }

    private fun migrateLegacyDir(context: Context, option: StorageOption) {
        if (option.type != StorageType.DOCUMENTS) return
        if (option.baseDir.exists()) return
        val documents = option.baseDir.parentFile ?: return
        for (legacyName in listOf("CMUD", "cmud", "Cmud")) {
            val legacy = File(documents, legacyName)
            if (legacy.isDirectory && legacy.renameTo(option.baseDir)) {
                return
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

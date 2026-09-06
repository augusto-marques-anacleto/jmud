package br.com.augusto.jmud.util

import java.io.File

enum class StorageType {
    DOCUMENTS,
    APP_INTERNAL,
    REMOVABLE
}

data class StorageOption(
    val id: String,
    val type: StorageType,
    val baseDir: File,
    val freeBytes: Long = 0L,
    val totalBytes: Long = 0L
)

object StorageLocations {

    const val ID_DOCUMENTS = "documents"
    const val ID_APP_INTERNAL = "app_internal"
    const val REMOVABLE_PREFIX = "removable_"

    fun pick(savedId: String, savedPath: String, options: List<StorageOption>): StorageOption? {
        if (options.isEmpty()) return null

        if (savedId.isNotBlank()) {
            options.firstOrNull { it.id == savedId }?.let { return it }
        }
        if (savedPath.isNotBlank()) {
            options.firstOrNull { it.baseDir.absolutePath == savedPath }?.let { return it }
        }
        return null
    }

    fun fallback(options: List<StorageOption>): StorageOption? =
        options.firstOrNull { it.type == StorageType.DOCUMENTS }
            ?: options.firstOrNull { it.type == StorageType.APP_INTERNAL }
            ?: options.firstOrNull()

    fun removableId(path: String): String = REMOVABLE_PREFIX + path.hashCode().toUInt().toString(16)

    fun hasContent(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val children = dir.listFiles() ?: return false
        return children.any { child ->
            if (child.isDirectory) {
                (child.listFiles()?.isNotEmpty() == true)
            } else {
                child.length() > 0L
            }
        }
    }
}

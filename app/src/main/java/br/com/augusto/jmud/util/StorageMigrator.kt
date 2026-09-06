package br.com.augusto.jmud.util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

data class MigrationProgress(
    val copiedFiles: Int,
    val totalFiles: Int,
    val percent: Int
)

enum class MigrationResult {
    SUCCESS,
    NOTHING_TO_MOVE,
    NO_SPACE,
    SAME_LOCATION,
    DESTINATION_UNUSABLE,
    COPY_FAILED,
    SOURCE_NOT_CLEARED
}

class StorageMigrator {

    suspend fun move(
        from: File,
        to: File,
        onProgress: (MigrationProgress) -> Unit
    ): MigrationResult {
        if (from.absolutePath == to.absolutePath) return MigrationResult.SAME_LOCATION
        if (isInside(to, from)) return MigrationResult.COPY_FAILED

        if (!AppStorage.ensureUsable(to)) return MigrationResult.DESTINATION_UNUSABLE

        val files = collectFiles(from)
        if (files.isEmpty()) return MigrationResult.NOTHING_TO_MOVE

        val totalBytes = files.sumOf { it.length() }
        val free = runCatching { to.usableSpace }.getOrDefault(0L)
        if (free in 1 until totalBytes + SPACE_MARGIN_BYTES) return MigrationResult.NO_SPACE

        val total = files.size
        var copied = 0
        onProgress(MigrationProgress(0, total, 0))

        for (file in files) {
            currentCoroutineContext().ensureActive()
            val relative = file.relativeToOrNull(from) ?: return MigrationResult.COPY_FAILED
            val target = File(to, relative.path)
            try {
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            } catch (e: Exception) {
                return MigrationResult.COPY_FAILED
            }
            if (!target.exists() || target.length() != file.length()) {
                return MigrationResult.COPY_FAILED
            }
            copied++
            onProgress(MigrationProgress(copied, total, copied * 100 / total))
        }

        val cleared = clearSource(from)
        return if (cleared) MigrationResult.SUCCESS else MigrationResult.SOURCE_NOT_CLEARED
    }

    fun collectFiles(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .filterNot { it.name == ".nomedia" }
            .filterNot { isInside(it, File(root, AppStorage.TEMP_DIR_NAME)) }
            .toList()
    }

    private fun clearSource(from: File): Boolean {
        val children = from.listFiles() ?: return true
        var allRemoved = true
        for (child in children) {
            if (!child.deleteRecursively()) {
                allRemoved = false
            }
        }
        return allRemoved
    }

    private fun isInside(candidate: File, parent: File): Boolean {
        val parentPath = parent.absolutePath
        val candidatePath = candidate.absolutePath
        return candidatePath == parentPath || candidatePath.startsWith(parentPath + File.separator)
    }

    private companion object {
        const val SPACE_MARGIN_BYTES = 16L * 1024L * 1024L
    }
}

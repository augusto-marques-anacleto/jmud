package br.com.augusto.jmud

import br.com.augusto.jmud.util.MigrationResult
import br.com.augusto.jmud.util.StorageMigrator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageMigratorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val migrator = StorageMigrator()

    private fun write(dir: File, relative: String, bytes: Int) {
        val file = File(dir, relative)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes) { 7 })
    }

    @Test
    fun movesEveryFileAndClearsTheSource() = runBlocking {
        val from = temp.newFolder("origem")
        val to = File(temp.root, "destino")
        write(from, "Sons/alerta.wav", 128)
        write(from, "fantasticmud/porta.wav", 64)
        write(from, "Logs/sessao.txt", 32)

        val progresses = mutableListOf<Int>()
        val result = migrator.move(from, to) { progresses.add(it.percent) }

        assertEquals(MigrationResult.SUCCESS, result)
        assertTrue(File(to, "Sons/alerta.wav").exists())
        assertEquals(128L, File(to, "Sons/alerta.wav").length())
        assertTrue(File(to, "fantasticmud/porta.wav").exists())
        assertTrue(File(to, "Logs/sessao.txt").exists())
        assertFalse(File(from, "Sons/alerta.wav").exists())
        assertEquals(100, progresses.last())
    }

    @Test
    fun temporaryFolderIsNotCarriedOver() = runBlocking {
        val from = temp.newFolder("origem")
        val to = File(temp.root, "destino")
        write(from, "Sons/alerta.wav", 16)
        write(from, ".temp/soundpack.zip", 4096)

        val result = migrator.move(from, to) {}

        assertEquals(MigrationResult.SUCCESS, result)
        assertTrue(File(to, "Sons/alerta.wav").exists())
        assertFalse(File(to, ".temp/soundpack.zip").exists())
    }

    @Test
    fun emptySourceReportsNothingToMove() = runBlocking {
        val from = temp.newFolder("origem")
        val to = File(temp.root, "destino")

        assertEquals(MigrationResult.NOTHING_TO_MOVE, migrator.move(from, to) {})
    }

    @Test
    fun sameFolderIsRejected() = runBlocking {
        val from = temp.newFolder("origem")

        assertEquals(MigrationResult.SAME_LOCATION, migrator.move(from, from) {})
    }

    @Test
    fun destinationInsideSourceIsRejected() = runBlocking {
        val from = temp.newFolder("origem")
        write(from, "Sons/alerta.wav", 8)
        val to = File(from, "dentro")

        assertEquals(MigrationResult.COPY_FAILED, migrator.move(from, to) {})
        assertTrue(File(from, "Sons/alerta.wav").exists())
    }

    @Test
    fun collectFilesIgnoresTemporaryFolder() {
        val root = temp.newFolder("raiz")
        write(root, "a.wav", 4)
        write(root, "sub/b.wav", 4)
        write(root, ".temp/lixo.zip", 4)

        val files = migrator.collectFiles(root).map { it.name }.sorted()

        assertEquals(listOf("a.wav", "b.wav"), files)
    }

    @Test
    fun existingDestinationFilesAreOverwritten() = runBlocking {
        val from = temp.newFolder("origem")
        val to = temp.newFolder("destino")
        write(from, "Sons/alerta.wav", 200)
        write(to, "Sons/alerta.wav", 10)

        val result = migrator.move(from, to) {}

        assertEquals(MigrationResult.SUCCESS, result)
        assertEquals(200L, File(to, "Sons/alerta.wav").length())
    }
}

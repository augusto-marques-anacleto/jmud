package br.com.augusto.jmud

import br.com.augusto.jmud.util.SoundPackInstaller
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SoundPackInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val installer = SoundPackInstaller()

    @Test
    fun cleanUrlExtractsFromDirtyText() {
        assertEquals(
            "https://exemplo.com/pacote.zip",
            installer.cleanUrl("baixe aqui: https://exemplo.com/pacote.zip e divirta-se")
        )
        assertEquals(
            "https://exemplo.com/a.zip",
            installer.cleanUrl("<https://exemplo.com/a.zip>")
        )
        assertEquals("", installer.cleanUrl("sem link nenhum"))
    }

    @Test
    fun driveIdExtraction() {
        assertEquals(
            "abc123_-XYZ",
            installer.extractDriveId("https://drive.google.com/file/d/abc123_-XYZ/view?usp=sharing")
        )
        assertEquals(
            "id456",
            installer.extractDriveId("https://drive.google.com/open?id=id456")
        )
        assertEquals(null, installer.extractDriveId("https://exemplo.com/a.zip"))
    }

    @Test
    fun windowsZipWithCp850NamesIsFixed() = runBlocking {
        val zip = tempFolder.newFile("pack.zip")
        ZipArchiveOutputStream(zip).use { out ->
            out.encoding = "CP850"
            out.setUseLanguageEncodingFlag(false)
            out.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER)
            out.putArchiveEntry(ZipArchiveEntry("sons/Abençoar_01.wav"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeArchiveEntry()
            out.putArchiveEntry(ZipArchiveEntry("sons/Abrindo_Túmulo_01.wav"))
            out.write(byteArrayOf(4, 5, 6))
            out.closeArchiveEntry()
        }

        val target = tempFolder.newFolder("destino")
        val result = installer.extractAndCopy(zip, target) {}

        assertTrue(result != null && result.keptExisting == 0)
        assertTrue(File(target, "Abençoar_01.wav").exists())
        assertTrue(File(target, "Abrindo_Túmulo_01.wav").exists())
    }

    @Test
    fun utf8FlaggedZipKeepsNames() = runBlocking {
        val zip = tempFolder.newFile("packutf8.zip")
        ZipArchiveOutputStream(zip).use { out ->
            out.putArchiveEntry(ZipArchiveEntry("músicas/Canção_01.wav"))
            out.write(byteArrayOf(1))
            out.closeArchiveEntry()
        }

        val target = tempFolder.newFolder("destinoutf8")
        val result = installer.extractAndCopy(zip, target) {}

        assertTrue(result != null && result.keptExisting == 0)
        assertTrue(File(target, "Canção_01.wav").exists())
    }

    @Test
    fun recoversWhenExistingFileIsNotWritable() = runBlocking {
        val zip = tempFolder.newFile("packkeep.zip")
        ZipArchiveOutputStream(zip).use { out ->
            out.putArchiveEntry(ZipArchiveEntry("sons/bloqueado.wav"))
            out.write(byteArrayOf(9, 9, 9))
            out.closeArchiveEntry()
            out.putArchiveEntry(ZipArchiveEntry("sons/livre.wav"))
            out.write(byteArrayOf(7))
            out.closeArchiveEntry()
        }

        val target = tempFolder.newFolder("destinokeep")
        val blocked = File(target, "bloqueado.wav")
        blocked.writeBytes(byteArrayOf(1, 2))
        blocked.setWritable(false)
        try {
            val result = installer.extractAndCopy(zip, target) {}

            assertTrue(result != null)
            val kept = result!!.keptExisting
            if (kept == 0) {
                assertEquals(3, blocked.length().toInt())
            } else {
                assertEquals(1, kept)
                assertEquals(2, blocked.length().toInt())
            }
            assertTrue(File(target, "livre.wav").exists())
        } finally {
            blocked.setWritable(true)
        }
    }
}

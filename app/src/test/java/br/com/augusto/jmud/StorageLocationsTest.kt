package br.com.augusto.jmud

import br.com.augusto.jmud.util.StorageLocations
import br.com.augusto.jmud.util.StorageOption
import br.com.augusto.jmud.util.StorageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageLocationsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun option(id: String, type: StorageType, dir: File) =
        StorageOption(id = id, type = type, baseDir = dir)

    @Test
    fun savedIdWins() {
        val documents = option("documents", StorageType.DOCUMENTS, File("/a/Documents/jMud"))
        val card = option("removable_1", StorageType.REMOVABLE, File("/card/jMud"))

        val picked = StorageLocations.pick("removable_1", "", listOf(documents, card))

        assertEquals(card, picked)
    }

    @Test
    fun savedPathIsUsedWhenIdChanged() {
        val card = option("removable_9f", StorageType.REMOVABLE, File("/card/jMud"))

        val picked = StorageLocations.pick("removable_old", card.baseDir.absolutePath, listOf(card))

        assertEquals(card, picked)
    }

    @Test
    fun removedCardYieldsNothingSoCallerCanFallBack() {
        val documents = option("documents", StorageType.DOCUMENTS, File("/a/Documents/jMud"))

        assertNull(StorageLocations.pick("removable_1", "/card/jMud", listOf(documents)))
    }

    @Test
    fun fallbackPrefersDocumentsThenAppFolder() {
        val documents = option("documents", StorageType.DOCUMENTS, File("/a/Documents/jMud"))
        val app = option("app_internal", StorageType.APP_INTERNAL, File("/a/Android/data/files/jMud"))
        val card = option("removable_1", StorageType.REMOVABLE, File("/card/jMud"))

        assertEquals(documents, StorageLocations.fallback(listOf(card, app, documents)))
        assertEquals(app, StorageLocations.fallback(listOf(card, app)))
        assertEquals(card, StorageLocations.fallback(listOf(card)))
        assertNull(StorageLocations.fallback(emptyList()))
    }

    @Test
    fun emptyOptionsPickNothing() {
        assertNull(StorageLocations.pick("documents", "/a", emptyList()))
    }

    @Test
    fun removableIdIsStableForTheSamePath() {
        val first = StorageLocations.removableId("/storage/1234-5678/Android/data/files")
        val second = StorageLocations.removableId("/storage/1234-5678/Android/data/files")
        val other = StorageLocations.removableId("/storage/ABCD-9999/Android/data/files")

        assertEquals(first, second)
        assertTrue(first != other)
        assertTrue(first.startsWith(StorageLocations.REMOVABLE_PREFIX))
    }

    @Test
    fun folderWithFilesIsDetectedAsUsed() {
        val dir = temp.newFolder("jMud")
        assertFalse(StorageLocations.hasContent(dir))

        File(dir, "Sons").mkdirs()
        assertFalse(StorageLocations.hasContent(dir))

        File(File(dir, "Sons"), "alerta.wav").writeBytes(ByteArray(10))
        assertTrue(StorageLocations.hasContent(dir))
    }

    @Test
    fun missingFolderHasNoContent() {
        assertFalse(StorageLocations.hasContent(File(temp.root, "naoexiste")))
    }
}

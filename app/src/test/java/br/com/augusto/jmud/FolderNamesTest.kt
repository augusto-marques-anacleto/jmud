package br.com.augusto.jmud

import br.com.augusto.jmud.util.FolderNames
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {

    @Test
    fun takesDomainLabelBeforeSuffixes() {
        assertEquals("esferasdavida", FolderNames.suggest("mud.esferasdavida.com.br", "Andrômeda"))
        assertEquals("fantasticmud", FolderNames.suggest("mud.fantasticmud.com", "Ares"))
        assertEquals("jogo", FolderNames.suggest("mud.jogo.com.br", "Fred"))
        assertEquals("aardwolf", FolderNames.suggest("play.aardwolf.com", "Fred"))
        assertEquals("bat", FolderNames.suggest("batmud.bat.org", "Fred"))
    }

    @Test
    fun fallsBackToCharacterName() {
        assertEquals("meu_heroi", FolderNames.suggest("127.0.0.1", "Meu Heroi"))
        assertEquals("fred", FolderNames.suggest("", "Fred"))
    }

    @Test
    fun plainHostnameIsUsedDirectly() {
        assertEquals("localhost", FolderNames.suggest("localhost", "Fred"))
    }
}

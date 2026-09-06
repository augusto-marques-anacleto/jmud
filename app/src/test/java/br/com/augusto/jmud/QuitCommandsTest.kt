package br.com.augusto.jmud

import br.com.augusto.jmud.util.QuitCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuitCommandsTest {

    private val defaults = QuitCommands.parse(QuitCommands.DEFAULT)

    @Test
    fun defaultListCoversCommonQuitCommands() {
        assertTrue(QuitCommands.matches("fim", defaults))
        assertTrue(QuitCommands.matches("sair", defaults))
        assertTrue(QuitCommands.matches("quit", defaults))
        assertTrue(QuitCommands.matches("exit", defaults))
        assertTrue(QuitCommands.matches("logout", defaults))
    }

    @Test
    fun matchingIgnoresCaseAndSurroundingSpaces() {
        assertTrue(QuitCommands.matches("  FIM  ", defaults))
        assertTrue(QuitCommands.matches("Quit", defaults))
    }

    @Test
    fun ordinaryCommandsAreNotQuit() {
        assertFalse(QuitCommands.matches("norte", defaults))
        assertFalse(QuitCommands.matches("olhar", defaults))
        assertFalse(QuitCommands.matches("", defaults))
    }

    @Test
    fun commandThatMerelyStartsWithAQuitWordIsNotQuit() {
        assertFalse(QuitCommands.matches("finalizar missao", defaults))
        assertFalse(QuitCommands.matches("sair da masmorra", defaults))
        assertFalse(QuitCommands.matches("quitar", defaults))
    }

    @Test
    fun customListReplacesTheDefault() {
        val custom = QuitCommands.parse("desconectar, tchau")

        assertTrue(QuitCommands.matches("tchau", custom))
        assertTrue(QuitCommands.matches("desconectar", custom))
        assertFalse(QuitCommands.matches("fim", custom))
    }

    @Test
    fun parsingCleansSpacesDuplicatesAndEmptyEntries() {
        val parsed = QuitCommands.parse(" fim , , FIM ,sair,")

        assertEquals(listOf("fim", "sair"), parsed)
    }

    @Test
    fun emptyListNeverMatchesSoReconnectKeepsWorking() {
        assertFalse(QuitCommands.matches("fim", QuitCommands.parse("")))
        assertFalse(QuitCommands.matches("fim", emptyList()))
    }

    @Test
    fun linesAreAcceptedAsSeparatorsToo() {
        val parsed = QuitCommands.parse("fim\nsair")

        assertEquals(listOf("fim", "sair"), parsed)
    }
}

package br.com.augusto.jmud

import br.com.augusto.jmud.util.MacroEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroEngineTest {

    @Test
    fun renamedMacroWithUnderscoreCanBeCalled() {
        val invocation = MacroEngine.parseInvocation("#rota_2")

        assertEquals("rota_2", invocation?.name)
        assertEquals("", invocation?.argsString)
    }

    @Test
    fun nameWithSpaceWouldLoseTheSuffix() {
        val invocation = MacroEngine.parseInvocation("#rota 2")

        assertEquals("rota", invocation?.name)
        assertEquals("2", invocation?.argsString)
    }

    @Test
    fun argumentsAreKeptAfterTheName() {
        val invocation = MacroEngine.parseInvocation("#tt zanand")

        assertEquals("tt", invocation?.name)
        assertEquals("zanand", invocation?.argsString)
    }

    @Test
    fun plainCommandIsNotAnInvocation() {
        assertNull(MacroEngine.parseInvocation("norte"))
        assertNull(MacroEngine.parseInvocation("#"))
    }
}

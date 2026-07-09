package br.com.augusto.jmud

import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.util.TriggerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TriggerEngineTest {

    private fun trigger(message: String, matchType: String) = MudTrigger(
        id = "t",
        name = "teste",
        message = message,
        matchType = matchType,
        commands = "",
        scope = Scope.ALL,
        scopeValue = "",
        enabled = true
    )

    @Test
    fun patternWordMatchesSingleWordOnly() {
        val t = trigger("@ morreu.", MudTrigger.MATCH_PATTERN)
        val match = TriggerEngine.match(t, "joão morreu.")
        assertNotNull(match)
        assertEquals(listOf("joão"), match!!.groups)
        assertNull(TriggerEngine.match(t, "joão morreu. do leste"))
    }

    @Test
    fun patternStarMatchesAnyPrefix() {
        val t = trigger("* joão morreu.", MudTrigger.MATCH_PATTERN)
        val match = TriggerEngine.match(t, "ontem à noite joão morreu.")
        assertNotNull(match)
        assertEquals(listOf("ontem à noite"), match!!.groups)
    }

    @Test
    fun patternNumberWildcard() {
        val t = trigger("& moedas", MudTrigger.MATCH_PATTERN)
        val match = TriggerEngine.match(t, "150 moedas")
        assertNotNull(match)
        assertEquals(listOf("150"), match!!.groups)
        assertNull(TriggerEngine.match(t, "muitas moedas"))
    }

    @Test
    fun escapePreventsWildcard() {
        val t = trigger("\\* importante", MudTrigger.MATCH_PATTERN)
        assertNotNull(TriggerEngine.match(t, "* importante"))
        assertNull(TriggerEngine.match(t, "aviso importante"))
    }

    @Test
    fun containsMatchInsideWord() {
        val t = trigger("x)", MudTrigger.MATCH_CONTAINS)
        val line = "(2x) uma pedra está em pé aqui"
        val match = TriggerEngine.match(t, line)
        assertNotNull(match)
        assertEquals(2, match!!.matchStart)
    }

    @Test
    fun substitutionsFromMatchPoint() {
        val t = trigger("x)", MudTrigger.MATCH_CONTAINS)
        val line = "(2x) uma pedra está em pé aqui"
        val match = TriggerEngine.match(t, line)!!
        assertEquals(listOf(line), TriggerEngine.expandCommands("$0", match))
        assertEquals(listOf("pegar uma"), TriggerEngine.expandCommands("pegar \$s", match))
        assertEquals(listOf("olhar pedra"), TriggerEngine.expandCommands("olhar \$t", match))
    }

    @Test
    fun groupSubstitutionInPatternMode() {
        val t = trigger("@ te atacou!", MudTrigger.MATCH_PATTERN)
        val match = TriggerEngine.match(t, "goblin te atacou!")!!
        assertEquals(listOf("matar goblin"), TriggerEngine.expandCommands("matar \$1", match))
    }

    @Test
    fun lineBreakCreatesMultipleCommands() {
        val t = trigger("fome", MudTrigger.MATCH_CONTAINS)
        val match = TriggerEngine.match(t, "você está com fome")!!
        assertEquals(
            listOf("pegar pão", "comer pão"),
            TriggerEngine.expandCommands("pegar pão\$zcomer pão", match)
        )
    }

    @Test
    fun matchingIgnoresCaseAndSurroundingSpaces() {
        val exact = trigger("plaft", MudTrigger.MATCH_EXACT)
        assertNotNull(TriggerEngine.match(exact, "PLAFT"))
        assertNotNull(TriggerEngine.match(exact, "  Plaft  "))

        val start = trigger("plaft ", MudTrigger.MATCH_START)
        assertNotNull(TriggerEngine.match(start, "Plaft! Você levou um tapa."))

        val pattern = trigger("@ Morreu.", MudTrigger.MATCH_PATTERN)
        assertNotNull(TriggerEngine.match(pattern, "joão morreu."))
    }

    @Test
    fun startEndExactModes() {
        assertNotNull(TriggerEngine.match(trigger("Você", MudTrigger.MATCH_START), "Você acordou."))
        assertNull(TriggerEngine.match(trigger("Você", MudTrigger.MATCH_START), "Agora Você acordou."))
        assertNotNull(TriggerEngine.match(trigger("acordou.", MudTrigger.MATCH_END), "Você acordou."))
        assertNotNull(TriggerEngine.match(trigger("Você acordou.", MudTrigger.MATCH_EXACT), "Você acordou."))
        assertNull(TriggerEngine.match(trigger("Você acordou.", MudTrigger.MATCH_EXACT), "Você acordou. agora"))
    }
}

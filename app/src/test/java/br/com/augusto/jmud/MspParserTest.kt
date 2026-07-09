package br.com.augusto.jmud

import br.com.augusto.jmud.util.MspParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MspParserTest {

    @Test
    fun tagAtLineStartIsParsed() {
        val parsed = MspParser.parse("!!MUSIC(Off)")
        assertEquals(1, parsed.commands.size)
        assertTrue(parsed.commands[0].isMusic)
        assertEquals("Off", parsed.commands[0].fileName)
        assertEquals("", parsed.cleanText)
    }

    @Test
    fun tagInMiddleOfLineIsIgnored() {
        val parsed = MspParser.parse("falar oi bom dia !!MUSIC(pornogay.mp3, V=40)")
        assertTrue(parsed.commands.isEmpty())
        assertEquals("falar oi bom dia !!MUSIC(pornogay.mp3, V=40)", parsed.cleanText)
    }

    @Test
    fun soundOffLowercaseIsParsed() {
        val parsed = MspParser.parse("!!SOUND(off)")
        assertEquals(1, parsed.commands.size)
        assertFalse(parsed.commands[0].isMusic)
        assertEquals("off", parsed.commands[0].fileName)
    }

    @Test
    fun lowercaseTagAndKeys() {
        val parsed = MspParser.parse("!!sound(fantastico.wav v=70)")
        assertEquals(1, parsed.commands.size)
        assertEquals("fantastico.wav", parsed.commands[0].fileName)
        assertEquals(70, parsed.commands[0].volume)
    }

    @Test
    fun allParametersAreParsed() {
        val parsed = MspParser.parse("!!SOUND(espada.wav V=80 L=3 P=90 T=combate U=https://sons.com/pack)")
        val command = parsed.commands[0]
        assertEquals("espada.wav", command.fileName)
        assertEquals(80, command.volume)
        assertEquals(3, command.loops)
        assertEquals(90, command.priority)
        assertEquals("combate", command.type)
        assertEquals("https://sons.com/pack", command.url)
    }

    @Test
    fun commaSeparatedParams() {
        val parsed = MspParser.parse("!!MUSIC(taverna.mp3, V=40, L=2)")
        val command = parsed.commands[0]
        assertEquals("taverna.mp3", command.fileName)
        assertEquals(40, command.volume)
        assertEquals(2, command.loops)
    }

    @Test
    fun continueParameterForMusic() {
        assertFalse(MspParser.parse("!!MUSIC(taverna.mp3 C=0)").commands[0].continueMusic)
        assertTrue(MspParser.parse("!!MUSIC(taverna.mp3 C=1)").commands[0].continueMusic)
        assertTrue(MspParser.parse("!!MUSIC(taverna.mp3)").commands[0].continueMusic)
    }

    @Test
    fun chainedLeadingTagsWithTrailingText() {
        val parsed = MspParser.parse("!!SOUND(passo.wav v=50 l=-1)!!MUSIC(floresta.mp3 V=60) Kalene morreu!")
        assertEquals(2, parsed.commands.size)
        assertEquals(50, parsed.commands[0].volume)
        assertEquals(-1, parsed.commands[0].loops)
        assertTrue(parsed.commands[1].isMusic)
        assertEquals("Kalene morreu!", parsed.cleanText)
    }

    @Test
    fun malformedLinesDoNotBreak() {
        val unterminated = MspParser.parse("!!SOUND(espada.wav V=80")
        assertEquals(1, unterminated.commands.size)
        assertEquals("espada.wav", unterminated.commands[0].fileName)

        val empty = MspParser.parse("!!SOUND()")
        assertTrue(empty.commands.isEmpty())

        val garbage = MspParser.parse("!!SOUND(,,, ,V=)")
        assertTrue(garbage.commands.isEmpty() || garbage.commands[0].fileName.isNotEmpty())
    }

    @Test
    fun lineWithoutMspIsUntouched() {
        val parsed = MspParser.parse("apenas uma linha comum!")
        assertTrue(parsed.commands.isEmpty())
        assertEquals("apenas uma linha comum!", parsed.cleanText)
    }
}

package br.com.augusto.jmud

import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.MudMacro
import br.com.augusto.jmud.domain.MudTimer
import br.com.augusto.jmud.domain.MudShortcut
import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.domain.ShortcutAction
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.domain.ShareBundle
import br.com.augusto.jmud.domain.ShareKind
import br.com.augusto.jmud.domain.ShareSection
import br.com.augusto.jmud.util.ShareFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFormatTest {

    private fun character() = MudCharacter(
        id = "c1",
        name = "Zanand",
        host = "mud.exemplo.com",
        port = 4000,
        password = "segredo",
        autoLogin = true,
        postConnectCommands = "olhar",
        useTTS = true,
        playSounds = true,
        soundsFolder = "Sons",
        autoReconnect = true
    )

    private fun trigger() = MudTrigger(
        id = "t1",
        name = "Alerta de vida",
        message = "Você está ferido",
        matchType = MudTrigger.MATCH_CONTAINS,
        commands = "beber cura",
        scope = Scope.MUD,
        scopeValue = "mud.exemplo.com",
        enabled = true,
        ignoreLine = true,
        historyName = "Combate",
        soundName = "alerta.wav"
    )

    private fun timer() = MudTimer(
        id = "tm1",
        seconds = 45,
        commands = "olhar",
        scope = Scope.ALL,
        scopeValue = "",
        enabled = true
    )

    private fun macro() = MudMacro(
        id = "m1",
        name = "caminho",
        commands = "n\nn\nl",
        scope = Scope.CHARACTER,
        scopeValue = "c1",
        enabled = true,
        intervalMs = 333
    )

    @Test
    fun backupRoundTripKeepsEveryItem() {
        val bundle = ShareBundle(
            kind = ShareKind.BACKUP,
            characters = listOf(character()),
            triggers = listOf(trigger()),
            timers = listOf(timer()),
            macros = listOf(macro()),
            settings = mapOf("encoding" to "UTF-8", "logs_enabled" to true, "command_interval_ms" to 400)
        )

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))

        assertNotNull(decoded)
        assertEquals(ShareKind.BACKUP, decoded!!.kind)
        assertEquals(listOf(character()), decoded.characters)
        assertEquals(listOf(trigger()), decoded.triggers)
        assertEquals(listOf(timer()), decoded.timers)
        assertEquals(listOf(macro()), decoded.macros)
        assertEquals("UTF-8", decoded.settings["encoding"])
        assertEquals(true, decoded.settings["logs_enabled"])
        assertEquals(400, decoded.settings["command_interval_ms"])
    }

    @Test
    fun sharedCharacterNeverCarriesPassword() {
        val bundle = ShareBundle(kind = ShareKind.SHARE, characters = listOf(character()))

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))!!

        assertEquals("", decoded.characters.first().password)
        assertFalse(decoded.characters.first().autoLogin)
        assertEquals("mud.exemplo.com", decoded.characters.first().host)
    }

    @Test
    fun backupCharacterKeepsPassword() {
        val bundle = ShareBundle(kind = ShareKind.BACKUP, characters = listOf(character()))

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))!!

        assertEquals("segredo", decoded.characters.first().password)
        assertTrue(decoded.characters.first().autoLogin)
    }

    @Test
    fun macroIntervalSurvivesTheRoundTrip() {
        val bundle = ShareBundle(kind = ShareKind.SHARE, macros = listOf(macro()))

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))!!

        assertEquals(333, decoded.macros.first().intervalMs)
    }

    @Test
    fun legacyBackupIsStillReadable() {
        val data = JSONObject()
        data.put("triggers_list", JSONObject().put("t", "s").put("v", ShareFormat.triggersToText(listOf(trigger()))))
        data.put("macros_list", JSONObject().put("t", "s").put("v", ShareFormat.macrosToText(listOf(macro()))))
        data.put("encoding", JSONObject().put("t", "s").put("v", "ISO-8859-1"))
        data.put("logs_enabled", JSONObject().put("t", "b").put("v", false))
        val root = JSONObject()
        root.put("app", "jMud")
        root.put("backupVersion", 1)
        root.put("data", data)

        val decoded = ShareFormat.decode(root.toString())

        assertNotNull(decoded)
        assertEquals(ShareKind.BACKUP, decoded!!.kind)
        assertEquals(listOf(trigger()), decoded.triggers)
        assertEquals(listOf(macro()), decoded.macros)
        assertEquals("ISO-8859-1", decoded.settings["encoding"])
        assertFalse(decoded.settings.containsKey("triggers_list"))
    }

    @Test
    fun rejectsFilesFromOtherApps() {
        val root = JSONObject().put("app", "OutroApp").put("formatVersion", 2)
        assertNull(ShareFormat.decode(root.toString()))
    }

    @Test
    fun rejectsGarbageText() {
        assertNull(ShareFormat.decode(""))
        assertNull(ShareFormat.decode("oi, tudo bem?"))
        assertNull(ShareFormat.decode("{\"app\":\"jMud\"}"))
    }

    @Test
    fun rejectsNewerFormatVersion() {
        val root = JSONObject()
        root.put("app", "jMud")
        root.put("formatVersion", ShareFormat.FORMAT_VERSION + 1)
        root.put("triggers", org.json.JSONArray())
        assertNull(ShareFormat.decode(root.toString()))
    }

    @Test
    fun rejectsBundleWithoutAnyContent() {
        val bundle = ShareBundle(kind = ShareKind.SHARE)
        assertNull(ShareFormat.decode(ShareFormat.encode(bundle)))
    }

    @Test
    fun itemWithoutIdGetsOne() {
        val json = """
            {"app":"jMud","formatVersion":2,"kind":"share",
             "macros":[{"name":"tapa","commands":"tapa","scope":"ALL","scopeValue":"","enabled":true}]}
        """.trimIndent()

        val decoded = ShareFormat.decode(json)!!

        assertTrue(decoded.macros.first().id.isNotBlank())
        assertEquals("tapa", decoded.macros.first().name)
    }

    @Test
    fun unknownFieldsAndSectionsAreIgnored() {
        val json = """
            {"app":"jMud","formatVersion":2,"kind":"share","widgets":[{"key":"F1"}],
             "timers":[{"id":"x","seconds":10,"commands":"olhar","scope":"ALL","scopeValue":"","enabled":true,"futuro":1}]}
        """.trimIndent()

        val decoded = ShareFormat.decode(json)!!

        assertEquals(1, decoded.timers.size)
        assertEquals(10, decoded.timers.first().seconds)
    }

    @Test
    fun filteringKeepsOnlySelectedSections() {
        val bundle = ShareBundle(
            kind = ShareKind.BACKUP,
            characters = listOf(character()),
            triggers = listOf(trigger()),
            macros = listOf(macro()),
            settings = mapOf("encoding" to "UTF-8")
        )

        val filtered = bundle.filtered(setOf(ShareSection.TRIGGERS))

        assertEquals(1, filtered.triggers.size)
        assertTrue(filtered.characters.isEmpty())
        assertTrue(filtered.macros.isEmpty())
        assertTrue(filtered.settings.isEmpty())
    }

    @Test
    fun availableSectionsListsOnlyWhatIsPresent() {
        val bundle = ShareBundle(kind = ShareKind.SHARE, macros = listOf(macro()))

        assertEquals(listOf(ShareSection.MACROS), bundle.availableSections())
        assertEquals(1, bundle.itemCount())
        assertFalse(bundle.isEmpty())
    }

    @Test
    fun scopeOverrideAppliesToEveryScopedItem() {
        val bundle = ShareBundle(
            kind = ShareKind.SHARE,
            characters = listOf(character()),
            triggers = listOf(trigger()),
            timers = listOf(timer()),
            macros = listOf(macro())
        )

        val rescoped = bundle.withScope(Scope.CHARACTER, "c9")

        assertEquals(Scope.CHARACTER, rescoped.triggers.first().scope)
        assertEquals("c9", rescoped.triggers.first().scopeValue)
        assertEquals(Scope.CHARACTER, rescoped.timers.first().scope)
        assertEquals("c9", rescoped.timers.first().scopeValue)
        assertEquals(Scope.CHARACTER, rescoped.macros.first().scope)
        assertEquals("c9", rescoped.macros.first().scopeValue)
        assertEquals(listOf(character()), rescoped.characters)
    }

    @Test
    fun listTextHelpersRoundTrip() {
        assertEquals(listOf(trigger()), ShareFormat.triggersFromText(ShareFormat.triggersToText(listOf(trigger()))))
        assertEquals(listOf(timer()), ShareFormat.timersFromText(ShareFormat.timersToText(listOf(timer()))))
        assertEquals(listOf(macro()), ShareFormat.macrosFromText(ShareFormat.macrosToText(listOf(macro()))))
        assertEquals(listOf(character()), ShareFormat.charactersFromText(ShareFormat.charactersToText(listOf(character()))))
    }

    @Test
    fun brokenStoredListReturnsEmptyInsteadOfCrashing() {
        assertTrue(ShareFormat.triggersFromText("nao e json").isEmpty())
        assertTrue(ShareFormat.macrosFromText(null).isEmpty())
        assertTrue(ShareFormat.timersFromText("").isEmpty())
    }

    private fun shortcut() = MudShortcut(
        id = "s1",
        label = "norte",
        command = "n",
        action = ShortcutAction.COMMAND,
        scope = Scope.ALL,
        scopeValue = "",
        enabled = true
    )

    @Test
    fun shortcutRoundTripKeepsEveryField() {
        val bundle = ShareBundle(kind = ShareKind.SHARE, shortcuts = listOf(shortcut()))

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))!!

        assertEquals(listOf(shortcut()), decoded.shortcuts)
        assertEquals(listOf(ShareSection.SHORTCUTS), decoded.availableSections())
    }

    @Test
    fun shortcutWithAppActionSurvives() {
        val stopSound = shortcut().copy(id = "s2", label = "parar som", command = "", action = ShortcutAction.STOP_SOUND)
        val bundle = ShareBundle(kind = ShareKind.SHARE, shortcuts = listOf(stopSound))

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))!!

        assertEquals(ShortcutAction.STOP_SOUND, decoded.shortcuts.first().action)
    }

    @Test
    fun unknownShortcutActionFallsBackToCommand() {
        val json = """
            {"app":"jMud","formatVersion":2,"kind":"share",
             "shortcuts":[{"id":"s9","label":"teste","command":"olhar","action":"VOAR","scope":"ALL","scopeValue":"","enabled":true}]}
        """.trimIndent()

        val decoded = ShareFormat.decode(json)!!

        assertEquals(ShortcutAction.COMMAND, decoded.shortcuts.first().action)
    }

    @Test
    fun shortcutWithoutLabelIsDropped() {
        val json = """
            {"app":"jMud","formatVersion":2,"kind":"share",
             "shortcuts":[{"id":"s9","label":"","command":"n"},{"id":"s10","label":"sul","command":"s"}]}
        """.trimIndent()

        val decoded = ShareFormat.decode(json)!!

        assertEquals(1, decoded.shortcuts.size)
        assertEquals("sul", decoded.shortcuts.first().label)
    }

    @Test
    fun scopeOverrideReachesShortcuts() {
        val bundle = ShareBundle(kind = ShareKind.SHARE, shortcuts = listOf(shortcut()))

        val rescoped = bundle.withScope(Scope.MUD, "mud.exemplo.com")

        assertEquals(Scope.MUD, rescoped.shortcuts.first().scope)
        assertEquals("mud.exemplo.com", rescoped.shortcuts.first().scopeValue)
    }

    @Test
    fun legacyBackupReadsStoredShortcuts() {
        val data = JSONObject()
        data.put("shortcuts_list", JSONObject().put("t", "s").put("v", ShareFormat.shortcutsToText(listOf(shortcut()))))
        val root = JSONObject()
        root.put("app", "jMud")
        root.put("backupVersion", 1)
        root.put("data", data)

        val decoded = ShareFormat.decode(root.toString())!!

        assertEquals(listOf(shortcut()), decoded.shortcuts)
        assertFalse(decoded.settings.containsKey("shortcuts_list"))
    }

    @Test
    fun directionKeySurvivesSoPresetsDoNotDuplicateAcrossLanguages() {
        val north = shortcut().copy(direction = "N")
        val bundle = ShareBundle(kind = ShareKind.SHARE, shortcuts = listOf(north))

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))!!

        assertEquals("N", decoded.shortcuts.first().direction)
    }

    @Test
    fun shortcutWithoutDirectionKeyStaysValid() {
        val json = """
            {"app":"jMud","formatVersion":2,"kind":"share",
             "shortcuts":[{"id":"s1","label":"norte","command":"n","action":"COMMAND","scope":"ALL","scopeValue":"","enabled":true}]}
        """.trimIndent()

        val decoded = ShareFormat.decode(json)!!

        assertEquals("", decoded.shortcuts.first().direction)
        assertEquals("norte", decoded.shortcuts.first().label)
    }

    @Test
    fun remappingCharacterIdsKeepsAutomationsAttachedToTheImportedCharacter() {
        val bundle = ShareBundle(
            kind = ShareKind.BACKUP,
            characters = listOf(character()),
            triggers = listOf(trigger().copy(scope = Scope.CHARACTER, scopeValue = "c1")),
            timers = listOf(timer().copy(scope = Scope.CHARACTER, scopeValue = "c1")),
            macros = listOf(macro()),
            shortcuts = listOf(shortcut().copy(scope = Scope.CHARACTER, scopeValue = "c1"))
        )

        val remapped = bundle.remapCharacterScope(mapOf("c1" to "novo-id"))

        assertEquals("novo-id", remapped.triggers.first().scopeValue)
        assertEquals("novo-id", remapped.timers.first().scopeValue)
        assertEquals("novo-id", remapped.macros.first().scopeValue)
        assertEquals("novo-id", remapped.shortcuts.first().scopeValue)
    }

    @Test
    fun remappingLeavesOtherScopesUntouched() {
        val bundle = ShareBundle(
            kind = ShareKind.BACKUP,
            triggers = listOf(trigger()),
            timers = listOf(timer()),
            shortcuts = listOf(shortcut())
        )

        val remapped = bundle.remapCharacterScope(mapOf("c1" to "novo-id"))

        assertEquals("mud.exemplo.com", remapped.triggers.first().scopeValue)
        assertEquals("", remapped.timers.first().scopeValue)
        assertEquals("", remapped.shortcuts.first().scopeValue)
    }

    @Test
    fun remappingWithoutChangesReturnsTheSameBundle() {
        val bundle = ShareBundle(kind = ShareKind.SHARE, macros = listOf(macro()))

        assertEquals(bundle, bundle.remapCharacterScope(emptyMap()))
    }

    @Test
    fun autoReconnectIsPerCharacterAndSurvivesTheRoundTrip() {
        val bundle = ShareBundle(kind = ShareKind.BACKUP, characters = listOf(character()))

        val decoded = ShareFormat.decode(ShareFormat.encode(bundle))!!

        assertTrue(decoded.characters.first().autoReconnect)
    }

    @Test
    fun characterWithoutAutoReconnectFieldDefaultsToOff() {
        val json = """
            {"app":"jMud","formatVersion":2,"kind":"backup",
             "characters":[{"id":"c1","name":"Zanand","host":"mud.exemplo.com","port":4000}]}
        """.trimIndent()

        val decoded = ShareFormat.decode(json)!!

        assertFalse(decoded.characters.first().autoReconnect)
    }
}

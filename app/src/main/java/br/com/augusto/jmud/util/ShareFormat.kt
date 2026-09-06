package br.com.augusto.jmud.util

import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.MudMacro
import br.com.augusto.jmud.domain.MudTimer
import br.com.augusto.jmud.domain.MudShortcut
import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.domain.ShortcutAction
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.domain.ShareBundle
import br.com.augusto.jmud.domain.ShareKind
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ShareFormat {

    const val APP = "jMud"
    const val FORMAT_VERSION = 2
    const val FILE_EXTENSION = "jmud"
    const val MIME_TYPE = "application/json"

    const val KEY_CHARACTERS = "characters_list"
    const val KEY_TRIGGERS = "triggers_list"
    const val KEY_TIMERS = "timers_list"
    const val KEY_MACROS = "macros_list"
    const val KEY_SHORTCUTS = "shortcuts_list"

    val LIST_PREF_KEYS = setOf(KEY_CHARACTERS, KEY_TRIGGERS, KEY_TIMERS, KEY_MACROS, KEY_SHORTCUTS)

    fun encode(bundle: ShareBundle, createdAt: String = ""): String {
        val root = JSONObject()
        root.put("app", APP)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("kind", bundle.kind)
        if (createdAt.isNotBlank()) {
            root.put("createdAt", createdAt)
        }
        val forShare = bundle.kind != ShareKind.BACKUP
        if (bundle.characters.isNotEmpty()) {
            root.put("characters", charactersToArray(bundle.characters, forShare))
        }
        if (bundle.triggers.isNotEmpty()) {
            root.put("triggers", triggersToArray(bundle.triggers))
        }
        if (bundle.timers.isNotEmpty()) {
            root.put("timers", timersToArray(bundle.timers))
        }
        if (bundle.macros.isNotEmpty()) {
            root.put("macros", macrosToArray(bundle.macros))
        }
        if (bundle.shortcuts.isNotEmpty()) {
            root.put("shortcuts", shortcutsToArray(bundle.shortcuts))
        }
        if (bundle.settings.isNotEmpty()) {
            root.put("settings", settingsToJson(bundle.settings))
        }
        return root.toString(2)
    }

    fun decode(text: String): ShareBundle? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val root = JSONObject(trimmed)
            if (root.optString("app") != APP) return null
            when {
                root.has("formatVersion") -> decodeCurrent(root)
                root.has("backupVersion") -> decodeLegacyBackup(root)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeCurrent(root: JSONObject): ShareBundle? {
        if (root.optInt("formatVersion", 0) > FORMAT_VERSION) return null
        val bundle = ShareBundle(
            kind = root.optString("kind", ShareKind.SHARE),
            characters = charactersFromArray(root.optJSONArray("characters")),
            triggers = triggersFromArray(root.optJSONArray("triggers")),
            timers = timersFromArray(root.optJSONArray("timers")),
            macros = macrosFromArray(root.optJSONArray("macros")),
            shortcuts = shortcutsFromArray(root.optJSONArray("shortcuts")),
            settings = settingsFromJson(root.optJSONObject("settings"))
        )
        return if (bundle.isEmpty()) null else bundle
    }

    private fun decodeLegacyBackup(root: JSONObject): ShareBundle? {
        val data = root.optJSONObject("data") ?: return null
        val settings = settingsFromJson(data).toMutableMap()
        val characters = charactersFromText(settings.remove(KEY_CHARACTERS) as? String)
        val triggers = triggersFromText(settings.remove(KEY_TRIGGERS) as? String)
        val timers = timersFromText(settings.remove(KEY_TIMERS) as? String)
        val macros = macrosFromText(settings.remove(KEY_MACROS) as? String)
        val shortcuts = shortcutsFromText(settings.remove(KEY_SHORTCUTS) as? String)
        val bundle = ShareBundle(
            kind = ShareKind.BACKUP,
            characters = characters,
            triggers = triggers,
            timers = timers,
            macros = macros,
            shortcuts = shortcuts,
            settings = settings
        )
        return if (bundle.isEmpty()) null else bundle
    }

    fun charactersToText(characters: List<MudCharacter>): String =
        charactersToArray(characters, false).toString()

    fun charactersFromText(text: String?): List<MudCharacter> =
        charactersFromArray(parseArray(text))

    fun triggersToText(triggers: List<MudTrigger>): String = triggersToArray(triggers).toString()

    fun triggersFromText(text: String?): List<MudTrigger> = triggersFromArray(parseArray(text))

    fun timersToText(timers: List<MudTimer>): String = timersToArray(timers).toString()

    fun timersFromText(text: String?): List<MudTimer> = timersFromArray(parseArray(text))

    fun macrosToText(macros: List<MudMacro>): String = macrosToArray(macros).toString()

    fun macrosFromText(text: String?): List<MudMacro> = macrosFromArray(parseArray(text))

    fun shortcutsToText(shortcuts: List<MudShortcut>): String = shortcutsToArray(shortcuts).toString()

    fun shortcutsFromText(text: String?): List<MudShortcut> = shortcutsFromArray(parseArray(text))

    private fun parseArray(text: String?): JSONArray? {
        if (text.isNullOrBlank()) return null
        return try {
            JSONArray(text)
        } catch (e: Exception) {
            null
        }
    }

    private fun charactersToArray(characters: List<MudCharacter>, forShare: Boolean): JSONArray {
        val array = JSONArray()
        for (c in characters) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("host", c.host)
            obj.put("port", c.port)
            obj.put("password", if (forShare) "" else c.password)
            obj.put("autoLogin", if (forShare) false else c.autoLogin)
            obj.put("postConnectCommands", c.postConnectCommands)
            obj.put("useTTS", c.useTTS)
            obj.put("playSounds", c.playSounds)
            obj.put("soundsFolder", c.soundsFolder)
            obj.put("autoReconnect", c.autoReconnect)
            array.put(obj)
        }
        return array
    }

    private fun charactersFromArray(array: JSONArray?): List<MudCharacter> {
        val list = mutableListOf<MudCharacter>()
        if (array == null) return list
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val host = obj.optString("host", "")
            if (host.isBlank()) continue
            list.add(
                MudCharacter(
                    id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                    name = obj.optString("name", ""),
                    host = host,
                    port = obj.optInt("port", 4000),
                    password = obj.optString("password", ""),
                    autoLogin = obj.optBoolean("autoLogin", false),
                    postConnectCommands = obj.optString("postConnectCommands", ""),
                    useTTS = obj.optBoolean("useTTS", true),
                    playSounds = obj.optBoolean("playSounds", true),
                    soundsFolder = obj.optString("soundsFolder", ""),
                    autoReconnect = obj.optBoolean("autoReconnect", false)
                )
            )
        }
        return list
    }

    private fun triggersToArray(triggers: List<MudTrigger>): JSONArray {
        val array = JSONArray()
        for (t in triggers) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("message", t.message)
            obj.put("matchType", t.matchType)
            obj.put("commands", t.commands)
            obj.put("scope", t.scope)
            obj.put("scopeValue", t.scopeValue)
            obj.put("enabled", t.enabled)
            obj.put("ignoreLine", t.ignoreLine)
            obj.put("historyName", t.historyName)
            obj.put("soundName", t.soundName)
            array.put(obj)
        }
        return array
    }

    private fun triggersFromArray(array: JSONArray?): List<MudTrigger> {
        val list = mutableListOf<MudTrigger>()
        if (array == null) return list
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                MudTrigger(
                    id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                    name = obj.optString("name", ""),
                    message = obj.optString("message", ""),
                    matchType = obj.optString("matchType", MudTrigger.MATCH_START),
                    commands = obj.optString("commands", ""),
                    scope = obj.optString("scope", Scope.ALL),
                    scopeValue = obj.optString("scopeValue", ""),
                    enabled = obj.optBoolean("enabled", true),
                    ignoreLine = obj.optBoolean("ignoreLine", false),
                    historyName = obj.optString("historyName", ""),
                    soundName = obj.optString("soundName", "")
                )
            )
        }
        return list
    }

    private fun timersToArray(timers: List<MudTimer>): JSONArray {
        val array = JSONArray()
        for (t in timers) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("seconds", t.seconds)
            obj.put("commands", t.commands)
            obj.put("scope", t.scope)
            obj.put("scopeValue", t.scopeValue)
            obj.put("enabled", t.enabled)
            array.put(obj)
        }
        return array
    }

    private fun timersFromArray(array: JSONArray?): List<MudTimer> {
        val list = mutableListOf<MudTimer>()
        if (array == null) return list
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                MudTimer(
                    id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                    seconds = obj.optInt("seconds", 60).coerceAtLeast(1),
                    commands = obj.optString("commands", ""),
                    scope = obj.optString("scope", Scope.ALL),
                    scopeValue = obj.optString("scopeValue", ""),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        return list
    }

    private fun macrosToArray(macros: List<MudMacro>): JSONArray {
        val array = JSONArray()
        for (m in macros) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("commands", m.commands)
            obj.put("scope", m.scope)
            obj.put("scopeValue", m.scopeValue)
            obj.put("enabled", m.enabled)
            obj.put("intervalMs", m.intervalMs)
            array.put(obj)
        }
        return array
    }

    private fun macrosFromArray(array: JSONArray?): List<MudMacro> {
        val list = mutableListOf<MudMacro>()
        if (array == null) return list
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(
                MudMacro(
                    id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                    name = obj.optString("name", ""),
                    commands = obj.optString("commands", ""),
                    scope = obj.optString("scope", Scope.ALL),
                    scopeValue = obj.optString("scopeValue", ""),
                    enabled = obj.optBoolean("enabled", true),
                    intervalMs = IntervalFormat.sanitize(
                        obj.optInt("intervalMs", IntervalFormat.INHERIT)
                    )
                )
            )
        }
        return list
    }

    private fun shortcutsToArray(shortcuts: List<MudShortcut>): JSONArray {
        val array = JSONArray()
        for (item in shortcuts) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("label", item.label)
            obj.put("command", item.command)
            obj.put("action", item.action)
            obj.put("scope", item.scope)
            obj.put("scopeValue", item.scopeValue)
            obj.put("enabled", item.enabled)
            obj.put("direction", item.direction)
            array.put(obj)
        }
        return array
    }

    private fun shortcutsFromArray(array: JSONArray?): List<MudShortcut> {
        val list = mutableListOf<MudShortcut>()
        if (array == null) return list
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val label = obj.optString("label", "")
            if (label.isBlank()) continue
            list.add(
                MudShortcut(
                    id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                    label = label,
                    command = obj.optString("command", ""),
                    action = ShortcutAction.normalize(obj.optString("action", ShortcutAction.COMMAND)),
                    scope = obj.optString("scope", Scope.ALL),
                    scopeValue = obj.optString("scopeValue", ""),
                    enabled = obj.optBoolean("enabled", true),
                    direction = obj.optString("direction", "")
                )
            )
        }
        return list
    }

    private fun settingsToJson(settings: Map<String, Any>): JSONObject {
        val data = JSONObject()
        for ((key, value) in settings) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is Double -> entry.put("t", "f").put("v", value)
                is String -> entry.put("t", "s").put("v", value)
                else -> continue
            }
            data.put(key, entry)
        }
        return data
    }

    private fun settingsFromJson(data: JSONObject?): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        if (data == null) return map
        for (key in data.keys()) {
            val entry = data.optJSONObject(key) ?: continue
            val value: Any = when (entry.optString("t")) {
                "b" -> entry.optBoolean("v")
                "i" -> entry.optInt("v")
                "l" -> entry.optLong("v")
                "f" -> entry.optDouble("v").toFloat()
                "s" -> entry.optString("v")
                else -> continue
            }
            map[key] = value
        }
        return map
    }
}

package br.com.augusto.jmud.domain

data class MudShortcut(
    val id: String,
    val label: String,
    val command: String,
    val action: String = ShortcutAction.COMMAND,
    val scope: String = Scope.ALL,
    val scopeValue: String = "",
    val enabled: Boolean = true,
    val direction: String = ""
)

object ShortcutAction {
    const val COMMAND = "COMMAND"
    const val REPEAT_LAST = "REPEAT_LAST"
    const val SPEAK_LAST = "SPEAK_LAST"
    const val STOP_SOUND = "STOP_SOUND"
    const val STOP_MACRO = "STOP_MACRO"

    val ALL = listOf(COMMAND, REPEAT_LAST, SPEAK_LAST, STOP_SOUND, STOP_MACRO)

    fun normalize(value: String): String = if (ALL.contains(value)) value else COMMAND
}

object ShortcutDirection {
    const val NORTH = "N"
    const val SOUTH = "S"
    const val EAST = "E"
    const val WEST = "W"

    val PRESET_ORDER = listOf("NW", NORTH, "NE", WEST, "LOOK", EAST, "SW", SOUTH, "SE", "UP", "DOWN")
}

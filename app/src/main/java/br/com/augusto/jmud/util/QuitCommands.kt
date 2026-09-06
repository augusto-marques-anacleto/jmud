package br.com.augusto.jmud.util

object QuitCommands {

    const val DEFAULT = "fim, sair, quit, exit, logout"

    fun parse(text: String): List<String> =
        text.split(',', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun matches(command: String, quitCommands: List<String>): Boolean {
        if (quitCommands.isEmpty()) return false
        val cleaned = command.trim().lowercase()
        if (cleaned.isEmpty()) return false
        return quitCommands.any { it == cleaned }
    }
}

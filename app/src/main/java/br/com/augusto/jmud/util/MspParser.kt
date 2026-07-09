package br.com.augusto.jmud.util

data class MspCommand(
    val isMusic: Boolean,
    val fileName: String,
    val volume: Int = 100,
    val loops: Int = 1,
    val url: String = "",
    val priority: Int = 50,
    val type: String = "",
    val continueMusic: Boolean = true
)

data class ParsedMsp(
    val cleanText: String,
    val commands: List<MspCommand>
)

object MspParser {
    private val mspRegex = Regex("^\\s*!!(SOUND|MUSIC)\\(([^)\\r\\n]*)(?:\\)|$)", RegexOption.IGNORE_CASE)
    private val paramSeparatorRegex = Regex("[\\s,]+")

    fun parse(input: String): ParsedMsp {
        if (!input.trimStart().startsWith("!!")) {
            return ParsedMsp(input, emptyList())
        }
        return try {
            val commands = mutableListOf<MspCommand>()
            var remaining = input
            while (true) {
                val match = mspRegex.find(remaining) ?: break
                val inner = match.groupValues[2].trim()
                if (inner.isNotBlank()) {
                    commands.add(parseCommand(match.groupValues[1].equals("MUSIC", ignoreCase = true), inner))
                }
                remaining = remaining.removeRange(match.range)
            }
            ParsedMsp(remaining.trim(), commands)
        } catch (e: Exception) {
            ParsedMsp(input, emptyList())
        }
    }

    private fun parseCommand(isMusic: Boolean, inner: String): MspCommand {
        val parts = inner.split(paramSeparatorRegex).filter { it.isNotBlank() }
        val fileName = parts.firstOrNull() ?: ""

        var volume = 100
        var loops = 1
        var url = ""
        var priority = 50
        var type = ""
        var continueMusic = true

        for (i in 1 until parts.size) {
            val part = parts[i]
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val key = part.substring(0, separator).uppercase()
            val value = part.substring(separator + 1)
            when (key) {
                "V" -> volume = value.toIntOrNull() ?: 100
                "L" -> loops = value.toIntOrNull() ?: 1
                "P" -> priority = value.toIntOrNull() ?: 50
                "T" -> type = value
                "U" -> url = value
                "C" -> continueMusic = value.trim() != "0"
            }
        }

        return MspCommand(
            isMusic = isMusic,
            fileName = fileName,
            volume = volume,
            loops = loops,
            url = url,
            priority = priority,
            type = type,
            continueMusic = continueMusic
        )
    }
}

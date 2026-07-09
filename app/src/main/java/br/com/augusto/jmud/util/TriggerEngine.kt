package br.com.augusto.jmud.util

import br.com.augusto.jmud.domain.MudTrigger

data class TriggerMatch(
    val line: String,
    val matchStart: Int,
    val groups: List<String>
)

object TriggerEngine {

    private val regexCache = mutableMapOf<String, Regex?>()
    private val whitespaceRegex = Regex("\\s+")

    fun match(trigger: MudTrigger, line: String): TriggerMatch? {
        val message = trigger.message.trim()
        if (message.isEmpty()) return null
        val target = line.trim()
        if (target.isEmpty()) return null
        return when (trigger.matchType) {
            MudTrigger.MATCH_START ->
                if (target.startsWith(message, ignoreCase = true)) TriggerMatch(target, 0, emptyList()) else null
            MudTrigger.MATCH_END ->
                if (target.endsWith(message, ignoreCase = true)) TriggerMatch(target, target.length - message.length, emptyList()) else null
            MudTrigger.MATCH_EXACT ->
                if (target.equals(message, ignoreCase = true)) TriggerMatch(target, 0, emptyList()) else null
            MudTrigger.MATCH_PATTERN -> {
                val regex = regexCache.getOrPut(message) { buildRegex(message) } ?: return null
                val result = regex.matchEntire(target) ?: return null
                TriggerMatch(target, 0, result.groupValues.drop(1))
            }
            else -> {
                val index = target.indexOf(message, ignoreCase = true)
                if (index >= 0) TriggerMatch(target, index, emptyList()) else null
            }
        }
    }

    fun expandCommands(commands: String, match: TriggerMatch): List<String> {
        return substitute(commands, match)
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun substitute(text: String, match: TriggerMatch): String {
        val words = match.line.substring(match.matchStart)
            .split(whitespaceRegex)
            .filter { it.isNotBlank() }
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    out.append(text[i + 1])
                    i += 2
                }
                c == '$' && i + 1 < text.length -> {
                    val code = text[i + 1]
                    when {
                        code == '0' -> out.append(match.line)
                        code.isDigit() -> {
                            val groupIndex = code.digitToInt() - 1
                            if (groupIndex < match.groups.size) {
                                out.append(match.groups[groupIndex])
                            }
                        }
                        code.lowercaseChar() == 'z' -> out.append('\n')
                        code.lowercaseChar() in 's'..'y' -> {
                            val wordIndex = code.lowercaseChar() - 's' + 1
                            if (wordIndex < words.size) {
                                out.append(words[wordIndex])
                            }
                        }
                        else -> {
                            out.append(c)
                            out.append(code)
                        }
                    }
                    i += 2
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun buildRegex(pattern: String): Regex? {
        return try {
            val sb = StringBuilder()
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                when {
                    c == '\\' && i + 1 < pattern.length -> {
                        sb.append(Regex.escape(pattern[i + 1].toString()))
                        i++
                    }
                    c == '*' -> sb.append("(.*)")
                    c == '@' -> sb.append("(\\S+)")
                    c == '?' -> sb.append("(.)")
                    c == '&' -> sb.append("(\\d+)")
                    else -> sb.append(Regex.escape(c.toString()))
                }
                i++
            }
            Regex(sb.toString(), RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            null
        }
    }
}

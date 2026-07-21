package br.com.augusto.jmud.util

object MacroEngine {

    const val PREFIX = '#'

    private val whitespaceRegex = Regex("\\s+")

    data class Invocation(val name: String, val argsString: String)

    fun parseInvocation(input: String): Invocation? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed[0] != PREFIX) return null
        val withoutPrefix = trimmed.substring(1)
        if (withoutPrefix.isBlank()) return null
        val spaceIndex = withoutPrefix.indexOfFirst { it.isWhitespace() }
        return if (spaceIndex == -1) {
            Invocation(withoutPrefix, "")
        } else {
            Invocation(withoutPrefix.substring(0, spaceIndex), withoutPrefix.substring(spaceIndex + 1).trim())
        }
    }

    fun expandCommands(commands: String, argsString: String): List<String> {
        val args = argsString.split(whitespaceRegex).filter { it.isNotBlank() }
        val lines = substitute(commands, argsString, args)
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (argsString.isNotBlank() && !usesPlaceholders(commands) && lines.isNotEmpty()) {
            val lastIndex = lines.lastIndex
            lines[lastIndex] = "${lines[lastIndex]} $argsString"
        }

        return lines
    }

    private fun usesPlaceholders(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                i += 2
                continue
            }
            if (c == '$' && i + 1 < text.length && text[i + 1].isDigit()) {
                return true
            }
            i++
        }
        return false
    }

    private fun substitute(text: String, argsString: String, args: List<String>): String {
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
                        code == '0' -> out.append(argsString)
                        code.isDigit() -> {
                            val index = code.digitToInt() - 1
                            if (index < args.size) out.append(args[index])
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
}

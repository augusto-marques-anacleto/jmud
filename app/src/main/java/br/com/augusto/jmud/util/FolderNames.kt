package br.com.augusto.jmud.util

object FolderNames {

    private val domainSuffixes = setOf(
        "com", "net", "org", "gov", "edu", "mil", "info", "biz", "online",
        "io", "co", "me", "app", "games", "game",
        "br", "pt", "us", "uk", "es", "de", "fr", "it", "nl", "eu", "ar", "mx"
    )

    fun suggest(host: String, characterName: String): String {
        val parts = host.trim().lowercase().split('.').filter { it.isNotBlank() }
        val trimmed = parts.dropLastWhile { it in domainSuffixes }
        val candidate = trimmed.lastOrNull { part -> part.any { it.isLetter() } }
        return candidate ?: characterName.trim().replace(" ", "_").lowercase()
    }
}

package br.com.augusto.jmud.domain

enum class ShareSection {
    CHARACTERS,
    TRIGGERS,
    TIMERS,
    MACROS,
    SHORTCUTS,
    SETTINGS
}

object ShareKind {
    const val BACKUP = "backup"
    const val SHARE = "share"
}

data class ShareBundle(
    val kind: String = ShareKind.SHARE,
    val characters: List<MudCharacter> = emptyList(),
    val triggers: List<MudTrigger> = emptyList(),
    val timers: List<MudTimer> = emptyList(),
    val macros: List<MudMacro> = emptyList(),
    val shortcuts: List<MudShortcut> = emptyList(),
    val settings: Map<String, Any> = emptyMap()
) {

    fun countOf(section: ShareSection): Int = when (section) {
        ShareSection.CHARACTERS -> characters.size
        ShareSection.TRIGGERS -> triggers.size
        ShareSection.TIMERS -> timers.size
        ShareSection.MACROS -> macros.size
        ShareSection.SHORTCUTS -> shortcuts.size
        ShareSection.SETTINGS -> settings.size
    }

    fun availableSections(): List<ShareSection> =
        ShareSection.entries.filter { countOf(it) > 0 }

    fun isEmpty(): Boolean = availableSections().isEmpty()

    fun itemCount(): Int =
        characters.size + triggers.size + timers.size + macros.size + shortcuts.size

    fun filtered(sections: Set<ShareSection>): ShareBundle = ShareBundle(
        kind = kind,
        characters = if (sections.contains(ShareSection.CHARACTERS)) characters else emptyList(),
        triggers = if (sections.contains(ShareSection.TRIGGERS)) triggers else emptyList(),
        timers = if (sections.contains(ShareSection.TIMERS)) timers else emptyList(),
        macros = if (sections.contains(ShareSection.MACROS)) macros else emptyList(),
        shortcuts = if (sections.contains(ShareSection.SHORTCUTS)) shortcuts else emptyList(),
        settings = if (sections.contains(ShareSection.SETTINGS)) settings else emptyMap()
    )

    fun remapCharacterScope(idMap: Map<String, String>): ShareBundle {
        if (idMap.isEmpty()) return this
        fun remap(scope: String, scopeValue: String): String =
            if (scope == Scope.CHARACTER) idMap[scopeValue] ?: scopeValue else scopeValue

        return copy(
            triggers = triggers.map { it.copy(scopeValue = remap(it.scope, it.scopeValue)) },
            timers = timers.map { it.copy(scopeValue = remap(it.scope, it.scopeValue)) },
            macros = macros.map { it.copy(scopeValue = remap(it.scope, it.scopeValue)) },
            shortcuts = shortcuts.map { it.copy(scopeValue = remap(it.scope, it.scopeValue)) }
        )
    }

    fun withScope(scope: String, scopeValue: String): ShareBundle = copy(
        triggers = triggers.map { it.copy(scope = scope, scopeValue = scopeValue) },
        timers = timers.map { it.copy(scope = scope, scopeValue = scopeValue) },
        macros = macros.map { it.copy(scope = scope, scopeValue = scopeValue) },
        shortcuts = shortcuts.map { it.copy(scope = scope, scopeValue = scopeValue) }
    )
}

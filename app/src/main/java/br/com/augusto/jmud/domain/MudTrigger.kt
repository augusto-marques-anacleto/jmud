package br.com.augusto.jmud.domain

data class MudTrigger(
    val id: String,
    val name: String,
    val message: String,
    val matchType: String,
    val commands: String,
    val scope: String,
    val scopeValue: String,
    val enabled: Boolean,
    val ignoreLine: Boolean = false,
    val historyName: String = "",
    val soundName: String = ""
) {
    companion object {
        const val MATCH_START = "START"
        const val MATCH_CONTAINS = "CONTAINS"
        const val MATCH_END = "END"
        const val MATCH_EXACT = "EXACT"
        const val MATCH_PATTERN = "PATTERN"
    }
}

package br.com.augusto.jmud.domain

data class MudTimer(
    val id: String,
    val seconds: Int,
    val commands: String,
    val scope: String,
    val scopeValue: String,
    val enabled: Boolean
) {
    companion object {
        const val SCOPE_ALL = Scope.ALL
        const val SCOPE_MUD = Scope.MUD
        const val SCOPE_CHARACTER = Scope.CHARACTER
    }
}

package br.com.augusto.jmud.domain

data class MudMacro(
    val id: String,
    val name: String,
    val commands: String,
    val scope: String,
    val scopeValue: String,
    val enabled: Boolean
)

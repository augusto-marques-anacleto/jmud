package br.com.augusto.jmud.domain

import br.com.augusto.jmud.util.IntervalFormat

data class MudMacro(
    val id: String,
    val name: String,
    val commands: String,
    val scope: String,
    val scopeValue: String,
    val enabled: Boolean,
    val intervalMs: Int = IntervalFormat.INHERIT
)

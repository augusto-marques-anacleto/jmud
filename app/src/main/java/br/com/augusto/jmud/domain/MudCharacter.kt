package br.com.augusto.jmud.domain

data class MudCharacter(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val password: String,
    val autoLogin: Boolean,
    val postConnectCommands: String,
    val useTTS: Boolean,
    val playSounds: Boolean,
    val soundsFolder: String
)
package br.com.augusto.jmud.data.network

sealed interface MudEvent {
    object Connected : MudEvent
    data class LineReceived(val text: String) : MudEvent
    data class ConnectionFailed(val detail: String?) : MudEvent
    object Disconnected : MudEvent
    object SendFailed : MudEvent
}

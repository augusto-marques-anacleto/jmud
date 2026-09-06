package br.com.augusto.jmud.data.network

sealed interface MudEvent {
    object Connected : MudEvent
    data class LineReceived(val text: String) : MudEvent
    data class ConnectionFailed(val reason: ConnectionFailureReason, val detail: String?) : MudEvent
    data class Disconnected(val serverClosed: Boolean) : MudEvent
    object SendFailed : MudEvent
}

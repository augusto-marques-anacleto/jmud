package br.com.augusto.jmud.data.network

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ConnectionFailureReason {
    NO_INTERNET,
    HOST_NOT_FOUND,
    SERVER_UNAVAILABLE,
    TIMEOUT,
    UNKNOWN
}

object ConnectionFailureClassifier {

    fun classify(error: Throwable?, hasNetwork: Boolean): ConnectionFailureReason {
        if (!hasNetwork) return ConnectionFailureReason.NO_INTERNET

        var current = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            when (current) {
                is SocketTimeoutException -> return ConnectionFailureReason.TIMEOUT
                is UnknownHostException -> return ConnectionFailureReason.HOST_NOT_FOUND
                is NoRouteToHostException -> return ConnectionFailureReason.SERVER_UNAVAILABLE
                is PortUnreachableException -> return ConnectionFailureReason.SERVER_UNAVAILABLE
                is ConnectException -> classifyMessage(current.message)?.let { return it }
            }
            classifyMessage(current.message)?.let { return it }
            current = current.cause
            depth++
        }

        return if (error is ConnectException) {
            ConnectionFailureReason.SERVER_UNAVAILABLE
        } else {
            ConnectionFailureReason.UNKNOWN
        }
    }

    private fun classifyMessage(message: String?): ConnectionFailureReason? {
        if (message.isNullOrBlank()) return null
        val text = message.lowercase()
        return when {
            text.contains("econnrefused") || text.contains("connection refused") ->
                ConnectionFailureReason.SERVER_UNAVAILABLE
            text.contains("ehostunreach") || text.contains("no route to host") ->
                ConnectionFailureReason.SERVER_UNAVAILABLE
            text.contains("econnreset") || text.contains("connection reset") ->
                ConnectionFailureReason.SERVER_UNAVAILABLE
            text.contains("enetunreach") || text.contains("network is unreachable") ->
                ConnectionFailureReason.NO_INTERNET
            text.contains("enetdown") || text.contains("network is down") ->
                ConnectionFailureReason.NO_INTERNET
            text.contains("etimedout") || text.contains("timed out") || text.contains("timeout") ->
                ConnectionFailureReason.TIMEOUT
            text.contains("unable to resolve host") || text.contains("no address associated") ->
                ConnectionFailureReason.HOST_NOT_FOUND
            else -> null
        }
    }

    private const val MAX_CAUSE_DEPTH = 5
}

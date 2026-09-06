package br.com.augusto.jmud

import br.com.augusto.jmud.data.network.ConnectionFailureClassifier
import br.com.augusto.jmud.data.network.ConnectionFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ConnectionFailureClassifierTest {

    @Test
    fun withoutNetworkAlwaysReportsNoInternet() {
        val error = ConnectException("ECONNREFUSED (Connection refused)")
        assertEquals(ConnectionFailureReason.NO_INTERNET, ConnectionFailureClassifier.classify(error, false))
    }

    @Test
    fun refusedConnectionMeansServerUnavailable() {
        val error = ConnectException(
            "failed to connect to mud.exemplo.com/1.2.3.4 (port 4000) from /10.0.0.2 (port 41234) after 20000ms: isConnected failed: ECONNREFUSED (Connection refused)"
        )
        assertEquals(ConnectionFailureReason.SERVER_UNAVAILABLE, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun connectTimeoutMeansTimeout() {
        val error = SocketTimeoutException("failed to connect to mud.exemplo.com (port 4000) after 20000ms")
        assertEquals(ConnectionFailureReason.TIMEOUT, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun etimedoutInsideConnectExceptionMeansTimeout() {
        val error = ConnectException("isConnected failed: ETIMEDOUT (Connection timed out)")
        assertEquals(ConnectionFailureReason.TIMEOUT, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun unknownHostMeansHostNotFound() {
        val error = UnknownHostException("Unable to resolve host \"mud.inexistente\": No address associated with hostname")
        assertEquals(ConnectionFailureReason.HOST_NOT_FOUND, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun unreachableNetworkMeansNoInternet() {
        val error = ConnectException("isConnected failed: ENETUNREACH (Network is unreachable)")
        assertEquals(ConnectionFailureReason.NO_INTERNET, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun noRouteToHostMeansServerUnavailable() {
        val error = NoRouteToHostException("EHOSTUNREACH (No route to host)")
        assertEquals(ConnectionFailureReason.SERVER_UNAVAILABLE, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun causeChainIsInspected() {
        val error = IOException("falha ao abrir socket", ConnectException("ECONNREFUSED (Connection refused)"))
        assertEquals(ConnectionFailureReason.SERVER_UNAVAILABLE, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun connectExceptionWithoutMessageIsServerUnavailable() {
        assertEquals(ConnectionFailureReason.SERVER_UNAVAILABLE, ConnectionFailureClassifier.classify(ConnectException(), true))
    }

    @Test
    fun unrecognizedErrorIsUnknown() {
        val error = IllegalStateException("algo inesperado")
        assertEquals(ConnectionFailureReason.UNKNOWN, ConnectionFailureClassifier.classify(error, true))
    }

    @Test
    fun nullErrorWithNetworkIsUnknown() {
        assertEquals(ConnectionFailureReason.UNKNOWN, ConnectionFailureClassifier.classify(null, true))
    }
}

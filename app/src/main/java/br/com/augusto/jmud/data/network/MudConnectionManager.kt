package br.com.augusto.jmud.data.network

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object MudConnectionManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _events = MutableSharedFlow<MudEvent>()
    val events: SharedFlow<MudEvent> = _events.asSharedFlow()

    private var socket: Socket? = null

    @Volatile
    private var charset: Charset = Charsets.ISO_8859_1

    fun setEncoding(name: String) {
        charset = try {
            Charset.forName(name)
        } catch (e: Exception) {
            Charsets.ISO_8859_1
        }
    }

    private const val TELNET_SE = 240
    private const val TELNET_SB = 250
    private const val TELNET_WILL = 251
    private const val TELNET_WONT = 252
    private const val TELNET_DO = 253
    private const val TELNET_DONT = 254
    private const val TELNET_IAC = 255
    private const val TELNET_OPTION_SGA = 3

    private enum class TelnetState { DATA, IAC, OPTION, SUBNEG, SUBNEG_IAC }

    private var telnetState = TelnetState.DATA
    private var telnetVerb = 0

    private val ansiRegex = Regex("\\x1B\\[[0-9;?]*[ -/]*[@-~]")
    private val escRegex = Regex("\\x1B.")
    private val controlRegex = Regex("[\\x00-\\x08\\x0B-\\x1F\\x7F]")

    private fun stripTelnet(buffer: ByteArray, length: Int, responses: ByteArrayOutputStream): ByteArray {
        val out = ByteArrayOutputStream(length)
        for (i in 0 until length) {
            val b = buffer[i].toInt() and 0xFF
            when (telnetState) {
                TelnetState.DATA -> {
                    if (b == TELNET_IAC) {
                        telnetState = TelnetState.IAC
                    } else {
                        out.write(b)
                    }
                }
                TelnetState.IAC -> {
                    when (b) {
                        TELNET_IAC -> {
                            out.write(b)
                            telnetState = TelnetState.DATA
                        }
                        TELNET_SB -> telnetState = TelnetState.SUBNEG
                        in TELNET_WILL..TELNET_DONT -> {
                            telnetVerb = b
                            telnetState = TelnetState.OPTION
                        }
                        else -> telnetState = TelnetState.DATA
                    }
                }
                TelnetState.OPTION -> {
                    negotiate(telnetVerb, b, responses)
                    telnetState = TelnetState.DATA
                }
                TelnetState.SUBNEG -> {
                    if (b == TELNET_IAC) {
                        telnetState = TelnetState.SUBNEG_IAC
                    }
                }
                TelnetState.SUBNEG_IAC -> {
                    telnetState = if (b == TELNET_SE) TelnetState.DATA else TelnetState.SUBNEG
                }
            }
        }
        return out.toByteArray()
    }

    private fun negotiate(verb: Int, option: Int, responses: ByteArrayOutputStream) {
        when (verb) {
            TELNET_WILL -> {
                responses.write(TELNET_IAC)
                responses.write(if (option == TELNET_OPTION_SGA) TELNET_DO else TELNET_DONT)
                responses.write(option)
            }
            TELNET_DO -> {
                responses.write(TELNET_IAC)
                responses.write(TELNET_WONT)
                responses.write(option)
            }
        }
    }

    private fun sendRaw(bytes: ByteArray) {
        try {
            val output = socket?.getOutputStream() ?: return
            output.write(bytes)
            output.flush()
        } catch (e: Exception) {
        }
    }

    fun connect(context: Context, host: String, port: Int) {
        scope.launch {
            try {
                disconnectSocketOnly()
                telnetState = TelnetState.DATA
                telnetVerb = 0
                val intent = Intent(context, MudService::class.java)
                context.startService(intent)

                val activeSocket = Socket(host, port)
                activeSocket.keepAlive = true
                activeSocket.tcpNoDelay = true
                socket = activeSocket

                listenForMessages(activeSocket)
            } catch (e: Exception) {
                _events.emit(MudEvent.ConnectionFailed(e.message))
            }
        }
    }

    private suspend fun listenForMessages(activeSocket: Socket) {
        try {
            val inputStream = activeSocket.getInputStream()
            val buffer = ByteArray(8192)
            val lineBuffer = ByteArrayOutputStream()
            val responses = ByteArrayOutputStream()
            val newline = '\n'.code.toByte()

            while (activeSocket.isConnected && !activeSocket.isClosed) {
                try {
                    activeSocket.soTimeout = if (lineBuffer.size() == 0) 0 else 300
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    val filtered = stripTelnet(buffer, bytesRead, responses)
                    if (responses.size() > 0) {
                        sendRaw(responses.toByteArray())
                        responses.reset()
                    }
                    for (byte in filtered) {
                        if (byte == newline) {
                            processLine(decodeBytes(lineBuffer.toByteArray()).trimEnd('\r'))
                            lineBuffer.reset()
                        } else {
                            lineBuffer.write(byte.toInt())
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    if (lineBuffer.size() > 0) {
                        processLine(decodeBytes(lineBuffer.toByteArray()))
                        lineBuffer.reset()
                    }
                }
            }

            if (lineBuffer.size() > 0) {
                processLine(decodeBytes(lineBuffer.toByteArray()))
            }
        } catch (e: Exception) {
        } finally {
            val isCurrent = socket === activeSocket
            try {
                activeSocket.close()
            } catch (e: Exception) {
            }
            if (isCurrent) {
                socket = null
                _events.emit(MudEvent.Disconnected)
            }
        }
    }

    private fun decodeBytes(bytes: ByteArray): String =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: Exception) {
            String(bytes, Charsets.ISO_8859_1)
        }

    private suspend fun processLine(line: String) {
        val cleaned = line
            .replace(ansiRegex, "")
            .replace(escRegex, "")
            .replace(controlRegex, "")
        _events.emit(MudEvent.LineReceived(cleaned))
    }

    fun sendMessage(message: String) {
        scope.launch {
            try {
                val output = socket?.getOutputStream() ?: throw IllegalStateException()
                output.write(("$message\r\n").toByteArray(charset))
                output.flush()
            } catch (e: Exception) {
                disconnectSocketOnly()
                _events.emit(MudEvent.SendFailed)
            }
        }
    }

    private fun disconnectSocketOnly() {
        try {
            socket?.close()
        } catch (e: Exception) {
        } finally {
            socket = null
        }
    }

    fun closeConnection() {
        disconnectSocketOnly()
    }

    fun disconnect(context: Context) {
        disconnectSocketOnly()
        val intent = Intent(context, MudService::class.java)
        context.stopService(intent)
    }
}

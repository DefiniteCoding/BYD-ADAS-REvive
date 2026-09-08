package com.definitecoding.bydadasrevive.adb

import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** adbd is listening in TLS mode, which the legacy protocol cannot talk to. */
class AdbTlsRequiredException : IOException(
    "adbd is in wireless-debugging TLS mode. Run \"adb tcpip 5555\" to switch it to the legacy port."
)

/** The car rejected our key, or nobody tapped Allow on the debugging prompt. */
class AdbAuthRejectedException : IOException(
    "The car did not accept this app's adb key. Look for the \"Allow debugging?\" prompt on the car screen."
)

data class ShellResult(val output: String, val exitCode: Int?) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Minimal adb client speaking the legacy (pre-TLS) protocol over a plain socket.
 * Enough to authenticate and run shell commands, which is all this app needs.
 *
 * The car reaches itself: adbd is bound on loopback after "adb tcpip 5555", so no
 * network hop and no wireless-debugging pairing handshake is involved.
 */
class AdbConnection private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: OutputStream,
    val banner: String,
) : Closeable {

    private var nextLocalId = 1

    /** Runs [command] through `sh -c` on the device and collects stdout and stderr. */
    fun shell(command: String): ShellResult {
        val localId = nextLocalId++
        // The shell service gives us no exit status of its own, so carry it in the stream.
        val wrapped = "$command; echo \"$RC_SENTINEL\$?\""
        write(AdbMessage(A_OPEN, localId, 0, nulTerminated("shell:$wrapped")))

        val collected = StringBuilder()
        var closed = false
        while (!closed) {
            val message = read()
            when (message.command) {
                A_OKAY -> Unit
                A_WRTE -> {
                    if (message.arg1 == localId) {
                        collected.append(String(message.payload))
                        write(AdbMessage(A_OKAY, localId, message.arg0, ByteArray(0)))
                    }
                }
                A_CLSE -> {
                    if (message.arg1 == localId) {
                        write(AdbMessage(A_CLSE, localId, message.arg0, ByteArray(0)))
                        closed = true
                    }
                }
                else -> Unit
            }
        }

        return parseExitCode(collected.toString())
    }

    private fun parseExitCode(raw: String): ShellResult {
        val marker = raw.lastIndexOf(RC_SENTINEL)
        if (marker < 0) return ShellResult(raw.trim(), null)
        val code = raw.substring(marker + RC_SENTINEL.length).trim().toIntOrNull()
        return ShellResult(raw.substring(0, marker).trim(), code)
    }

    private fun write(message: AdbMessage) {
        output.write(message.encode())
        output.flush()
    }

    private fun read(): AdbMessage = AdbMessage.readFrom(input)

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        private const val RC_SENTINEL = "__REVIVE_RC__:"

        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 5555

        /**
         * Opens and authenticates a connection. The first attempt with an already
         * trusted key returns immediately; an unknown key makes the car show the
         * "Allow debugging?" dialog, so that read waits [promptTimeoutMs].
         */
        fun connect(
            host: String = DEFAULT_HOST,
            port: Int = DEFAULT_PORT,
            keyPair: AdbKeyPair,
            connectTimeoutMs: Int = 4_000,
            ioTimeoutMs: Int = 15_000,
            promptTimeoutMs: Int = 120_000,
        ): AdbConnection {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.tcpNoDelay = true
            socket.soTimeout = ioTimeoutMs

            val input = DataInputStream(socket.getInputStream().buffered())
            val output = socket.getOutputStream()

            fun send(message: AdbMessage) {
                output.write(message.encode())
                output.flush()
            }

            send(AdbMessage(A_CNXN, A_VERSION, MAX_PAYLOAD, nulTerminated("host::features=shell_v2,cmd")))

            var signatureSent = false
            var publicKeySent = false
            try {
                while (true) {
                    val message = AdbMessage.readFrom(input)
                    when (message.command) {
                        A_AUTH -> when {
                            message.arg0 != AUTH_TOKEN -> Unit
                            !signatureSent -> {
                                signatureSent = true
                                send(AdbMessage(A_AUTH, AUTH_SIGNATURE, 0, keyPair.signToken(message.payload)))
                            }
                            !publicKeySent -> {
                                publicKeySent = true
                                socket.soTimeout = promptTimeoutMs
                                send(AdbMessage(A_AUTH, AUTH_RSAPUBLICKEY, 0, keyPair.adbPublicKey()))
                            }
                            else -> throw AdbAuthRejectedException()
                        }
                        A_CNXN -> {
                            socket.soTimeout = ioTimeoutMs
                            return AdbConnection(
                                socket,
                                input,
                                output,
                                String(message.payload).trimEnd(Char(0)),
                            )
                        }
                        A_STLS -> throw AdbTlsRequiredException()
                        else -> Unit
                    }
                }
            } catch (timeout: SocketTimeoutException) {
                socket.close()
                throw if (publicKeySent) AdbAuthRejectedException() else timeout
            } catch (error: Throwable) {
                socket.close()
                throw error
            }
        }
    }
}

package com.definitecoding.bydadasrevive.adb

import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val A_CNXN = 0x4e584e43
const val A_AUTH = 0x48545541
const val A_OPEN = 0x4e45504f
const val A_OKAY = 0x59414b4f
const val A_CLSE = 0x45534c43
const val A_WRTE = 0x45545257
const val A_STLS = 0x534c5453

const val A_VERSION = 0x01000000
const val MAX_PAYLOAD = 256 * 1024

const val AUTH_TOKEN = 1
const val AUTH_SIGNATURE = 2
const val AUTH_RSAPUBLICKEY = 3

private const val HEADER_SIZE = 24

fun nulTerminated(value: String): ByteArray = value.toByteArray() + 0.toByte()

/** One frame of the adb transport protocol. */
data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val payload: ByteArray,
) {

    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(payload.size)
        buffer.putInt(checksum(payload))
        buffer.putInt(command.inv())
        buffer.put(payload)
        return buffer.array()
    }

    // Generated data class equals/hashCode would compare the payload by identity.
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = command

    companion object {
        private fun checksum(payload: ByteArray): Int {
            var sum = 0
            for (byte in payload) sum += byte.toInt() and 0xFF
            return sum
        }

        fun readFrom(input: DataInputStream): AdbMessage {
            val header = ByteArray(HEADER_SIZE)
            input.readFully(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val length = buffer.int
            buffer.int // data checksum, not verified: adbd stopped filling it in reliably
            val magic = buffer.int

            if (magic != command.inv()) {
                throw IOException("Corrupt adb frame: command 0x%08x, magic 0x%08x".format(command, magic))
            }
            if (length < 0 || length > MAX_PAYLOAD) {
                throw IOException("Refusing adb payload of $length bytes")
            }

            val payload = ByteArray(length)
            if (length > 0) input.readFully(payload)
            return AdbMessage(command, arg0, arg1, payload)
        }
    }
}

package com.definitecoding.bydadasrevive.adb

import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * The RSA identity this app presents to adbd. Generated once and kept in the app's
 * private files dir, so the car's "Always allow" grant survives app restarts.
 */
class AdbKeyPair private constructor(
    private val privateKey: PrivateKey,
    private val publicKey: RSAPublicKey,
) {

    /**
     * adb expects a PKCS#1 v1.5 signature over the SHA-1 DigestInfo wrapping of the
     * 20-byte challenge. The token already is the digest, so we only prepend the
     * ASN.1 header and raw-encrypt with the private key.
     */
    fun signToken(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(SHA1_DIGEST_INFO + token)
    }

    /**
     * The public key in adb's own wire format: a little-endian RSAPublicKey struct,
     * base64'd, then a space, a user@host label and a NUL terminator.
     */
    fun adbPublicKey(): ByteArray {
        val modulus = publicKey.modulus
        val blob = ByteArray(PUBKEY_BLOB_SIZE)
        var offset = 0

        fun putInt(value: Long) {
            blob[offset] = (value and 0xFF).toByte()
            blob[offset + 1] = ((value shr 8) and 0xFF).toByte()
            blob[offset + 2] = ((value shr 16) and 0xFF).toByte()
            blob[offset + 3] = ((value shr 24) and 0xFF).toByte()
            offset += 4
        }

        fun putLittleEndian(value: BigInteger) {
            val bigEndian = value.toByteArray()
            var start = 0
            while (start < bigEndian.size && bigEndian[start] == ZERO_BYTE) start++
            val length = bigEndian.size - start
            for (i in 0 until length) {
                blob[offset + i] = bigEndian[bigEndian.size - 1 - i]
            }
            offset += MODULUS_BYTES
        }

        val word = BigInteger.ONE.shiftLeft(32)
        val n0inv = word.subtract(modulus.mod(word).modInverse(word))
        val rr = BigInteger.ONE.shiftLeft(MODULUS_BYTES * 8 * 2).mod(modulus)

        putInt(MODULUS_WORDS.toLong())
        putInt(n0inv.toLong())
        putLittleEndian(modulus)
        putLittleEndian(rr)
        putInt(publicKey.publicExponent.toLong())

        val encoded = Base64.encodeToString(blob, Base64.NO_WRAP)
        return "$encoded byd-adas-revive@android".toByteArray() + ZERO_BYTE
    }

    companion object {
        private const val MODULUS_BYTES = 256
        private const val MODULUS_WORDS = MODULUS_BYTES / 4
        private const val PUBKEY_BLOB_SIZE = 4 + 4 + MODULUS_BYTES + MODULUS_BYTES + 4
        private const val ZERO_BYTE: Byte = 0

        private val SHA1_DIGEST_INFO = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        /**
         * A key that will not parse is worse than no key: without a rebuild the app can
         * never authenticate again, and clearing app data would be the only cure. So an
         * unreadable pair is discarded and replaced. The car will ask to allow debugging
         * once more, because the identity it trusted is gone.
         */
        fun loadOrCreate(dir: File): AdbKeyPair {
            val privateFile = File(dir, "adbkey.pk8")
            val publicFile = File(dir, "adbkey.x509")

            if (privateFile.exists() && publicFile.exists()) {
                val restored = runCatching {
                    val factory = KeyFactory.getInstance("RSA")
                    AdbKeyPair(
                        factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())),
                        factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes())) as RSAPublicKey,
                    )
                }.getOrNull()
                if (restored != null) return restored
                privateFile.delete()
                publicFile.delete()
            }

            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val pair = generator.generateKeyPair()
            // Written aside and moved into place, so a crash mid-write leaves the old
            // pair or no pair, never half of one.
            writeAtomically(privateFile, pair.private.encoded)
            writeAtomically(publicFile, pair.public.encoded)
            return AdbKeyPair(pair.private, pair.public as RSAPublicKey)
        }

        private fun writeAtomically(target: File, bytes: ByteArray) {
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.writeBytes(bytes)
                temp.delete()
            }
        }
    }
}

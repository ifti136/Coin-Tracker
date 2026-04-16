package com.cointracker.mobile.data

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class WerkzeugPasswordHasher {

    fun verify(password: String, storedHash: String): Boolean {
        val cleanHash = storedHash.trim()
        if (!cleanHash.startsWith("pbkdf2:sha256:")) return false

        val parts = cleanHash.split("$")
        if (parts.size != 3) return false

        val meta = parts[0].split(":")
        if (meta.size < 3) return false

        val iterations = meta[2].toIntOrNull() ?: return false

        // Fix: Werkzeug uses raw strings and Hex, NOT Base64
        val saltString = parts[1]
        val expectedHexHash = parts[2]

        // Fix: Trim the password to prevent Android keyboard auto-spaces from failing the check
        val cleanPassword = password.trim()

        val calculatedHexHash = generatePbkdf2Sha256(cleanPassword, saltString, iterations)

        return calculatedHexHash == expectedHexHash
    }

    private fun generatePbkdf2Sha256(password: String, salt: String, iterations: Int): String? {
        return try {
            val chars = password.toCharArray()
            val saltBytes = salt.toByteArray(Charsets.UTF_8)

            val spec = PBEKeySpec(chars, saltBytes, iterations, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hashBytes = factory.generateSecret(spec).encoded

            bytesToHex(hashBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Fix: Proper Hexadecimal encoding to match Python's output
    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = "0123456789abcdef".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
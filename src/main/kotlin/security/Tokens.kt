package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom

object Tokens {
    private val rnd = SecureRandom()

    fun newToken(lengthBytes: Int = 32): String {
        val bytes = ByteArray(lengthBytes)
        rnd.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) } // hex
    }

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
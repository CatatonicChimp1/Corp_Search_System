package com.example.security

import org.mindrot.jbcrypt.BCrypt

object Passwords {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))
    fun verify(password: String, hash: String): Boolean = BCrypt.checkpw(password, hash)
}
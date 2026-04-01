package com.example.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.config.AppConfig
import java.time.Instant
import java.util.Date

object JwtConfig {
    private lateinit var cfg: AppConfig
    private lateinit var algorithm: Algorithm
    lateinit var verifier: JWTVerifier
        private set

    fun init(cfg: AppConfig) {
        this.cfg = cfg
        algorithm = Algorithm.HMAC256(cfg.jwt.secret)
        verifier = JWT
            .require(algorithm)
            .withIssuer(cfg.jwt.issuer)
            .withAudience(cfg.jwt.audience)
            .build()
    }

    fun sign(email: String, role: String): String {
        val now = Instant.now()
        val exp = now.plusSeconds(cfg.jwt.expiresSeconds)
        return JWT.create()
            .withIssuer(cfg.jwt.issuer)
            .withAudience(cfg.jwt.audience)
            .withClaim("email", email)
            .withClaim("role", role)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(exp))
            .sign(algorithm)
    }
}
package com.example.config

import io.ktor.server.config.ApplicationConfig


data class AppConfig(
    val dbPath: String,
    val jwt: Jwt,
    val admin: AdminSeed
) {
    data class Jwt(
        val issuer: String,
        val audience: String,
        val realm: String,
        val secret: String,
        val expiresSeconds: Long
    )

    data class AdminSeed(
        val email: String,
        val password: String
    )

    companion object {
        fun from(cfg: ApplicationConfig): AppConfig {
            return AppConfig(
                dbPath = cfg.property("app.dbPath").getString(),
                jwt = Jwt(
                    issuer = cfg.property("app.jwt.issuer").getString(),
                    audience = cfg.property("app.jwt.audience").getString(),
                    realm = cfg.property("app.jwt.realm").getString(),
                    secret = cfg.property("app.jwt.secret").getString(),
                    expiresSeconds = cfg.property("app.jwt.expiresSeconds").getString().toLong()
                ),
                admin = AdminSeed(
                    email = cfg.property("app.admin.email").getString(),
                    password = cfg.property("app.admin.password").getString()
                )
            )
        }
    }
}
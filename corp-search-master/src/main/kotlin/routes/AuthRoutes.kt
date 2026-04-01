package com.example.routes

import com.example.config.AppConfig
import com.example.db.Repos
import com.example.security.JwtConfig
import com.example.security.Passwords
import com.example.util.normalizeEmail
import com.example.util.requireTrimmed
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class LoginReq(val email: String, val password: String)
@Serializable data class LoginRes(val token: String, val email: String, val role: String)

fun Route.authRoutes(cfg: AppConfig) {
    route("/api/auth") {
        post("/login") {
            val req = call.receive<LoginReq>()
            val email = normalizeEmail(req.email)
            val password = requireTrimmed(req.password, "password", 3, 200)

            val user = Repos.findUserByEmail(email)
            if (user == null || !Passwords.verify(password, user.passwordHash)) {
                Repos.insertAudit(actorEmail = email, action = "LOGIN_FAIL")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Bad credentials"))
                return@post
            }

            val token = JwtConfig.sign(user.email, user.role)
            Repos.insertAudit(actorEmail = user.email, action = "LOGIN_OK")

            call.respond(LoginRes(token = token, email = user.email, role = user.role))
        }
    }
}

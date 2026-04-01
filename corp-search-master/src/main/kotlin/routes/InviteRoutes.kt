package com.example.routes

import com.example.db.Repos
import com.example.security.Passwords
import com.example.security.Tokens
import com.example.util.normalizeEmail
import com.example.util.requireTrimmed
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data class InviteDto(
    val id: Long,
    val email: String,
    val role: String,
    val expiresAt: String,
    val usedAt: String? = null,
    val createdAt: String,
    val createdBy: String? = null
)

@Serializable data class CreateInviteReq(
    val email: String,
    val role: String, // ADMIN / USER
    val ttlHours: Int = 72
)

@Serializable data class CreateInviteRes(
    val inviteId: Long,
    val token: String,
    val url: String
)

@Serializable data class AcceptInviteReq(
    val token: String,
    val password: String
)

private fun ApplicationCall.actorEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()

private fun ApplicationCall.actorRole(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()

private fun requireAdmin(call: ApplicationCall): Boolean = (call.actorRole() == "ADMIN")

fun Route.inviteAdminRoutes(frontendBaseUrl: String = "http://localhost:5173") {
    route("/api/invites") {

        get {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@get
            }
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
            val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)

            val items = Repos.listInvites(limit, offset).map {
                InviteDto(
                    id = it.id,
                    email = it.email,
                    role = it.role,
                    expiresAt = it.expiresAt.toString(),
                    usedAt = it.usedAt?.toString(),
                    createdAt = it.createdAt.toString(),
                    createdBy = it.createdBy
                )
            }
            call.respond(items)
        }

        post {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@post
            }

            val req = call.receive<CreateInviteReq>()

            val email = normalizeEmail(req.email)
            val role = requireTrimmed(req.role, "role", 3, 32).uppercase()

            if (role != "ADMIN" && role != "USER") {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "role must be ADMIN or USER"))
                return@post
            }

            if (Repos.findUserByEmail(email) != null) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "User already exists"))
                return@post
            }

            val ttl = req.ttlHours.coerceIn(1, 24 * 30)
            val token = Tokens.newToken()
            val tokenHash = Tokens.sha256Hex(token)
            val expiresAt = Instant.now().plusSeconds((ttl * 3600).toLong())

            val inviteId = Repos.createInvite(
                tokenHash = tokenHash,
                email = email,
                role = role,
                expiresAt = expiresAt,
                createdBy = call.actorEmail()
            )

            Repos.insertAudit(call.actorEmail(), "CREATE_INVITE", "inviteId=$inviteId email=$email role=$role ttlHours=$ttl")

            val url = "$frontendBaseUrl/#/accept?token=$token"
            call.respond(HttpStatusCode.Created, CreateInviteRes(inviteId, token, url))
        }

        delete("/{id}") {
            if (!requireAdmin(call)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@delete
            }

            val id = call.parameters["id"]!!.toLong()
            val ok = Repos.deleteInvite(id)
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@delete
            }

            Repos.insertAudit(call.actorEmail(), "DELETE_INVITE", "id=$id")
            call.respond(mapOf("ok" to true))
        }
    }
}

fun Route.invitePublicRoutes() {
    route("/api/public") {
        post("/accept-invite") {
            val req = call.receive<AcceptInviteReq>()
            val token = requireTrimmed(req.token, "token", 16, 256)
            val password = requireTrimmed(req.password, "password", 6, 200)

            val tokenHash = Tokens.sha256Hex(token)
            val invite = Repos.findInviteByTokenHash(tokenHash)
            if (invite == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid invite token"))
                return@post
            }
            if (invite.usedAt != null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invite already used"))
                return@post
            }
            if (invite.expiresAt.isBefore(Instant.now())) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invite expired"))
                return@post
            }

            if (Repos.findUserByEmail(invite.email) != null) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "User already exists"))
                return@post
            }

            val userId = Repos.createUser(invite.email, Passwords.hash(password), invite.role)
            Repos.markInviteUsed(invite.id)

            Repos.insertAudit(actorEmail = invite.email, action = "ACCEPT_INVITE", meta = "inviteId=${invite.id} userId=$userId role=${invite.role}")
            call.respond(mapOf("ok" to true))
        }
    }
}

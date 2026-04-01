package com.example.routes

import com.example.db.Repos
import com.example.util.normalizeEmail
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class PermDto(val email: String, val canRead: Boolean, val canWrite: Boolean)
@Serializable data class SetPermReq(val email: String, val canRead: Boolean, val canWrite: Boolean)

private fun ApplicationCall.actorEmail(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("email").asString()

private fun ApplicationCall.actorRole(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("role").asString()

fun Route.sourceRbacRoutes() {
    route("/api/sources/{id}/permissions") {

        get {
            if (call.actorRole() != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@get
            }
            val sourceId = call.parameters["id"]!!.toLong()
            val perms = Repos.listSourcePermissions(sourceId).mapNotNull { p ->
                val email = Repos.listUsers().firstOrNull { it.id == p.userId }?.email ?: return@mapNotNull null
                PermDto(email, p.canRead, p.canWrite)
            }
            call.respond(perms)
        }

        post {
            if (call.actorRole() != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@post
            }
            val sourceId = call.parameters["id"]!!.toLong()
            val req = call.receive<SetPermReq>()
            val email = normalizeEmail(req.email)
            val user = Repos.findUserByEmail(email) ?: run {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                return@post
            }
            Repos.upsertSourcePermission(user.id, sourceId, req.canRead, req.canWrite)
            Repos.insertAudit(call.actorEmail(), "SET_SOURCE_PERMISSION", "sourceId=$sourceId email=$email read=${req.canRead} write=${req.canWrite}")
            call.respond(mapOf("ok" to true))
        }

        delete {
            if (call.actorRole() != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@delete
            }
            val sourceId = call.parameters["id"]!!.toLong()
            val email = normalizeEmail(call.request.queryParameters["email"] ?: "")
            val user = Repos.findUserByEmail(email) ?: run {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                return@delete
            }
            Repos.deleteSourcePermission(user.id, sourceId)
            Repos.insertAudit(call.actorEmail(), "DELETE_SOURCE_PERMISSION", "sourceId=$sourceId email=$email")
            call.respond(mapOf("ok" to true))
        }
    }
}

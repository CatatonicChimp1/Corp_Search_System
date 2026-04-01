package com.example.routes

import com.example.db.Repos
import com.example.util.requireTrimmed
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class SourceDto(
    val id: Long,
    val name: String,
    val kind: String,
    val description: String? = null,
    val isActive: Boolean
)

@Serializable data class CreateSourceReq(val name: String, val kind: String, val description: String? = null)
@Serializable data class UpdateSourceReq(val name: String, val kind: String, val description: String? = null, val isActive: Boolean)

private fun ApplicationCall.actorEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()

private fun ApplicationCall.actorRole(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()

fun Route.sourceRoutes() {
    route("/api/sources") {
        get {
            val actor = call.actorEmail()
            val isAdmin = call.actorRole() == "ADMIN"
            val userId = Repos.getUserIdByEmail(actor ?: "")!!
            val items = Repos.listSourcesForUser(userId, isAdmin).map {
                SourceDto(it.id, it.name, it.kind, it.description, it.isActive)
            }
            call.respond(items)
        }

        post {
            if (call.actorRole() != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@post
            }
            val req = call.receive<CreateSourceReq>()
            val name = requireTrimmed(req.name, "name", 2, 120)
            val kind = requireTrimmed(req.kind, "kind", 2, 32)
            val created = Repos.createSource(name, kind, req.description?.trim()?.take(500))
            Repos.insertAudit(call.actorEmail(), "CREATE_SOURCE", "id=${created.id}")
            call.respond(HttpStatusCode.Created, SourceDto(created.id, created.name, created.kind, created.description, created.isActive))
        }

        put("/{id}") {
            if (call.actorRole() != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@put
            }
            val id = call.parameters["id"]!!.toLong()
            val req = call.receive<UpdateSourceReq>()
            val ok = Repos.updateSource(
                id = id,
                name = requireTrimmed(req.name, "name", 2, 120),
                kind = requireTrimmed(req.kind, "kind", 2, 32),
                description = req.description?.trim()?.take(500),
                isActive = req.isActive
            )
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@put
            }
            Repos.insertAudit(call.actorEmail(), "UPDATE_SOURCE", "id=$id")
            call.respond(mapOf("ok" to true))
        }

        delete("/{id}") {
            if (call.actorRole() != "ADMIN") {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
                return@delete
            }
            val id = call.parameters["id"]!!.toLong()
            val ok = Repos.deleteSource(id)
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@delete
            }
            Repos.insertAudit(call.actorEmail(), "DELETE_SOURCE", "id=$id")
            call.respond(mapOf("ok" to true))
        }
    }
}

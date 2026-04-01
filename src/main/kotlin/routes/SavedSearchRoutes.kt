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

@Serializable data class SavedSearchDto(
    val id: Long, val name: String, val query: String, val sourceId: Long?, val tag: String?, val createdAt: String
)

@Serializable data class CreateSavedSearchReq(val name: String, val query: String, val sourceId: Long? = null, val tag: String? = null)

private fun ApplicationCall.actorEmail(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("email").asString()

fun Route.savedSearchRoutes() {
    route("/api/saved-searches") {

        get {
            val userId = Repos.getUserIdByEmail(call.actorEmail())!!
            call.respond(
                Repos.listSavedSearches(userId).map {
                    SavedSearchDto(it.id, it.name, it.query, it.sourceId, it.tag, it.createdAt.toString())
                }
            )
        }

        post {
            val userId = Repos.getUserIdByEmail(call.actorEmail())!!
            val req = call.receive<CreateSavedSearchReq>()
            val name = requireTrimmed(req.name, "name", 1, 80)
            val q = req.query.trim().take(300)
            val tag = req.tag?.trim()?.takeIf { it.isNotBlank() }?.take(64)
            val id = Repos.createSavedSearch(userId, name, q, req.sourceId, tag)
            Repos.insertAudit(call.actorEmail(), "CREATE_SAVED_SEARCH", "id=$id name=$name")
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }

        delete("/{id}") {
            val userId = Repos.getUserIdByEmail(call.actorEmail())!!
            val id = call.parameters["id"]!!.toLong()
            val ok = Repos.deleteSavedSearch(userId, id)
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@delete
            }
            Repos.insertAudit(call.actorEmail(), "DELETE_SAVED_SEARCH", "id=$id")
            call.respond(mapOf("ok" to true))
        }
    }
}

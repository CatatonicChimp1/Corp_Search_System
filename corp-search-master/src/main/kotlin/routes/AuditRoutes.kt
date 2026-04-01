package com.example.routes

import com.example.db.Repos
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class AuditDto(
    val id: Long,
    val at: String,
    val actorEmail: String? = null,
    val action: String,
    val meta: String? = null
)

private fun ApplicationCall.actorRole(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()

private fun requireAdmin(call: ApplicationCall): Boolean = (call.actorRole() == "ADMIN")

fun Route.auditRoutes() {
    get("/api/audit") {
        if (!requireAdmin(call)) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
            return@get
        }
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 300)
        val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        val action = call.request.queryParameters["action"]
        val actor = call.request.queryParameters["actorEmail"]

        val items = Repos.listAudit(limit, offset, action, actor).map {
            AuditDto(
                id = it.id,
                at = it.at.toString(),
                actorEmail = it.actorEmail,
                action = it.action,
                meta = it.meta
            )
        }
        call.respond(items)
    }
}

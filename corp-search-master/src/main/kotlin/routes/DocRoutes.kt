package com.example.routes

import com.example.db.Repos
import com.example.search.DocumentIndexService
import com.example.storage.FileStorage
import com.example.util.parseTags
import com.example.util.requireTrimmed
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class DocDto(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val body: String,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String
)

@Serializable data class CreateDocReq(
    val sourceId: Long,
    val title: String,
    val body: String,
    val author: String? = null,
    val tagsCsv: String? = null
)

@Serializable data class UpdateDocReq(
    val title: String,
    val body: String,
    val author: String? = null,
    val tagsCsv: String? = null
)

private fun ApplicationCall.actorEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()

private fun ApplicationCall.actorRole(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("role").asString()

fun Route.docRoutes() {
    route("/api/docs") {
        get {
            val sourceId = call.request.queryParameters["sourceId"]?.toLongOrNull()
            val tag = call.request.queryParameters["tag"]
            val q = call.request.queryParameters["q"]
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
            val offset = (call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L)

            val actor = call.actorEmail()
            val isAdmin = call.actorRole() == "ADMIN"
            val userId = Repos.getUserIdByEmail(actor ?: "")
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unknown actor"))
                return@get
            }

            if (sourceId != null && !Repos.canReadSource(userId, sourceId, isAdmin)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No access to source"))
                return@get
            }

            val allowedSourceIds = if (isAdmin) null else Repos.listSourcesForUser(userId, isAdmin = false).map { it.id }.toSet()
            val allowedStatuses = if (isAdmin) setOf("DRAFT", "PUBLISHED", "ARCHIVED") else setOf("PUBLISHED")

            val docs = Repos.listDocs(sourceId, tag, q, limit, offset, allowedSourceIds, allowedStatuses)
                .map {
                    DocDto(
                        id = it.id,
                        sourceId = it.sourceId,
                        title = it.title,
                        body = it.body,
                        author = it.author,
                        tags = it.tags,
                        createdAt = it.createdAt.toString(),
                        updatedAt = it.updatedAt.toString()
                    )
                }
            call.respond(docs)
        }

        post {
            val req = call.receive<CreateDocReq>()
            val actor = call.actorEmail()
            val isAdmin = call.actorRole() == "ADMIN"
            val userId = Repos.getUserIdByEmail(actor ?: "")
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unknown actor"))
                return@post
            }
            if (!Repos.canWriteSource(userId, req.sourceId, isAdmin)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No write access to source"))
                return@post
            }

            val id = Repos.createDoc(
                sourceId = req.sourceId,
                title = requireTrimmed(req.title, "title", 2, 300),
                body = requireTrimmed(req.body, "body", 1, 200_000),
                author = req.author?.trim()?.take(120),
                tags = parseTags(req.tagsCsv)
            )
            DocumentIndexService.reindexDocument(id)
            Repos.insertAudit(call.actorEmail(), "CREATE_DOC", "id=$id")
            call.respond(HttpStatusCode.Created, mapOf("id" to id))
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            val doc = Repos.getDoc(id)
            if (doc == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@get
            }
            val actor = call.actorEmail()
            val isAdmin = call.actorRole() == "ADMIN"
            val userId = Repos.getUserIdByEmail(actor ?: "")
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unknown actor"))
                return@get
            }
            if (!Repos.canReadSource(userId, doc.sourceId, isAdmin)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No access to source"))
                return@get
            }
            call.respond(
                DocDto(
                    id = doc.id,
                    sourceId = doc.sourceId,
                    title = doc.title,
                    body = doc.body,
                    author = doc.author,
                    tags = doc.tags,
                    createdAt = doc.createdAt.toString(),
                    updatedAt = doc.updatedAt.toString()
                )
            )
        }

        put("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            val req = call.receive<UpdateDocReq>()
            val existing = Repos.getDoc(id)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@put
            }
            val actor = call.actorEmail()
            val isAdmin = call.actorRole() == "ADMIN"
            val userId = Repos.getUserIdByEmail(actor ?: "")
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unknown actor"))
                return@put
            }
            if (!Repos.canWriteSource(userId, existing.sourceId, isAdmin)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No write access to source"))
                return@put
            }
            val ok = Repos.updateDoc(
                id = id,
                title = requireTrimmed(req.title, "title", 2, 300),
                body = requireTrimmed(req.body, "body", 1, 200_000),
                author = req.author?.trim()?.take(120),
                tags = parseTags(req.tagsCsv)
            )
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@put
            }
            DocumentIndexService.reindexDocument(id)
            Repos.insertAudit(call.actorEmail(), "UPDATE_DOC", "id=$id")
            call.respond(mapOf("ok" to true))
        }

        delete("/{id}") {
            val id = call.parameters["id"]!!.toLong()
            val existing = Repos.getDoc(id)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@delete
            }
            val actor = call.actorEmail()
            val isAdmin = call.actorRole() == "ADMIN"
            val userId = Repos.getUserIdByEmail(actor ?: "")
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unknown actor"))
                return@delete
            }
            if (!Repos.canWriteSource(userId, existing.sourceId, isAdmin)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No write access to source"))
                return@delete
            }
            val ok = Repos.deleteDoc(id)
            if (!ok) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@delete
            }
            DocumentIndexService.deleteDocument(id)
            Repos.insertAudit(call.actorEmail(), "DELETE_DOC", "id=$id")
            call.respond(mapOf("ok" to true))
        }

        get("/{id}/download") {
            val id = call.parameters["id"]!!.toLong()
            val doc = Repos.getDoc(id)
            if (doc == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                return@get
            }

            val actor = call.actorEmail()
            val isAdmin = call.actorRole() == "ADMIN"
            val userId = Repos.getUserIdByEmail(actor ?: "")
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unknown actor"))
                return@get
            }
            if (!Repos.canReadSource(userId, doc.sourceId, isAdmin)) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No access to source"))
                return@get
            }

            val file = FileStorage.resolveStoredFile(doc.body)
            if (file == null || !file.exists() || !file.isFile) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Stored file not found"))
                return@get
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    FileStorage.presentableFileName(file.name)
                ).toString()
            )
            call.respondFile(file)
        }
    }
}

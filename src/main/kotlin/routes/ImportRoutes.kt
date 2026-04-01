package com.example.corpsearch.routes

import com.example.db.Documents
import com.example.db.Repos
import com.example.search.DocumentIndexService
import com.example.storage.FileStorage
import com.example.util.parseTags
import com.example.util.requireTrimmed
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.Instant

private fun ApplicationCall.actorEmail(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("email").asString()

private fun ApplicationCall.actorRole(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("role").asString()

fun Route.importRoutes(storageDirPath: String = FileStorage.DEFAULT_UPLOADS_DIR) {
    post("/api/docs/import") {
        val actor = call.actorEmail()
        val isAdmin = call.actorRole() == "ADMIN"
        val userId = Repos.getUserIdByEmail(actor)!!

        val multipart = call.receiveMultipart()
        var sourceId: Long? = null
        var status: String = "PUBLISHED"
        var tagsCsv: String? = null
        var title: String? = null
        var filename: String? = null
        var textBody: String = ""

        val storageDir = FileStorage.ensureUploadsDir(storageDirPath)

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "sourceId" -> sourceId = part.value.toLongOrNull()
                        "status" -> status = part.value.trim().uppercase()
                        "tagsCsv" -> tagsCsv = part.value
                        "title" -> title = part.value
                    }
                }
                is PartData.FileItem -> {
                    if (part.name == "file") {
                        filename = part.originalFileName ?: "file"
                        val bytes = part.streamProvider().readBytes()
                        val ext = filename!!.substringAfterLast('.', "").lowercase()


                        if (ext in listOf("txt", "md", "csv", "log", "json", "xml")) {
                            textBody = bytes.toString(Charsets.UTF_8)
                        } else {

                            val safeName = "${System.currentTimeMillis()}_${filename!!.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                            val f = File(storageDir, safeName)
                            f.writeBytes(bytes)
                            textBody = "FILE:$safeName"
                        }
                    }
                }
                else -> {}
            }
            part.dispose()
        }

        if (sourceId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "sourceId required"))
            return@post
        }

        if (status !in setOf("DRAFT", "PUBLISHED", "ARCHIVED")) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "status must be DRAFT/PUBLISHED/ARCHIVED"))
            return@post
        }

        if (!Repos.canWriteSource(userId, sourceId!!, isAdmin)) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No write access to source"))
            return@post
        }

        val docTitle = (title?.trim()?.takeIf { it.isNotBlank() } ?: filename ?: "Imported").take(300)
        val body = requireTrimmed(textBody, "body", 1, 200_000)

        val id = Repos.createDoc(
            sourceId = sourceId!!,
            title = docTitle,
            body = body,
            author = actor,
            tags = parseTags(tagsCsv)
        )

        transaction {
            Documents.update({ Documents.id eq id }) {
                it[Documents.status] = status
                it[Documents.updatedAt] = Instant.now()
            }
        }

        DocumentIndexService.reindexDocument(id, storageDirPath)
        Repos.insertAudit(actor, "IMPORT_DOC", "id=$id sourceId=$sourceId status=$status filename=$filename")
        call.respond(HttpStatusCode.Created, mapOf("id" to id))
    }
}

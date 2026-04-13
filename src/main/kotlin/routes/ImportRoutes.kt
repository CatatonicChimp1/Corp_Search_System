package com.example.corpsearch.routes

import com.example.db.Documents
import com.example.db.Repos
import com.example.importing.JdbcImportService
import com.example.importing.JdbcImportRequest
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
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.time.Instant

private fun ApplicationCall.actorEmail(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("email").asString()

private fun ApplicationCall.actorRole(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("role").asString()

@Serializable
data class DbInspectReq(
    val jdbcUrl: String,
    val username: String = "",
    val password: String = ""
)

@Serializable
data class DbTableDto(val name: String, val estimatedRows: Int? = null)

@Serializable
data class DbImportReq(
    val jdbcUrl: String,
    val username: String = "",
    val password: String = "",
    val tables: List<String>,
    val sourceId: Long,
    val status: String = "PUBLISHED",
    val rowLimitPerTable: Int = 200,
    val tagsCsv: String? = null
)

fun Route.importRoutes(storageDirPath: String = FileStorage.DEFAULT_UPLOADS_DIR) {
    post("/api/import/db/inspect") {
        if (call.actorRole() != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
            return@post
        }
        val req = call.receive<DbInspectReq>()
        val jdbcUrl = requireTrimmed(req.jdbcUrl, "jdbcUrl", 10, 1_000)

        try {
            val tables = JdbcImportService.inspectTables(
                jdbcUrl = jdbcUrl,
                username = req.username.trim(),
                password = req.password
            ).map { DbTableDto(it.name, it.estimatedRows) }

            Repos.insertAudit(call.actorEmail(), "INSPECT_DB", "tables=${tables.size}")
            call.respond(tables)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid DB connection")))
        }
    }

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

    post("/api/docs/import-db") {
        val actor = call.actorEmail()
        val isAdmin = call.actorRole() == "ADMIN"
        val userId = Repos.getUserIdByEmail(actor)!!
        if (!isAdmin) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
            return@post
        }
        val req = call.receive<DbImportReq>()

        if (req.status !in setOf("DRAFT", "PUBLISHED", "ARCHIVED")) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "status must be DRAFT/PUBLISHED/ARCHIVED"))
            return@post
        }

        if (!Repos.canWriteSource(userId, req.sourceId, isAdmin)) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No write access to source"))
            return@post
        }

        val jdbcUrl = requireTrimmed(req.jdbcUrl, "jdbcUrl", 10, 1_000)
        val tables = req.tables.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (tables.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "At least one table must be selected"))
            return@post
        }

        try {
            val result = JdbcImportService.importTables(
                JdbcImportRequest(
                    jdbcUrl = jdbcUrl,
                    username = req.username.trim(),
                    password = req.password,
                    tables = tables,
                    sourceId = req.sourceId,
                    actor = actor,
                    status = req.status,
                    rowLimitPerTable = req.rowLimitPerTable.coerceIn(1, 2_000),
                    tags = parseTags(req.tagsCsv)
                )
            )

            Repos.insertAudit(
                actor,
                "IMPORT_DB",
                "sourceId=${req.sourceId} tables=${tables.joinToString(",")} importedDocs=${result.importedDocs}"
            )
            call.respond(HttpStatusCode.Created, result)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid DB import request")))
        }
    }
}

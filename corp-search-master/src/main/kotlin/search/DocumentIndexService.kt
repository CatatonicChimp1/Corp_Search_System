package com.example.search

import com.example.db.DocumentSearchIndex
import com.example.db.Repos
import com.example.db.SearchableDocRow
import com.example.storage.FileStorage
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.Connection
import java.time.Instant
import kotlin.math.abs

data class IndexedDocumentHit(
    val documentId: Long,
    val sourceId: Long,
    val sourceName: String,
    val sourceKind: String,
    val status: String,
    val title: String,
    val bodyText: String,
    val author: String?,
    val tags: List<String>,
    val fileName: String?,
    val extractedText: String,
    val isFile: Boolean,
    val updatedAt: Instant,
    val score: Int
)

object DocumentIndexService {
    private const val FTS_TABLE = "document_search_fts"

    fun rebuildAll(storageDirPath: String = FileStorage.DEFAULT_UPLOADS_DIR) = transaction {
        ensureFtsSchema()
        DocumentSearchIndex.deleteAll()
        currentJdbc().prepareStatement("DELETE FROM $FTS_TABLE").use { it.executeUpdate() }

        Repos.listSearchableDocs(
            sourceId = null,
            tag = null,
            allowedSourceIds = null,
            allowedStatuses = setOf("DRAFT", "PUBLISHED", "ARCHIVED")
        ).forEach { upsertDocumentInternal(it, storageDirPath) }
    }

    fun reindexDocument(documentId: Long, storageDirPath: String = FileStorage.DEFAULT_UPLOADS_DIR) = transaction {
        ensureFtsSchema()
        val doc = Repos.getSearchableDoc(documentId)
        if (doc == null) {
            deleteDocumentInternal(documentId)
            return@transaction
        }
        upsertDocumentInternal(doc, storageDirPath)
    }

    fun deleteDocument(documentId: Long) = transaction {
        ensureFtsSchema()
        deleteDocumentInternal(documentId)
    }

    fun search(
        query: String,
        allowedSourceIds: Set<Long>?,
        allowedStatuses: Set<String>,
        sourceId: Long?,
        tag: String?,
        includeFiles: Boolean,
        limit: Int,
        offset: Int
    ): List<IndexedDocumentHit> = transaction {
        if (query.isBlank()) return@transaction emptyList()
        ensureFtsSchema()

        val sql = buildString {
            append(
                """
                SELECT idx.document_id,
                       idx.source_id,
                       s.name AS source_name,
                       s.kind AS source_kind,
                       idx.status,
                       idx.title,
                       idx.body_text,
                       idx.author,
                       idx.tags_display,
                       idx.file_name,
                       idx.extracted_text,
                       idx.is_file,
                       idx.updated_at,
                       bm25($FTS_TABLE, 9.0, 4.0, 2.0, 1.5, 6.0, 5.0) AS rank
                FROM $FTS_TABLE fts
                JOIN document_search_index idx ON idx.document_id = fts.rowid
                JOIN sources s ON s.id = idx.source_id
                WHERE $FTS_TABLE MATCH ?
                """.trimIndent()
            )

            if (sourceId != null) append(" AND idx.source_id = ?")
            if (allowedSourceIds != null) append(" AND idx.source_id IN (${placeholders(allowedSourceIds.size)})")
            append(" AND idx.status IN (${placeholders(allowedStatuses.size)})")
            append(" AND idx.is_file = ?")
            if (!tag.isNullOrBlank()) append(" AND instr(idx.tags_lookup, ?) > 0")
            append(" ORDER BY rank ASC, idx.updated_at DESC LIMIT ? OFFSET ?")
        }

        currentJdbc().prepareStatement(sql).use { stmt ->
            var index = 1
            stmt.setString(index++, toFtsQuery(query))

            if (sourceId != null) stmt.setLong(index++, sourceId)
            allowedSourceIds?.forEach { stmt.setLong(index++, it) }
            allowedStatuses.forEach { stmt.setString(index++, it) }
            stmt.setBoolean(index++, includeFiles)
            if (!tag.isNullOrBlank()) stmt.setString(index++, "|${tag.trim().lowercase()}|")
            stmt.setInt(index++, limit)
            stmt.setInt(index, offset)

            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val rank = rs.getDouble("rank")
                        add(
                            IndexedDocumentHit(
                                documentId = rs.getLong("document_id"),
                                sourceId = rs.getLong("source_id"),
                                sourceName = rs.getString("source_name"),
                                sourceKind = rs.getString("source_kind"),
                                status = rs.getString("status"),
                                title = rs.getString("title"),
                                bodyText = rs.getString("body_text") ?: "",
                                author = rs.getString("author"),
                                tags = parseTags(rs.getString("tags_display")),
                                fileName = rs.getString("file_name"),
                                extractedText = rs.getString("extracted_text") ?: "",
                                isFile = rs.getBoolean("is_file"),
                                updatedAt = rs.getTimestamp("updated_at").toInstant(),
                                score = scoreFromRank(rank)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun upsertDocumentInternal(doc: SearchableDocRow, storageDirPath: String) {
        val storedFileName = FileStorage.extractStoredFileName(doc.body)
        val storedFile = storedFileName?.let { FileStorage.resolveStoredFile(doc.body, storageDirPath) }
        val extractedText = storedFile?.let { DocumentContentExtractor.extract(it)?.text }.orEmpty()
        val titleLooksLikeFile = doc.title.contains('.')

        val bodyText = if (storedFileName == null) doc.body else ""
        val fileName = when {
            storedFileName != null -> FileStorage.presentableFileName(storedFileName)
            titleLooksLikeFile -> doc.title
            else -> null
        }
        val tagsDisplay = doc.tags.joinToString(", ")
        val tagsLookup = doc.tags.joinToString(separator = "") { "|${it.trim().lowercase()}|" }

        DocumentSearchIndex.insertIgnore {
            it[DocumentSearchIndex.documentId] = doc.id
            it[DocumentSearchIndex.sourceId] = doc.sourceId
            it[DocumentSearchIndex.status] = doc.status
            it[DocumentSearchIndex.title] = doc.title
            it[DocumentSearchIndex.bodyText] = bodyText
            it[DocumentSearchIndex.author] = doc.author
            it[DocumentSearchIndex.tagsDisplay] = tagsDisplay
            it[DocumentSearchIndex.tagsLookup] = tagsLookup
            it[DocumentSearchIndex.fileName] = fileName
            it[DocumentSearchIndex.extractedText] = extractedText
            it[DocumentSearchIndex.isFile] = storedFileName != null
            it[DocumentSearchIndex.updatedAt] = doc.updatedAt
        }

        DocumentSearchIndex.update({ DocumentSearchIndex.documentId eq doc.id }) {
            it[DocumentSearchIndex.sourceId] = doc.sourceId
            it[DocumentSearchIndex.status] = doc.status
            it[DocumentSearchIndex.title] = doc.title
            it[DocumentSearchIndex.bodyText] = bodyText
            it[DocumentSearchIndex.author] = doc.author
            it[DocumentSearchIndex.tagsDisplay] = tagsDisplay
            it[DocumentSearchIndex.tagsLookup] = tagsLookup
            it[DocumentSearchIndex.fileName] = fileName
            it[DocumentSearchIndex.extractedText] = extractedText
            it[DocumentSearchIndex.isFile] = storedFileName != null
            it[DocumentSearchIndex.updatedAt] = doc.updatedAt
        }

        deleteFtsRow(doc.id)
        currentJdbc().prepareStatement(
            """
            INSERT INTO $FTS_TABLE(rowid, title, body_text, author, tags_display, file_name, extracted_text)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setLong(1, doc.id)
            stmt.setString(2, doc.title)
            stmt.setString(3, bodyText)
            stmt.setString(4, doc.author)
            stmt.setString(5, tagsDisplay)
            stmt.setString(6, fileName)
            stmt.setString(7, extractedText)
            stmt.executeUpdate()
        }
    }

    private fun deleteDocumentInternal(documentId: Long) {
        DocumentSearchIndex.deleteWhere { DocumentSearchIndex.documentId eq documentId }
        deleteFtsRow(documentId)
    }

    private fun deleteFtsRow(documentId: Long) {
        currentJdbc().prepareStatement("DELETE FROM $FTS_TABLE WHERE rowid = ?").use { stmt ->
            stmt.setLong(1, documentId)
            stmt.executeUpdate()
        }
    }

    private fun ensureFtsSchema() {
        currentJdbc().prepareStatement(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts5(
                title,
                body_text,
                author,
                tags_display,
                file_name,
                extracted_text,
                tokenize = 'unicode61'
            )
            """.trimIndent()
        ).use { it.execute() }
    }

    private fun currentJdbc(): Connection =
        TransactionManager.current().connection.connection as Connection

    private fun placeholders(count: Int): String =
        List(count) { "?" }.joinToString(", ")

    private fun toFtsQuery(query: String): String =
        query
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }

    private fun parseTags(raw: String?): List<String> =
        raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    private fun scoreFromRank(rank: Double): Int {
        val normalized = if (rank.isNaN()) 1_000.0 else abs(rank)
        return (100_000.0 / (1.0 + normalized)).toInt()
    }
}

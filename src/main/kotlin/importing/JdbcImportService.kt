package com.example.importing

import com.example.db.Documents
import com.example.db.Repos
import com.example.search.DocumentIndexService
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

@Serializable
data class JdbcTableInfo(
    val name: String,
    val estimatedRows: Int? = null
)

data class JdbcImportRequest(
    val jdbcUrl: String,
    val username: String? = null,
    val password: String? = null,
    val tables: List<String>,
    val sourceId: Long,
    val actor: String?,
    val status: String,
    val rowLimitPerTable: Int = 200,
    val tags: List<String> = emptyList()
)

@Serializable
data class JdbcImportResult(
    val importedDocs: Int,
    val tables: List<String>
)

object JdbcImportService {
    private val preferredTitleColumns = listOf("name", "title", "subject", "caption")

    fun inspectTables(jdbcUrl: String, username: String?, password: String?): List<JdbcTableInfo> =
        withConnection(jdbcUrl, username, password) { connection ->
            val meta = connection.metaData
            val tables = mutableListOf<JdbcTableInfo>()
            meta.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    val tableName = rs.getString("TABLE_NAME") ?: continue
                    tables += JdbcTableInfo(name = tableName)
                }
            }
            tables.sortedBy { it.name.lowercase() }
        }

    fun importTables(request: JdbcImportRequest): JdbcImportResult {
        require(request.tables.isNotEmpty()) { "At least one table must be selected" }

        return withConnection(request.jdbcUrl, request.username, request.password) { connection ->
            var importedCount = 0
            val importedTables = mutableListOf<String>()

            for (table in request.tables.distinct()) {
                val importedForTable = importTable(connection, request, table)
                if (importedForTable > 0) {
                    importedTables += table
                    importedCount += importedForTable
                }
            }

            JdbcImportResult(importedDocs = importedCount, tables = importedTables)
        }
    }

    private fun importTable(connection: Connection, request: JdbcImportRequest, table: String): Int {
        val sql = "SELECT * FROM ${quoteIdentifier(table)} LIMIT ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, request.rowLimitPerTable.coerceIn(1, 5_000))

            stmt.executeQuery().use { rs ->
                val meta = rs.metaData
                val columnCount = meta.columnCount
                var importedCount = 0
                var rowIndex = 0

                while (rs.next()) {
                    rowIndex += 1
                    val fields = buildMap {
                        for (i in 1..columnCount) {
                            val key = meta.getColumnLabel(i) ?: meta.getColumnName(i) ?: "col_$i"
                            val value = rs.getObject(i)?.toString()?.trim().orEmpty()
                            put(key, value)
                        }
                    }

                    val title = buildTitle(table, rowIndex, fields)
                    val body = fields.entries.joinToString("\n") { (key, value) -> "$key: $value" }.take(200_000)
                    val tags = (request.tags + listOf("db", table.take(64))).distinct()
                    val docId = Repos.createDoc(
                        sourceId = request.sourceId,
                        title = title.take(300),
                        body = body.ifBlank { "Imported from table $table" },
                        author = request.actor,
                        tags = tags
                    )

                    transaction {
                        Documents.update({ Documents.id eq docId }) {
                            it[Documents.status] = request.status
                            it[Documents.updatedAt] = Instant.now()
                        }
                    }
                    DocumentIndexService.reindexDocument(docId)
                    importedCount += 1
                }

                return importedCount
            }
        }
    }

    private fun buildTitle(table: String, rowIndex: Int, fields: Map<String, String>): String {
        val preferred = preferredTitleColumns.firstNotNullOfOrNull { preferredColumn ->
            fields.entries.firstOrNull { it.key.equals(preferredColumn, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }
        }

        val idValue = fields.entries.firstOrNull {
            val key = it.key.lowercase()
            key == "id" || key.endsWith("_id") || key.endsWith("id")
        }?.value?.takeIf { it.isNotBlank() }

        return buildString {
            append(table)
            append(" #")
            append(idValue ?: rowIndex)
            if (!preferred.isNullOrBlank()) {
                append(" | ")
                append(preferred)
            }
        }
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"${identifier.replace("\"", "\"\"")}\""

    private fun <T> withConnection(jdbcUrl: String, username: String?, password: String?, block: (Connection) -> T): T {
        val normalizedUser = username?.takeIf { it.isNotBlank() }
        val normalizedPassword = password ?: ""
        return DriverManager.getConnection(jdbcUrl, normalizedUser, normalizedPassword).use(block)
    }
}

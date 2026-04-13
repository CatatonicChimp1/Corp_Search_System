package com.example.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit

data class UserRow(val id: Long, val email: String, val passwordHash: String, val role: String)
data class SourceRow(val id: Long, val name: String, val kind: String, val description: String?, val isActive: Boolean)
data class DocRow(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val body: String,
    val author: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val tags: List<String>
)

data class SearchableDocRow(
    val id: Long,
    val sourceId: Long,
    val sourceName: String,
    val sourceKind: String,
    val title: String,
    val body: String,
    val author: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: String,
    val tags: List<String>
)

data class InviteRow(
    val id: Long,
    val email: String,
    val role: String,
    val expiresAt: java.time.Instant,
    val usedAt: java.time.Instant?,
    val createdAt: java.time.Instant,
    val createdBy: String?
)

data class AuditRow(
    val id: Long,
    val at: java.time.Instant,
    val actorEmail: String?,
    val action: String,
    val meta: String?
)

data class SourcePermRow(val userId: Long, val sourceId: Long, val canRead: Boolean, val canWrite: Boolean)

data class SavedSearchRow(
    val id: Long, val userId: Long, val name: String, val query: String,
    val sourceId: Long?, val tag: String?, val createdAt: java.time.Instant
)

data class SearchEventRow(
    val id: Long, val at: java.time.Instant, val actorEmail: String?,
    val query: String, val sourceId: Long?, val tag: String?, val resultsCount: Int, val clickedDocId: Long?
)

data class AnalyticsSummaryRow(
    val totalDocuments: Long,
    val publishedDocuments: Long,
    val totalGroups: Long,
    val activeGroups: Long,
    val totalSearches: Long,
    val searchesLast7Days: Long,
    val zeroResultSearches: Long,
    val zeroResultRate: Double,
    val clickedSearches: Long,
    val clickThroughRate: Double,
    val importsTotal: Long,
    val importsLast7Days: Long
)

data class GroupDocumentMetricRow(val sourceId: Long, val sourceName: String, val documents: Long)

object Repos {

    fun findUserByEmail(email: String): UserRow? = transaction {
        val e = email.trim().lowercase()
        Users.select { Users.email.lowerCase() eq e }
            .limit(1)
            .map {
                UserRow(
                    id = it[Users.id],
                    email = it[Users.email],
                    passwordHash = it[Users.passwordHash],
                    role = it[Users.role]
                )
            }.singleOrNull()
    }

    fun insertAudit(actorEmail: String?, action: String, meta: String? = null) = transaction {
        AuditLog.insert {
            it[at] = Instant.now()
            it[AuditLog.actorEmail] = actorEmail
            it[AuditLog.action] = action
            it[AuditLog.meta] = meta
        }
    }

    // Sources
    fun listSources(): List<SourceRow> = transaction {
        Sources.selectAll()
            .orderBy(Sources.createdAt, SortOrder.DESC)
            .map {
                SourceRow(
                    id = it[Sources.id],
                    name = it[Sources.name],
                    kind = it[Sources.kind],
                    description = it[Sources.description],
                    isActive = it[Sources.isActive]
                )
            }
    }

    fun createSource(name: String, kind: String, description: String?): SourceRow = transaction {
        val id = Sources.insert {
            it[Sources.name] = name
            it[Sources.kind] = kind
            it[Sources.description] = description
            it[Sources.isActive] = true
            it[Sources.createdAt] = Instant.now()
        }[Sources.id]

        val row = Sources.selectAll().where { Sources.id eq id }.single()
        SourceRow(
            id = row[Sources.id],
            name = row[Sources.name],
            kind = row[Sources.kind],
            description = row[Sources.description],
            isActive = row[Sources.isActive]
        )
    }

    fun updateSource(id: Long, name: String, kind: String, description: String?, isActive: Boolean): Boolean = transaction {
        Sources.update({ Sources.id eq id }) {
            it[Sources.name] = name
            it[Sources.kind] = kind
            it[Sources.description] = description
            it[Sources.isActive] = isActive
        } > 0
    }

    fun deleteSource(id: Long): Boolean = transaction {
        // cascade manual: docs -> tags -> docs -> source
        val docIds = Documents.slice(Documents.id).selectAll().where { Documents.sourceId eq id }.map { it[Documents.id] }
        DocumentTags.deleteWhere { DocumentTags.documentId inList docIds }
        Documents.deleteWhere { Documents.sourceId eq id }
        Sources.deleteWhere { Sources.id eq id } > 0
    }

    // Documents
    fun createDoc(
        sourceId: Long,
        title: String,
        body: String,
        author: String?,
        tags: List<String>
    ): Long = transaction {
        val now = Instant.now()
        val id = Documents.insert {
            it[Documents.sourceId] = sourceId
            it[Documents.title] = title
            it[Documents.body] = body
            it[Documents.author] = author
            it[createdAt] = now
            it[updatedAt] = now
        }[Documents.id]

        tags.distinct().filter { it.isNotBlank() }.forEach { t ->
            DocumentTags.insertIgnore {
                it[documentId] = id
                it[tag] = t.trim()
            }
        }
        id
    }

    fun getDoc(id: Long): DocRow? = transaction {
        val doc = Documents.select { Documents.id eq id }.singleOrNull() ?: return@transaction null
        val tags = DocumentTags.select { DocumentTags.documentId eq id }.map { it[DocumentTags.tag] }

        DocRow(
            id = doc[Documents.id],
            sourceId = doc[Documents.sourceId],
            title = doc[Documents.title],
            body = doc[Documents.body],
            author = doc[Documents.author],
            createdAt = doc[Documents.createdAt],
            updatedAt = doc[Documents.updatedAt],
            tags = tags
        )
    }

    fun updateDoc(
        id: Long,
        title: String,
        body: String,
        author: String?,
        tags: List<String>
    ): Boolean = transaction {
        val updated = Documents.update({ Documents.id eq id }) {
            it[Documents.title] = title
            it[Documents.body] = body
            it[Documents.author] = author
            it[updatedAt] = Instant.now()
        } > 0

        if (updated) {
            DocumentTags.deleteWhere { DocumentTags.documentId eq id }
            tags.distinct().filter { it.isNotBlank() }.forEach { t ->
                DocumentTags.insertIgnore {
                    it[documentId] = id
                    it[tag] = t.trim()
                }
            }
        }
        updated
    }

    fun deleteDoc(id: Long): Boolean = transaction {
        DocumentTags.deleteWhere { DocumentTags.documentId eq id }
        Documents.deleteWhere { Documents.id eq id } > 0
    }

    fun listDocs(
        sourceId: Long?,
        tag: String?,
        q: String?,
        limit: Int,
        offset: Long,
        allowedSourceIds: Set<Long>? = null,
        allowedStatuses: Set<String>  = setOf("PUBLISHED")
    ): List<DocRow> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(Op.TRUE)

        if (sourceId != null) conditions += (Documents.sourceId eq sourceId)
        if (allowedSourceIds != null) conditions += (Documents.sourceId inList allowedSourceIds.toList())
        conditions += (Documents.status inList allowedStatuses.toList())

        if (!tag.isNullOrBlank()) {
            conditions += Exists(
                DocumentTags.select { (DocumentTags.documentId eq Documents.id) and (DocumentTags.tag eq tag) }
            )
        }
        if (!q.isNullOrBlank()) {
            val like = "%${q.trim()}%"
            conditions += ((Documents.title like like) or (Documents.body like like))
        }

        val where = conditions.reduce { a, b -> a and b }

        val docs = Documents.select { where }
            .orderBy(Documents.updatedAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row -> row[Documents.id] to row }

        val ids = docs.map { it.first }
        val tagsByDoc = if (ids.isEmpty()) emptyMap() else
            DocumentTags.select { DocumentTags.documentId inList ids }
                .groupBy({ it[DocumentTags.documentId] }, { it[DocumentTags.tag] })

        docs.map { (id, row) ->
            DocRow(
                id = id,
                sourceId = row[Documents.sourceId],
                title = row[Documents.title],
                body = row[Documents.body],
                author = row[Documents.author],
                createdAt = row[Documents.createdAt],
                updatedAt = row[Documents.updatedAt],
                tags = tagsByDoc[id].orEmpty()
            )
        }
    }

    fun listSearchableDocs(
        sourceId: Long?,
        tag: String?,
        allowedSourceIds: Set<Long>? = null,
        allowedStatuses: Set<String> = setOf("PUBLISHED")
    ): List<SearchableDocRow> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(Op.TRUE)

        if (sourceId != null) conditions += (Documents.sourceId eq sourceId)
        if (allowedSourceIds != null) conditions += (Documents.sourceId inList allowedSourceIds.toList())
        conditions += (Documents.status inList allowedStatuses.toList())

        if (!tag.isNullOrBlank()) {
            conditions += Exists(
                DocumentTags.select { (DocumentTags.documentId eq Documents.id) and (DocumentTags.tag eq tag) }
            )
        }

        val where = conditions.reduce { a, b -> a and b }

        val docs = (Documents innerJoin Sources)
            .select { where }
            .orderBy(Documents.updatedAt, SortOrder.DESC)
            .map { row -> row[Documents.id] to row }

        val ids = docs.map { it.first }
        val tagsByDoc = if (ids.isEmpty()) emptyMap() else
            DocumentTags.select { DocumentTags.documentId inList ids }
                .groupBy({ it[DocumentTags.documentId] }, { it[DocumentTags.tag] })

        docs.map { (id, row) ->
            SearchableDocRow(
                id = id,
                sourceId = row[Documents.sourceId],
                sourceName = row[Sources.name],
                sourceKind = row[Sources.kind],
                title = row[Documents.title],
                body = row[Documents.body],
                author = row[Documents.author],
                createdAt = row[Documents.createdAt],
                updatedAt = row[Documents.updatedAt],
                status = row[Documents.status],
                tags = tagsByDoc[id].orEmpty()
            )
        }
    }

    fun getSearchableDoc(id: Long): SearchableDocRow? = transaction {
        val row = (Documents innerJoin Sources)
            .select { Documents.id eq id }
            .singleOrNull() ?: return@transaction null

        val tags = DocumentTags.select { DocumentTags.documentId eq id }.map { it[DocumentTags.tag] }

        SearchableDocRow(
            id = row[Documents.id],
            sourceId = row[Documents.sourceId],
            sourceName = row[Sources.name],
            sourceKind = row[Sources.kind],
            title = row[Documents.title],
            body = row[Documents.body],
            author = row[Documents.author],
            createdAt = row[Documents.createdAt],
            updatedAt = row[Documents.updatedAt],
            status = row[Documents.status],
            tags = tags
        )
    }

    fun listUsers(): List<UserRow> = transaction {
        Users.selectAll()
            .orderBy(Users.createdAt, SortOrder.DESC)
            .map {
                UserRow(
                    id = it[Users.id],
                    email = it[Users.email],
                    passwordHash = it[Users.passwordHash],
                    role = it[Users.role]
                )
            }
    }

    fun createUser(email: String, passwordHash: String, role: String): Long = transaction {
        Users.insert {
            it[Users.email] = email.trim().lowercase()
            it[Users.passwordHash] = passwordHash
            it[Users.role] = role
            it[createdAt] = java.time.Instant.now()
        }[Users.id]
    }

    fun updateUserRole(id: Long, role: String): Boolean = transaction {
        Users.update({ Users.id eq id }) {
            it[Users.role] = role
        } > 0
    }

    fun resetUserPassword(id: Long, passwordHash: String): Boolean = transaction {
        Users.update({ Users.id eq id }) {
            it[Users.passwordHash] = passwordHash
        } > 0
    }

    fun deleteUser(id: Long): Boolean = transaction {
        Users.deleteWhere { Users.id eq id } > 0
    }
    fun countAdmins(): Long = transaction {
        Users.select { Users.role eq "ADMIN" }.count()
    }

    fun isLastAdmin(userId: Long): Boolean = transaction {
        val isAdmin = Users.select { Users.id eq userId }
            .limit(1)
            .map { it[Users.role] == "ADMIN" }
            .singleOrNull() ?: false

        if (!isAdmin) return@transaction false
        countAdmins() <= 1
    }

    // AUDIT
    fun listAudit(limit: Int, offset: Long, action: String?, actorEmail: String?): List<AuditRow> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(Op.TRUE)
        if (!action.isNullOrBlank()) conditions += (AuditLog.action eq action.trim())
        if (!actorEmail.isNullOrBlank()) conditions += (AuditLog.actorEmail eq actorEmail.trim())
        val where = conditions.reduce { a, b -> a and b }

        AuditLog.select { where }
            .orderBy(AuditLog.at, SortOrder.DESC)
            .limit(limit, offset)
            .map {
                AuditRow(
                    id = it[AuditLog.id],
                    at = it[AuditLog.at],
                    actorEmail = it[AuditLog.actorEmail],
                    action = it[AuditLog.action],
                    meta = it[AuditLog.meta]
                )
            }
    }

    // INVITES
    fun createInvite(
        tokenHash: String,
        email: String,
        role: String,
        expiresAt: java.time.Instant,
        createdBy: String?
    ): Long = transaction {
        Invites.insert {
            it[Invites.tokenHash] = tokenHash
            it[Invites.email] = email
            it[Invites.role] = role
            it[Invites.expiresAt] = expiresAt
            it[Invites.usedAt] = null
            it[Invites.createdAt] = java.time.Instant.now()
            it[Invites.createdBy] = createdBy
        }[Invites.id]
    }

    fun listInvites(limit: Int, offset: Long): List<InviteRow> = transaction {
        Invites.selectAll()
            .orderBy(Invites.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map {
                InviteRow(
                    id = it[Invites.id],
                    email = it[Invites.email],
                    role = it[Invites.role],
                    expiresAt = it[Invites.expiresAt],
                    usedAt = it[Invites.usedAt],
                    createdAt = it[Invites.createdAt],
                    createdBy = it[Invites.createdBy]
                )
            }
    }

    fun findInviteByTokenHash(tokenHash: String): InviteRow? = transaction {
        Invites.select { Invites.tokenHash eq tokenHash }
            .limit(1)
            .map {
                InviteRow(
                    id = it[Invites.id],
                    email = it[Invites.email],
                    role = it[Invites.role],
                    expiresAt = it[Invites.expiresAt],
                    usedAt = it[Invites.usedAt],
                    createdAt = it[Invites.createdAt],
                    createdBy = it[Invites.createdBy]
                )
            }.singleOrNull()
    }

    fun markInviteUsed(id: Long): Boolean = transaction {
        Invites.update({ Invites.id eq id }) {
            it[usedAt] = java.time.Instant.now()
        } > 0
    }

    fun deleteInvite(id: Long): Boolean = transaction {
        Invites.deleteWhere { Invites.id eq id } > 0
    }

    fun getUserIdByEmail(email: String): Long? = transaction {
        Users.slice(Users.id).select { Users.email eq email }.limit(1).map { it[Users.id] }.singleOrNull()
    }

    fun listSourcesForUser(userId: Long, isAdmin: Boolean): List<SourceRow> = transaction {
        if (isAdmin) {
            return@transaction Sources.selectAll().orderBy(Sources.createdAt, SortOrder.DESC).map {
                SourceRow(it[Sources.id], it[Sources.name], it[Sources.kind], it[Sources.description], it[Sources.isActive])
            }
        }

        (Sources innerJoin SourcePermissions)
            .select { (SourcePermissions.userId eq userId) and (SourcePermissions.canRead eq true) }
            .orderBy(Sources.createdAt, SortOrder.DESC)
            .map {
                SourceRow(it[Sources.id], it[Sources.name], it[Sources.kind], it[Sources.description], it[Sources.isActive])
            }
    }

    fun canReadSource(userId: Long, sourceId: Long, isAdmin: Boolean): Boolean = transaction {
        if (isAdmin) return@transaction true
        SourcePermissions.select {
            (SourcePermissions.userId eq userId) and (SourcePermissions.sourceId eq sourceId) and (SourcePermissions.canRead eq true)
        }.limit(1).any()
    }

    fun canWriteSource(userId: Long, sourceId: Long, isAdmin: Boolean): Boolean = transaction {
        if (isAdmin) return@transaction true
        SourcePermissions.select {
            (SourcePermissions.userId eq userId) and (SourcePermissions.sourceId eq sourceId) and (SourcePermissions.canWrite eq true)
        }.limit(1).any()
    }

    fun listSourcePermissions(sourceId: Long): List<SourcePermRow> = transaction {
        SourcePermissions.select { SourcePermissions.sourceId eq sourceId }.map {
            SourcePermRow(it[SourcePermissions.userId], it[SourcePermissions.sourceId], it[SourcePermissions.canRead], it[SourcePermissions.canWrite])
        }
    }

    fun upsertSourcePermission(userId: Long, sourceId: Long, canRead: Boolean, canWrite: Boolean) = transaction {
        SourcePermissions.insertIgnore {
            it[SourcePermissions.userId] = userId
            it[SourcePermissions.sourceId] = sourceId
            it[SourcePermissions.canRead] = canRead
            it[SourcePermissions.canWrite] = canWrite
        }
        SourcePermissions.update({ (SourcePermissions.userId eq userId) and (SourcePermissions.sourceId eq sourceId) }) {
            it[SourcePermissions.canRead] = canRead
            it[SourcePermissions.canWrite] = canWrite
        }
    }

    fun deleteSourcePermission(userId: Long, sourceId: Long) = transaction {
        SourcePermissions.deleteWhere { (SourcePermissions.userId eq userId) and (SourcePermissions.sourceId eq sourceId) }
    }

    fun listSavedSearches(userId: Long): List<SavedSearchRow> = transaction {
        SavedSearches.select { SavedSearches.userId eq userId }
            .orderBy(SavedSearches.createdAt, SortOrder.DESC)
            .map {
                SavedSearchRow(
                    id = it[SavedSearches.id],
                    userId = it[SavedSearches.userId],
                    name = it[SavedSearches.name],
                    query = it[SavedSearches.query],
                    sourceId = it[SavedSearches.sourceId],
                    tag = it[SavedSearches.tag],
                    createdAt = it[SavedSearches.createdAt]
                )
            }
    }

    fun createSavedSearch(userId: Long, name: String, query: String, sourceId: Long?, tag: String?): Long = transaction {
        SavedSearches.insert {
            it[SavedSearches.userId] = userId
            it[SavedSearches.name] = name
            it[SavedSearches.query] = query
            it[SavedSearches.sourceId] = sourceId
            it[SavedSearches.tag] = tag
            it[SavedSearches.createdAt] = java.time.Instant.now()
        }[SavedSearches.id]
    }

    fun deleteSavedSearch(userId: Long, id: Long): Boolean = transaction {
        SavedSearches.deleteWhere { (SavedSearches.id eq id) and (SavedSearches.userId eq userId) } > 0
    }

    fun insertSearchEvent(actorEmail: String?, query: String, sourceId: Long?, tag: String?, resultsCount: Int): Long = transaction {
        SearchEvents.insert {
            it[at] = java.time.Instant.now()
            it[SearchEvents.actorEmail] = actorEmail
            it[SearchEvents.query] = query
            it[SearchEvents.sourceId] = sourceId
            it[SearchEvents.tag] = tag
            it[SearchEvents.resultsCount] = resultsCount
            it[SearchEvents.clickedDocId] = null
        }[SearchEvents.id]
    }

    fun markSearchClick(eventId: Long, docId: Long): Boolean = transaction {
        SearchEvents.update({ SearchEvents.id eq eventId }) { it[clickedDocId] = docId } > 0
    }

    data class QueryAgg(val query: String, val count: Long)

    fun topQueries(limit: Int = 20): List<QueryAgg> = transaction {
        SearchEvents.slice(SearchEvents.query, SearchEvents.id.count())
            .selectAll()
            .groupBy(SearchEvents.query)
            .orderBy(SearchEvents.id.count(), SortOrder.DESC)
            .limit(limit)
            .map { QueryAgg(it[SearchEvents.query], it[SearchEvents.id.count()]) }
    }

    fun zeroResultQueries(limit: Int = 20): List<QueryAgg> = transaction {
        SearchEvents.slice(SearchEvents.query, SearchEvents.id.count())
            .select { SearchEvents.resultsCount eq 0 }
            .groupBy(SearchEvents.query)
            .orderBy(SearchEvents.id.count(), SortOrder.DESC)
            .limit(limit)
            .map { QueryAgg(it[SearchEvents.query], it[SearchEvents.id.count()]) }
    }

    fun analyticsSummary(): AnalyticsSummaryRow = transaction {
        val since = Instant.now().minus(7, ChronoUnit.DAYS)
        val totalDocuments = Documents.selectAll().count()
        val publishedDocuments = Documents.select { Documents.status eq "PUBLISHED" }.count()
        val totalGroups = Sources.selectAll().count()
        val activeGroups = Sources.select { Sources.isActive eq true }.count()
        val totalSearches = SearchEvents.selectAll().count()
        val searchesLast7Days = SearchEvents.select { SearchEvents.at greaterEq since }.count()
        val zeroResultSearches = SearchEvents.select { SearchEvents.resultsCount eq 0 }.count()
        val clickedSearches = SearchEvents.select { SearchEvents.clickedDocId.isNotNull() }.count()
        val importsTotal = AuditLog.select { AuditLog.action inList listOf("IMPORT_DOC", "IMPORT_DB") }.count()
        val importsLast7Days = AuditLog.select {
            (AuditLog.action inList listOf("IMPORT_DOC", "IMPORT_DB")) and (AuditLog.at greaterEq since)
        }.count()

        AnalyticsSummaryRow(
            totalDocuments = totalDocuments,
            publishedDocuments = publishedDocuments,
            totalGroups = totalGroups,
            activeGroups = activeGroups,
            totalSearches = totalSearches,
            searchesLast7Days = searchesLast7Days,
            zeroResultSearches = zeroResultSearches,
            zeroResultRate = if (totalSearches == 0L) 0.0 else zeroResultSearches.toDouble() / totalSearches.toDouble(),
            clickedSearches = clickedSearches,
            clickThroughRate = if (totalSearches == 0L) 0.0 else clickedSearches.toDouble() / totalSearches.toDouble(),
            importsTotal = importsTotal,
            importsLast7Days = importsLast7Days
        )
    }

    fun documentsByGroup(limit: Int = 10): List<GroupDocumentMetricRow> = transaction {
        (Sources leftJoin Documents)
            .slice(Sources.id, Sources.name, Documents.id.count())
            .selectAll()
            .groupBy(Sources.id, Sources.name)
            .orderBy(Documents.id.count(), SortOrder.DESC)
            .limit(limit)
            .map {
                GroupDocumentMetricRow(
                    sourceId = it[Sources.id],
                    sourceName = it[Sources.name],
                    documents = it[Documents.id.count()]
                )
            }
    }

}

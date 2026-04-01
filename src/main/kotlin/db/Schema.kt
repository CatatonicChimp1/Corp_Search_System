package com.example.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 120)
    val role = varchar("role", 32)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Sources : Table("sources") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 120)
    val kind = varchar("kind", 32)
    val description = varchar("description", 500).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Documents : Table("documents") {
    val id = long("id").autoIncrement()
    val status = varchar("status", 16).default("PUBLISHED")
    val sourceId = long("source_id").references(Sources.id)
    val title = varchar("title", 300)
    val body = text("body")
    val author = varchar("author", 120).nullable()
    val updatedAt = timestamp("updated_at")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, sourceId)
        index(false, title)
    }
}

object DocumentTags : Table("document_tags") {
    val documentId = long("document_id").references(Documents.id)
    val tag = varchar("tag", 64)
    override val primaryKey = PrimaryKey(documentId, tag)
}

object DocumentSearchIndex : Table("document_search_index") {
    val documentId = long("document_id").references(Documents.id).uniqueIndex()
    val sourceId = long("source_id").references(Sources.id)
    val status = varchar("status", 16)
    val title = varchar("title", 300)
    val bodyText = text("body_text")
    val author = varchar("author", 120).nullable()
    val tagsDisplay = text("tags_display")
    val tagsLookup = text("tags_lookup")
    val fileName = varchar("file_name", 300).nullable()
    val extractedText = text("extracted_text")
    val isFile = bool("is_file").default(false)
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(documentId)

    init {
        index(false, sourceId)
        index(false, status)
        index(false, isFile)
    }
}

object AuditLog : Table("audit_log") {
    val id = long("id").autoIncrement()
    val at = timestamp("at")
    val actorEmail = varchar("actor_email", 255).nullable()
    val action = varchar("action", 64)
    val meta = text("meta").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Invites : Table("invites") {
    val id = long("id").autoIncrement()
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val email = varchar("email", 255)
    val role = varchar("role", 32)
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at")
    val createdBy = varchar("created_by", 255).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, email)
        index(false, expiresAt)
        index(false, usedAt)
    }
}

object SourcePermissions : Table("source_permissions") {
    val userId = long("user_id").references(Users.id)
    val sourceId = long("source_id").references(Sources.id)
    val canRead = bool("can_read").default(true)
    val canWrite = bool("can_write").default(false)
    override val primaryKey = PrimaryKey(userId, sourceId)

    init {
        index(false, userId)
        index(false, sourceId)
    }
}

object SavedSearches : Table("saved_searches") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val name = varchar("name", 80)
    val query = varchar("query", 300).default("")
    val sourceId = long("source_id").nullable()
    val tag = varchar("tag", 64).nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId)
    }
}

object SearchEvents : Table("search_events") {
    val id = long("id").autoIncrement()
    val at = timestamp("at")
    val actorEmail = varchar("actor_email", 255).nullable()
    val query = varchar("query", 500)
    val sourceId = long("source_id").nullable()
    val tag = varchar("tag", 64).nullable()
    val resultsCount = integer("results_count")
    val clickedDocId = long("clicked_doc_id").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, at)
        index(false, actorEmail)
        index(false, resultsCount)
    }
}

package com.example.db

import com.example.config.AppConfig
import com.example.search.DocumentIndexService
import com.example.security.Passwords
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object DatabaseFactory {

    fun init(cfg: AppConfig) {
        val dbFile = File(cfg.dbPath)
        dbFile.parentFile?.mkdirs()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                Sources,
                Documents,
                DocumentTags,
                DocumentSearchIndex,
                AuditLog,
                Invites,
                SourcePermissions,
                SavedSearches,
                SearchEvents
            )

            Users.selectAll().forEach { row ->
                val id = row[Users.id]
                val e = row[Users.email]
                val lower = e.trim().lowercase()
                if (e != lower) {
                    Users.update({ Users.id eq id }) { it[email] = lower }
                }
            }

            seedAdminIfEmpty(cfg)
        }

        DocumentIndexService.rebuildAll()
    }

    private fun seedAdminIfEmpty(cfg: AppConfig) {
        val exists = Users.selectAll().limit(1).any()
        if (exists) return

        Users.insert {
            it[email] = cfg.admin.email.trim().lowercase()
            it[passwordHash] = Passwords.hash(cfg.admin.password)
            it[role] = "ADMIN"
            it[createdAt] = java.time.Instant.now()
        }
    }
}

package com.example.search

import com.example.db.Repos
import com.example.db.SearchableDocRow
import com.example.db.SourceRow
import com.example.db.UserRow
import com.example.storage.FileStorage

private val tokenSplitRegex = Regex("\\s+")

interface UnifiedSearchProvider {
    fun search(actor: SearchActor, request: UnifiedSearchRequest): List<UnifiedSearchHit>
}

class DocumentSearchProvider : UnifiedSearchProvider {
    override fun search(actor: SearchActor, request: UnifiedSearchRequest): List<UnifiedSearchHit> {
        val docs = indexedHits(actor, request, includeFiles = false)
        if (docs.isNotEmpty()) {
            return docs.map { hit ->
                UnifiedSearchHit(
                    kind = SearchHitKind.DOCUMENT,
                    title = hit.title,
                    snippet = documentSnippet(hit, request.query),
                    route = "#/doc/${hit.documentId}",
                    score = hit.score,
                    docId = hit.documentId,
                    sourceId = hit.sourceId,
                    sourceName = hit.sourceName,
                    sourceKind = hit.sourceKind,
                    match = detectDocumentMatch(hit, request.query),
                    tags = hit.tags
                )
            }
        }

        return recentDocs(actor, request)
            .filterNot { FileStorage.isStoredFileReference(it.body) }
            .map {
                UnifiedSearchHit(
                    kind = SearchHitKind.DOCUMENT,
                    title = it.title,
                    snippet = if (it.body.isBlank()) "Источник: ${it.sourceName} (${it.sourceKind})" else makeSnippet(it.body, request.query, 170),
                    route = "#/doc/${it.id}",
                    score = 1,
                    docId = it.id,
                    sourceId = it.sourceId,
                    sourceName = it.sourceName,
                    sourceKind = it.sourceKind,
                    match = "recent",
                    tags = it.tags
                )
            }
    }
}

class FileSearchProvider : UnifiedSearchProvider {
    override fun search(actor: SearchActor, request: UnifiedSearchRequest): List<UnifiedSearchHit> {
        val hits = indexedHits(actor, request, includeFiles = true)
        if (hits.isNotEmpty()) {
            return hits.map { hit ->
                val fileName = hit.fileName ?: hit.title
                UnifiedSearchHit(
                    kind = SearchHitKind.FILE,
                    title = fileName,
                    snippet = fileSnippet(hit, request.query),
                    route = "#/doc/${hit.documentId}",
                    score = hit.score,
                    docId = hit.documentId,
                    sourceId = hit.sourceId,
                    sourceName = hit.sourceName,
                    sourceKind = hit.sourceKind,
                    match = detectFileMatch(hit, request.query),
                    tags = hit.tags,
                    meta = mapOf("downloadUrl" to "/api/docs/${hit.documentId}/download")
                )
            }
        }

        return recentDocs(actor, request)
            .filter { FileStorage.isStoredFileReference(it.body) }
            .mapNotNull { doc ->
                val storedName = FileStorage.extractStoredFileName(doc.body) ?: return@mapNotNull null
                val fileName = FileStorage.presentableFileName(storedName)
                UnifiedSearchHit(
                    kind = SearchHitKind.FILE,
                    title = fileName,
                    snippet = "Файл: $fileName • Источник: ${doc.sourceName} (${doc.sourceKind})",
                    route = "#/doc/${doc.id}",
                    score = 1,
                    docId = doc.id,
                    sourceId = doc.sourceId,
                    sourceName = doc.sourceName,
                    sourceKind = doc.sourceKind,
                    match = "file",
                    tags = doc.tags,
                    meta = mapOf("downloadUrl" to "/api/docs/${doc.id}/download")
                )
            }
    }
}

class SourceSearchProvider : UnifiedSearchProvider {
    override fun search(actor: SearchActor, request: UnifiedSearchRequest): List<UnifiedSearchHit> {
        val sources = Repos.listSourcesForUser(actor.userId, actor.isAdmin)
        if (request.query.isBlank()) return emptyList()

        return sources.asSequence()
            .mapNotNull { source ->
                val match = scoreSource(source, request.query) ?: return@mapNotNull null
                UnifiedSearchHit(
                    kind = SearchHitKind.SOURCE,
                    title = source.name,
                    snippet = buildSourceSnippet(source),
                    route = buildSearchRoute(query = request.query, sourceId = source.id, tag = request.tag, scopes = request.scopes),
                    score = match,
                    sourceId = source.id,
                    sourceName = source.name,
                    sourceKind = source.kind,
                    match = "source",
                    meta = mapOf("isActive" to source.isActive.toString())
                )
            }
            .toList()
    }
}

class UserSearchProvider : UnifiedSearchProvider {
    override fun search(actor: SearchActor, request: UnifiedSearchRequest): List<UnifiedSearchHit> {
        if (!actor.isAdmin || request.query.isBlank()) return emptyList()

        return Repos.listUsers().asSequence()
            .mapNotNull { user ->
                val score = scoreUser(user, request.query) ?: return@mapNotNull null
                UnifiedSearchHit(
                    kind = SearchHitKind.USER,
                    title = user.email,
                    snippet = "Роль: ${user.role}",
                    route = "#/admin/users",
                    score = score,
                    match = "email",
                    meta = mapOf("userId" to user.id.toString(), "role" to user.role)
                )
            }
            .toList()
    }
}

private fun indexedHits(actor: SearchActor, request: UnifiedSearchRequest, includeFiles: Boolean): List<IndexedDocumentHit> =
    DocumentIndexService.search(
        query = request.query,
        allowedSourceIds = actor.allowedSourceIds(),
        allowedStatuses = actor.allowedStatuses(),
        sourceId = request.sourceId,
        tag = request.tag,
        includeFiles = includeFiles,
        limit = (request.limit + request.offset).coerceAtLeast(50),
        offset = 0
    )

private fun recentDocs(actor: SearchActor, request: UnifiedSearchRequest): List<SearchableDocRow> =
    Repos.listSearchableDocs(
        sourceId = request.sourceId,
        tag = request.tag,
        allowedSourceIds = actor.allowedSourceIds(),
        allowedStatuses = actor.allowedStatuses()
    ).take((request.limit + request.offset).coerceAtLeast(50))

private fun SearchActor.allowedSourceIds(): Set<Long>? =
    if (isAdmin) null else Repos.listSourcesForUser(userId, isAdmin = false).map { it.id }.toSet()

private fun SearchActor.allowedStatuses(): Set<String> =
    if (isAdmin) setOf("DRAFT", "PUBLISHED", "ARCHIVED") else setOf("PUBLISHED")

private fun detectDocumentMatch(hit: IndexedDocumentHit, query: String): String =
    when {
        containsQuery(hit.title, query) -> "title"
        containsQuery(hit.tags.joinToString(" "), query) -> "tag"
        containsQuery(hit.author.orEmpty(), query) -> "author"
        containsQuery(hit.bodyText, query) -> "body"
        containsQuery(hit.extractedText, query) -> "content"
        else -> "text"
    }

private fun detectFileMatch(hit: IndexedDocumentHit, query: String): String =
    when {
        containsQuery(hit.fileName.orEmpty(), query) -> "filename"
        containsQuery(hit.extractedText, query) -> "content"
        containsQuery(hit.tags.joinToString(" "), query) -> "tag"
        else -> "file"
    }

private fun documentSnippet(hit: IndexedDocumentHit, query: String): String =
    when {
        containsQuery(hit.bodyText, query) -> makeSnippet(hit.bodyText, query, 170)
        containsQuery(hit.tags.joinToString(" "), query) -> "Теги: ${hit.tags.joinToString(", ")}"
        containsQuery(hit.author.orEmpty(), query) -> "Автор: ${hit.author ?: "—"}"
        else -> "Источник: ${hit.sourceName} (${hit.sourceKind})"
    }

private fun fileSnippet(hit: IndexedDocumentHit, query: String): String =
    when {
        containsQuery(hit.extractedText, query) -> makeSnippet(hit.extractedText, query, 170)
        containsQuery(hit.fileName.orEmpty(), query) -> "Файл: ${hit.fileName ?: hit.title}"
        else -> "Файл: ${hit.fileName ?: hit.title} • Источник: ${hit.sourceName} (${hit.sourceKind})"
    }

private fun scoreSource(source: SourceRow, query: String): Int? {
    val tokens = query.lowercase().split(tokenSplitRegex).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    val haystack = listOf(source.name, source.kind, source.description.orEmpty())
        .joinToString(" ")
        .lowercase()

    val matched = tokens.count { haystack.contains(it) }
    if (matched == 0) return null
    return 250 + matched * 20
}

private fun scoreUser(user: UserRow, query: String): Int? {
    val tokens = query.lowercase().split(tokenSplitRegex).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    val matched = tokens.count { user.email.lowercase().contains(it) || user.role.lowercase().contains(it) }
    if (matched == 0) return null
    return 220 + matched * 20
}

private fun buildSourceSnippet(source: SourceRow): String =
    buildString {
        append("Тип: ").append(source.kind)
        if (!source.description.isNullOrBlank()) {
            append(" • ").append(source.description)
        }
    }

private fun makeSnippet(text: String, query: String, maxLength: Int): String {
    val normalized = text.replace('\n', ' ').trim()
    if (normalized.isBlank()) return ""
    if (query.isBlank()) return normalized.take(maxLength)

    val token = query.lowercase().split(tokenSplitRegex).firstOrNull { it.isNotBlank() } ?: return normalized.take(maxLength)
    val index = normalized.lowercase().indexOf(token)
    if (index < 0) return normalized.take(maxLength)

    val radius = (maxLength / 2).coerceAtLeast(40)
    val start = (index - radius).coerceAtLeast(0)
    val end = (index + token.length + radius).coerceAtMost(normalized.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < normalized.length) "…" else ""
    return prefix + normalized.substring(start, end).trim() + suffix
}

private fun containsQuery(value: String, query: String): Boolean {
    if (value.isBlank() || query.isBlank()) return false
    val tokens = query.lowercase().split(tokenSplitRegex).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return false
    val normalized = value.lowercase()
    return tokens.any { normalized.contains(it) }
}

private fun buildSearchRoute(
    query: String,
    sourceId: Long?,
    tag: String?,
    scopes: Set<SearchScope>
): String {
    val params = mutableListOf<String>()
    if (query.isNotBlank()) params += "q=${encodeHashComponent(query)}"
    if (sourceId != null) params += "sourceId=$sourceId"
    if (!tag.isNullOrBlank()) params += "tag=${encodeHashComponent(tag)}"
    if (scopes.isNotEmpty()) params += "scopes=${encodeHashComponent(scopes.joinToString("," ) { it.name })}"
    return if (params.isEmpty()) "#/search" else "#/search?${params.joinToString("&")}"
}

private fun encodeHashComponent(value: String): String =
    java.net.URLEncoder.encode(value, Charsets.UTF_8)

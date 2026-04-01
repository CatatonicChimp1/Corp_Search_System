package com.example.routes

import com.example.db.Repos
import com.example.search.SearchActor
import com.example.search.SearchScope
import com.example.search.UnifiedSearchRequest
import com.example.search.UnifiedSearchService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class SearchHitDto(
    val kind: String,
    val title: String,
    val snippet: String,
    val route: String,
    val docId: Long? = null,
    val sourceId: Long? = null,
    val sourceName: String? = null,
    val sourceKind: String? = null,
    val match: String? = null,
    val tags: List<String> = emptyList(),
    val meta: Map<String, String> = emptyMap()
)

@Serializable
data class SearchRes(
    val eventId: Long,
    val query: String,
    val total: Int,
    val appliedScopes: List<String>,
    val hits: List<SearchHitDto>
)

private val unifiedSearchService = UnifiedSearchService()

private fun ApplicationCall.actorEmail(): String? =
    principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString()

private fun ApplicationCall.actorRole(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("role").asString()

private fun parseScopes(raw: String?): Set<SearchScope> {
    if (raw.isNullOrBlank()) return SearchScope.entries.toSet()

    return raw.split(",")
        .mapNotNull { token ->
            SearchScope.entries.firstOrNull { it.name.equals(token.trim(), ignoreCase = true) }
        }
        .toSet()
        .ifEmpty { SearchScope.entries.toSet() }
}

fun Route.searchRoutes() {
    get("/api/search") {
        val q = (call.request.queryParameters["q"] ?: "").trim()
        val sourceId = call.request.queryParameters["sourceId"]?.toLongOrNull()
        val tag = call.request.queryParameters["tag"]?.trim()?.takeIf { it.isNotBlank() }
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val offset = (call.request.queryParameters["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val scopes = parseScopes(call.request.queryParameters["scopes"])

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

        val searchResult = unifiedSearchService.search(
            actor = SearchActor(email = actor, userId = userId, isAdmin = isAdmin),
            request = UnifiedSearchRequest(
                query = q,
                sourceId = sourceId,
                tag = tag,
                scopes = scopes,
                limit = limit,
                offset = offset
            )
        )

        val hits = searchResult.hits.map {
            SearchHitDto(
                kind = it.kind.name,
                title = it.title,
                snippet = it.snippet,
                route = it.route,
                docId = it.docId,
                sourceId = it.sourceId,
                sourceName = it.sourceName,
                sourceKind = it.sourceKind,
                match = it.match,
                tags = it.tags,
                meta = it.meta
            )
        }

        val eventId = Repos.insertSearchEvent(actor, q, sourceId, tag, searchResult.total)
        Repos.insertAudit(
            actor,
            "SEARCH",
            "eventId=$eventId q=$q sourceId=$sourceId tag=$tag scopes=${scopes.joinToString(",")} results=${searchResult.total}"
        )

        call.respond(
            SearchRes(
                eventId = eventId,
                query = q,
                total = searchResult.total,
                appliedScopes = scopes.map { it.name },
                hits = hits
            )
        )
    }
}

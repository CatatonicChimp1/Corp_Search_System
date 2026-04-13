package com.example.routes

import com.example.db.Repos
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class ClickReq(val eventId: Long, val docId: Long)
@Serializable data class AggDto(val query: String, val count: Long)
@Serializable
data class AnalyticsSummaryDto(
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

@Serializable data class GroupMetricDto(val sourceId: Long, val sourceName: String, val documents: Long)

private fun ApplicationCall.actorEmail(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("email").asString()

private fun ApplicationCall.actorRole(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("role").asString()

fun Route.analyticsRoutes() {

    post("/api/analytics/click") {
        val req = call.receive<ClickReq>()
        val ok = Repos.markSearchClick(req.eventId, req.docId)
        if (!ok) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Event not found"))
            return@post
        }
        Repos.insertAudit(call.actorEmail(), "SEARCH_CLICK", "eventId=${req.eventId} docId=${req.docId}")
        call.respond(mapOf("ok" to true))
    }

    get("/api/analytics/top-queries") {
        if (call.actorRole() != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
            return@get
        }
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 200)
        call.respond(Repos.topQueries(limit).map { AggDto(it.query, it.count) })
    }

    get("/api/analytics/zero-results") {
        if (call.actorRole() != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
            return@get
        }
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 200)
        call.respond(Repos.zeroResultQueries(limit).map { AggDto(it.query, it.count) })
    }

    get("/api/analytics/summary") {
        if (call.actorRole() != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
            return@get
        }
        val summary = Repos.analyticsSummary()
        call.respond(
            AnalyticsSummaryDto(
                totalDocuments = summary.totalDocuments,
                publishedDocuments = summary.publishedDocuments,
                totalGroups = summary.totalGroups,
                activeGroups = summary.activeGroups,
                totalSearches = summary.totalSearches,
                searchesLast7Days = summary.searchesLast7Days,
                zeroResultSearches = summary.zeroResultSearches,
                zeroResultRate = summary.zeroResultRate,
                clickedSearches = summary.clickedSearches,
                clickThroughRate = summary.clickThroughRate,
                importsTotal = summary.importsTotal,
                importsLast7Days = summary.importsLast7Days
            )
        )
    }

    get("/api/analytics/groups") {
        if (call.actorRole() != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin only"))
            return@get
        }
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 10).coerceIn(1, 100)
        call.respond(Repos.documentsByGroup(limit).map { GroupMetricDto(it.sourceId, it.sourceName, it.documents) })
    }
}

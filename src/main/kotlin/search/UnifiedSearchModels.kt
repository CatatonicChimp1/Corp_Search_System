package com.example.search

enum class SearchScope {
    DOCUMENTS,
    FILES,
    SOURCES,
    USERS
}

enum class SearchHitKind {
    DOCUMENT,
    FILE,
    SOURCE,
    USER
}

data class SearchActor(
    val email: String?,
    val userId: Long,
    val isAdmin: Boolean
)

data class UnifiedSearchRequest(
    val query: String,
    val sourceId: Long? = null,
    val tag: String? = null,
    val scopes: Set<SearchScope> = setOf(
        SearchScope.DOCUMENTS,
        SearchScope.FILES,
        SearchScope.SOURCES,
        SearchScope.USERS
    ),
    val limit: Int = 20,
    val offset: Int = 0
)

data class UnifiedSearchHit(
    val kind: SearchHitKind,
    val title: String,
    val snippet: String,
    val route: String,
    val score: Int,
    val docId: Long? = null,
    val sourceId: Long? = null,
    val sourceName: String? = null,
    val sourceKind: String? = null,
    val match: String? = null,
    val tags: List<String> = emptyList(),
    val meta: Map<String, String> = emptyMap()
)

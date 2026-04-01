package com.example.search

class UnifiedSearchService(
    private val providers: Map<SearchScope, UnifiedSearchProvider> = mapOf(
        SearchScope.DOCUMENTS to DocumentSearchProvider(),
        SearchScope.FILES to FileSearchProvider(),
        SearchScope.SOURCES to SourceSearchProvider(),
        SearchScope.USERS to UserSearchProvider()
    )
) {
    data class SearchResultPage(
        val total: Int,
        val hits: List<UnifiedSearchHit>
    )

    fun search(actor: SearchActor, request: UnifiedSearchRequest): SearchResultPage {
        val allHits = request.scopes.asSequence()
            .mapNotNull { scope -> providers[scope] }
            .flatMap { provider -> provider.search(actor, request).asSequence() }
            .sortedWith(
                compareByDescending<UnifiedSearchHit> { it.score }
                    .thenBy { it.kind.name }
                    .thenBy { it.title.lowercase() }
            )
            .toList()

        return SearchResultPage(
            total = allHits.size,
            hits = allHits.drop(request.offset).take(request.limit)
        )
    }
}

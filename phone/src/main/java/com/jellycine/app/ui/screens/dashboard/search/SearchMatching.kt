package com.jellycine.app.ui.screens.dashboard.search

import java.util.Locale

internal fun bestSearchMatch(query: String, vararg texts: String?): SearchMatch? {
    return texts
        .filterNotNull()
        .mapNotNull { text -> textSearchMatch(text, query) }
        .minByOrNull { match -> match.priority }
}

private fun textSearchMatch(
    text: String,
    query: String
): SearchMatch? {
    val trimmedQuery = query.trim()
    if (text.isBlank() || trimmedQuery.isBlank()) return null

    val lowerQuery = trimmedQuery.lowercase(Locale.US)
    val normalizedQuery = trimmedQuery.normalizedSearchKey()
    val lowerText = text.lowercase(Locale.US)
    val normalizedText = text.normalizedSearchKey()
    val queryWords = lowerQuery
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { word -> word.isNotBlank() }
    val tokens = lowerText.split(Regex("[^\\p{L}\\p{N}]+")).filter { token -> token.isNotBlank() }
    val allWordsMatch = queryWords.isNotEmpty() && queryWords.all { word ->
        tokens.any { token ->
            token == word || token.startsWith(word) || token.contains(word)
        }
    }

    return when {
        normalizedQuery.isNotEmpty() && normalizedText == normalizedQuery -> SearchMatch.EXACT
        lowerText == lowerQuery -> SearchMatch.EXACT_TEXT
        lowerText.contains(lowerQuery) -> SearchMatch.PHRASE
        normalizedQuery.isNotEmpty() && normalizedText.contains(normalizedQuery) -> SearchMatch.NORMALIZED_PHRASE
        allWordsMatch -> SearchMatch.ALL_WORDS
        queryWords.size == 1 && tokens.any { token ->
            token.startsWith(queryWords.first()) || token.contains(queryWords.first())
        } -> SearchMatch.SINGLE_WORD
        else -> null
    }
}

private fun String.normalizedSearchKey(): String {
    return lowercase(Locale.US).replace(Regex("[^\\p{L}\\p{N}]+"), "")
}


internal enum class SearchMatch(val priority: Int) {
    EXACT(0),
    EXACT_TEXT(1),
    PHRASE(2),
    NORMALIZED_PHRASE(3),
    ALL_WORDS(4),
    SINGLE_WORD(5)
}

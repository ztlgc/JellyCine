package com.jellycine.app.ui.screens.dashboard.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchMatchingTest {
    @Test fun chineseTitlesMatchWithoutCollapsingToAnEmptyKey() {
        assertEquals(SearchMatch.EXACT, bestSearchMatch("旅行", "旅行"))
        assertEquals(SearchMatch.PHRASE, bestSearchMatch("旅行", "家庭旅行记录"))
        assertNull(bestSearchMatch("旅行", "生日聚会"))
        assertNull(bestSearchMatch("旅行", "Holiday"))
    }

    @Test fun originalTitlesAndMixedLanguagesRemainSearchable() {
        assertEquals(SearchMatch.EXACT, bestSearchMatch("Holiday", "假日", "Holiday"))
        assertEquals(SearchMatch.PHRASE, bestSearchMatch("旅行 2026", "家庭旅行 2026"))
        assertEquals(SearchMatch.NORMALIZED_PHRASE, bestSearchMatch("旅行2026", "家庭旅行-2026"))
    }

    @Test fun blankOrPunctuationQueriesDoNotMatchEveryTitle() {
        assertNull(bestSearchMatch(" ", "Title"))
        assertNull(bestSearchMatch("---", "Title"))
        assertNull(bestSearchMatch("旅行", null, ""))
    }
}

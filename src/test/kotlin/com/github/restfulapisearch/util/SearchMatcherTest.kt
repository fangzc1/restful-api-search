package com.github.restfulapisearch.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchMatcherTest {

    @Test
    fun `应支持复用预处理查询并保持匹配结果一致`() {
        val query = "GET user stop"
        val searchableText = "/api/users/stop GET UserController stopUser".lowercase()
        val path = "/api/users/stop"
        val otherPath = "/api/users/status"

        val preparedQuery = SearchMatcher.prepareQuery(query)

        val directMatch = assertNotNull(SearchMatcher.match(searchableText, query))
        val preparedMatch = assertNotNull(SearchMatcher.match(searchableText, preparedQuery))
        assertEquals(directMatch, preparedMatch)
        assertEquals(
            SearchMatcher.findMatchPositions(searchableText, query),
            SearchMatcher.findMatchPositions(searchableText, preparedQuery)
        )

        val directCompare = SearchMatcher.comparePathsByRelevance(path, otherPath, query)
        val preparedCompare = SearchMatcher.comparePathsByRelevance(path, otherPath, preparedQuery)
        assertEquals(directCompare, preparedCompare)
    }

    @Test
    fun `应按相关度优先返回额外层级更少的路径`() {
        val query = "projects/{}task-types/selector"
        val milestonePath = "/api/v1/projects/{projectId}/milestones/{id}/task-types/selector"
        val exactPath = "/api/v1/projects/{projectId}/task-types/selector"
        val paths = listOf(
            milestonePath,
            "/api/v1/projects/{projectId}/sprints/{sprintId}/task-types/selector",
            exactPath,
            "/api/v1/projects/{projectId}/task-types/task-types/selector"
        )

        val milestoneMatch = assertNotNull(SearchMatcher.match(milestonePath, query))
        val exactMatch = assertNotNull(SearchMatcher.match(exactPath, query))
        assertTrue(
            SearchMatcher.comparePathsByRelevance(exactPath, milestonePath, query, exactMatch, milestoneMatch) < 0,
            "exact=$exactMatch milestone=$milestoneMatch"
        )

        val ranked = SearchMatcher.sortByRelevance(paths, query)

        assertEquals(
            exactPath,
            ranked.first(),
            ranked.joinToString(separator = "\n")
        )
    }
}

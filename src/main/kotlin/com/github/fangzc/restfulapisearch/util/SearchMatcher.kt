package com.github.restfulapisearch.util

/**
 * 搜索匹配工具
 * 支持子串匹配和连续分段匹配，并返回匹配位置用于高亮
 *
 * 连续分段匹配规则：将查询拆为若干连续段，每段在文本中必须是连续子串，
 * 且每段至少 2 个字符（最后剩余 1 个字符时放宽为 1）。
 * 例如 "userstop" 可拆为 "user"+"stop"，分别在文本中连续命中。
 */
object SearchMatcher {

    private val PATH_VARIABLE_REGEX = "\\{[^}/]+}".toRegex()
    private val WHITESPACE_REGEX = "\\s+".toRegex()

    data class PreparedQuery(
        val rawQuery: String,
        val lowerQuery: String,
        val tokens: List<String>,
        val normalizedQuery: String,
        val normalizedTokens: List<String>
    )

    data class MatchResult(
        val positions: Set<Int>,
        val exactTokenMatches: Int,
        val totalGap: Int,
        val totalSpan: Int,
        val earliestStart: Int
    )

    private data class TokenMatchResult(
        val positions: Set<Int>,
        val exactSubstring: Boolean,
        val start: Int,
        val span: Int
    )

    fun prepareQuery(query: String): PreparedQuery {
        val lowerQuery = query.lowercase()
        val normalizedQuery = normalizePathForRanking(query)
        return PreparedQuery(
            rawQuery = query,
            lowerQuery = lowerQuery,
            tokens = lowerQuery.split(WHITESPACE_REGEX).filter { it.isNotEmpty() },
            normalizedQuery = normalizedQuery,
            normalizedTokens = normalizedQuery.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        )
    }

    /**
     * 判断文本是否匹配查询（空格分词，AND 逻辑）
     */
    fun matches(text: String, query: String): Boolean {
        return match(text, prepareQuery(query)) != null
    }

    fun matches(text: String, preparedQuery: PreparedQuery): Boolean {
        return match(text, preparedQuery) != null
    }

    /**
     * 查找查询在文本中的匹配字符位置（用于高亮渲染）
     */
    fun findMatchPositions(text: String, query: String): Set<Int> {
        return match(text, prepareQuery(query))?.positions ?: emptySet()
    }

    fun findMatchPositions(text: String, preparedQuery: PreparedQuery): Set<Int> {
        return match(text, preparedQuery)?.positions ?: emptySet()
    }

    /**
     * 计算文本对查询的匹配结果，供过滤、高亮和排序复用。
     */
    fun match(text: String, query: String): MatchResult? {
        return match(text, prepareQuery(query))
    }

    fun match(text: String, preparedQuery: PreparedQuery): MatchResult? {
        val lowerText = text.lowercase()
        return matchLowercaseText(lowerText, preparedQuery)
            ?: if (' ' !in text) {
                matchNormalizedPath(normalizePathForRanking(text), preparedQuery)
            } else {
                null
            }
    }

    /**
     * 用于已预先 lowercase 的高频文本，避免重复 lowercase 分配。
     */
    fun matchLowercaseText(lowerText: String, preparedQuery: PreparedQuery): MatchResult? {
        return matchPrepared(lowerText, preparedQuery)
    }

    fun matchNormalizedPath(normalizedText: String, preparedQuery: PreparedQuery): MatchResult? {
        return matchNormalizedPrepared(normalizedText, preparedQuery)
    }

    /**
     * 按相关度排序：
     * 1. 更多完整子串命中优先
     * 2. 匹配跨越的无关字符越少越优先
     * 3. 整体命中跨度越短越优先
     * 4. 更早出现的命中优先
     * 5. 路径更短优先
     */
    fun sortByRelevance(paths: List<String>, query: String): List<String> {
        if (query.isBlank()) return paths
        return sortByRelevance(paths, prepareQuery(query))
    }

    fun sortByRelevance(paths: List<String>, preparedQuery: PreparedQuery): List<String> {
        if (preparedQuery.tokens.isEmpty()) return paths
        return paths.sortedWith { left, right ->
            comparePathsByRelevance(left, right, preparedQuery)
        }
    }

    /**
     * 比较两个路径对查询的相关度。
     * 先按归一化后的紧凑路径比较，再回退到原始路径比较。
     */
    fun comparePathsByRelevance(
        leftText: String,
        rightText: String,
        query: String,
        leftRawMatch: MatchResult? = match(leftText, query),
        rightRawMatch: MatchResult? = match(rightText, query)
    ): Int {
        return comparePathsByRelevance(
            leftText = leftText,
            rightText = rightText,
            preparedQuery = prepareQuery(query),
            leftRawMatch = leftRawMatch,
            rightRawMatch = rightRawMatch
        )
    }

    fun comparePathsByRelevance(
        leftText: String,
        rightText: String,
        preparedQuery: PreparedQuery,
        leftRawMatch: MatchResult? = match(leftText, preparedQuery),
        rightRawMatch: MatchResult? = match(rightText, preparedQuery),
        leftNormalized: String = normalizePathForRanking(leftText),
        rightNormalized: String = normalizePathForRanking(rightText)
    ): Int {
        val normalizedCompare = compareMatchResult(
            matchNormalizedPrepared(leftNormalized, preparedQuery),
            leftNormalized,
            matchNormalizedPrepared(rightNormalized, preparedQuery),
            rightNormalized
        )
        if (normalizedCompare != 0) return normalizedCompare

        return compareMatchResult(leftRawMatch, leftText, rightRawMatch, rightText)
    }

    /**
     * 比较两个匹配结果的相关度，返回值语义与 Comparator 一致。
     */
    fun compareMatchResult(
        left: MatchResult?,
        leftText: String,
        right: MatchResult?,
        rightText: String
    ): Int {
        if (left == null && right == null) return leftText.compareTo(rightText)
        if (left == null) return 1
        if (right == null) return -1

        compareValues(right.exactTokenMatches, left.exactTokenMatches).takeIf { it != 0 }?.let { return it }
        compareValues(left.totalGap, right.totalGap).takeIf { it != 0 }?.let { return it }
        compareValues(left.totalSpan, right.totalSpan).takeIf { it != 0 }?.let { return it }
        compareValues(left.earliestStart, right.earliestStart).takeIf { it != 0 }?.let { return it }
        compareValues(leftText.length, rightText.length).takeIf { it != 0 }?.let { return it }
        return leftText.compareTo(rightText)
    }

    fun normalizePathForRanking(value: String): String {
        return value.lowercase().replace(PATH_VARIABLE_REGEX, "{}").replace("/", "")
    }

    private fun matchPrepared(lowerText: String, preparedQuery: PreparedQuery): MatchResult? {
        if (preparedQuery.lowerQuery.isBlank()) return MatchResult(emptySet(), 0, 0, 0, Int.MAX_VALUE)
        if (preparedQuery.tokens.isEmpty()) return MatchResult(emptySet(), 0, 0, 0, Int.MAX_VALUE)

        val tokenMatches = preparedQuery.tokens.map { token ->
            matchToken(lowerText, token) ?: return null
        }

        val positions = buildSet {
            tokenMatches.forEach { addAll(it.positions) }
        }
        val sortedPositions = positions.sorted()
        val totalSpan = if (sortedPositions.isEmpty()) 0 else sortedPositions.last() - sortedPositions.first() + 1

        return MatchResult(
            positions = positions,
            exactTokenMatches = tokenMatches.count { it.exactSubstring },
            totalGap = tokenMatches.sumOf { it.span - it.positions.size },
            totalSpan = totalSpan,
            earliestStart = tokenMatches.minOf { it.start }
        )
    }

    private fun matchNormalizedPrepared(normalizedText: String, preparedQuery: PreparedQuery): MatchResult? {
        if (preparedQuery.normalizedQuery.isBlank()) return MatchResult(emptySet(), 0, 0, 0, Int.MAX_VALUE)
        if (preparedQuery.normalizedTokens.isEmpty()) return MatchResult(emptySet(), 0, 0, 0, Int.MAX_VALUE)

        val tokenMatches = preparedQuery.normalizedTokens.map { token ->
            matchToken(normalizedText, token) ?: return null
        }

        val positions = buildSet {
            tokenMatches.forEach { addAll(it.positions) }
        }
        val sortedPositions = positions.sorted()
        val totalSpan = if (sortedPositions.isEmpty()) 0 else sortedPositions.last() - sortedPositions.first() + 1

        return MatchResult(
            positions = positions,
            exactTokenMatches = tokenMatches.count { it.exactSubstring },
            totalGap = tokenMatches.sumOf { it.span - it.positions.size },
            totalSpan = totalSpan,
            earliestStart = tokenMatches.minOf { it.start }
        )
    }

    /**
     * 单个 token 的匹配结果：先子串，再连续分段
     */
    private fun matchToken(lowerText: String, token: String): TokenMatchResult? {
        val subIdx = lowerText.indexOf(token)
        if (subIdx >= 0) {
            return TokenMatchResult(
                positions = (subIdx until subIdx + token.length).toSet(),
                exactSubstring = true,
                start = subIdx,
                span = token.length
            )
        }

        val positions = chunkedMatch(lowerText, token) ?: return null
        val sortedPositions = positions.sorted()
        return TokenMatchResult(
            positions = positions,
            exactSubstring = false,
            start = sortedPositions.first(),
            span = sortedPositions.last() - sortedPositions.first() + 1
        )
    }

    /**
     * 连续分段匹配：将 token 拆为若干连续段在 text 中依次查找
     * 每段必须 ≥ 2 个字符（token 最后仅剩 1 字符时放宽为 1）
     * 返回匹配到的文本位置集合，无法匹配返回 null
     */
    private fun chunkedMatch(text: String, token: String): Set<Int>? {
        val positions = mutableSetOf<Int>()
        var queryPos = 0
        var textSearchFrom = 0

        while (queryPos < token.length) {
            val remaining = token.length - queryPos
            var found = false

            // 在 text 中从 textSearchFrom 开始寻找匹配起点
            var textStart = textSearchFrom
            while (textStart < text.length) {
                if (text[textStart] != token[queryPos]) {
                    textStart++
                    continue
                }

                // 从 textStart 开始贪心扩展连续匹配
                var matchLen = 1
                while (queryPos + matchLen < token.length
                    && textStart + matchLen < text.length
                    && token[queryPos + matchLen] == text[textStart + matchLen]
                ) {
                    matchLen++
                }

                // 每段至少 2 个字符；token 最后仅剩 1 字符时允许 1
                if (matchLen >= 2 || remaining == 1) {
                    for (i in 0 until matchLen) {
                        positions.add(textStart + i)
                    }
                    queryPos += matchLen
                    textSearchFrom = textStart + matchLen
                    found = true
                    break
                }

                textStart++
            }

            if (!found) return null
        }
        return positions
    }
}

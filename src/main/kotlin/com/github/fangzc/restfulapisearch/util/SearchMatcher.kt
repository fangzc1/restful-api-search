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

    private val WHITESPACE_REGEX = "\\s+".toRegex()

    /**
     * 判断文本是否匹配查询（空格分词，AND 逻辑）
     */
    fun matches(text: String, query: String): Boolean {
        if (query.isBlank()) return true
        val lowerText = text.lowercase()
        val tokens = query.lowercase().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        return tokens.all { token -> matchesToken(lowerText, token) }
    }

    /**
     * 查找查询在文本中的匹配字符位置（用于高亮渲染）
     */
    fun findMatchPositions(text: String, query: String): Set<Int> {
        if (query.isBlank()) return emptySet()
        val lowerText = text.lowercase()
        val tokens = query.lowercase().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val positions = mutableSetOf<Int>()
        for (token in tokens) {
            positions.addAll(findTokenPositions(lowerText, token))
        }
        return positions
    }

    /**
     * 单个 token 的匹配判断：先子串，再连续分段
     */
    private fun matchesToken(lowerText: String, token: String): Boolean {
        if (lowerText.contains(token)) return true
        return chunkedMatch(lowerText, token) != null
    }

    /**
     * 查找单个 token 的匹配位置
     */
    private fun findTokenPositions(lowerText: String, token: String): Set<Int> {
        // 优先子串匹配
        val subIdx = lowerText.indexOf(token)
        if (subIdx >= 0) {
            return (subIdx until subIdx + token.length).toSet()
        }
        // 回退连续分段匹配
        return chunkedMatch(lowerText, token) ?: emptySet()
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

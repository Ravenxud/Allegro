package com.ravenxud.allegro.update

/**
 * 版本号比较（够用就好，不引入 semver 库）。
 *
 * 规则：
 * - 忽略前缀 `v`/`V`，只比数字段，缺失段按 0 处理（`0.27` == `0.27.0`）；
 * - 数字段相同则看预发布标记：`0.27.2-beta` < `0.27.2`；
 * - 预发布里再按字母序比较（`alpha` < `beta` < `rc`），够覆盖本项目的命名习惯。
 */
data class ReleaseVersion(
    val numbers: List<Int>,
    val prerelease: String?,
) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int {
        val size = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until size) {
            val a = numbers.getOrElse(i) { 0 }
            val b = other.numbers.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        // 数字相同：正式版 > 预发布
        val mine = prerelease
        val theirs = other.prerelease
        return when {
            mine == null && theirs == null -> 0
            mine == null -> 1
            theirs == null -> -1
            else -> rank(mine).compareTo(rank(theirs)).takeIf { it != 0 } ?: mine.compareTo(theirs)
        }
    }

    private fun rank(pre: String): Int = when {
        pre.startsWith("alpha") -> 0
        pre.startsWith("beta") -> 1
        pre.startsWith("rc") -> 3
        else -> 2
    }

    companion object {
        /** 解析失败返回 null（例如 tag 是 `nightly` 这种非版本号）。 */
        fun parse(raw: String): ReleaseVersion? {
            val text = raw.trim().removePrefix("v").removePrefix("V")
            if (text.isEmpty()) return null
            val dash = text.indexOf('-')
            val numeric = if (dash >= 0) text.substring(0, dash) else text
            val pre = if (dash >= 0) text.substring(dash + 1).ifBlank { null } else null
            val parts = numeric.split('.').map { part ->
                val digits = part.takeWhile { it.isDigit() }
                if (digits.isEmpty()) return null
                digits.toIntOrNull() ?: return null
            }
            if (parts.isEmpty()) return null
            return ReleaseVersion(parts, pre?.lowercase())
        }
    }
}

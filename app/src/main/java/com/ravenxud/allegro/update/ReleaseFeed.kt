package com.ravenxud.allegro.update

/** Release 里的一个资产。 */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long,
)

/** 从 GitHub `/releases` 列表里解析出的候选版本（纯数据，便于单测）。 */
data class ReleaseCandidate(
    val tag: String,
    val title: String,
    val notes: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>,
)

/**
 * 选出版本号最大的一个非 draft 发布。
 *
 * 为什么不用 `/releases/latest`：GitHub 的那个接口**会跳过 prerelease**，
 * 而本项目所有 beta 都标了 prerelease —— 结果就是"检查更新"永远提示已是最新（0.27.1 才发现的历史 bug）。
 * 这里直接读列表自己比，prerelease 也能正常收到更新。
 */
fun pickLatestRelease(candidates: List<ReleaseCandidate>): ReleaseCandidate? =
    candidates
        .asSequence()
        .filter { !it.draft }
        .mapNotNull { candidate -> ReleaseVersion.parse(candidate.tag)?.let { it to candidate } }
        .maxByOrNull { it.first }
        ?.second

/**
 * 版本号是否比当前安装的新。
 *
 * 两端解析失败都返回 false：宁可"不提示更新"，也不要因为一个奇怪的 tag/版本号去骚扰用户。
 */
fun isNewerVersion(candidateTag: String, currentVersion: String): Boolean {
    val candidate = ReleaseVersion.parse(candidateTag) ?: return false
    val current = ReleaseVersion.parse(currentVersion) ?: return false
    return candidate > current
}

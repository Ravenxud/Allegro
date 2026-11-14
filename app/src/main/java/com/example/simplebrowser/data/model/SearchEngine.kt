package com.example.simplebrowser.data.model

/**
 * 搜索引擎。queryUrl 中用 %s 占位查询词。
 * 本期为常量；T12 会改为 DataStore 配置并允许用户自定义。
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val queryUrl: String,
)

object SearchEngines {

    val BAIDU = SearchEngine("baidu", "百度", "https://www.baidu.com/s?wd=%s")
    val BING = SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s")
    val GOOGLE = SearchEngine("google", "Google", "https://www.google.com/search?q=%s")

    val builtIn: List<SearchEngine> = listOf(BAIDU, BING, GOOGLE)

    val default: SearchEngine = BAIDU
}

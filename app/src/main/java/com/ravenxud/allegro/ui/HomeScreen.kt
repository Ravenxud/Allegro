package com.ravenxud.allegro.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.browser.UrlParser
import com.ravenxud.allegro.data.BingWallpaper
import com.ravenxud.allegro.data.entity.ClosedTabEntity
import com.ravenxud.allegro.data.model.WallpaperSource
import com.ravenxud.allegro.data.model.SearchEngine
import com.ravenxud.allegro.data.repo.BookmarkRepository
import com.ravenxud.allegro.data.repo.HistoryRepository
import com.ravenxud.allegro.ui.theme.AllegroIcons
import com.ravenxud.allegro.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyles
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 壁纸上文字的主色：白字 + 深浅两档透明度。 */
private val OnWallpaper = Color.White
private val OnWallpaperDim = Color.White.copy(alpha = 0.72f)
private val OnWallpaperFaint = Color.White.copy(alpha = 0.55f)

/**
 * 新标签页（Luna 形态）：
 * 壁纸铺底 → 玻璃搜索钮 → 隐私报告 → 最常访问 → 书签。
 *
 * 玻璃走 miuix-glass：先把「壁纸 + 压暗层」录进 backdrop，再让卡片采样，
 * 所以卡片里看到的是被折射过的壁纸，而不是半透明色块。
 */
@Composable
fun HomeScreen(
    controller: BrowserController,
    searchEngine: SearchEngine,
    bookmarks: BookmarkRepository,
    history: HistoryRepository? = null,
    blockedCount: Int = 0,
    wallpaperSource: WallpaperSource = WallpaperSource.DEFAULT,
    wallpaperUri: String? = null,
    showBookmarks: Boolean = true,
    onOpenPrivacy: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var recent by remember { mutableStateOf<List<ClosedTabEntity>>(emptyList()) }

    val bookmarkList by bookmarks.observeAll().collectAsState(initial = emptyList())
    val historyList = history?.let { repo ->
        repo.observeRecent(40).collectAsState(initial = emptyList())
    }?.value.orEmpty()

    LaunchedEffect(Unit) {
        recent = runCatching { controller.recentClosed(3) }.getOrDefault(emptyList())
    }

    // 最常访问：历史里按最近访问去重（同一域名只留一条，避免整屏都是 baidu.com）
    val frequent = remember(historyList) {
        historyList.filter { it.url.startsWith("http") }
            .distinctBy { hostOf(it.url) }
            .take(4)
    }

    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // 只记录「壁纸 + 压暗」，卡片本身不进层，避免自采样
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Wallpaper(wallpaperSource, wallpaperUri)
            Scrim()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.l),
        ) {
            Spacer(Modifier.height(Spacing.m))

            SearchPill(
                query = query,
                onQueryChange = { query = it },
                backdrop = backdrop,
                onGo = {
                    controller.openTab(UrlParser.toUrl(query.text, searchEngine.queryUrl))
                    query = TextFieldValue("")
                    focusManager.clearFocus()
                },
            )

            run {
                SectionHeader(title = stringResource(R.string.home_privacy_title), onAction = onOpenPrivacy)
                GlassCard(backdrop = backdrop) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = AllegroIcons.Blocklist,
                            contentDescription = null,
                            modifier = Modifier.height(28.dp).width(28.dp),
                            tint = OnWallpaper,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.home_privacy_summary, blockedCount),
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = OnWallpaper,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = AllegroIcons.ChevronForward,
                            contentDescription = null,
                            tint = OnWallpaperFaint,
                        )
                    }
                }
            }

            if (frequent.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.home_section_frequent), onAction = onOpenHistory)
                GlassCard(backdrop = backdrop) {
                    frequent.forEachIndexed { index, entry ->
                        if (index > 0) CardDivider()
                        SiteRow(
                            title = entry.title.ifBlank { entry.url },
                            subtitle = hostOf(entry.url),
                            onClick = { controller.openTab(entry.url) },
                        )
                    }
                }
            } else if (recent.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.home_section_recent), onAction = onOpenHistory)
                GlassCard(backdrop = backdrop) {
                    recent.forEachIndexed { index, item ->
                        if (index > 0) CardDivider()
                        SiteRow(
                            title = item.title.ifBlank { item.url },
                            subtitle = hostOf(item.url),
                            onClick = { controller.openTab(item.url) },
                        )
                    }
                }
            }

            if (showBookmarks && bookmarkList.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.home_section_bookmarks), onAction = onOpenBookmarks)
                GlassCard(backdrop = backdrop) {
                    bookmarkList.take(4).forEachIndexed { index, item ->
                        if (index > 0) CardDivider()
                        SiteRow(
                            title = item.title.ifBlank { item.url },
                            subtitle = hostOf(item.url),
                            onClick = { controller.openTab(item.url) },
                        )
                    }
                }
            }

            // —— 以下两块是占位，功能接入后把 onAction 换成真实入口 ——
            SectionHeader(title = stringResource(R.string.home_ai_assistant))
            GlassCard(backdrop = backdrop) {
                PlaceholderRow(
                    text = stringResource(R.string.home_ai_assistant_hint),
                    icon = AllegroIcons.Edit,
                )
            }

            SectionHeader(title = stringResource(R.string.home_ai_artifacts))
            GlassCard(backdrop = backdrop) {
                PlaceholderRow(
                    text = stringResource(R.string.home_ai_artifacts_hint),
                    icon = AllegroIcons.Folder,
                )
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

/** 顶部搜索：玻璃药丸，高度 52dp。 */
@Composable
private fun SearchPill(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    backdrop: Backdrop,
    onGo: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .glassPill(backdrop = backdrop, corner = 26.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = stringResource(R.string.home_search_hint),
            useLabelAsPlaceholder = true,
            cornerRadius = 26.dp,
            leadingIcon = { Icon(AllegroIcons.Search, contentDescription = null) },
            colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
        )
    }
}

/** 二级标题：19sp 半粗白字，右侧可带一个动作。 */
@Composable
private fun SectionHeader(title: String, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnWallpaper,
        )
        if (onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.001f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_see_all),
                    fontSize = 13.sp,
                    color = OnWallpaperDim,
                )
                Icon(
                    imageVector = AllegroIcons.ChevronForward,
                    contentDescription = null,
                    modifier = Modifier.height(14.dp).width(14.dp),
                    tint = OnWallpaperDim,
                )
            }
        }
    }
}

/** 玻璃卡：miuix-glass 折射壁纸，圆角 24dp。 */
@Composable
private fun GlassCard(
    backdrop: Backdrop,
    corner: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPill(backdrop = backdrop, corner = corner),
        content = content,
    )
}

/** 一行的站点：标题 + 域名。 */
@Composable
private fun SiteRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent)
            .clickableNoIndication(onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = OnWallpaper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = OnWallpaperFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = AllegroIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.height(16.dp).width(16.dp),
            tint = OnWallpaperFaint,
        )
    }
}

/** AI 区块的占位行：等真实功能接入后替换，样式与普通行一致，避免将来再调排版。 */
@Composable
private fun PlaceholderRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.height(20.dp).width(20.dp),
            tint = OnWallpaperFaint,
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, fontSize = 14.sp, color = OnWallpaperDim)
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

/** miuix 玻璃：圆角 + 内容裁剪，材质跟随深色模式。 */
@Composable
private fun Modifier.glassPill(backdrop: Backdrop, corner: androidx.compose.ui.unit.Dp): Modifier =
    this
        .glass(
            backdrop = backdrop,
            shape = GlassShape(cornerRadius = corner),
            style = GlassStyles.CommonMediumRegularLowLight,
            material = GlassMaterials.puredThinGlass(isSystemInDarkTheme()),
            shading = false,
        )
        .clip(RoundedCornerShape(corner))

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
private fun Wallpaper(source: WallpaperSource, uri: String?) {
    val context = LocalContext.current
    var image by remember(source, uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(source, uri) {
        image = when (source) {
            WallpaperSource.DEFAULT -> null
            WallpaperSource.CUSTOM -> uri?.takeIf { it.isNotBlank() }?.let {
                withContext(Dispatchers.IO) { loadWallpaper(context, it) }
            }
            WallpaperSource.BING -> withContext(Dispatchers.IO) {
                BingWallpaper.load(context)?.asImageBitmap()
            }
        }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        DefaultBackdrop()
    }
}

/** 没设壁纸时的默认底：主题色光斑，模糊后当壁纸用。 */
@Composable
private fun DefaultBackdrop() {
    val primary = MiuixTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().blur(72.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = primary.copy(alpha = 0.55f),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                )
                drawCircle(
                    color = Color(0xFF6EE7F9).copy(alpha = 0.42f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.92f, size.height * 0.28f),
                )
                drawCircle(
                    color = Color(0xFFFFA26B).copy(alpha = 0.35f),
                    radius = size.minDimension * 0.50f,
                    center = Offset(size.width * 0.55f, size.height * 0.85f),
                )
            }
        }
    }
}

/** 上下压暗：保证白字在亮壁纸上也读得清。 */
@Composable
private fun Scrim() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.00f to Color.Black.copy(alpha = 0.38f),
                    0.35f to Color.Black.copy(alpha = 0.16f),
                    0.75f to Color.Black.copy(alpha = 0.24f),
                    1.00f to Color.Black.copy(alpha = 0.42f),
                ),
            ),
    )
}

private fun loadWallpaper(context: Context, uri: String): ImageBitmap? = runCatching {
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?.asImageBitmap()
}.getOrNull()

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("").removePrefix("www.")

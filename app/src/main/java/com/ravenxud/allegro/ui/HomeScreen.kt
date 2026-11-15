package com.ravenxud.allegro.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.data.BingWallpaper
import com.ravenxud.allegro.data.entity.ClosedTabEntity
import com.ravenxud.allegro.data.model.WallpaperSource
import com.ravenxud.allegro.data.repo.BookmarkRepository
import com.ravenxud.allegro.data.repo.HistoryRepository
import com.ravenxud.allegro.ui.theme.AllegroIcons
import com.ravenxud.allegro.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.glass
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassStyles
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 壁纸是否偏亮：决定文字与磨砂底色，跟随「图」而不是跟随系统深浅色。 */
private val LocalBrightWallpaper = staticCompositionLocalOf { false }

/** 判定阈值：sRGB 相对亮度，0.55 以上算亮壁纸（浅底深字）。 */
private const val BRIGHT_THRESHOLD = 0.55f

/**
 * 新标签页（Luna 形态）：壁纸铺底 → 隐私报告 → 最常访问 → 书签 → AI 占位。
 *
 * 文字颜色由**壁纸平均亮度**决定（浅壁纸深字、深壁纸白字），卡片内部再压一层同向的磨砂底，
 * 这样换任何壁纸都不会出现「白字压浅景」。
 */
@Composable
fun HomeScreen(
    controller: BrowserController,
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
    val context = LocalContext.current
    var recent by remember { mutableStateOf<List<ClosedTabEntity>>(emptyList()) }

    val bookmarkList by bookmarks.observeAll().collectAsState(initial = emptyList())
    val historyList = history?.let { repo ->
        repo.observeRecent(40).collectAsState(initial = emptyList())
    }?.value.orEmpty()

    // 壁纸位图：默认源为 null（画主题光斑）
    var bitmap by remember(wallpaperSource, wallpaperUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wallpaperSource, wallpaperUri) {
        bitmap = when (wallpaperSource) {
            WallpaperSource.DEFAULT -> null
            WallpaperSource.CUSTOM -> wallpaperUri
                ?.takeIf { it.isNotBlank() }
                ?.let { withContext(Dispatchers.IO) { decodeUri(context, it) } }
            WallpaperSource.BING -> withContext(Dispatchers.IO) { BingWallpaper.load(context) }
        }
    }

    val systemDark = isSystemInDarkTheme()
    val bright = remember(bitmap, systemDark) {
        bitmap?.let { averageLuminance(it) > BRIGHT_THRESHOLD } ?: !systemDark
    }

    LaunchedEffect(Unit) {
        recent = runCatching { controller.recentClosed(3) }.getOrDefault(emptyList())
    }

    // 最常访问：历史里按域名去重取 4 条
    val frequent = remember(historyList) {
        historyList.filter { it.url.startsWith("http") }
            .distinctBy { hostOf(it.url) }
            .take(4)
    }

    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalBrightWallpaper provides bright) {
        Box(Modifier.fillMaxSize()) {
            // 只记录「壁纸 + 压暗」，卡片本身不进层，避免自采样
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Wallpaper(bitmap)
                Scrim(bright)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.l),
            ) {
                Spacer(Modifier.height(Spacing.m))

                SectionHeader(stringResource(R.string.home_privacy_title), onAction = onOpenPrivacy)
                GlassCard(backdrop = backdrop) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = AllegroIcons.Blocklist,
                            contentDescription = null,
                            modifier = Modifier.height(28.dp).width(28.dp),
                            tint = ink(),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.home_privacy_summary, blockedCount),
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = ink(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = AllegroIcons.ChevronForward,
                            contentDescription = null,
                            tint = inkFaint(),
                        )
                    }
                }

                if (frequent.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.home_section_frequent), onAction = onOpenHistory)
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
                    SectionHeader(stringResource(R.string.home_section_recent), onAction = onOpenHistory)
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
                    SectionHeader(stringResource(R.string.home_section_bookmarks), onAction = onOpenBookmarks)
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

                // —— AI 区块占位：排版已定稿，功能接入时只换内容 ——
                SectionHeader(stringResource(R.string.home_ai_assistant))
                GlassCard(backdrop = backdrop) {
                    PlaceholderRow(stringResource(R.string.home_ai_assistant_hint), AllegroIcons.Edit)
                }

                SectionHeader(stringResource(R.string.home_ai_artifacts))
                GlassCard(backdrop = backdrop) {
                    PlaceholderRow(stringResource(R.string.home_ai_artifacts_hint), AllegroIcons.Folder)
                }

                Spacer(Modifier.height(140.dp))
            }

        }
    }
}


/* ---------------------------------------------------------------- 文字颜色 */

@Composable
private fun ink(): Color = if (LocalBrightWallpaper.current) Color(0xFF141414) else Color.White

@Composable
private fun inkDim(): Color = ink().copy(alpha = 0.62f)

@Composable
private fun inkFaint(): Color = ink().copy(alpha = 0.42f)

/* ---------------------------------------------------------------- 组件 */

@Composable
private fun SectionHeader(title: String, onAction: (() -> Unit)? = null) {
    val bright = LocalBrightWallpaper.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink(),
            style = TextStyle(
                shadow = Shadow(
                    color = if (bright) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 1f),
                    blurRadius = 3f,
                ),
            ),
        )
        if (onAction != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_see_all),
                    fontSize = 13.sp,
                    color = inkDim(),
                )
                Icon(
                    imageVector = AllegroIcons.ChevronForward,
                    contentDescription = null,
                    modifier = Modifier.height(14.dp).width(14.dp),
                    tint = inkDim(),
                )
            }
        }
    }
}

@Composable
private fun GlassCard(
    backdrop: Backdrop,
    corner: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().glassSurface(backdrop, corner),
        content = content,
    )
}

@Composable
private fun SiteRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = ink(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = inkFaint(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = AllegroIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.height(16.dp).width(16.dp),
            tint = inkFaint(),
        )
    }
}

@Composable
private fun PlaceholderRow(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.height(20.dp).width(20.dp),
            tint = inkFaint(),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, fontSize = 14.sp, color = inkDim())
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(ink().copy(alpha = 0.10f)),
    )
}

/**
 * miuix 玻璃 + 一层同向磨砂底：
 * 亮壁纸用「白底 + 深字」，暗壁纸用「黑底 + 白字」，两种都能保住折射又读得清。
 */
@Composable
private fun Modifier.glassSurface(backdrop: Backdrop, corner: Dp): Modifier {
    val bright = LocalBrightWallpaper.current
    val frost = if (bright) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.40f)
    val radius = with(LocalDensity.current) { corner.toPx() }
    return this
        .glass(
            backdrop = backdrop,
            shape = GlassShape(cornerRadius = corner),
            style = GlassStyles.CommonMediumRegularLowLight,
            material = GlassMaterials.puredThinGlass(isSystemInDarkTheme()),
            shading = false,
        )
        .drawBehind { drawRoundRect(color = frost, cornerRadius = CornerRadius(radius, radius)) }
        .clip(RoundedCornerShape(corner))
}

/* ---------------------------------------------------------------- 壁纸 */

@Composable
private fun Wallpaper(bitmap: Bitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        DefaultBackdrop()
    }
}

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

/** 亮壁纸少压暗、暗壁纸多压暗，保证文字有底但仍保留照片观感。 */
@Composable
private fun Scrim(bright: Boolean) {
    val stops = if (bright) {
        listOf(
            0.00f to Color.Black.copy(alpha = 0.18f),
            0.35f to Color.Black.copy(alpha = 0.06f),
            0.75f to Color.Black.copy(alpha = 0.10f),
            1.00f to Color.Black.copy(alpha = 0.26f),
        )
    } else {
        listOf(
            0.00f to Color.Black.copy(alpha = 0.46f),
            0.35f to Color.Black.copy(alpha = 0.22f),
            0.75f to Color.Black.copy(alpha = 0.30f),
            1.00f to Color.Black.copy(alpha = 0.50f),
        )
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*stops.toTypedArray())))
}

/* ---------------------------------------------------------------- 工具 */

/** 平均亮度：网格采样，避免整图逐像素。 */
private fun averageLuminance(bitmap: Bitmap): Float {
    val stepX = (bitmap.width / 12).coerceAtLeast(1)
    val stepY = (bitmap.height / 20).coerceAtLeast(1)
    var sum = 0f
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = bitmap.getPixel(x, y)
            val r = (c shr 16 and 0xFF) / 255f
            val g = (c shr 8 and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
            count++
            x += stepX
        }
        y += stepY
    }
    return if (count == 0) 0f else sum / count
}

private fun decodeUri(context: Context, uri: String): Bitmap? = runCatching {
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("").removePrefix("www.")

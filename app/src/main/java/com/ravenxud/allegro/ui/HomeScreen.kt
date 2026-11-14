package com.ravenxud.allegro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.ravenxud.allegro.browser.BrowserController
import com.ravenxud.allegro.browser.UrlParser
import com.ravenxud.allegro.data.entity.ClosedTabEntity
import com.ravenxud.allegro.data.model.SearchEngine
import com.ravenxud.allegro.data.repo.BookmarkRepository
import com.ravenxud.allegro.ui.home.HomeDate
import com.ravenxud.allegro.ui.theme.Radius
import com.ravenxud.allegro.ui.theme.Motion
import com.ravenxud.allegro.ui.theme.Spacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ravenxud.allegro.ui.glass.appGlass
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 新标签页：问候语 + 毛玻璃搜索 + 最近关闭 + 书签。
 * 不放置任何推荐站点，保持空白开始页。
 */
@Composable
fun HomeScreen(
    controller: BrowserController,
    searchEngine: SearchEngine,
    bookmarks: BookmarkRepository,
    showBookmarks: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var recent by remember { mutableStateOf<List<ClosedTabEntity>>(emptyList()) }
    val bookmarkList by bookmarks.observeAll().collectAsState(initial = emptyList())

    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        recent = runCatching { controller.recentClosed(3) }.getOrDefault(emptyList())
        appear = true
    }

    // 背景是彩色光斑，玻璃卡能"吸"到颜色；捕获层只包背景，避免卡片自捕获
    val surface = MiuixTheme.colorScheme.background
    val bgLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val bgBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop(bgLayer) {
        drawRect(surface)
        drawContent()
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(bgBackdrop)) {
            Backdrop()
        }

        AnimatedVisibility(
            visible = appear,
            enter = fadeIn(Motion.enter(Motion.Duration.Long)) + slideInVertically(Motion.slide(Motion.Duration.Long)) { it / 28 },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l),
            ) {
                Spacer(Modifier.height(64.dp))

                Text(HomeDate.greeting(), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    HomeDate.todayLabel(),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                Spacer(Modifier.height(Spacing.xl))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .appGlass(
                            backdrop = bgBackdrop,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.field),
                            spec = com.ravenxud.allegro.ui.glass.GlassSpec(blur = 28.dp, surfaceAlpha = 0.18f),
                        ),
                ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = stringResource(R.string.home_search_hint),
                    useLabelAsPlaceholder = true,
                    cornerRadius = Radius.field,
                    leadingIcon = { Icon(MiuixIcons.Search, contentDescription = null) },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        controller.openTab(UrlParser.toUrl(query.text, searchEngine.queryUrl))
                        query = TextFieldValue("")
                        focusManager.clearFocus()
                    }),
                )
                }

                if (recent.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.home_section_recent))
                    GlassCard(backdrop = bgBackdrop) {
                        recent.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                            BasicComponent(
                                title = item.title.ifBlank { item.url },
                                summary = item.url,
                                startAction = { Icon(MiuixIcons.Refresh, contentDescription = null) },
                                onClick = { controller.openTab(item.url) },
                            )
                        }
                    }
                }

                if (showBookmarks && bookmarkList.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.home_section_bookmarks))
                    GlassCard(backdrop = bgBackdrop) {
                        bookmarkList.take(4).forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                            BasicComponent(
                                title = item.title.ifBlank { item.url },
                                summary = item.url,
                                startAction = { Icon(MiuixIcons.Folder, contentDescription = null) },
                                onClick = { controller.openTab(item.url) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

/** 首页的玻璃卡：模糊背后的彩色光斑，而不是"半透明色块"。 */
@Composable
private fun GlassCard(
    backdrop: com.kyant.backdrop.Backdrop,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .appGlass(
                backdrop = backdrop,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.card),
                spec = com.ravenxud.allegro.ui.glass.GlassSpec(blur = 24.dp),
            ),
        content = content,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.l, bottom = Spacing.s, start = 4.dp),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun Backdrop() {
    val primary = MiuixTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().blur(72.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = primary.copy(alpha = 0.30f),
                    radius = size.minDimension * 0.60f,
                    center = Offset(size.width * 0.15f, size.height * 0.10f),
                )
                drawCircle(
                    color = Color(0xFF6EE7F9).copy(alpha = 0.20f),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * 0.92f, size.height * 0.26f),
                )
                drawCircle(
                    color = primary.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width * 0.55f, size.height * 0.82f),
                )
            }
        }
    }
}

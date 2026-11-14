package com.ravenxud.allegro.ui.screens

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ravenxud.allegro.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.ravenxud.allegro.ui.theme.Spacing
import com.ravenxud.allegro.util.DiagLog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * AGSL 冒烟页（P0 专用，确认环境支持后再删）。
 *
 * 要回答三个问题：
 * 1. 这台设备的 GPU 能不能编译 `RuntimeShader`（AGSL）；
 * 2. `uniform shader content` 与 `RenderEffect.createRuntimeShaderEffect(..., "content")` 的绑定是否成立；
 *    —— 用「故意写错 uniform 名」对比，错的时候应该全黑，说明名字必须严格匹配；
 * 3. Backdrop 库的类是否真的进了 APK（R8/依赖是否生效）。
 */
@Composable
fun ShaderSmokeScreen(onBack: () -> Unit) {
    var compileOk by remember { mutableStateOf<Boolean?>(null) }
    var compileError by remember { mutableStateOf("") }
    var uniformName by remember { mutableStateOf("content") }
    var showGlass by remember { mutableStateOf(true) }
    var backdropFound by remember { mutableStateOf(false) }
    var gpu by remember { mutableStateOf("未知") }

    val density = LocalDensity.current
    // 卡片固定 240dp x 120dp，转成 px 供 shader 计算边缘折射
    val sizePx = remember(density) {
        with(density) { Pair(240.dp.toPx(), 120.dp.toPx()) }
    }

    val shader = remember(compileOk) {
        runCatching {
            RuntimeShader(GLASS_SMOKE_SHADER).also {
                it.setFloatUniform("size", sizePx.first, sizePx.second)
                it.setFloatUniform("amount", 18f)
                compileOk = true
                DiagLog.log("shader-smoke", "compile ok sdk=${Build.VERSION.SDK_INT}")
            }
        }.getOrElse { error ->
            compileOk = false
            compileError = error.message ?: error::class.java.simpleName
            DiagLog.log("shader-smoke", "compile failed: $compileError")
            null
        }
    }

    remember(Unit) {
        backdropFound = runCatching { Class.forName("com.kyant.backdrop.Backdrop") }.isSuccess
        gpu = runCatching {
            val egl = android.opengl.EGL14.eglGetCurrentContext()
            if (egl != null) "EGL ready" else "EGL 未初始化（可在浏览器页再试）"
        }.getOrDefault("未知")
        DiagLog.log("shader-smoke", "backdropClass=$backdropFound gpu=$gpu")
        true
    }

    ScreenScaffold(title = stringResource(R.string.shader_smoke_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.m)) {
                    Text(
                        stringResource(R.string.shader_smoke_env, Build.VERSION.SDK_INT.toString()),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(gpu, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (compileOk) {
                            true -> "AGSL：编译成功"
                            false -> "AGSL：编译失败 → $compileError"
                            null -> "AGSL：未编译"
                        },
                        fontSize = 13.sp,
                        color = when (compileOk) {
                            true -> Color(0xFF2E7D32)
                            false -> Color(0xFFC62828)
                            null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (backdropFound) "Backdrop：类已打包 ✓" else "Backdrop：未找到类 ✗",
                        fontSize = 13.sp,
                        color = if (backdropFound) Color(0xFF2E7D32) else Color(0xFFC62828),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.m))
            Text(
                stringResource(R.string.shader_smoke_preview),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
            )

            // 预览区：背景条纹，玻璃卡片叠在上面，方便看出"折射是否歪了"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFFEDEDED)),
                contentAlignment = Alignment.Center,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(6) {
                        Box(Modifier.size(18.dp, 180.dp).background(Color(0xFF3D7EFF)))
                    }
                }
                if (showGlass && shader != null) {
                    Box(
                        modifier = Modifier
                            .size(240.dp, 120.dp)
                            .graphicsLayer {
                                renderEffect = runCatching {
                                    RenderEffect.createRuntimeShaderEffect(shader, uniformName)
                                }.getOrNull()?.asComposeRenderEffect()
                            }
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp)),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.shader_smoke_backdrop),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.s),
            )
            BackdropDemo()

            Spacer(Modifier.height(Spacing.m))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    checked = showGlass,
                    onCheckedChange = { showGlass = it },
                    title = stringResource(R.string.shader_smoke_enable),
                    summary = stringResource(R.string.shader_smoke_enable_summary),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                ArrowPreference(
                    title = stringResource(R.string.shader_smoke_uniform),
                    summary = "当前：$uniformName（切成错误名应全黑，说明名字必须匹配）",
                    onClick = {
                        uniformName = if (uniformName == "content") "contentX" else "content"
                    },
                )
            }

            Spacer(Modifier.height(Spacing.m))
            Text(
                stringResource(R.string.shader_smoke_note),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = Spacing.xs),
            )
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

/**
 * Backdrop 实跑演示：验证「层捕获 + blur + vibrancy」这条链路在设备上是否成立。
 * 如果这里能出模糊效果，说明 P2 可以直接用库能力；否则整库退回自研 Effect。
 */
@Composable
private fun BackdropDemo() {
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backdrop = rememberLayerBackdrop(layer) {
        drawRect(Color(0xFFEDEDED))
        drawContent()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .then(Modifier.layerBackdrop(backdrop)),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(6) {
                Box(Modifier.size(18.dp, 140.dp).background(Color(0xFFFF6B6B)))
            }
        }
        Box(
            modifier = Modifier
                .size(200.dp, 90.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(24.dp) },
                    effects = {
                        blur(20.dp.toPx())
                        vibrancy()
                    },
                ),
        )
    }
}

/**
 * 极简折射：边缘把内容向内"吸"一点，用来验证 AGSL 管线是否通。
 * 真正的 IOR 玻璃在 P3 落地，这里只做环境探测。
 */
private const val GLASS_SMOKE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float amount;

half4 main(float2 coord) {
    float2 h = size * 0.5;
    float2 p = coord - h;
    float edge = min(h.x, h.y) - length(p);      // 到边缘的距离
    float t = clamp(1.0 - edge / 28.0, 0.0, 1.0);
    float2 dir = normalize(p + float2(1e-4));
    return content.eval(coord + dir * t * amount);
}
"""

// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.material

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/** True if the current platform supports RenderEffect-based effects. */
@SuppressLint("ObsoleteSdkInt")
@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

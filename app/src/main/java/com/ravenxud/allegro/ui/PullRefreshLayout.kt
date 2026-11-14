package com.ravenxud.allegro.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.min

/**
 * 轻量下拉刷新容器。
 *
 * 关键点：
 * 1. WebView 滚动时会调用 `requestDisallowInterceptTouchEvent(true)`，
 *    父容器再也收不到 MOVE，所以这里**在页面顶部时忽略该请求**；
 * 2. 下拉过程同时位移子视图（0.35 系数）+ 移动指示器，松手用 ValueAnimator 回弹。
 */
class PullRefreshLayout(context: Context) : FrameLayout(context) {

    var onRefresh: (() -> Unit)? = null

    /** 由外部（控制器）判断当前页面是否真的在顶部；默认 true。 */
    var atTopProvider: () -> Boolean = { true }

    private val density = resources.displayMetrics.density
    private val indicator = ProgressBar(context).apply {
        isIndeterminate = true
        visibility = View.GONE
    }
    private val indicatorSize = (34 * density).toInt()
    private val indicatorTop = (10 * density).toInt()
    private val triggerDistance = 72 * density
    private val maxDistance = 140 * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var startY = 0f
    private var distance = 0f
    private var pulling = false
    private var rebound: ValueAnimator? = null

    var isRefreshing: Boolean = false
        set(value) {
            field = value
            if (value) {
                indicator.visibility = View.VISIBLE
                animateIndicator(indicatorTop.toFloat())
                content().translationY = 0f
            } else {
                animateIndicator(-indicatorSize.toFloat())
                indicator.postDelayed({ if (!isRefreshing) indicator.visibility = View.GONE }, 180)
            }
        }

    init {
        addView(
            indicator,
            LayoutParams(indicatorSize, indicatorSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = indicatorTop
            },
        )
        indicator.translationY = -indicatorSize.toFloat()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            (indicator.layoutParams as LayoutParams).topMargin = top + indicatorTop
            insets
        }
    }

    /** 内容视图（第 2 个子 View）。 */
    private fun content(): View = getChildAt(1) ?: this

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // 只有"确实在顶部"时才允许父容器抢占；否则尊重 WebView 的滚动请求
        if (disallowIntercept && !atTopProvider()) {
            super.requestDisallowInterceptTouchEvent(true)
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isRefreshing) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y
                pulling = false
                rebound?.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - startY
                // 必须"在顶部"且"手指向下拉"，否则交给页面滚动
                if (dy > touchSlop && atTopProvider() && !canChildScrollUp()) {
                    pulling = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!pulling) return false
                distance = (ev.y - startY).coerceAtLeast(0f)
                val eased = min(distance * 0.55f, maxDistance)
                indicator.visibility = View.VISIBLE
                indicator.translationY = -indicatorSize + eased + indicatorTop
                indicator.alpha = (eased / triggerDistance).coerceIn(0.2f, 1f)
                // 不再位移内容：GeckoView 是 SurfaceView，父容器位移会导致画面异常
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!pulling) return false
                pulling = false
                if (distance >= triggerDistance) {
                    isRefreshing = true
                    onRefresh?.invoke()
                } else {
                    animateIndicator(-indicatorSize.toFloat())
                    indicator.postDelayed({
                        if (!isRefreshing) indicator.visibility = View.GONE
                    }, 180)
                }
                distance = 0f
                return true
            }
        }
        return true
    }

    private fun animateIndicator(target: Float) {
        rebound?.cancel()
        rebound = ValueAnimator.ofFloat(indicator.translationY, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { indicator.translationY = it.animatedValue as Float }
            start()
        }
    }

    /** 子视图（WebView）是否已经可以向上滚动。 */
    private fun canChildScrollUp(): Boolean {
        val view = content()
        if (view.canScrollVertically(-1) || view.scrollY > 0) return true
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child === indicator) continue
                if (child.canScrollVertically(-1) || child.scrollY > 0) return true
            }
        }
        // 兜底：交给外部（页面真实滚动位置）
        return !atTopProvider()
    }
}

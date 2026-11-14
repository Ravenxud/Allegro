package com.ravenxud.allegro.ui.home

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 首页的时间文案。
 *
 * 注意：日期必须用 [LocalDate] 格式化——LocalTime 没有 MonthOfYear/DayOfMonth 字段，
 * 用 "M月d日" 去格式化会抛 UnsupportedTemporalTypeException（0.13.0 闪退的原因）。
 */
object HomeDate {

    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")

    fun todayLabel(date: LocalDate = LocalDate.now()): String = date.format(dateFormatter)

    fun greeting(hour: Int = LocalTime.now().hour): String = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        else -> "晚上好"
    }
}

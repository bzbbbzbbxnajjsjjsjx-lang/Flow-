package com.akis.app.domain

/** Window-size rules: Android 17 ignores orientation and size locks on large screens, so layouts follow the width. */
object Adaptive {
    private const val MEDIUM = 600
    private const val EXPANDED = 900
    private const val WIDE_COLUMNS = 4
    private const val MEDIUM_COLUMNS = 3
    private const val NARROW_COLUMNS = 2

    /** Picture tiles per row for a window [widthDp] wide. */
    fun columns(widthDp: Int): Int = when {
        widthDp >= EXPANDED -> WIDE_COLUMNS
        widthDp >= MEDIUM -> MEDIUM_COLUMNS
        else -> NARROW_COLUMNS
    }

    /** Text screens stop growing here and sit in the middle of a wide window. */
    const val READING_MAX_DP = 720
}

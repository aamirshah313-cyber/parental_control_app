package com.guardianlink.ui

import android.content.Context

/** Small persistent preference shared by every app screen; no account data is involved. */
object AppTheme {
    private const val PREFS = "guardian_appearance"
    private const val DARK_MODE = "dark_mode"

    fun isDark(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DARK_MODE, true)

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DARK_MODE, dark).apply()
    }

    fun toggle(context: Context): Boolean {
        val dark = !isDark(context)
        setDark(context, dark)
        return dark
    }
}

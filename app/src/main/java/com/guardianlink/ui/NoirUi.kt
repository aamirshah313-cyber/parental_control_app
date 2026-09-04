package com.guardianlink.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

/** Shared adaptive graphite/gold visual language for parent and child experiences. */
object NoirUi {
    private var darkMode = true

    val BACKGROUND get() = if (darkMode) 0xFF17181E.toInt() else 0xFFF6F4EE.toInt()
    val SURFACE get() = if (darkMode) 0xFF23242C.toInt() else 0xFFFFFDFC.toInt()
    val SURFACE_RAISED get() = if (darkMode) 0xFF2B2D36.toInt() else 0xFFE9E5DA.toInt()
    val GOLD get() = if (darkMode) 0xFFD8B65B.toInt() else 0xFF8A6715.toInt()
    val GOLD_DIM get() = if (darkMode) 0xFF7D6A3B.toInt() else 0xFFC5A654.toInt()
    val TEXT get() = if (darkMode) 0xFFF5F2EA.toInt() else 0xFF202128.toInt()
    val MUTED get() = if (darkMode) 0xFFAFAFBA.toInt() else 0xFF62646C.toInt()
    val DANGER get() = if (darkMode) 0xFFC76870.toInt() else 0xFFB53643.toInt()
    /** The mode set by the last apply()/isDark(context) call, for callers building a screen-local
     * color that needs to branch on dark/light without holding a Context at property-init time. */
    val isDarkCached get() = darkMode

    fun apply(context: Context) { darkMode = AppTheme.isDark(context) }
    fun isDark(context: Context): Boolean { apply(context); return darkMode }
    fun toggle(context: Context): Boolean {
        val dark = !AppTheme.isDark(context)
        AppTheme.setDark(context, dark)
        darkMode = dark
        return dark
    }

    /**
     * The app theme is forced to Theme.Material.Light (see themes.xml), so a plain
     * AlertDialog.Builder always renders as a light system dialog even in dark mode --
     * mismatched against every custom NoirUi-drawn screen, and unreadable for any
     * NoirUi.TEXT-colored widget placed inside one. Use this everywhere instead so dialogs
     * follow the same dark/light state as the rest of the app.
     */
    fun dialogBuilder(context: Context) = android.app.AlertDialog.Builder(
        context,
        if (isDark(context)) android.R.style.Theme_Material_Dialog else android.R.style.Theme_Material_Light_Dialog
    )

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun rounded(context: Context, fill: Int = SURFACE, stroke: Int = SURFACE_RAISED, radius: Int = 20) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(context, radius).toFloat(); setStroke(dp(context, 1), stroke)
    }
    /** Touchable states make controls clear on touch screens and when a keyboard or mouse is used. */
    fun interactiveBackground(context: Context, normal: Int = SURFACE, active: Int = SURFACE_RAISED, stroke: Int = SURFACE_RAISED, radius: Int = 20) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(context, GOLD_DIM, GOLD, radius))
        addState(intArrayOf(android.R.attr.state_hovered), rounded(context, active, GOLD_DIM, radius))
        addState(intArrayOf(android.R.attr.state_selected), rounded(context, active, GOLD, radius))
        addState(intArrayOf(), rounded(context, normal, stroke, radius))
    }
    fun title(context: Context, text: String) = TextView(context).apply {
        this.text = text; textSize = 28f; setTextColor(TEXT); typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
    }
    fun eyebrow(context: Context, text: String) = TextView(context).apply {
        this.text = text.uppercase(); textSize = 11f; letterSpacing = .14f; setTextColor(GOLD); typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    fun primaryButton(context: Context, text: String, action: () -> Unit) = Button(context).apply {
        this.text = text; isAllCaps = false; setTextColor(BACKGROUND); textSize = 14f; background = interactiveBackground(context, GOLD, GOLD_DIM, GOLD_DIM, 16); minHeight = dp(context, 48); setOnClickListener { action() }
    }
    fun secondaryButton(context: Context, text: String, action: () -> Unit) = Button(context).apply {
        this.text = text; isAllCaps = false; setTextColor(TEXT); textSize = 14f; background = interactiveBackground(context); minHeight = dp(context, 48); setOnClickListener { action() }
    }
    fun avatar(context: Context, initials: String) = TextView(context).apply {
        text = initials.take(2).uppercase(); textSize = 17f; gravity = Gravity.CENTER; setTextColor(BACKGROUND); typeface = android.graphics.Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { setColor(GOLD); shape = GradientDrawable.OVAL; setStroke(dp(context, 3), GOLD_DIM) }
    }
}

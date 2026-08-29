package com.guardianlink.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

/** Shared graphite-and-gold visual language derived from the approved dashboard reference. */
object NoirUi {
    const val BACKGROUND = 0xFF17181E.toInt()
    const val SURFACE = 0xFF23242C.toInt()
    const val SURFACE_RAISED = 0xFF2B2D36.toInt()
    const val GOLD = 0xFFD8B65B.toInt()
    const val GOLD_DIM = 0xFF7D6A3B.toInt()
    const val TEXT = 0xFFF5F2EA.toInt()
    const val MUTED = 0xFFAFAFBA.toInt()
    const val DANGER = 0xFFC76870.toInt()

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun rounded(context: Context, fill: Int = SURFACE, stroke: Int = SURFACE_RAISED, radius: Int = 20) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(context, radius).toFloat(); setStroke(dp(context, 1), stroke)
    }
    fun title(context: Context, text: String) = TextView(context).apply {
        this.text = text; textSize = 28f; setTextColor(TEXT); typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
    }
    fun eyebrow(context: Context, text: String) = TextView(context).apply {
        this.text = text.uppercase(); textSize = 11f; letterSpacing = .14f; setTextColor(GOLD); typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    fun primaryButton(context: Context, text: String, action: () -> Unit) = Button(context).apply {
        this.text = text; isAllCaps = false; setTextColor(BACKGROUND); textSize = 14f; backgroundTintList = ColorStateList.valueOf(GOLD); minHeight = dp(context, 48); setOnClickListener { action() }
    }
    fun secondaryButton(context: Context, text: String, action: () -> Unit) = Button(context).apply {
        this.text = text; isAllCaps = false; setTextColor(TEXT); textSize = 14f; background = rounded(context); minHeight = dp(context, 48); setOnClickListener { action() }
    }
    fun avatar(context: Context, initials: String) = TextView(context).apply {
        text = initials.take(2).uppercase(); textSize = 17f; gravity = Gravity.CENTER; setTextColor(BACKGROUND); typeface = android.graphics.Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { setColor(GOLD); shape = GradientDrawable.OVAL; setStroke(dp(context, 3), GOLD_DIM) }
    }
}

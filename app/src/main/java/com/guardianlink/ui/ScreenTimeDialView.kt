package com.guardianlink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/** Compact radial visual for the parent home; it renders only existing child-reported usage totals. */
class ScreenTimeDialView(context: Context) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var usedMinutes = 0
    private var allowanceMinutes = 0

    fun setUsage(used: Int, allowance: Int) { usedMinutes = used.coerceAtLeast(0); allowanceMinutes = allowance.coerceAtLeast(0); contentDescription = "$usedMinutes minutes of ${if (allowanceMinutes == 0) "unlimited" else "$allowanceMinutes minutes"} used today"; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = NoirUi.dp(context, 220)
        val size = min(resolveSize(desired, widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
        setMeasuredDimension(size, size)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = width / 2f; val diameter = min(width, height).toFloat(); val inset = diameter * .10f
        ring.strokeWidth = diameter * .055f; ring.color = NoirUi.SURFACE_RAISED
        canvas.drawArc(RectF(inset, inset, diameter - inset, diameter - inset), -90f, 360f, false, ring)
        val fraction = if (allowanceMinutes > 0) (usedMinutes.toFloat() / allowanceMinutes).coerceIn(0f, 1f) else .18f
        ring.color = NoirUi.GOLD
        canvas.drawArc(RectF(inset, inset, diameter - inset, diameter - inset), -90f, 360f * fraction, false, ring)
        text.typeface = Typeface.create("serif", Typeface.NORMAL); text.textSize = diameter * .20f; text.color = NoirUi.TEXT
        val hours = usedMinutes / 60; val minutes = usedMinutes % 60
        canvas.drawText(String.format(java.util.Locale.US, "%02dh %02dm", hours, minutes), center, center + diameter * .035f, text)
        text.typeface = Typeface.DEFAULT; text.textSize = diameter * .06f; text.color = NoirUi.MUTED
        canvas.drawText(if (allowanceMinutes > 0) "of ${allowanceMinutes} min today" else "screen time today", center, center + diameter * .17f, text)
    }
}

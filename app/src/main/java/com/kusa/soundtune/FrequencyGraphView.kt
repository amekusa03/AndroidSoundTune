package com.kusa.soundtune

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10

data class NamedCurve(
    val id: String,
    val name: String,
    val color: Int,
    val points: List<SweepAcousticUtils.FreqResponsePoint>,
    var isVisible: Boolean = true
)

class FrequencyGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val curves = mutableListOf<NamedCurve>()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33888888")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val gridTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textSize = 24f
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val minFreq = 20.0
    private val maxFreq = 20000.0
    private val minDb = -60.0
    private val maxDb = 0.0

    private val gridFreqs = listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)
    private val gridDbs = listOf(0.0, -10.0, -20.0, -30.0, -40.0, -50.0, -60.0)

    fun setCurves(newCurves: List<NamedCurve>) {
        curves.clear()
        curves.addAll(newCurves)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 60f
        val paddingRight = 30f
        val paddingTop = 30f
        val paddingBottom = 50f

        val plotWidth = width - paddingLeft - paddingRight
        val plotHeight = height - paddingTop - paddingBottom

        if (plotWidth <= 0 || plotHeight <= 0) return

        // 1. Draw Grid lines & labels (dB horizontal lines)
        gridDbs.forEach { db ->
            val y = paddingTop + plotHeight * ((maxDb - db) / (maxDb - minDb)).toFloat()
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            canvas.drawText("${db.toInt()}", 10f, y + 8f, gridTextPaint)
        }

        // 2. Draw Grid lines & labels (Frequency vertical lines - log scale)
        val logMin = log10(minFreq)
        val logMax = log10(maxFreq)
        val logRange = logMax - logMin

        gridFreqs.forEach { freq ->
            val frac = (log10(freq) - logMin) / logRange
            val x = paddingLeft + (frac * plotWidth).toFloat()
            canvas.drawLine(x, paddingTop, x, height - paddingBottom, gridPaint)

            val label = if (freq >= 1000.0) "${(freq / 1000).toInt()}k" else "${freq.toInt()}"
            canvas.drawText(label, x - 18f, height - 15f, gridTextPaint)
        }

        // 3. Draw Curves
        curves.filter { it.isVisible && it.points.isNotEmpty() }.forEach { curve ->
            curvePaint.color = curve.color
            dotPaint.color = curve.color

            val path = Path()
            curve.points.forEachIndexed { i, p ->
                val fFrac = ((log10(p.freqHz) - logMin) / logRange).coerceIn(0.0, 1.0)
                val x = paddingLeft + (fFrac * plotWidth).toFloat()
                val dbFrac = ((maxDb - p.levelDb) / (maxDb - minDb)).coerceIn(0.0, 1.0)
                val y = paddingTop + (dbFrac * plotHeight).toFloat()

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
                canvas.drawCircle(x, y, 3f, dotPaint)
            }
            canvas.drawPath(path, curvePaint)
        }
    }
}

package com.dansemur.constructioncalc

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * CurvedBezelButton
 *
 * Custom Wear OS action button that conforms strictly to the circular bezel
 * of the 45mm / 41mm watch display.
 * Draws an arc contouring the watch perimeter with smooth, organic rounded pill caps.
 * Supports click, long-press, and continuous auto-repeat actions.
 */
class CurvedBezelButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var buttonText: String = ""
    private var startAngle: Float = 0f
    private var sweepAngle: Float = 0f
    private var normalColor: Int = Color.DKGRAY
    private var pressedColor: Int = Color.GRAY
    private var textColor: Int = Color.WHITE
    private var textSizeSp: Float = 18f

    private var outerRadiusDp: Float = 115f
    private var thicknessDp: Float = 20f

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var isTouchPressed = false
    private var didTriggerLongOrRepeat = false

    var onLongClickAction: (() -> Unit)? = null
    var onRepeatAction: (() -> Unit)? = null
    var isAutoRepeat: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val longClickRunnable = Runnable {
        if (isTouchPressed && !didTriggerLongOrRepeat) {
            didTriggerLongOrRepeat = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongClickAction?.invoke()
        }
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isTouchPressed) {
                didTriggerLongOrRepeat = true
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onRepeatAction?.invoke()
                mainHandler.postDelayed(this, 90)
            }
        }
    }

    fun setup(
        text: String,
        startAngle: Float,
        sweepAngle: Float,
        normalColor: Int,
        pressedColor: Int,
        textColor: Int,
        textSizeSp: Float,
        outerRadiusDp: Float = 115f,
        thicknessDp: Float = 20f
    ) {
        this.buttonText = text
        this.startAngle = startAngle
        this.sweepAngle = sweepAngle
        this.normalColor = normalColor
        this.pressedColor = pressedColor
        this.textColor = textColor
        this.textSizeSp = textSizeSp
        this.outerRadiusDp = outerRadiusDp
        this.thicknessDp = thicknessDp
        this.isClickable = true
        this.isFocusable = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val dm = resources.displayMetrics
        val density = dm.density
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()

        val loc = IntArray(2)
        getLocationInWindow(loc)

        // Center of circular watch in this view's local coordinate system
        val cx = (screenW / 2f) - loc[0]
        val cy = (screenH / 2f) - loc[1]

        val rMid = (outerRadiusDp - (thicknessDp / 2f)) * density
        val strokeW = thicknessDp * density

        val ovalMid = RectF(cx - rMid, cy - rMid, cx + rMid, cy + rMid)

        arcPaint.color = if (isTouchPressed) pressedColor else normalColor
        arcPaint.strokeWidth = strokeW
        canvas.drawArc(ovalMid, startAngle, sweepAngle, false, arcPaint)

        // Draw centered button label
        if (buttonText.isNotEmpty()) {
            textPaint.color = textColor
            textPaint.textSize = textSizeSp * density

            val midAngleDeg = startAngle + (sweepAngle / 2f)
            val midAngleRad = Math.toRadians(midAngleDeg.toDouble())

            val tx = cx + (rMid * cos(midAngleRad)).toFloat()
            val ty = cy + (rMid * sin(midAngleRad)).toFloat()

            val fontMetrics = textPaint.fontMetrics
            val baselineOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

            canvas.drawText(buttonText, tx, ty - baselineOffset, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable) return super.onTouchEvent(event)

        val dm = resources.displayMetrics
        val density = dm.density
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()

        val loc = IntArray(2)
        getLocationInWindow(loc)

        val cx = (screenW / 2f) - loc[0]
        val cy = (screenH / 2f) - loc[1]

        val rMid = (outerRadiusDp - (thicknessDp / 2f)) * density
        val rCap = ((thicknessDp / 2f) + 8f) * density // generous 8dp finger margin

        val dx = event.x - cx
        val dy = event.y - cy
        val dist = sqrt(dx * dx + dy * dy)

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f

        val inBody = (dist in (rMid - rCap)..(rMid + rCap)) &&
                (angle >= startAngle - 2f) && (angle <= startAngle + sweepAngle + 2f)

        val aStartRad = Math.toRadians(startAngle.toDouble())
        val startCapX = cx + (rMid * cos(aStartRad)).toFloat()
        val startCapY = cy + (rMid * sin(aStartRad)).toFloat()
        val inStartCap = hypot(event.x - startCapX, event.y - startCapY) <= rCap

        val aEndRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        val endCapX = cx + (rMid * cos(aEndRad)).toFloat()
        val endCapY = cy + (rMid * sin(aEndRad)).toFloat()
        val inEndCap = hypot(event.x - endCapX, event.y - endCapY) <= rCap

        val inArc = inBody || inStartCap || inEndCap

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (inArc) {
                    isTouchPressed = true
                    didTriggerLongOrRepeat = false
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    invalidate()

                    if (onLongClickAction != null) {
                        mainHandler.postDelayed(longClickRunnable, 400)
                    }
                    if (isAutoRepeat && onRepeatAction != null) {
                        mainHandler.postDelayed(repeatRunnable, 300)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouchPressed != inArc) {
                    isTouchPressed = inArc
                    if (!inArc) {
                        mainHandler.removeCallbacks(longClickRunnable)
                        mainHandler.removeCallbacks(repeatRunnable)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longClickRunnable)
                mainHandler.removeCallbacks(repeatRunnable)
                if (isTouchPressed) {
                    isTouchPressed = false
                    invalidate()
                    if (!didTriggerLongOrRepeat) {
                        performClick()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longClickRunnable)
                mainHandler.removeCallbacks(repeatRunnable)
                isTouchPressed = false
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

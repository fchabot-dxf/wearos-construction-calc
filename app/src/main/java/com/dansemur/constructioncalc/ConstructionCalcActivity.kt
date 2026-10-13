package com.dansemur.constructioncalc

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import kotlinx.coroutines.launch

/**
 * ConstructionCalcActivity
 *
 * Standalone Wear OS Fractional Inches Construction Calculator.
 * - Conforming sleek arced buttons for Equal and Backspace.
 * - Long-press Precision Menu (1/8, 1/16, 1/32, 1/64, DEC).
 * - Long-press Conversions Peek Card (Fraction, Decimal, Millimeters, Feet).
 * - Long-press Quick Fraction Picker (1/2, 1/4, 3/4, 1/8, 3/8, ...).
 * - Long-press & Auto-repeat Backspace.
 * - Long-press Pythagoras helpers (+ for √, × for x²).
 */
class ConstructionCalcActivity : Activity() {

    private val engine = ConstructionCalculatorEngine()

    private lateinit var btnPrecision: TextView
    private lateinit var tvExpression: TextView
    private lateinit var tvMain: TextView

    // Overlays
    private lateinit var overlayContainer: FrameLayout
    private lateinit var overlayPrecisionMenu: LinearLayout
    private lateinit var overlayConversion: LinearLayout
    private lateinit var overlayFractionPicker: LinearLayout

    // Conversion display views
    private lateinit var tvConvFraction: TextView
    private lateinit var tvConvDecimal: TextView
    private lateinit var tvConvMetric: TextView

    // Precision menu buttons
    private lateinit var btnPrec1_8: TextView
    private lateinit var btnPrec1_16: TextView
    private lateinit var btnPrec1_32: TextView
    private lateinit var btnPrec1_64: TextView
    private lateinit var btnPrecDec: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_construction_calc)

        btnPrecision = findViewById(R.id.btnPrecision)
        tvExpression = findViewById(R.id.tvExpression)
        tvMain = findViewById(R.id.tvMain)

        overlayContainer = findViewById(R.id.overlayContainer)
        overlayPrecisionMenu = findViewById(R.id.overlayPrecisionMenu)
        overlayConversion = findViewById(R.id.overlayConversion)
        overlayFractionPicker = findViewById(R.id.overlayFractionPicker)

        tvConvFraction = findViewById(R.id.tvConvFraction)
        tvConvDecimal = findViewById(R.id.tvConvDecimal)
        tvConvMetric = findViewById(R.id.tvConvMetric)

        btnPrec1_8 = findViewById(R.id.btnPrec1_8)
        btnPrec1_16 = findViewById(R.id.btnPrec1_16)
        btnPrec1_32 = findViewById(R.id.btnPrec1_32)
        btnPrec1_64 = findViewById(R.id.btnPrec1_64)
        btnPrecDec = findViewById(R.id.btnPrecDec)

        overlayContainer.setOnClickListener {
            closeOverlays()
        }

        setupPrecisionHandlers()
        setupHeaderArcedButtons()
        setupKeypadButtons()
        setupFractionPickerHandlers()

        renderUI()

        // Background Silent OTA Update Check
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.dansemur.constructioncalc.ota.SilentSelfUpdater.checkForUpdate(applicationContext)
            } catch (e: Exception) {
                android.util.Log.e("ConstructionCalc", "OTA check failed: ${e.message}")
            }
        }
    }

    private fun setupPrecisionHandlers() {
        btnPrecision.setOnPressAndHoldListener(
            onHold = { openPrecisionMenu() },
            onClick = {
                engine.cyclePrecision()
                renderUI()
            }
        )

        bindPrecisionMenuItem(btnPrec1_8, "8")
        bindPrecisionMenuItem(btnPrec1_16, "16")
        bindPrecisionMenuItem(btnPrec1_32, "32")
        bindPrecisionMenuItem(btnPrec1_64, "64")
        bindPrecisionMenuItem(btnPrecDec, "dec")
    }

    private fun bindPrecisionMenuItem(btn: TextView, prec: String) {
        btn.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            engine.setPrecision(prec)
            closeOverlays()
            renderUI()
        }
    }

    private fun openPrecisionMenu() {
        val curr = engine.currentPrecision
        btnPrec1_8.setBackgroundResource(if (curr == "8") R.drawable.bg_btn_menu_active else R.drawable.bg_btn_menu_item)
        btnPrec1_16.setBackgroundResource(if (curr == "16") R.drawable.bg_btn_menu_active else R.drawable.bg_btn_menu_item)
        btnPrec1_32.setBackgroundResource(if (curr == "32") R.drawable.bg_btn_menu_active else R.drawable.bg_btn_menu_item)
        btnPrec1_64.setBackgroundResource(if (curr == "64") R.drawable.bg_btn_menu_active else R.drawable.bg_btn_menu_item)
        btnPrecDec.setBackgroundResource(if (curr == "dec") R.drawable.bg_btn_menu_active else R.drawable.bg_btn_menu_item)

        overlayConversion.visibility = View.GONE
        overlayFractionPicker.visibility = View.GONE
        overlayPrecisionMenu.visibility = View.VISIBLE
        overlayContainer.visibility = View.VISIBLE
    }

    private fun openConversionCard() {
        val conv = engine.getConversionData()
        tvConvFraction.text = conv.fractional
        tvConvDecimal.text = conv.decimal
        tvConvMetric.text = conv.metricMm

        overlayPrecisionMenu.visibility = View.GONE
        overlayFractionPicker.visibility = View.GONE
        overlayConversion.visibility = View.VISIBLE
        overlayContainer.visibility = View.VISIBLE
    }

    private fun openFractionPicker() {
        overlayPrecisionMenu.visibility = View.GONE
        overlayConversion.visibility = View.GONE
        overlayFractionPicker.visibility = View.VISIBLE
        overlayContainer.visibility = View.VISIBLE
    }

    private fun closeOverlays() {
        overlayContainer.visibility = View.GONE
        overlayPrecisionMenu.visibility = View.GONE
        overlayConversion.visibility = View.GONE
        overlayFractionPicker.visibility = View.GONE
    }

    private fun setupHeaderArcedButtons() {
        val btnEq = findViewById<CurvedBezelButton>(R.id.btnEqHeader)
        btnEq?.setup(
            text = "=",
            startAngle = 230f,
            sweepAngle = 20f,
            normalColor = android.graphics.Color.parseColor("#D97706"),
            pressedColor = android.graphics.Color.parseColor("#B45309"),
            textColor = android.graphics.Color.WHITE,
            textSizeSp = 18f,
            outerRadiusDp = 115f,
            thicknessDp = 20f
        )
        btnEq?.setOnClickListener {
            engine.handleEquals()
            renderUI()
        }
        btnEq?.onLongClickAction = {
            openConversionCard()
        }

        val btnDel = findViewById<CurvedBezelButton>(R.id.btnDelHeader)
        btnDel?.setup(
            text = "⌫",
            startAngle = 290f,
            sweepAngle = 20f,
            normalColor = android.graphics.Color.parseColor("#334155"),
            pressedColor = android.graphics.Color.parseColor("#1E293B"),
            textColor = android.graphics.Color.parseColor("#E2E8F0"),
            textSizeSp = 15f,
            outerRadiusDp = 115f,
            thicknessDp = 20f
        )
        btnDel?.setOnClickListener {
            engine.handleBackspace()
            renderUI()
        }
        btnDel?.isAutoRepeat = true
        btnDel?.onRepeatAction = {
            engine.handleBackspace()
            renderUI()
        }
        btnDel?.onLongClickAction = {
            engine.handleClearEntry()
            renderUI()
        }

        // Tap on tvMain: Equals; Hold: peek conversions
        tvMain.setOnPressAndHoldListener(
            onHold = { openConversionCard() },
            onClick = {
                engine.handleEquals()
                renderUI()
            }
        )
    }

    private fun setupKeypadButtons() {
        // Row 1: AC, &, /, ÷
        bindButton(R.id.btnAC) { engine.handleClear() }
        bindButton(R.id.btnSlash) { engine.handleSlash() }
        bindButton(R.id.btnDiv) { engine.handleOperator("÷") }

        // Tap '&': Space; Hold '&': Quick fraction picker
        findViewById<View>(R.id.btnSpace)?.setOnPressAndHoldListener(
            onHold = { openFractionPicker() },
            onClick = {
                engine.handleSpace()
                renderUI()
            }
        )

        // Row 2: 7, 8, 9, ×
        bindButton(R.id.btn7) { engine.handleDigit("7") }
        bindButton(R.id.btn8) { engine.handleDigit("8") }
        bindButton(R.id.btn9) { engine.handleDigit("9") }

        // Tap '×': Operator; Hold '×': x² (Square)
        findViewById<View>(R.id.btnMul)?.setOnPressAndHoldListener(
            onHold = {
                engine.handleSquare()
                renderUI()
            },
            onClick = {
                engine.handleOperator("×")
                renderUI()
            }
        )

        // Row 3: 4, 5, 6, -
        bindButton(R.id.btn4) { engine.handleDigit("4") }
        bindButton(R.id.btn5) { engine.handleDigit("5") }
        bindButton(R.id.btn6) { engine.handleDigit("6") }
        bindButton(R.id.btnSub) { engine.handleOperator("-") }

        // Row 4: 1, 2, 3, +
        bindButton(R.id.btn1) { engine.handleDigit("1") }
        bindButton(R.id.btn2) { engine.handleDigit("2") }
        bindButton(R.id.btn3) { engine.handleDigit("3") }

        // Tap '+': Operator; Hold '+': √ (Square Root)
        findViewById<View>(R.id.btnAdd)?.setOnPressAndHoldListener(
            onHold = {
                engine.handleSquareRoot()
                renderUI()
            },
            onClick = {
                engine.handleOperator("+")
                renderUI()
            }
        )

        // Row 5: 0, .
        bindButton(R.id.btn0) { engine.handleDigit("0") }
        bindButton(R.id.btnDot) { engine.handleDigit(".") }
    }

    private fun View.setOnPressAndHoldListener(onHold: () -> Unit, onClick: () -> Unit) {
        var isHeld = false
        var downX = 0f
        var downY = 0f
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val holdRunnable = Runnable {
            isHeld = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onHold()
        }

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHeld = false
                    downX = event.x
                    downY = event.y
                    v.isPressed = true
                    handler.postDelayed(holdRunnable, 350)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dist = Math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
                    if (dist > touchSlop && !isHeld) {
                        handler.removeCallbacks(holdRunnable)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    handler.removeCallbacks(holdRunnable)
                    if (!isHeld) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    }
                    v.invalidate()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(holdRunnable)
                    v.invalidate()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFractionPickerHandlers() {
        val fractionMap = mapOf(
            R.id.btnFrac1_2 to "1/2",
            R.id.btnFrac1_4 to "1/4",
            R.id.btnFrac3_4 to "3/4",
            R.id.btnFrac1_8 to "1/8",
            R.id.btnFrac3_8 to "3/8",
            R.id.btnFrac5_8 to "5/8",
            R.id.btnFrac7_8 to "7/8",
            R.id.btnFrac1_16 to "1/16",
            R.id.btnFrac3_16 to "3/16",
            R.id.btnFrac5_16 to "5/16",
            R.id.btnFrac7_16 to "7/16"
        )
        for ((id, frac) in fractionMap) {
            findViewById<View>(id)?.setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                engine.insertQuickFraction(frac)
                closeOverlays()
                renderUI()
            }
        }
    }

    private fun bindButton(resId: Int, action: () -> Unit) {
        val btn = findViewById<View>(resId) ?: return
        btn.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            action()
            renderUI()
        }
    }

    private fun renderUI() {
        val ui = engine.getUIData()
        btnPrecision.text = ui.precisionLabel
        tvExpression.text = ui.expression
        tvMain.text = ui.mainText
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
        ) {
            val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL)
            if (Math.abs(delta) > 0.5f) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                engine.cyclePrecision()
                renderUI()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}

package com.sshpeaches.app.ui.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalRenderer
import kotlin.math.max

class TerminalRenderView(context: Context) : View(context) {

    private var emulatorProvider: (() -> TerminalEmulator)? = null
    private var onSingleTap: (() -> Unit)? = null
    private var onScaleDelta: ((Float) -> Unit)? = null
    private var onResize: ((Int, Int, Int, Int) -> Unit)? = null

    private var textSizePx: Int = DEFAULT_TEXT_SIZE_PX
    private var renderer: TerminalRenderer = TerminalRenderer(textSizePx, Typeface.MONOSPACE)
    private var terminalBackgroundColor: Int = Color.BLACK
    private var topRow: Int = 0
    private var scrollRemainder: Float = 0f

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onSingleTap?.invoke()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val emulator = emulatorProvider?.invoke() ?: return false
                if (emulator.isAlternateBufferActive) return false
                scrollRemainder += distanceY
                val lineHeight = renderer.fontLineSpacing.toFloat().coerceAtLeast(1f)
                val rowsDown = (scrollRemainder / lineHeight).toInt()
                if (rowsDown == 0) return true
                scrollRemainder -= rowsDown * lineHeight
                scrollByRows(rowsDown)
                return true
            }
        }
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onScaleDelta?.invoke(detector.scaleFactor)
                return true
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun bind(
        emulatorProvider: () -> TerminalEmulator,
        onSingleTap: () -> Unit,
        onScaleDelta: (Float) -> Unit,
        onResize: (Int, Int, Int, Int) -> Unit
    ) {
        this.emulatorProvider = emulatorProvider
        this.onSingleTap = onSingleTap
        this.onScaleDelta = onScaleDelta
        this.onResize = onResize
        clampTopRow()
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    fun setTerminalTextSizePx(size: Int) {
        val safeSize = size.coerceAtLeast(8)
        if (safeSize == textSizePx) return
        textSizePx = safeSize
        renderer = TerminalRenderer(textSizePx, Typeface.MONOSPACE)
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    fun onTerminalUpdated() {
        clampTopRow()
        postInvalidateOnAnimation()
    }

    fun setTerminalBackgroundColor(color: Int) {
        if (terminalBackgroundColor == color) return
        terminalBackgroundColor = color
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSize()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(terminalBackgroundColor)
        val emulator = emulatorProvider?.invoke() ?: return
        renderer.render(
            emulator,
            canvas,
            topRow,
            -1,
            -1,
            -1,
            -1
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            scrollRemainder = 0f
        }
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    private fun updateTerminalSize() {
        emulatorProvider?.invoke() ?: return
        if (width <= 0 || height <= 0) return
        val cellWidthPx = max(1, renderer.fontWidth.toInt())
        val cellHeightPx = max(1, renderer.fontLineSpacing)
        val columns = max(4, width / cellWidthPx)
        val rows = max(4, height / cellHeightPx)
        onResize?.invoke(columns, rows, cellWidthPx, cellHeightPx)
        clampTopRow()
    }

    private fun scrollByRows(rowsDown: Int) {
        val emulator = emulatorProvider?.invoke() ?: return
        val minTop = -emulator.screen.activeTranscriptRows
        val clamped = (topRow + rowsDown).coerceIn(minTop, 0)
        if (clamped == topRow) return
        topRow = clamped
        postInvalidateOnAnimation()
    }

    private fun clampTopRow() {
        val emulator = emulatorProvider?.invoke() ?: return
        val minTop = -emulator.screen.activeTranscriptRows
        topRow = topRow.coerceIn(minTop, 0)
    }

    private companion object {
        private const val DEFAULT_TEXT_SIZE_PX = 24
    }
}

package com.sapta.antigravity.remote.ui.controls

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.sapta.antigravity.remote.data.AppPreferences
import kotlin.math.hypot

/**
 * DraggableControlsController
 *
 * Controls drag-and-drop physics, boundary clamping, and click recognition
 * for the floating Material 3 Controls pill.
 */
class DraggableControlsController(
    private val pillView: View,
    private val containerView: View,
    private val appPreferences: AppPreferences,
    private val onPillClicked: () -> Unit
) {

    private val touchSlop = ViewConfiguration.get(pillView.context).scaledTouchSlop
    private var dX = 0f
    private var dY = 0f
    private var startRawX = 0f
    private var startRawY = 0f
    private var isDragging = false

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        pillView.post {
            val rootW = containerView.width.toFloat()
            val rootH = containerView.height.toFloat()
            val pillW = pillView.width.toFloat()
            val pillH = pillView.height.toFloat()

            if (rootW > 0 && rootH > 0) {
                val density = pillView.resources.displayMetrics.density
                val defaultX = rootW - pillW - (16f * density)
                val defaultY = rootH - pillH - (90f * density)

                val savedX = if (appPreferences.controlsPillX >= 0) appPreferences.controlsPillX else defaultX
                val savedY = if (appPreferences.controlsPillY >= 0) appPreferences.controlsPillY else defaultY

                val maxX = maxOf(0f, rootW - pillW)
                val maxY = maxOf(0f, rootH - pillH)

                pillView.x = savedX.coerceIn(0f, maxX)
                pillView.y = savedY.coerceIn(0f, maxY)
            }
        }

        pillView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    startRawX = event.rawX
                    startRawY = event.rawY
                    isDragging = false
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dist = hypot(
                        (event.rawX - startRawX).toDouble(),
                        (event.rawY - startRawY).toDouble()
                    )
                    if (dist > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val rootW = containerView.width.toFloat()
                        val rootH = containerView.height.toFloat()
                        val pillW = v.width.toFloat()
                        val pillH = v.height.toFloat()

                        val maxX = maxOf(0f, rootW - pillW)
                        val maxY = maxOf(0f, rootH - pillH)

                        val newX = (event.rawX + dX).coerceIn(0f, maxX)
                        val newY = (event.rawY + dY).coerceIn(0f, maxY)

                        v.x = newX
                        v.y = newY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
                    if (!isDragging) {
                        onPillClicked()
                    } else {
                        appPreferences.controlsPillX = v.x
                        appPreferences.controlsPillY = v.y
                    }
                    true
                }
                else -> false
            }
        }
    }
}

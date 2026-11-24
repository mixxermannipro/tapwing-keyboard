package com.tapwing.keyboard.ui.keyboard

import kotlin.math.abs

data class CursorStepDelta(
    val dx: Int = 0,
    val dy: Int = 0
) {
    val hasMovement: Boolean get() = dx != 0 || dy != 0
}

/**
 * Pure, density-independent tracker for spacebar cursor control:
 * - Quick horizontal swipe mode: moves cursor left/right immediately without waiting for long-press.
 * - Full 2D trackpad mode (after long-press on spacebar): moves cursor left, right, up, and down
 *   with axis-drift damping and gentle velocity acceleration for effortless navigation.
 */
class SpacebarTrackpadTracker(
    private val horizontalStepPx: Float,
    private val verticalStepPx: Float,
    private val activationSlopPx: Float,
    private val fastVelocityPxPerMove: Float = horizontalStepPx * 1.8f
) {
    var is2DTrackpadActive: Boolean = false
        private set

    var isQuickHorizontalSwiping: Boolean = false
        private set

    private var accumX: Float = 0f
    private var accumY: Float = 0f
    private var totalDxFromDown: Float = 0f

    fun activate2DTrackpad() {
        is2DTrackpadActive = true
        isQuickHorizontalSwiping = false
        accumX = 0f
        accumY = 0f
    }

    fun reset() {
        is2DTrackpadActive = false
        isQuickHorizontalSwiping = false
        accumX = 0f
        accumY = 0f
        totalDxFromDown = 0f
    }

    fun onDragDelta(dx: Float, dy: Float): CursorStepDelta {
        if (horizontalStepPx <= 0f || verticalStepPx <= 0f) return CursorStepDelta()

        if (!is2DTrackpadActive) {
            totalDxFromDown += dx
            if (!isQuickHorizontalSwiping && abs(totalDxFromDown) >= activationSlopPx) {
                isQuickHorizontalSwiping = true
                accumX = 0f
            }
            if (!isQuickHorizontalSwiping) return CursorStepDelta()

            val boostedDx = applyAcceleration(dx, fastVelocityPxPerMove)
            accumX += boostedDx
            var stepsX = 0
            while (accumX >= horizontalStepPx) {
                stepsX++
                accumX -= horizontalStepPx
            }
            while (accumX <= -horizontalStepPx) {
                stepsX--
                accumX += horizontalStepPx
            }
            return CursorStepDelta(dx = stepsX, dy = 0)
        }

        // Full 2D trackpad mode with gentle dominant-axis stabilization to prevent accidental diagonal drift
        val absDx = abs(dx)
        val absDy = abs(dy)
        val effectiveDx = when {
            absDy > absDx * 2.2f -> dx * 0.25f
            else -> applyAcceleration(dx, fastVelocityPxPerMove)
        }
        val effectiveDy = when {
            absDx > absDy * 2.2f -> dy * 0.25f
            else -> dy
        }

        accumX += effectiveDx
        accumY += effectiveDy

        var stepsX = 0
        while (accumX >= horizontalStepPx) {
            stepsX++
            accumX -= horizontalStepPx
        }
        while (accumX <= -horizontalStepPx) {
            stepsX--
            accumX += horizontalStepPx
        }

        var stepsY = 0
        while (accumY >= verticalStepPx) {
            stepsY++
            accumY -= verticalStepPx
        }
        while (accumY <= -verticalStepPx) {
            stepsY--
            accumY += verticalStepPx
        }

        return CursorStepDelta(dx = stepsX, dy = stepsY)
    }

    private fun applyAcceleration(delta: Float, fastThreshold: Float): Float {
        val mag = abs(delta)
        return when {
            mag >= fastThreshold * 1.6f -> delta * 1.85f
            mag >= fastThreshold -> delta * 1.35f
            else -> delta
        }
    }
}

package com.tapwing.keyboard.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacebarTrackpadTest {

    @Test
    fun quickHorizontalSwipeMovesLeftAndRightWithoutVertical() {
        val tracker = SpacebarTrackpadTracker(
            horizontalStepPx = 20f,
            verticalStepPx = 44f,
            activationSlopPx = 15f
        )

        // Small jitter below slop does not activate
        val jitter = tracker.onDragDelta(10f, 30f)
        assertFalse(tracker.isQuickHorizontalSwiping)
        assertFalse(jitter.hasMovement)

        // Crossing slop activates quick horizontal swipe
        val activate = tracker.onDragDelta(10f, 30f)
        assertTrue(tracker.isQuickHorizontalSwiping)
        assertFalse(tracker.is2DTrackpadActive)
        assertEquals(0, activate.dy)

        // Drag right 25px -> 1 step right, vertical ignored
        val right = tracker.onDragDelta(25f, 100f)
        assertEquals(1, right.dx)
        assertEquals(0, right.dy)

        // Drag left 45px -> at least 2 steps left, vertical still 0
        val left = tracker.onDragDelta(-45f, -100f)
        assertTrue("Expected dx <= -2, got ${left.dx}", left.dx <= -2)
        assertEquals(0, left.dy)
    }

    @Test
    fun longPress2DTrackpadMovesInAllFourDirections() {
        val tracker = SpacebarTrackpadTracker(
            horizontalStepPx = 20f,
            verticalStepPx = 40f,
            activationSlopPx = 15f
        )

        tracker.activate2DTrackpad()
        assertTrue(tracker.is2DTrackpadActive)

        // Right
        val right = tracker.onDragDelta(24f, 0f)
        assertEquals(1, right.dx)
        assertEquals(0, right.dy)

        // Left
        val left = tracker.onDragDelta(-24f, 0f)
        assertEquals(-1, left.dx)
        assertEquals(0, left.dy)

        // Down
        val down = tracker.onDragDelta(0f, 44f)
        assertEquals(0, down.dx)
        assertEquals(1, down.dy)

        // Up
        val up = tracker.onDragDelta(0f, -44f)
        assertEquals(0, up.dx)
        assertEquals(-1, up.dy)
    }

    @Test
    fun trackpadDampensAccidentalVerticalDriftDuringHorizontalScrub() {
        val tracker = SpacebarTrackpadTracker(
            horizontalStepPx = 20f,
            verticalStepPx = 40f,
            activationSlopPx = 15f
        )
        tracker.activate2DTrackpad()

        // Horizontal sweep with slight accidental vertical drift (dy = 6px while dx = 22px)
        var totalX = 0
        var totalY = 0
        repeat(4) {
            val step = tracker.onDragDelta(22f, 6f)
            totalX += step.dx
            totalY += step.dy
        }
        assertTrue("Expected horizontal movement >= 4, got $totalX", totalX >= 4)
        assertEquals("Expected zero accidental vertical line jumps", 0, totalY)
    }

    @Test
    fun fastHorizontalDragAcceleratesCursor() {
        val tracker = SpacebarTrackpadTracker(
            horizontalStepPx = 20f,
            verticalStepPx = 40f,
            activationSlopPx = 15f
        )
        tracker.activate2DTrackpad()

        val fastStep = tracker.onDragDelta(70f, 0f)
        // Without acceleration 70/20 = 3 steps; with acceleration should be >= 5 steps
        assertTrue("Expected accelerated dx >= 5, got ${fastStep.dx}", fastStep.dx >= 5)

        tracker.reset()
        assertFalse(tracker.is2DTrackpadActive)
        assertFalse(tracker.isQuickHorizontalSwiping)
    }
}

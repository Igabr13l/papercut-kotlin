package com.igabr13l.papercut.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class SketchTest {

    @Test
    fun testBoxEdgesGeneratesGeometry() {
        val arr = FloatArray(1024)
        val count = Sketch.boxEdges(0f, 0f, 0f, 2f, 2f, 2f, arr, 0, jit = 0.01f)

        assertTrue("boxEdges should generate vertices", count > 0)
        assertEquals("boxEdges must produce sets of 3D line segment coordinates (multiple of 6)", 0, count % 6)

        // Ensure no NaN or infinite coordinates
        for (i in 0 until count) {
            assertFalse(arr[i].isNaN())
            assertFalse(arr[i].isInfinite())
        }
    }

    @Test
    fun testCirclePairsGeneratesValidPoints() {
        val pts = Sketch.circlePairs(r = 3f, n = 8, jit = 0.05f)
        assertTrue(pts.isNotEmpty())
        assertEquals("circlePairs should produce line segments (multiple of 6)", 0, pts.size % 6)

        for (v in pts) {
            assertFalse(v.isNaN())
            assertFalse(v.isInfinite())
        }
    }

    @Test
    fun testStickmanGeneratesValidGeometryForStates() {
        val arr = FloatArray(4096)
        val states = listOf(
            0, // idle
            1, // walk
            2, // windup
            3, // shoot
            4, // stun
            5, // yank
            6  // dead
        )

        for (st in states) {
            val count = Sketch.stickman(
                px = 5f, py = 0f, pz = 10f,
                s = 1.2f, facing = 0.7f,
                state = st, phase = 1.5f,
                deadT = 0.3f, deadDir = 1f,
                arr = arr, pos = 0
            )

            assertTrue("State $st should generate stickman vertices", count > 0)
            assertEquals("Vertices must form 3D line segments (multiple of 6)", 0, count % 6)

            for (i in 0 until count) {
                assertFalse("Index $i for state $st must not be NaN", arr[i].isNaN())
                assertFalse("Index $i for state $st must not be infinite", arr[i].isInfinite())
            }
        }
    }

    @Test
    fun testStickmanOrientationWithDifferentFacings() {
        val arr = FloatArray(4096)
        val facings = listOf(0f, (PI / 2).toFloat(), PI.toFloat(), (-PI / 2).toFloat())

        for (f in facings) {
            val count = Sketch.stickman(
                px = 0f, py = 0f, pz = 0f,
                s = 1.0f, facing = f,
                state = 1, phase = 0.5f,
                deadT = 0f, deadDir = 1f,
                arr = arr, pos = 0
            )

            assertTrue(count > 0)
            for (i in 0 until count) {
                assertFalse(arr[i].isNaN())
            }
        }
    }
}

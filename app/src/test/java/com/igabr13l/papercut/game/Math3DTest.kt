package com.igabr13l.papercut.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class Math3DTest {

    private val eps = 1e-4f

    @Test
    fun testVec3BasicOperations() {
        val a = Vec3(1f, 2f, 3f)
        val b = Vec3(4f, 5f, 6f)

        // add
        val sum = a.copy().add(b)
        assertEquals(5f, sum.x, eps)
        assertEquals(7f, sum.y, eps)
        assertEquals(9f, sum.z, eps)

        // sub
        val diff = b.copy().sub(a)
        assertEquals(3f, diff.x, eps)
        assertEquals(3f, diff.y, eps)
        assertEquals(3f, diff.z, eps)

        // scale & addScaled
        val scaled = a.copy().scale(2f)
        assertEquals(2f, scaled.x, eps)
        assertEquals(4f, scaled.y, eps)
        assertEquals(6f, scaled.z, eps)

        val addScaled = a.copy().addScaled(b, 0.5f)
        assertEquals(3f, addScaled.x, eps)
        assertEquals(4.5f, addScaled.y, eps)
        assertEquals(6f, addScaled.z, eps)

        // length and norm
        val v = Vec3(3f, 4f, 0f)
        assertEquals(5f, v.len(), eps)
        assertEquals(25f, v.len2(), eps)
        v.norm()
        assertEquals(1f, v.len(), eps)
        assertEquals(0.6f, v.x, eps)
        assertEquals(0.8f, v.y, eps)

        // dot product
        val v1 = Vec3(1f, 0f, 0f)
        val v2 = Vec3(0f, 1f, 0f)
        assertEquals(0f, v1.dot(v2), eps)
        assertEquals(1f, v1.dot(v1), eps)

        // cross product
        val cross = v1.cross(v2)
        assertEquals(0f, cross.x, eps)
        assertEquals(0f, cross.y, eps)
        assertEquals(1f, cross.z, eps)

        // distTo
        val p1 = Vec3(1f, 1f, 1f)
        val p2 = Vec3(4f, 5f, 1f)
        assertEquals(5f, p1.distTo(p2), eps)

        // equals & approxEquals
        assertTrue(p1.approxEquals(Vec3(1.00001f, 0.99999f, 1f)))
        assertEquals(Vec3(1f, 2f, 3f), Vec3(1f, 2f, 3f))
    }

    @Test
    fun testMat4IdentityAndMul() {
        val id = FloatArray(16)
        Mat4.identity(id)

        // check diagonal
        for (i in 0..3) {
            for (j in 0..3) {
                val expected = if (i == j) 1f else 0f
                assertEquals(expected, id[j * 4 + i], eps)
            }
        }

        // transform by identity should not alter point
        val out = FloatArray(4)
        Mat4.transform(id, 2.5f, -3.7f, 8.1f, out)
        assertEquals(2.5f, out[0], eps)
        assertEquals(-3.7f, out[1], eps)
        assertEquals(8.1f, out[2], eps)
        assertEquals(1f, out[3], eps)

        // mul(I, I) = I
        val res = FloatArray(16)
        Mat4.mul(res, id, id)
        for (k in 0..15) {
            assertEquals(id[k], res[k], eps)
        }
    }

    @Test
    fun testMat4Perspective() {
        val proj = FloatArray(16)
        val fovy = 60f * (PI.toFloat() / 180f)
        val aspect = 16f / 9f
        val near = 0.1f
        val far = 100f
        Mat4.perspective(proj, fovy, aspect, near, far)

        // point at near plane on z-axis: (0, 0, -near)
        val clipNear = FloatArray(4)
        Mat4.transform(proj, 0f, 0f, -near, clipNear)
        assertEquals(near, clipNear[3], eps) // w = -z = near
        val ndcZNear = clipNear[2] / clipNear[3]
        assertEquals(-1f, ndcZNear, eps) // In OpenGL NDC, near maps to -1

        // point at far plane on z-axis: (0, 0, -far)
        val clipFar = FloatArray(4)
        Mat4.transform(proj, 0f, 0f, -far, clipFar)
        assertEquals(far, clipFar[3], eps) // w = far
        val ndcZFar = clipFar[2] / clipFar[3]
        assertEquals(1f, ndcZFar, eps) // far maps to +1
    }

    @Test
    fun testMat4FpsViewEyeToOrigin() {
        val view = FloatArray(16)
        val eye = Vec3(12.5f, 3.4f, -7.8f)
        Mat4.fpsView(view, eye, 0.7f, -0.3f)

        // The eye position in view space must map to (0, 0, 0, 1)
        val out = FloatArray(4)
        Mat4.transform(view, eye.x, eye.y, eye.z, out)
        assertEquals(0f, out[0], eps)
        assertEquals(0f, out[1], eps)
        assertEquals(0f, out[2], eps)
        assertEquals(1f, out[3], eps)
    }

    @Test
    fun testMat4FpsViewDirectionMappings() {
        // Test multiple yaw and pitch combinations
        val testAngles = listOf(
            0f to 0f,
            (PI / 2).toFloat() to 0f,
            PI.toFloat() to 0f,
            (-PI / 2).toFloat() to 0f,
            0.5f to 0.3f,
            -1.1f to -0.4f,
            2.2f to 0.8f,
        )

        val eye = Vec3(5f, 2f, -10f)
        val view = FloatArray(16)
        val out = FloatArray(4)

        for ((yaw, pitch) in testAngles) {
            Mat4.fpsView(view, eye, yaw, pitch)

            // 1. Orthonormality check for upper-left 3x3 rotation matrix
            // Columns:
            val c0 = Vec3(view[0], view[1], view[2])
            val c1 = Vec3(view[4], view[5], view[6])
            val c2 = Vec3(view[8], view[9], view[10])

            assertEquals(1f, c0.len(), eps)
            assertEquals(1f, c1.len(), eps)
            assertEquals(1f, c2.len(), eps)
            assertEquals(0f, c0.dot(c1), eps)
            assertEquals(0f, c0.dot(c2), eps)
            assertEquals(0f, c1.dot(c2), eps)

            // Determinant of rotation matrix should be +1 (proper rotation, no reflection)
            val det = c0.dot(c1.cross(c2))
            assertEquals(1f, det, eps)

            // 2. Forward aim vector: in world space, forward = (-sin(yaw)*cos(pitch), sin(pitch), -cos(yaw)*cos(pitch))
            val cp = cos(pitch); val sp = sin(pitch)
            val cy = cos(yaw); val sy = sin(yaw)
            val fwd = Vec3(-sy * cp, sp, -cy * cp)

            // In camera view space, a point at eye + fwd must map to (0, 0, -1)
            val ptFwd = eye.copy().add(fwd)
            Mat4.transform(view, ptFwd.x, ptFwd.y, ptFwd.z, out)
            assertEquals("Yaw=$yaw, Pitch=$pitch: view space X should be 0", 0f, out[0], eps)
            assertEquals("Yaw=$yaw, Pitch=$pitch: view space Y should be 0", 0f, out[1], eps)
            assertEquals("Yaw=$yaw, Pitch=$pitch: view space Z should be -1", -1f, out[2], eps)

            // 3. Right vector: in world space, right = (cos(yaw), 0, -sin(yaw))
            val right = Vec3(cy, 0f, -sy)
            val ptRight = eye.copy().add(right)
            Mat4.transform(view, ptRight.x, ptRight.y, ptRight.z, out)
            assertEquals(1f, out[0], eps) // Camera view space X should be +1
            assertEquals(0f, out[1], eps)
            assertEquals(0f, out[2], eps)
        }
    }
}

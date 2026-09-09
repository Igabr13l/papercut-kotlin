package com.igabr13l.papercut.game

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(x: Float, y: Float, z: Float): Vec3 { this.x = x; this.y = y; this.z = z; return this }
    fun set(o: Vec3): Vec3 { x = o.x; y = o.y; z = o.z; return this }
    fun add(o: Vec3): Vec3 { x += o.x; y += o.y; z += o.z; return this }
    fun addScaled(o: Vec3, s: Float): Vec3 { x += o.x * s; y += o.y * s; z += o.z * s; return this }
    fun sub(o: Vec3): Vec3 { x -= o.x; y -= o.y; z -= o.z; return this }
    fun scale(s: Float): Vec3 { x *= s; y *= s; z *= s; return this }
    fun len(): Float = kotlin.math.sqrt(x * x + y * y + z * z)
    fun len2(): Float = x * x + y * y + z * z
    fun norm(): Vec3 { val l = len(); if (l > 1e-8f) scale(1f / l); return this }
    fun copy(): Vec3 = Vec3(x, y, z)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun distTo(o: Vec3): Float = kotlin.math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z))
    fun approxEquals(o: Vec3, eps: Float = 1e-4f): Boolean =
        kotlin.math.abs(x - o.x) < eps && kotlin.math.abs(y - o.y) < eps && kotlin.math.abs(z - o.z) < eps
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vec3) return false
        return x == other.x && y == other.y && z == other.z
    }
    override fun hashCode(): Int = 31 * (31 * x.hashCode() + y.hashCode()) + z.hashCode()
    override fun toString() = "($x,$y,$z)"
}

fun v3(x: Float = 0f, y: Float = 0f, z: Float = 0f) = Vec3(x, y, z)

object Mat4 {
    fun identity(out: FloatArray) {
        java.util.Arrays.fill(out, 0f)
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
    }

    // column-major, GL convention: out = a * b
    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
        val r = FloatArray(16)
        for (c in 0..3) for (rw in 0..3) {
            var s = 0f
            for (k in 0..3) s += a[k * 4 + rw] * b[c * 4 + k]
            r[c * 4 + rw] = s
        }
        System.arraycopy(r, 0, out, 0, 16)
    }

    fun perspective(out: FloatArray, fovyRad: Float, aspect: Float, near: Float, far: Float) {
        java.util.Arrays.fill(out, 0f)
        val f = 1f / tan(fovyRad / 2f)
        out[0] = f / aspect
        out[5] = f
        out[10] = (far + near) / (near - far)
        out[11] = -1f
        out[14] = 2f * far * near / (near - far)
    }

    // FPS view: Rx(-pitch) * Ry(-yaw) * T(-eye)
    fun fpsView(out: FloatArray, eye: Vec3, yaw: Float, pitch: Float) {
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        // R = Rx(-pitch) * Ry(-yaw)
        val m00 = cy; val m01 = 0f; val m02 = -sy
        val m10 = sp * sy; val m11 = cp; val m12 = sp * cy
        val m20 = cp * sy; val m21 = -sp; val m22 = cp * cy
        java.util.Arrays.fill(out, 0f)
        out[0] = m00; out[4] = m01; out[8] = m02
        out[1] = m10; out[5] = m11; out[9] = m12
        out[2] = m20; out[6] = m21; out[10] = m22
        out[12] = -(m00 * eye.x + m01 * eye.y + m02 * eye.z)
        out[13] = -(m10 * eye.x + m11 * eye.y + m12 * eye.z)
        out[14] = -(m20 * eye.x + m21 * eye.y + m22 * eye.z)
        out[15] = 1f
    }

    // p' = M * p (w=1), returns clip-space in out4 (x,y,z,w)
    fun transform(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15]
    }
}

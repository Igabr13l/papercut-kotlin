package com.igabr13l.papercut.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Doodle-style line generation, ported from the web sketch.ts helpers.
object Sketch {
    val INK = floatArrayOf(0.18f, 0.24f, 0.66f)
    val INK_DEEP = floatArrayOf(0.125f, 0.17f, 0.49f)
    val RED = floatArrayOf(0.78f, 0.16f, 0.24f)
    val RED_BRIGHT = floatArrayOf(0.85f, 0.25f, 0.3f)
    val ORANGE = floatArrayOf(0.85f, 0.49f, 0.06f)
    val GREEN = floatArrayOf(0.24f, 0.54f, 0.29f)
    val YELLOW = floatArrayOf(0.72f, 0.63f, 0.07f)
    val PAPER = floatArrayOf(0.957f, 0.945f, 0.902f)
    val SHADE = floatArrayOf(0.89f, 0.87f, 0.80f)
    val SHADE_LIGHT = floatArrayOf(0.85f, 0.85f, 0.93f)

    fun rndJitter(a: Float) = (Math.random().toFloat() * 2f - 1f) * a

    // Append a slightly wobbly segment (broken into n jittered subsegments) into arr.
    fun jitterSeg(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, arr: FloatArray, pos: Int, jit: Float = 0.02f, n: Int = 4): Int {
        var p = pos
        for (k in 0 until n) {
            val t0 = k.toFloat() / n
            val t1 = (k + 1).toFloat() / n
            val ax = x0 + (x1 - x0) * t0 + rndJitter(jit)
            val ay = y0 + (y1 - y0) * t0 + rndJitter(jit)
            val az = z0 + (z1 - z0) * t0 + rndJitter(jit)
            val bx = x0 + (x1 - x0) * t1 + rndJitter(jit)
            val by = y0 + (y1 - y0) * t1 + rndJitter(jit)
            val bz = z0 + (z1 - z0) * t1 + rndJitter(jit)
            arr[p++] = ax; arr[p++] = ay; arr[p++] = az
            arr[p++] = bx; arr[p++] = by; arr[p++] = bz
        }
        return p
    }

    // Circle made of 2 slightly offset rings (double-stroke pen look), as a line loop pairs.
    fun circlePairs(r: Float, n: Int, jit: Float, cx: Float = 0f, cy: Float = 0f, cz: Float = 0f, plane: String = "xy"): FloatArray {
        val pts = ArrayList<Float>(n * 6)
        for (i in 0 until n) {
            val a0 = (i.toFloat() / n) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / n) * 2f * PI.toFloat()
            val j = floatArrayOf(rndJitter(jit), rndJitter(jit), 0f)
            val c0 = floatArrayOf(cos(a0) * r + cx, sin(a0) * r + cy, cz)
            val c1 = floatArrayOf(cos(a1) * r + cx, sin(a1) * r + cy, cz)
            for ((a, b) in listOf(c0 to c1, c0.mapIndexed { k, v -> v + j[k % 3] }.toFloatArray() to c1.mapIndexed { k, v -> v + j[k % 3] }.toFloatArray())) {
                when (plane) {
                    "xy" -> { pts.add(a[0]); pts.add(a[1]); pts.add(a[2]); pts.add(b[0]); pts.add(b[1]); pts.add(b[2]) }
                    "zy" -> { pts.add(a[2]); pts.add(a[1]); pts.add(a[0]); pts.add(b[2]); pts.add(b[1]); pts.add(b[0]) }
                    "xz" -> { pts.add(a[0]); pts.add(a[2]); pts.add(a[1]); pts.add(b[0]); pts.add(b[2]); pts.add(b[1]) }
                }
            }
        }
        return pts.toFloatArray()
    }

    // Cross marker as 2 segments.
    fun cross(x: Float, y: Float, z: Float, r: Float): FloatArray {
        return floatArrayOf(
            x - r, y, z, x + r, y, z,
            x, y - r, z, x, y + r, z,
            x, y, z - r, x, y, z + r,
        )
    }

    // Axis-aligned box edges (slightly inflated) into arr.
    fun boxEdges(cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float, arr: FloatArray, pos: Int, jit: Float = 0.015f): Int {
        val hw = w / 2f * 1.004f
        val hh = h / 2f * 1.004f
        val hd = d / 2f * 1.004f
        val x0 = cx - hw; val x1 = cx + hw
        val y0 = cy - hh; val y1 = cy + hh
        val z0 = cz - hd; val z1 = cz + hd
        var p = pos
        // bottom & top rectangles
        p = jitterSeg(x0, y0, z0, x1, y0, z0, arr, p, jit)
        p = jitterSeg(x1, y0, z0, x1, y0, z1, arr, p, jit)
        p = jitterSeg(x1, y0, z1, x0, y0, z1, arr, p, jit)
        p = jitterSeg(x0, y0, z1, x0, y0, z0, arr, p, jit)
        p = jitterSeg(x0, y1, z0, x1, y1, z0, arr, p, jit)
        p = jitterSeg(x1, y1, z0, x1, y1, z1, arr, p, jit)
        p = jitterSeg(x1, y1, z1, x0, y1, z1, arr, p, jit)
        p = jitterSeg(x0, y1, z1, x0, y1, z0, arr, p, jit)
        // verticals
        p = jitterSeg(x0, y0, z0, x0, y1, z0, arr, p, jit)
        p = jitterSeg(x1, y0, z0, x1, y1, z0, arr, p, jit)
        p = jitterSeg(x1, y0, z1, x1, y1, z1, arr, p, jit)
        p = jitterSeg(x0, y0, z1, x0, y1, z1, arr, p, jit)
        return p
    }

    // Hatch (diagonal strokes) on the +Z / -Z faces of a box, for visual variety.
    fun boxHatch(cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float, arr: FloatArray, pos: Int, spacing: Float = 0.5f): Int {
        var p = pos
        val hw = w / 2f; val hh = h / 2f
        var x = cx - hw + spacing
        while (x < cx + hw) {
            p = jitterSeg(x, cy - hh, cz + d / 2f, x + hh, cy + hh, cz + d / 2f, arr, p, 0.01f, 3)
            p = jitterSeg(x, cy - hh, cz - d / 2f, x + hh, cy + hh, cz - d / 2f, arr, p, 0.01f, 3)
            x += spacing
        }
        return p
    }

    // Stickman pose as line segments (world-space, feet at (px,py,pz), scale s).
    // state: 0 idle, 1 walk, 2 windup, 3 shoot, 4 stun, 5 yank, 6 dead
    fun stickman(px: Float, py: Float, pz: Float, s: Float, facing: Float, state: Int, phase: Float, deadT: Float, deadDir: Float, arr: FloatArray, pos: Int): Int {
        var p = pos
        fun seg(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
            arr[p++] = px + ax; arr[p++] = py + ay; arr[p++] = pz + az
            arr[p++] = px + bx; arr[p++] = py + by; arr[p++] = pz + bz
        }
        // local: x = right of enemy, z = forward (towards facing dir handled by caller rotation? we fake with facing cos/sin)
        val fx = sin(facing); val fz = cos(facing) // forward
        val rx = fz; val rz = -fx                  // right
        fun L(l: Float, r: Float, h: Float): FloatArray {
            // local (l = right offset, h = up) -> world
            return floatArrayOf(l * rx + h * 0f, h, l * rz + 0f).let { it }
        }
        val walk = if (state == 1) sin(phase * 9f) else if (state == 5) sin(phase * 14f) else 0f
        val bob = if (state == 1) kotlin.math.abs(sin(phase * 9f)) * 0.06f else 0f
        val hipY = 0.55f * s
        val shY = 1.3f * s + bob * s
        val legSwing = walk * 0.35f * s
        // legs (world-space forward offset)
        fun leg(hipR: Float, swing: Float) {
            val hx = hipR * rx; val hz = hipR * rz
            val kx = hx + swing * s * fx * 0.5f; val kz = hz + swing * s * fz * 0.5f
            val fx2 = hx + swing * s * fx; val fz2 = hz + swing * s * fz
            arr[p++] = px + hx; arr[p++] = py + hipY; arr[p++] = pz + hz
            arr[p++] = px + kx; arr[p++] = py + hipY * 0.5f; arr[p++] = pz + kz
            arr[p++] = px + kx; arr[p++] = py + hipY * 0.5f; arr[p++] = pz + kz
            arr[p++] = px + fx2; arr[p++] = py; arr[p++] = pz + fz2
        }
        leg(0.16f * s, legSwing)
        leg(-0.16f * s, -legSwing)
        // spine
        seg(0f, hipY + bob * s, 0f, 0f, shY, 0f)
        // head (as 2 crossed arcs: use small octagon via 4 segs)
        val hy = 1.55f * s + bob * s
        val hr = 0.28f * s
        var a0 = 0f
        while (a0 < 2f * PI.toFloat() - 0.01f) {
            val a1 = a0 + (2f * PI.toFloat()) / 7f
            seg(cos(a0) * hr, hy + sin(a0) * hr, 0f, cos(a1) * hr, hy + sin(a1) * hr, 0f)
            a0 = a1
        }
        // arms
        val armR = 0.35f * s
        when (state) {
            2 -> { // windup: both arms up forward
                seg(0.1f * s, shY, 0f, armR * 0.6f * fx + 0.1f * s * rx, shY + 0.5f * s, armR * 0.6f * fz + 0.1f * s * rz)
                seg(-0.1f * s, shY, 0f, 0.2f * s * fx - 0.1f * s * rx, shY + 0.45f * s, 0.2f * s * fz - 0.1f * s * rz)
            }
            3 -> { // shoot: one arm forward
                seg(0.1f * s, shY, 0f, 0.55f * s * fx + 0.1f * s * rx, shY, 0.55f * s * fz + 0.1f * s * rz)
                seg(-0.1f * s, shY, 0f, -0.15f * s * rx, shY - 0.35f * s, -0.15f * s * rz)
            }
            else -> {
                val sw = walk * 0.3f * s
                seg(0.12f * s, shY, 0f, (0.12f * s) * rx - sw * fx, shY - 0.55f * s, (0.12f * s) * rz - sw * fz)
                seg(-0.12f * s, shY, 0f, (-0.12f * s) * rx + sw * fx, shY - 0.55f * s, (-0.12f * s) * rz + sw * fz)
            }
        }
        // dead: tilt lines (approximate by skewing x/z with deadT handled by caller sinking)
        if (state == 6) {
            val tilt = kotlin.math.min(1.5f, deadT * 3f) * deadDir
            val c = cos(tilt); val si = sin(tilt)
            // rewrite: rotate all appended points around feet pivot
            var q = pos
            while (q < p) {
                val wx = arr[q] - px; val wy = arr[q + 1] - py
                arr[q] = px + wx * c + wy * si
                arr[q + 1] = py + wy * c - wx * si
                q += 3
            }
        }
        return p
    }
}

package com.igabr13l.papercut.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Doodle-style line generation, ported from the web sketch.ts helpers.
object Sketch {
    val INK = floatArrayOf(0.18f, 0.24f, 0.66f, 1f)
    val INK_DEEP = floatArrayOf(0.125f, 0.17f, 0.49f, 1f)
    val RED = floatArrayOf(0.78f, 0.16f, 0.24f, 1f)
    val RED_BRIGHT = floatArrayOf(0.85f, 0.25f, 0.3f, 1f)
    val ORANGE = floatArrayOf(0.85f, 0.49f, 0.06f, 1f)
    val GREEN = floatArrayOf(0.24f, 0.54f, 0.29f, 1f)
    val YELLOW = floatArrayOf(0.72f, 0.63f, 0.07f, 1f)
    val PAPER = floatArrayOf(0.957f, 0.945f, 0.902f, 1f)
    val SHADE = floatArrayOf(0.89f, 0.87f, 0.80f, 1f)
    val SHADE_LIGHT = floatArrayOf(0.85f, 0.85f, 0.93f, 1f)

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

    // Arc segment pairs
    fun arcPairs(r: Float, a0: Float, a1: Float, segs: Int = 5, ox: Float = 0f, oy: Float = 0f, oz: Float = 0f): FloatArray {
        val pts = ArrayList<Float>(segs * 6)
        var px = cos(a0) * r + ox; var py = sin(a0) * r + oy
        for (i in 1..segs) {
            val a = a0 + ((a1 - a0) * i) / segs
            val nx = cos(a) * r + ox; val ny = sin(a) * r + oy
            pts.add(px); pts.add(py); pts.add(oz)
            pts.add(nx); pts.add(ny); pts.add(oz)
            px = nx; py = ny
        }
        return pts.toFloatArray()
    }

    // Helper to transform local (lx, ly, lz) to world with scaling, dead tilt, facing, and translation
    private inline fun transformPoint(
        lx: Float, ly: Float, lz: Float, s: Float,
        facing: Float, tilt: Float, px: Float, py: Float, pz: Float,
        out: (Float, Float, Float) -> Unit
    ) {
        val sx = lx * s; val sy = ly * s; val sz = lz * s
        // Tilt around Z if dead
        val tx = if (tilt != 0f) sx * cos(tilt) - sy * sin(tilt) else sx
        val ty = if (tilt != 0f) sx * sin(tilt) + sy * cos(tilt) else sy
        val tz = sz
        // Rotate around Y by facing (facing = 0 faces +Z)
        val sinF = sin(facing); val cosF = cos(facing)
        val wx = tx * cosF + tz * sinF
        val wy = ty
        val wz = -tx * sinF + tz * cosF
        out(px + wx, py + wy, pz + wz)
    }

    // Stickman pose as line segments (world-space, feet at (px,py,pz), scale s).
    // state: 0 idle, 1 walk, 2 windup, 3 shoot, 4 stun, 5 yank, 6 dead
    fun stickman(px: Float, py: Float, pz: Float, s: Float, facing: Float, state: Int, phase: Float, deadT: Float, deadDir: Float, arr: FloatArray, pos: Int): Int {
        var p = pos
        val tilt = if (state == 6) kotlin.math.min(1.5f, deadT * 3f) * deadDir else 0f

        fun seg(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
            transformPoint(ax, ay, az, s, facing, tilt, px, py, pz) { x, y, z ->
                arr[p++] = x; arr[p++] = y; arr[p++] = z
            }
            transformPoint(bx, by, bz, s, facing, tilt, px, py, pz) { x, y, z ->
                arr[p++] = x; arr[p++] = y; arr[p++] = z
            }
        }

        val walk = if (state == 1) sin(phase * 9f) else if (state == 5) sin(phase * 17f) else 0f
        val cosW = if (state == 1) cos(phase * 9f) else 0f
        val lean = if (state == 1) 0.07f else if (state == 2) -0.16f else if (state == 3) 0.3f else 0f
        val neck = floatArrayOf(lean, 1.38f, 0f)
        val hip = floatArrayOf(lean * 0.5f, 0.95f, 0f)

        // 0. Torso
        seg(neck[0], neck[1], neck[2], hip[0], hip[1], hip[2])

        val sh = floatArrayOf(lean * 0.9f, 1.3f, 0f)

        // 1-4. Arms and Legs according to web stickman rig
        when (state) {
            1 -> { // walk
                seg(sh[0], sh[1], sh[2], -walk * 0.2f + lean, 1.05f, 0.08f)
                seg(-walk * 0.2f + lean, 1.05f, 0.08f, -walk * 0.4f + lean, 0.82f, 0.18f)
                seg(sh[0], sh[1], sh[2], walk * 0.2f + lean, 1.05f, 0.08f)
                seg(walk * 0.2f + lean, 1.05f, 0.08f, walk * 0.4f + lean, 0.82f, 0.18f)
                seg(hip[0], hip[1], hip[2], cosW * 0.28f + lean * 0.5f, 0.5f, 0.06f)
                seg(cosW * 0.28f + lean * 0.5f, 0.5f, 0.06f, cosW * 0.5f + lean * 0.5f, 0.05f + maxOf(0f, -walk) * 0.2f, 0f)
                seg(hip[0], hip[1], hip[2], -cosW * 0.28f + lean * 0.5f, 0.5f, 0.06f)
                seg(-cosW * 0.28f + lean * 0.5f, 0.5f, 0.06f, -cosW * 0.5f + lean * 0.5f, 0.05f + maxOf(0f, walk) * 0.2f, 0f)
            }
            2 -> { // windup
                seg(sh[0], sh[1], sh[2], -0.22f, 1.28f, -0.22f)
                seg(-0.22f, 1.28f, -0.22f, -0.28f, 1.5f, -0.4f)
                seg(sh[0], sh[1], sh[2], 0.22f, 1.28f, -0.22f)
                seg(0.22f, 1.28f, -0.22f, 0.28f, 1.5f, -0.4f)
                seg(hip[0], hip[1], hip[2], -0.2f, 0.5f, -0.05f)
                seg(-0.2f, 0.5f, -0.05f, -0.3f, 0.03f, -0.12f)
                seg(hip[0], hip[1], hip[2], 0.2f, 0.5f, 0.05f)
                seg(0.2f, 0.5f, 0.05f, 0.3f, 0.03f, 0.12f)
            }
            3 -> { // attack / shoot
                val f = 0.55f
                seg(sh[0], sh[1], sh[2], -0.16f, 1.18f, 0.22f)
                seg(-0.16f, 1.18f, 0.22f, -0.18f, 1.05f, f * 0.7f)
                seg(sh[0], sh[1], sh[2], 0.16f, 1.2f, 0.26f)
                seg(0.16f, 1.2f, 0.26f, 0.18f, 1.12f, f)
                seg(hip[0], hip[1], hip[2], -0.22f, 0.5f, 0.1f)
                seg(-0.22f, 0.5f, 0.1f, -0.32f, 0.03f, 0.2f)
                seg(hip[0], hip[1], hip[2], 0.24f, 0.5f, -0.08f)
                seg(0.24f, 0.5f, -0.08f, 0.36f, 0.03f, -0.16f)
            }
            5 -> { // yank / stun
                val sPhase = sin(phase * 17f)
                val cPhase = cos(phase * 17f)
                seg(sh[0], sh[1], sh[2], -0.3f, 1.4f + sPhase * 0.12f, -0.05f)
                seg(-0.3f, 1.4f + sPhase * 0.12f, -0.05f, -0.46f, 1.66f + cPhase * 0.14f, 0.02f)
                seg(sh[0], sh[1], sh[2], 0.3f, 1.4f + cPhase * 0.12f, -0.05f)
                seg(0.3f, 1.4f + cPhase * 0.12f, -0.05f, 0.46f, 1.66f + sPhase * 0.14f, 0.02f)
                seg(hip[0], hip[1], hip[2], -0.24f, 0.52f, 0f)
                seg(-0.24f, 0.52f, 0f, -0.34f, 0.06f, 0.06f)
                seg(hip[0], hip[1], hip[2], 0.24f, 0.52f, 0f)
                seg(0.24f, 0.52f, 0f, 0.34f, 0.06f, -0.06f)
            }
            6 -> { // dead
                seg(sh[0], sh[1], sh[2], -0.3f, 1.1f, 0.1f)
                seg(-0.3f, 1.1f, 0.1f, -0.5f, 0.9f, 0.2f)
                seg(sh[0], sh[1], sh[2], 0.3f, 1.1f, 0.1f)
                seg(0.3f, 1.1f, 0.1f, 0.5f, 0.9f, 0.2f)
                seg(hip[0], hip[1], hip[2], -0.2f, 0.5f, 0.1f)
                seg(-0.2f, 0.5f, 0.1f, -0.3f, 0.05f, 0.3f)
                seg(hip[0], hip[1], hip[2], 0.2f, 0.5f, 0.1f)
                seg(0.2f, 0.5f, 0.1f, 0.3f, 0.05f, 0.3f)
            }
            else -> { // idle
                val b = sin(phase * 3.5f) * 0.03f
                seg(sh[0], sh[1], sh[2], -0.16f, 1.05f + b, 0.06f)
                seg(-0.16f, 1.05f + b, 0.06f, -0.2f, 0.82f + b, 0.14f)
                seg(sh[0], sh[1], sh[2], 0.16f, 1.05f - b, 0.06f)
                seg(0.16f, 1.05f - b, 0.06f, 0.2f, 0.82f - b, 0.14f)
                seg(hip[0], hip[1], hip[2], -0.14f, 0.5f, 0.02f)
                seg(-0.14f, 0.5f, 0.02f, -0.2f, 0.03f, 0f)
                seg(hip[0], hip[1], hip[2], 0.14f, 0.5f, 0.02f)
                seg(0.14f, 0.5f, 0.02f, 0.2f, 0.03f, 0f)
            }
        }

        // Belly outline (circle in XY at y=1.02, z=0.03)
        val bellyR = 0.44f
        val bsegs = 12
        var ba0 = 0f
        for (i in 0 until bsegs) {
            val ba1 = ((i + 1).toFloat() / bsegs) * 2f * PI.toFloat()
            seg(cos(ba0) * bellyR, 1.02f + sin(ba0) * bellyR, 0.03f, cos(ba1) * bellyR, 1.02f + sin(ba1) * bellyR, 0.03f)
            ba0 = ba1
        }

        // Head position (y=1.62)
        val headY = 1.62f
        val headR = 0.27f
        val hsegs = 10
        var ha0 = 0f
        // 1. Head circle XY
        for (i in 0 until hsegs) {
            val ha1 = ((i + 1).toFloat() / hsegs) * 2f * PI.toFloat()
            seg(cos(ha0) * headR, headY + sin(ha0) * headR, 0f, cos(ha1) * headR, headY + sin(ha1) * headR, 0f)
            ha0 = ha1
        }
        // 2. Head circle ZY (giving 3D sphere volume)
        ha0 = 0f
        for (i in 0 until hsegs) {
            val ha1 = ((i + 1).toFloat() / hsegs) * 2f * PI.toFloat()
            seg(0f, headY + sin(ha0) * headR, cos(ha0) * headR, 0f, headY + sin(ha1) * headR, cos(ha1) * headR)
            ha0 = ha1
        }

        // 3. Eyes (two small circles at -0.09 and +0.09)
        val eyeR = 0.04f
        for (side in floatArrayOf(-0.09f, 0.09f)) {
            var ea0 = 0f
            for (i in 0 until 5) {
                val ea1 = ((i + 1).toFloat() / 5) * 2f * PI.toFloat()
                seg(side + cos(ea0) * eyeR, headY + 0.06f + sin(ea0) * eyeR, 0.16f, side + cos(ea1) * eyeR, headY + 0.06f + sin(ea1) * eyeR, 0.16f)
                ea0 = ea1
            }
        }

        // 4. Mouth (arc at y=-0.08 relative to head)
        var ma0 = PI.toFloat() * 1.15f
        val maEnd = PI.toFloat() * 1.85f
        val mouthR = 0.11f
        for (i in 0 until 4) {
            val ma1 = ma0 + (maEnd - PI.toFloat() * 1.15f) / 4f
            seg(cos(ma0) * mouthR, headY - 0.08f + sin(ma0) * mouthR, 0.16f, cos(ma1) * mouthR, headY - 0.08f + sin(ma1) * mouthR, 0.16f)
            ma0 = ma1
        }

        // 5. Eyebrows (jittered expressive segments)
        seg(-0.16f, headY + 0.16f, 0.16f, -0.04f, headY + 0.12f, 0.16f)
        seg(0.04f, headY + 0.12f, 0.16f, 0.16f, headY + 0.16f, 0.16f)

        return p
    }

    // Generate solid paper-white fan triangles for belly and head
    fun stickmanFills(
        px: Float, py: Float, pz: Float, s: Float, facing: Float, state: Int, deadT: Float, deadDir: Float,
        putVertex: (Float, Float, Float) -> Unit
    ) {
        val tilt = if (state == 6) kotlin.math.min(1.5f, deadT * 3f) * deadDir else 0f

        // Belly fan (y=1.02, z=0.02, r=0.44)
        val bellyR = 0.44f
        val bsegs = 12
        var cx = 0f; var cy = 0f; var cz = 0f
        transformPoint(0f, 1.02f, 0.02f, s, facing, tilt, px, py, pz) { x, y, z -> cx = x; cy = y; cz = z }

        for (k in 0 until bsegs) {
            val a0 = (k.toFloat() / bsegs) * 2f * PI.toFloat()
            val a1 = ((k + 1).toFloat() / bsegs) * 2f * PI.toFloat()
            var x0 = 0f; var y0 = 0f; var z0 = 0f
            var x1 = 0f; var y1 = 0f; var z1 = 0f
            transformPoint(cos(a0) * bellyR, 1.02f + sin(a0) * bellyR, 0.02f, s, facing, tilt, px, py, pz) { x, y, z -> x0 = x; y0 = y; z0 = z }
            transformPoint(cos(a1) * bellyR, 1.02f + sin(a1) * bellyR, 0.02f, s, facing, tilt, px, py, pz) { x, y, z -> x1 = x; y1 = y; z1 = z }
            putVertex(cx, cy, cz)
            putVertex(x0, y0, z0)
            putVertex(x1, y1, z1)
        }

        // Head fan (y=1.62, z=-0.02, r=0.25)
        val headR = 0.25f
        val hsegs = 10
        transformPoint(0f, 1.62f, -0.02f, s, facing, tilt, px, py, pz) { x, y, z -> cx = x; cy = y; cz = z }
        for (k in 0 until hsegs) {
            val a0 = (k.toFloat() / hsegs) * 2f * PI.toFloat()
            val a1 = ((k + 1).toFloat() / hsegs) * 2f * PI.toFloat()
            var x0 = 0f; var y0 = 0f; var z0 = 0f
            var x1 = 0f; var y1 = 0f; var z1 = 0f
            transformPoint(cos(a0) * headR, 1.62f + sin(a0) * headR, -0.02f, s, facing, tilt, px, py, pz) { x, y, z -> x0 = x; y0 = y; z0 = z }
            transformPoint(cos(a1) * headR, 1.62f + sin(a1) * headR, -0.02f, s, facing, tilt, px, py, pz) { x, y, z -> x1 = x; y1 = y; z1 = z }
            putVertex(cx, cy, cz)
            putVertex(x0, y0, z0)
            putVertex(x1, y1, z1)
        }
    }
}

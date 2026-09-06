package com.igabr13l.papercut.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Software renderer: draws the same doodle world as the web version — paper fills
 * with per-face shading, jittered ink edges, notebook rules — projected with Canvas 2D.
 * No OpenGL needed, so it also runs on emulators whose GL layer is broken.
 */
class SoftView(context: Context, val game: Game, val onFrame: () -> Unit) :
    SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var running = false
    private var lastFrame = System.nanoTime()

    val projM = FloatArray(16)
    val viewM = FloatArray(16)
    val vpM = FloatArray(16)
    val camPos = Vec3()

    // static world line soup (jittered box edges + notebook rules)
    var worldLines: FloatArray = FloatArray(0)
    var worldLineCount = 0
    var ruleLines: FloatArray = FloatArray(0)
    var ruleCount = 0
    var skyLines: FloatArray = FloatArray(0)
    var skyCount = 0

    // box faces for painter's-algorithm fills
    class BoxFaces(val corners: FloatArray, val faces: IntArray, val normals: FloatArray, val centers: FloatArray, val shades: IntArray)
    var boxes: List<BoxFaces> = emptyList()

    class DrawItem(val depth: Float, val xs: FloatArray, val ys: FloatArray, val color: Int, val isLine: Boolean)
    var drawItems = ArrayList<DrawItem>(512)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    var linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    val dynArr = FloatArray(80000)

    val paperPaint = Paint().apply { color = Color.rgb(244, 241, 230) }

    init {
        holder.addCallback(this)
    }

    fun buildStatic() {
        // ink edges (slightly inflated, jittered)
        val segs = ArrayList<Float>(1 shl 16)
        val scratch = FloatArray(4096)
        val boxesTmp = ArrayList<BoxFaces>()
        for (c in game.colliders) {
            val w = c.max.x - c.min.x; val h = c.max.y - c.min.y; val d = c.max.z - c.min.z
            val cx = (c.max.x + c.min.x) / 2f; val cy = (c.max.y + c.min.y) / 2f; val cz = (c.max.z + c.min.z) / 2f
            val n = Sketch.boxEdges(cx, cy, cz, w, h, d, scratch, 0)
            for (i in 0 until n) segs.add(scratch[i])
            // face fill data
            val x0 = c.min.x; val x1 = c.max.x
            val y0 = c.min.y; val y1 = c.max.y
            val z0 = c.min.z; val z1 = c.max.z
            val corners = floatArrayOf(
                x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, // 0..3  -Z face
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, // 4..7  +Z face
            )
            // face = 4 corner indices (CCW seen from outside not needed; we cull via normal)
            val faces = intArrayOf(
                1, 5, 6, 2,    // +X
                4, 0, 3, 7,    // -X
                3, 2, 6, 7,    // +Y (top)
                0, 1, 5, 4,    // -Y (bottom)
                5, 4, 7, 6,    // +Z
                0, 3, 2, 1,    // -Z
            )
            val normals = floatArrayOf(
                1f, 0f, 0f, -1f, 0f, 0f,
                0f, 1f, 0f, 0f, -1f, 0f,
                0f, 0f, 1f, 0f, 0f, -1f,
            )
            val centers = floatArrayOf(
                x1, cy, cz, x0, cy, cz,
                cx, y1, cz, cx, y0, cz,
                cx, cy, z1, cx, cy, z0,
            )
            val shades = intArrayOf(
                Color.rgb(228, 223, 205), Color.rgb(228, 223, 205), // sides ±X
                Color.rgb(251, 248, 240), Color.rgb(215, 210, 196), // top / bottom
                Color.rgb(241, 237, 223), Color.rgb(241, 237, 223), // ±Z
            )
            boxesTmp.add(BoxFaces(corners, faces, normals, centers, shades))
        }
        worldLines = segs.toFloatArray()
        worldLineCount = worldLines.size / 3
        boxes = boxesTmp

        // notebook ruled lines on the ground
        val rules = ArrayList<Float>(4096)
        var z = -46f
        while (z <= 46f) {
            rules.add(-46f); rules.add(0.012f); rules.add(z)
            rules.add(46f); rules.add(0.012f); rules.add(z)
            z += 2.3f
        }
        ruleLines = rules.toFloatArray()
        ruleCount = ruleLines.size / 3

        // sky: sun doodle + clouds (outlines only, like the web page)
        val sky = ArrayList<Float>(2048)
        val sx = -30f; val sy = 34f; val sz = -46f
        val circ = Sketch.circlePairs(3f, 14, 0.08f)
        var i = 0
        while (i < circ.size) { sky.add(sx + circ[i]); sky.add(sy + circ[i + 1]); sky.add(sz + circ[i + 2]); i += 3 }
        // sun rays
        for (r in 0 until 8) {
            val a = (r / 8.0) * 2.0 * Math.PI
            val c0 = cos(a).toFloat(); val s0 = sin(a).toFloat()
            sky.add(sx + c0 * 3.6f); sky.add(sy + s0 * 3.6f); sky.add(sz)
            sky.add(sx + c0 * 4.6f); sky.add(sy + s0 * 4.6f); sky.add(sz)
        }
        for (cc in listOf(floatArrayOf(20f, 30f, -46f), floatArrayOf(40f, 26f, -45f), floatArrayOf(-8f, 27f, -46f))) {
            for (off in listOf(floatArrayOf(-1.5f, 0f, 1.5f), floatArrayOf(0.4f, 0.35f, 2f), floatArrayOf(2.2f, 0f, 1.3f))) {
                val c2 = Sketch.circlePairs(off[2], 11, 0.08f, cc[0] + off[0], cc[1] + off[1], cc[2])
                i = 0
                while (i < c2.size) { sky.add(c2[i]); sky.add(c2[i + 1]); sky.add(c2[i + 2]); i += 3 }
            }
        }
        skyLines = sky.toFloatArray()
        skyCount = skyLines.size / 3
    }

    // near-plane clip in clip space, then project to screen. writes 4 floats or null
    val clipA = FloatArray(4); val clipB = FloatArray(4)
    fun segToScreen(m: FloatArray, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, out: FloatArray, W: Float, H: Float): Boolean {
        Mat4.transform(m, ax, ay, az, clipA)
        Mat4.transform(m, bx, by, bz, clipB)
        val NEAR = 0.02f
        var x0 = clipA[0]; var y0 = clipA[1]; var w0 = clipA[3]
        var x1 = clipB[0]; var y1 = clipB[1]; var w1 = clipB[3]
        if (w0 < NEAR && w1 < NEAR) return false
        if (w0 < NEAR) {
            val t = (NEAR - w0) / (w1 - w0)
            x0 += (x1 - x0) * t; y0 += (y1 - y0) * t; w0 = NEAR
        } else if (w1 < NEAR) {
            val t = (NEAR - w1) / (w0 - w1)
            x1 += (x0 - x1) * t; y1 += (y0 - y1) * t; w1 = NEAR
        }
        out[0] = (x0 / w0 * 0.5f + 0.5f) * W
        out[1] = (0.5f - y0 / w0 * 0.5f) * H
        out[2] = (x1 / w1 * 0.5f + 0.5f) * W
        out[3] = (0.5f - y1 / w1 * 0.5f) * H
        return true
    }

    fun pointToScreen(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray, W: Float, H: Float): Float {
        Mat4.transform(m, x, y, z, clipA)
        if (clipA[3] < 0.02f) return -1f
        out[0] = (clipA[0] / clipA[3] * 0.5f + 0.5f) * W
        out[1] = (0.5f - clipA[1] / clipA[3] * 0.5f) * H
        return clipA[3]
    }

    fun drawBatch(canvas: Canvas, m: FloatArray, arr: FloatArray, count: Int, paint: Paint, W: Float, H: Float) {
        val s = FloatArray(4)
        var i = 0
        while (i < count) {
            if (segToScreen(m, arr[i], arr[i + 1], arr[i + 2], arr[i + 3], arr[i + 4], arr[i + 5], s, W, H)) {
                canvas.drawLine(s[0], s[1], s[2], s[3], paint)
            }
            i += 6
        }
    }

    // Sutherland–Hodgman clip of a convex polygon against clip-space w >= NEAR, then project
    val polyIn = FloatArray(64); val polyOut = FloatArray(64)
    val scrX = FloatArray(16); val scrY = FloatArray(16)
    fun polyToPath(m: FloatArray, verts: FloatArray, nVerts: Int, path: Path, W: Float, H: Float): Boolean {
        val NEAR = 0.02f
        var nIn = 0
        for (i in 0 until nVerts) {
            Mat4.transform(m, verts[i * 3], verts[i * 3 + 1], verts[i * 3 + 2], clipA)
            polyIn[nIn++] = clipA[0]; polyIn[nIn++] = clipA[1]; polyIn[nIn++] = clipA[2]; polyIn[nIn++] = clipA[3]
        }
        var n = nIn
        var inArr = polyIn; var outArr = polyOut
        // clip against each of 1 plane (w = NEAR); single stage: in→out
        var nOut = 0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val wi = inArr[i * 4 + 3]; val wj = inArr[j * 4 + 3]
            val inI = wi >= NEAR; val inJ = wj >= NEAR
            if (inI) {
                outArr[nOut * 4] = inArr[i * 4]; outArr[nOut * 4 + 1] = inArr[i * 4 + 1]
                outArr[nOut * 4 + 2] = inArr[i * 4 + 2]; outArr[nOut * 4 + 3] = wi; nOut++
            }
            if (inI != inJ) {
                val t = (NEAR - wi) / (wj - wi)
                outArr[nOut * 4] = inArr[i * 4] + (inArr[j * 4] - inArr[i * 4]) * t
                outArr[nOut * 4 + 1] = inArr[i * 4 + 1] + (inArr[j * 4 + 1] - inArr[i * 4 + 1]) * t
                outArr[nOut * 4 + 2] = 0f; outArr[nOut * 4 + 3] = NEAR; nOut++
            }
        }
        if (nOut < 3) return false
        path.reset()
        for (i in 0 until nOut) {
            val x = (outArr[i * 4] / outArr[i * 4 + 3] * 0.5f + 0.5f) * W
            val y = (0.5f - outArr[i * 4 + 1] / outArr[i * 4 + 3] * 0.5f) * H
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return true
    }

    fun cameraVP(target: FloatArray) {
        val P = game.player
        val eyeY = P.pos.y + 1.6f + sin(game.bobT * 2f) * 0.02f
        val shakeA = game.shake * game.shake * 0.14f
        val ex = P.pos.x + (Math.random().toFloat() - 0.5f) * shakeA
        val ey = eyeY + (Math.random().toFloat() - 0.5f) * shakeA
        val ez = P.pos.z + (Math.random().toFloat() - 0.5f) * shakeA
        camPos.set(ex, ey, ez)
        Mat4.perspective(projM, game.fovCur * (Math.PI.toFloat() / 180f), width.toFloat() / height.toFloat(), 0.05f, 400f)
        if (game.phase == 0) {
            val eye = game.menuEye
            camPos.set(eye)
            val dx = 0f - eye.x; val dy = 5f - eye.y; val dz = 0f - eye.z
            val yaw = kotlin.math.atan2(-dx, -dz)
            val pitch = kotlin.math.atan2(dy, hypot(dx.toDouble(), dz.toDouble()).toFloat())
            Mat4.fpsView(viewM, eye, yaw, pitch)
            Mat4.perspective(projM, 60f * (Math.PI.toFloat() / 180f), width.toFloat() / height.toFloat(), 0.05f, 400f)
        } else {
            Mat4.fpsView(viewM, Vec3(ex, ey, ez), P.yaw, P.pitch + game.kick * 0.045f)
        }
        Mat4.mul(target, projM, viewM)
        System.arraycopy(target, 0, game.lastVP, 0, 16)
    }

    fun render(canvas: Canvas) {
        val W = canvas.width.toFloat(); val H = canvas.height.toFloat()
        val dt = kotlin.math.min(0.05f, (System.nanoTime() - lastFrame) / 1_000_000_000f)
        lastFrame = System.nanoTime()
        if (game.phase == 1) { game.time += dt; game.update(dt) } else if (game.phase == 0) game.menuCamera(dt)
        onFrame()

        cameraVP(vpM)
        canvas.drawRect(0f, 0f, W, H, paperPaint)

        val lw = (W / 480f).coerceIn(1.5f, 4f)
        val items = drawItems
        items.clear()
        val path = Path()
        val fverts = FloatArray(12)

        // sky (farthest)
        linePaint.color = Color.argb(150, 46, 61, 168)
        linePaint.strokeWidth = lw * 0.8f
        drawBatch(canvas, vpM, skyLines, skyCount * 3, linePaint, W, H)

        // collect box faces with depth
        for (box in boxes) {
            for (f in 0 until 6) {
                val nx = box.normals[f * 3]; val ny = box.normals[f * 3 + 1]; val nz = box.normals[f * 3 + 2]
                val ccx = box.centers[f * 3]; val ccy = box.centers[f * 3 + 1]; val ccz = box.centers[f * 3 + 2]
                if (nx * (camPos.x - ccx) + ny * (camPos.y - ccy) + nz * (camPos.z - ccz) <= 0) continue
                for (k in 0 until 4) {
                    val vi = box.faces[f * 4 + k] * 3
                    fverts[k * 3] = box.corners[vi]; fverts[k * 3 + 1] = box.corners[vi + 1]; fverts[k * 3 + 2] = box.corners[vi + 2]
                }
                val depth = (ccx - camPos.x) * (ccx - camPos.x) + (ccy - camPos.y) * (ccy - camPos.y) + (ccz - camPos.z) * (ccz - camPos.z)
                val pv = FloatArray(8)
                var ok = true
                for (k in 0 until 4) {
                    val w = pointToScreen(vpM, fverts[k * 3], fverts[k * 3 + 1], fverts[k * 3 + 2], clipB, W, H)
                    if (w < 0) { ok = false; break }
                    pv[k * 2] = clipB[0]; pv[k * 2 + 1] = clipB[1]
                }
                if (!ok) continue
                items.add(DrawItem(depth, pv, FloatArray(0), box.shades[f], false))
            }
        }
        // ground rules as depth-sorted line items
        for (i in 0 until ruleCount * 3 step 6) {
            val mx = (ruleLines[i] + ruleLines[i + 3]) / 2f; val mz = (ruleLines[i + 2] + ruleLines[i + 5]) / 2f
            val my = 0.012f
            val depth = (mx - camPos.x) * (mx - camPos.x) + (my - camPos.y) * (my - camPos.y) + (mz - camPos.z) * (mz - camPos.z)
            val s = FloatArray(4)
            if (segToScreen(vpM, ruleLines[i], ruleLines[i + 1], ruleLines[i + 2], ruleLines[i + 3], ruleLines[i + 4], ruleLines[i + 5], s, W, H)) {
                items.add(DrawItem(depth, floatArrayOf(s[0], s[2]), floatArrayOf(s[1], s[3]), Color.argb(120, 158, 173, 222), true))
            }
        }
        items.sortByDescending { it.depth }

        // paint fills far→near; rules are lines drawn in order with them
        for (it2 in items) {
            if (it2.isLine) {
                linePaint.color = it2.color
                linePaint.strokeWidth = 1.2f
                canvas.drawLine(it2.xs[0], it2.ys[0], it2.xs[1], it2.ys[1], linePaint)
            } else {
                fillPaint.color = it2.color
                path.reset()
                path.moveTo(it2.xs[0], it2.xs[1])
                for (k in 1 until 4) path.lineTo(it2.xs[k * 2], it2.xs[k * 2 + 1])
                path.close()
                canvas.drawPath(path, fillPaint)
            }
        }

        // ink edges on top
        linePaint.color = Color.argb(235, 46, 61, 168)
        linePaint.strokeWidth = lw
        drawBatch(canvas, vpM, worldLines, worldLineCount * 3, linePaint, W, H)

        val arr = dynArr
        // barrels
        var np = 0
        for (b in game.barrels) {
            if (!b.alive) continue
            np = Sketch.boxEdges(b.pos.x, b.pos.y, b.pos.z, 0.9f, 1.1f, 0.9f, arr, np)
        }
        linePaint.color = Color.rgb(217, 126, 16)
        if (np > 0) drawBatch(canvas, vpM, arr, np / 3, linePaint, W, H)

        // enemies
        np = 0
        for (e in game.enemies) {
            np = Sketch.stickman(e.pos.x, e.pos.y, e.pos.z, e.scale, e.facing, e.state, e.walkPhase, e.deadT, if (e.phase > 4.5f) 1f else -1f, arr, np)
        }
        if (np > 0) {
            linePaint.color = Color.argb(255, 200, 40, 60)
            linePaint.strokeWidth = lw * 1.3f
            drawBatch(canvas, vpM, arr, np / 3, linePaint, W, H)
            var fp = 0
            val flashArr = FloatArray(np)
            for (e in game.enemies) {
                if (e.dead() || e.flash <= 0.05f) continue
                fp = Sketch.stickman(e.pos.x, e.pos.y, e.pos.z, e.scale, e.facing, e.state, e.walkPhase, e.deadT, 1f, flashArr, fp)
            }
            if (fp > 0) {
                linePaint.color = Color.argb((game.enemies.maxOf { it.flash } * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                linePaint.strokeWidth = lw * 1.7f
                drawBatch(canvas, vpM, flashArr, fp / 3, linePaint, W, H)
            }
        }
        linePaint.strokeWidth = lw

        // rope
        game.grapplePoint?.let { gp ->
            val eye = game.eyePos()
            val s = FloatArray(4)
            if (segToScreen(vpM, eye.x, eye.y, eye.z, gp.x, gp.y, gp.z, s, W, H)) {
                linePaint.color = Color.rgb(32, 43, 125)
                canvas.drawLine(s[0], s[1], s[2], s[3], linePaint)
                val mark = Sketch.cross(gp.x, gp.y, gp.z, 0.4f)
                linePaint.color = Color.rgb(217, 126, 16)
                drawBatch(canvas, vpM, mark, 6, linePaint, W, H)
            }
        }

        // projectiles + particles
        val p3 = FloatArray(3)
        for (p in game.projs) {
            val w = pointToScreen(vpM, p.pos.x, p.pos.y, p.pos.z, p3, W, H)
            if (w > 0) {
                val r = (0.14f / w * H).coerceIn(2f, 26f)
                fillPaint.color = if (p.deflected) Color.rgb(61, 138, 75) else Color.rgb(185, 194, 230)
                canvas.drawCircle(p3[0], p3[1], r, fillPaint)
            }
        }
        for (p in game.parts) {
            val w = pointToScreen(vpM, p.obj.x, p.obj.y, p.obj.z, p3, W, H)
            if (w > 0) {
                val r = (p.size / w * H * 0.7f).coerceIn(1.2f, 30f)
                fillPaint.color = Color.argb(
                    ((p.life / p.max * 230).toInt()).coerceIn(0, 255),
                    (p.color[0] * 255).toInt(), (p.color[1] * 255).toInt(), (p.color[2] * 255).toInt(),
                )
                canvas.drawCircle(p3[0], p3[1], r, fillPaint)
            }
        }

        // viewmodel + slash arc (camera space: vp = projection only)
        if (game.phase == 1 && !game.isZooming()) drawViewmodel(canvas, W, H)
    }

    fun drawViewmodel(canvas: Canvas, W: Float, H: Float) {
        val bobX = cos(game.bobT) * 0.008f
        val bobY = sin(game.bobT * 2f) * 0.012f
        val reload = game.weapons[game.wIdx]
        val drop = if (reload.reloadT > 0 && reload.def.reload > 0) reload.reloadT / reload.def.reload * 0.25f else 0f
        val baseX = 0.27f + bobX + game.kick * 0.02f
        val baseY = -0.25f + bobY - drop + game.kick * 0.05f
        val z0 = -0.42f
        val lines = ArrayList<Float>(64)
        fun seg(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
            lines.add(baseX + ax); lines.add(baseY + ay); lines.add(z0 + az)
            lines.add(baseX + bx); lines.add(baseY + by); lines.add(z0 + bz)
        }
        val isKatana = game.wIdx == 4
        if (isKatana) {
            seg(0.05f, 0.05f, -0.1f, -0.12f, 0.28f, -1.1f)
            seg(-0.02f, -0.05f, -0.05f, 0.05f, 0.05f, -0.1f)
            seg(0f, -0.15f, 0.05f, 0f, -0.02f, 0f)
        } else {
            val len = when (game.wIdx) { 1 -> 0.9f; 3 -> 1.1f; else -> 0.55f }
            seg(0f, 0f, -0.1f, 0f, 0.02f, -0.1f - len)
            seg(-0.045f, -0.12f, -0.05f, 0.045f, -0.12f, -0.05f)
            seg(-0.045f, -0.12f, -0.05f, -0.045f, 0f, -0.05f)
            seg(0.045f, -0.12f, -0.05f, 0.045f, 0f, -0.05f)
            seg(-0.05f, 0.02f, -0.2f, 0.05f, 0.02f, -0.2f)
        }
        val a = lines.toFloatArray()
        linePaint.color = Color.argb(255, 46, 61, 168)
        linePaint.strokeWidth = (W / 300f).coerceIn(2f, 5f)
        drawBatch(canvas, projM, a, a.size / 3, linePaint, W, H)
        if (game.kick > 0.75f && !isKatana) {
            val fl = floatArrayOf(
                baseX - 0.12f, baseY + 0.02f, z0 - 0.7f, baseX + 0.12f, baseY + 0.02f, z0 - 0.7f,
                baseX, baseY - 0.1f, z0 - 0.7f, baseX, baseY + 0.14f, z0 - 0.7f,
            )
            linePaint.color = Color.rgb(240, 166, 60)
            drawBatch(canvas, projM, fl, 4, linePaint, W, H)
        }
        if (game.slashT > 0) {
            val segs = 10
            val arc = ArrayList<Float>(segs * 12)
            val r0 = 0.5f; val r1 = 0.6f
            for (i in 0 until segs) {
                val a0 = -0.7f + (i.toFloat() / segs) * 1.5f
                val a1 = -0.7f + ((i + 1).toFloat() / segs) * 1.5f
                arc.add(cos(a0) * r0); arc.add(sin(a0) * r0 - 0.1f); arc.add(-1.4f)
                arc.add(cos(a1) * r0); arc.add(sin(a1) * r0 - 0.1f); arc.add(-1.4f)
                arc.add(cos(a0) * r1); arc.add(sin(a0) * r1 - 0.1f); arc.add(-1.4f)
                arc.add(cos(a1) * r1); arc.add(sin(a1) * r1 - 0.1f); arc.add(-1.4f)
            }
            val aa = arc.toFloatArray()
            linePaint.color = Color.argb(220, 217, 64, 77)
            drawBatch(canvas, projM, aa, aa.size / 3, linePaint, W, H)
        }
    }

    /* ------------- surface/thread ------------- */
    override fun run() {
        while (running) {
            val t0 = System.nanoTime()
            val c = holder.lockCanvas()
            if (c != null) {
                try { render(c) } catch (e: Exception) { android.util.Log.e("SOFT", "render fail", e) } finally { try { holder.unlockCanvasAndPost(c) } catch (_: Exception) {} }
            }
            val elapsed = (System.nanoTime() - t0) / 1_000_000L
            if (elapsed < 15) try { Thread.sleep(15 - elapsed) } catch (_: InterruptedException) {}
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (worldLineCount == 0) buildStatic()
        running = true
        thread = Thread(this, "SoftRender")
        thread!!.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        game.screenW = width; game.screenH = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join() } catch (_: InterruptedException) {}
        thread = null
    }
}

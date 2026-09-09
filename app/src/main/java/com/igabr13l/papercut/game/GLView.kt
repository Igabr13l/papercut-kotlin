package com.igabr13l.papercut.game

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardware-accelerated OpenGL ES 2.0 renderer that faithfully replicates the WebGL
 * Three.js visual style of doodle-wave-shooter:
 *  - Solid pastel blue/lavender shaded box faces (with Z-buffer depth testing)
 *  - Ink edge lines with polygon offset so back edges and hidden rooms are naturally occluded
 *  - 3D first-person viewmodels (Rifle with red holo sight, Shotgun, Revolver, Sniper, Katana)
 *  - Opaque stickmen with white paper bellies and heads (no see-through wireframes)
 *  - Colored dynamic entities: orange crane boom, orange barrels, green/yellow pickups
 */
class GLView(context: Context, val game: Game, val onFrame: () -> Unit) :
    GLSurfaceView(context), GLSurfaceView.Renderer {

    private var program = 0
    private var uMVPLoc = 0
    private var aPosLoc = 0
    private var aColLoc = 0

    // Matrices
    private val projM = FloatArray(16)
    private val viewM = FloatArray(16)
    private val vpM = FloatArray(16)
    private val mvpM = FloatArray(16)

    // Static geometry buffers
    private var staticSolidCount = 0
    private var staticSolidVBO = 0
    private var staticLineCount = 0
    private var staticLineVBO = 0

    // Dynamic buffer for frame-by-frame entities
    private val dynSolidFloats = FloatArray(65536 * 7) // pos(3) + col(4)
    private val dynLineFloats = FloatArray(65536 * 7)
    private lateinit var dynSolidBuffer: FloatBuffer
    private lateinit var dynLineBuffer: FloatBuffer
    private val boxScratch = FloatArray(1024)
    private val stickScratch = FloatArray(4096)

    private var lastTime = System.nanoTime()

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    object ColorPalette {
        val paper = floatArrayOf(0.957f, 0.945f, 0.902f, 1f)       // #f4f1e6
        val paper2 = floatArrayOf(0.937f, 0.925f, 0.878f, 1f)      // #efece0
        val paperBelly = floatArrayOf(0.984f, 0.973f, 0.933f, 1f)  // #fbf8ee
        val ink = floatArrayOf(0.180f, 0.239f, 0.659f, 1f)         // #2e3da8
        val inkDeep = floatArrayOf(0.125f, 0.169f, 0.490f, 1f)     // #202b7d
        val shade = floatArrayOf(0.663f, 0.710f, 0.890f, 1f)       // #a9b5e3
        val shadeLight = floatArrayOf(0.800f, 0.827f, 0.937f, 1f)  // #ccd3ef
        val red = floatArrayOf(0.784f, 0.157f, 0.235f, 1f)         // #c8283c
        val redBright = floatArrayOf(0.878f, 0.200f, 0.251f, 1f)   // #e03340
        val orange = floatArrayOf(0.961f, 0.604f, 0.137f, 1f)      // #f59a23
        val green = floatArrayOf(0.310f, 0.682f, 0.373f, 1f)       // #4fae5f
        val yellow = floatArrayOf(0.949f, 0.820f, 0.239f, 1f)      // #f2d13d
        val white = floatArrayOf(1f, 1f, 1f, 1f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vs = """
            uniform mat4 uMVP;
            attribute vec3 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                vColor = aColor;
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val fs = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
            }
        """.trimIndent()

        program = createProgram(vs, fs)
        uMVPLoc = GLES20.glGetUniformLocation(program, "uMVP")
        aPosLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aColLoc = GLES20.glGetAttribLocation(program, "aColor")

        dynSolidBuffer = ByteBuffer.allocateDirect(dynSolidFloats.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        dynLineBuffer = ByteBuffer.allocateDirect(dynLineFloats.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        buildStaticWorld()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        game.screenW = width
        game.screenH = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = minOf(0.05f, (now - lastTime) / 1_000_000_000f)
        lastTime = now

        if (game.phase == 1) {
            game.time += dt
            game.update(dt)
        } else if (game.phase == 0) {
            game.menuCamera(dt)
        }

        onFrame()

        // Background paper color
        GLES20.glClearColor(
            ColorPalette.paper[0], ColorPalette.paper[1], ColorPalette.paper[2], 1f
        )
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(program)

        // Setup camera VP
        setupCameraVP()

        // 1. Draw static world
        drawStaticWorld()

        // 2. Draw dynamic world entities (barrels, pickups, enemies, projs, fx)
        drawDynamicEntities(dt)

        // 3. Draw viewmodel (first person weapon) with cleared depth
        if (game.phase == 1 && !game.isZooming()) {
            drawViewmodel()
        }
    }

    private fun setupCameraVP() {
        val P = game.player
        val aspect = width.toFloat() / maxOf(1f, height.toFloat())

        if (game.phase == 0) {
            val eye = game.menuEye
            val dx = 0f - eye.x; val dy = 5f - eye.y; val dz = 0f - eye.z
            val yaw = kotlin.math.atan2(-dx, -dz)
            val pitch = kotlin.math.atan2(dy, kotlin.math.hypot(dx.toDouble(), dz.toDouble()).toFloat())
            Mat4.perspective(projM, 60f * (Math.PI.toFloat() / 180f), aspect, 0.05f, 400f)
            Mat4.fpsView(viewM, eye, yaw, pitch)
        } else {
            val shakeA = game.shake * game.shake * 0.14f
            val ex = P.pos.x + (Math.random().toFloat() - 0.5f) * shakeA
            val ey = P.pos.y + 1.6f + sin(game.bobT * 2f) * 0.02f + (Math.random().toFloat() - 0.5f) * shakeA
            val ez = P.pos.z + (Math.random().toFloat() - 0.5f) * shakeA

            Mat4.perspective(projM, game.fovCur * (Math.PI.toFloat() / 180f), aspect, 0.05f, 400f)
            Mat4.fpsView(viewM, Vec3(ex, ey, ez), P.yaw, P.pitch + game.kick * 0.045f)
        }
        Mat4.mul(vpM, projM, viewM)
        System.arraycopy(vpM, 0, game.lastVP, 0, 16)
        GLES20.glUniformMatrix4fv(uMVPLoc, 1, false, vpM, 0)
    }

    private fun drawStaticWorld() {
        if (staticSolidCount == 0) return

        // Draw solid box faces with polygon offset so lines sit cleanly on top
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(1.2f, 1.2f)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, staticSolidVBO)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 7 * 4, 0)
        GLES20.glEnableVertexAttribArray(aColLoc)
        GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, staticSolidCount)

        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)

        // Draw ink edge lines (depth tested, so only visible outlines show!)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, staticLineVBO)
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 7 * 4, 0)
        GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 7 * 4, 3 * 4)

        GLES20.glLineWidth(2.2f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, staticLineCount)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawDynamicEntities(dt: Float) {
        var sIdx = 0
        var lIdx = 0

        fun putSolidVert(x: Float, y: Float, z: Float, c: FloatArray) {
            if (sIdx + 7 > dynSolidFloats.size) return
            dynSolidFloats[sIdx++] = x; dynSolidFloats[sIdx++] = y; dynSolidFloats[sIdx++] = z
            dynSolidFloats[sIdx++] = c[0]; dynSolidFloats[sIdx++] = c[1]; dynSolidFloats[sIdx++] = c[2]; dynSolidFloats[sIdx++] = c[3]
        }

        fun putLineVert(x: Float, y: Float, z: Float, c: FloatArray) {
            if (lIdx + 7 > dynLineFloats.size) return
            dynLineFloats[lIdx++] = x; dynLineFloats[lIdx++] = y; dynLineFloats[lIdx++] = z
            dynLineFloats[lIdx++] = c[0]; dynLineFloats[lIdx++] = c[1]; dynLineFloats[lIdx++] = c[2]; dynLineFloats[lIdx++] = c[3]
        }

        fun addSolidBox(cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float, c: FloatArray) {
            val x0 = cx - w / 2; val x1 = cx + w / 2
            val y0 = cy - h / 2; val y1 = cy + h / 2
            val z0 = cz - d / 2; val z1 = cz + d / 2

            fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx2: Float, cy2: Float, cz2: Float, dx: Float, dy: Float, dz: Float) {
                putSolidVert(ax, ay, az, c); putSolidVert(bx, by, bz, c); putSolidVert(cx2, cy2, cz2, c)
                putSolidVert(ax, ay, az, c); putSolidVert(cx2, cy2, cz2, c); putSolidVert(dx, dy, dz, c)
            }
            // 6 faces
            quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1) // +Z
            quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0) // -Z
            quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1) // +X
            quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0) // -X
            quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0) // +Y
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1) // -Y
        }

        fun addBoxOutline(cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float, c: FloatArray) {
            val n = Sketch.boxEdges(cx, cy, cz, w, h, d, boxScratch, 0)
            var i = 0
            while (i < n) {
                putLineVert(boxScratch[i], boxScratch[i + 1], boxScratch[i + 2], c)
                putLineVert(boxScratch[i + 3], boxScratch[i + 4], boxScratch[i + 5], c)
                i += 6
            }
        }

        // 1. Barrels
        for (b in game.barrels) {
            if (!b.alive) continue
            addSolidBox(b.pos.x, b.pos.y, b.pos.z, 0.9f, 1.1f, 0.9f, ColorPalette.orange)
            addSolidBox(b.pos.x, b.pos.y, b.pos.z, 0.92f, 0.22f, 0.92f, ColorPalette.yellow)
            addBoxOutline(b.pos.x, b.pos.y, b.pos.z, 0.9f, 1.1f, 0.9f, ColorPalette.ink)
        }

        // 2. Pickups
        for (p in game.pickups) {
            if (p.kind == 0) { // HP green cross
                addSolidBox(p.obj.x, p.obj.y, p.obj.z, 0.5f, 0.16f, 0.16f, ColorPalette.green)
                addSolidBox(p.obj.x, p.obj.y, p.obj.z, 0.16f, 0.5f, 0.16f, ColorPalette.green)
                addBoxOutline(p.obj.x, p.obj.y, p.obj.z, 0.5f, 0.16f, 0.16f, ColorPalette.ink)
                addBoxOutline(p.obj.x, p.obj.y, p.obj.z, 0.16f, 0.5f, 0.16f, ColorPalette.ink)
            } else { // Ammo yellow crate
                addSolidBox(p.obj.x, p.obj.y, p.obj.z, 0.45f, 0.3f, 0.3f, ColorPalette.yellow)
                addBoxOutline(p.obj.x, p.obj.y, p.obj.z, 0.45f, 0.3f, 0.3f, ColorPalette.ink)
            }
        }

        // 3. Decals (blood/ink puddles on floor and impacts on walls)
        for (d in game.decals) {
            val segs = 8
            val r = d.size
            val up = if (kotlin.math.abs(d.normal.y) > 0.9f) Vec3(0f, 0f, 1f) else Vec3(0f, 1f, 0f)
            val right = Vec3(
                d.normal.y * up.z - d.normal.z * up.y,
                d.normal.z * up.x - d.normal.x * up.z,
                d.normal.x * up.y - d.normal.y * up.x
            ).norm()
            val u = Vec3(
                right.y * d.normal.z - right.z * d.normal.y,
                right.z * d.normal.x - right.x * d.normal.z,
                right.x * d.normal.y - right.y * d.normal.x
            ).norm()

            for (k in 0 until segs) {
                val a0 = (k.toFloat() / segs) * 2f * Math.PI.toFloat()
                val a1 = ((k + 1).toFloat() / segs) * 2f * Math.PI.toFloat()
                val c0 = cos(a0) * r; val s0 = sin(a0) * r
                val c1 = cos(a1) * r; val s1 = sin(a1) * r

                putSolidVert(d.pos.x, d.pos.y, d.pos.z, d.color)
                putSolidVert(d.pos.x + right.x * c0 + u.x * s0, d.pos.y + right.y * c0 + u.y * s0, d.pos.z + right.z * c0 + u.z * s0, d.color)
                putSolidVert(d.pos.x + right.x * c1 + u.x * s1, d.pos.y + right.y * c1 + u.y * s1, d.pos.z + right.z * c1 + u.z * s1, d.color)
            }
        }

        // 4. Stickmen Enemies
        for (e in game.enemies) {
            val color = if (e.kind == 1) ColorPalette.inkDeep else ColorPalette.red
            val deadTilt = if (e.phase > 4.5f) 1f else -1f

            // Solid opaque white paper belly + head circle, perfectly oriented with enemy facing and death tilt
            Sketch.stickmanFills(e.pos.x, e.pos.y, e.pos.z, e.scale, e.facing, e.state, e.deadT, deadTilt) { vx, vy, vz ->
                putSolidVert(vx, vy, vz, ColorPalette.paperBelly)
            }

            // Stickman ink lines (head 3D loops, eyes, mouth, eyebrows, belly outline, animated limbs)
            val numLines = Sketch.stickman(e.pos.x, e.pos.y, e.pos.z, e.scale, e.facing, e.state, e.walkPhase, e.deadT, deadTilt, stickScratch, 0)
            var i = 0
            val lineColor = if (e.flash > 0.05f) ColorPalette.white else color
            while (i < numLines) {
                putLineVert(stickScratch[i], stickScratch[i + 1], stickScratch[i + 2], lineColor)
                putLineVert(stickScratch[i + 3], stickScratch[i + 4], stickScratch[i + 5], lineColor)
                i += 6
            }
        }

        // 5. Projectiles
        for (p in game.projs) {
            val pCol = if (p.deflected) ColorPalette.green else ColorPalette.shadeLight
            addSolidBox(p.pos.x, p.pos.y, p.pos.z, 0.22f, 0.22f, 0.22f, pCol)
            addBoxOutline(p.pos.x, p.pos.y, p.pos.z, 0.22f, 0.22f, 0.22f, ColorPalette.ink)
        }

        // 6. Particles
        for (pt in game.parts) {
            val pCol = floatArrayOf(pt.color[0], pt.color[1], pt.color[2], (pt.life / pt.max).coerceIn(0f, 1f))
            addSolidBox(pt.obj.x, pt.obj.y, pt.obj.z, pt.size, pt.size, pt.size, pCol)
        }

        // 6. Rope
        game.grapplePoint?.let { gp ->
            val eye = game.eyePos()
            putLineVert(eye.x, eye.y, eye.z, ColorPalette.inkDeep)
            putLineVert(gp.x, gp.y, gp.z, ColorPalette.inkDeep)
            val mark = Sketch.cross(gp.x, gp.y, gp.z, 0.4f)
            var i = 0
            while (i < mark.size) {
                putLineVert(mark[i], mark[i + 1], mark[i + 2], ColorPalette.orange)
                putLineVert(mark[i + 3], mark[i + 4], mark[i + 5], ColorPalette.orange)
                i += 6
            }
        }

        // 7. Bullet Tracers
        for (tr in game.tracers) {
            val trCol = if (tr.color == 0xFFD9404D.toInt()) ColorPalette.red else ColorPalette.redBright
            putLineVert(tr.from.x, tr.from.y, tr.from.z, trCol)
            putLineVert(tr.to.x, tr.to.y, tr.to.z, trCol)
        }

        // Upload and draw dynamic buffers
        val solidVertCount = sIdx / 7
        if (solidVertCount > 0) {
            dynSolidBuffer.position(0)
            dynSolidBuffer.put(dynSolidFloats, 0, sIdx)

            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
            GLES20.glPolygonOffset(1f, 1f)

            GLES20.glEnableVertexAttribArray(aPosLoc)
            dynSolidBuffer.position(0)
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 7 * 4, dynSolidBuffer)
            GLES20.glEnableVertexAttribArray(aColLoc)
            dynSolidBuffer.position(3)
            GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 7 * 4, dynSolidBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, solidVertCount)
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        }

        val lineVertCount = lIdx / 7
        if (lineVertCount > 0) {
            dynLineBuffer.position(0)
            dynLineBuffer.put(dynLineFloats, 0, lIdx)

            GLES20.glEnableVertexAttribArray(aPosLoc)
            dynLineBuffer.position(0)
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 7 * 4, dynLineBuffer)
            GLES20.glEnableVertexAttribArray(aColLoc)
            dynLineBuffer.position(3)
            GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 7 * 4, dynLineBuffer)

            GLES20.glLineWidth(2.4f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineVertCount)
        }
    }

    private fun drawViewmodel() {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)

        // Viewmodel camera: projection matrix only (model is in camera space)
        Mat4.perspective(mvpM, 70f * (Math.PI.toFloat() / 180f), width.toFloat() / maxOf(1f, height.toFloat()), 0.01f, 10f)
        GLES20.glUniformMatrix4fv(uMVPLoc, 1, false, mvpM, 0)

        val bobX = cos(game.bobT) * 0.008f
        val bobY = sin(game.bobT * 2f) * 0.012f
        val reload = game.weapons[game.wIdx]
        val drop = if (reload.reloadT > 0 && reload.def.reload > 0) reload.reloadT / reload.def.reload * 0.25f else 0f
        val baseX = 0.27f + bobX + game.kick * 0.02f
        val baseY = -0.25f + bobY - drop + game.kick * 0.05f
        val baseZ = -0.42f

        var sIdx = 0
        var lIdx = 0

        fun putSolidVert(x: Float, y: Float, z: Float, c: FloatArray) {
            if (sIdx + 7 > dynSolidFloats.size) return
            dynSolidFloats[sIdx++] = x; dynSolidFloats[sIdx++] = y; dynSolidFloats[sIdx++] = z
            dynSolidFloats[sIdx++] = c[0]; dynSolidFloats[sIdx++] = c[1]; dynSolidFloats[sIdx++] = c[2]; dynSolidFloats[sIdx++] = c[3]
        }

        fun putLineVert(x: Float, y: Float, z: Float, c: FloatArray) {
            if (lIdx + 7 > dynLineFloats.size) return
            dynLineFloats[lIdx++] = x; dynLineFloats[lIdx++] = y; dynLineFloats[lIdx++] = z
            dynLineFloats[lIdx++] = c[0]; dynLineFloats[lIdx++] = c[1]; dynLineFloats[lIdx++] = c[2]; dynLineFloats[lIdx++] = c[3]
        }

        fun addVmBox(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: FloatArray = ColorPalette.shadeLight) {
            val cx = baseX + x; val cy = baseY + y; val cz = baseZ + z
            val x0 = cx - w / 2; val x1 = cx + w / 2
            val y0 = cy - h / 2; val y1 = cy + h / 2
            val z0 = cz - d / 2; val z1 = cz + d / 2

            fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx2: Float, cy2: Float, cz2: Float, dx: Float, dy: Float, dz: Float) {
                putSolidVert(ax, ay, az, color); putSolidVert(bx, by, bz, color); putSolidVert(cx2, cy2, cz2, color)
                putSolidVert(ax, ay, az, color); putSolidVert(cx2, cy2, cz2, color); putSolidVert(dx, dy, dz, color)
            }
            quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1)
            quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0)
            quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1)
            quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0)
            quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0)
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1)

            // Outline edges
            fun line(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
                putLineVert(ax, ay, az, ColorPalette.ink); putLineVert(bx, by, bz, ColorPalette.ink)
            }
            line(x0, y0, z0, x1, y0, z0); line(x1, y0, z0, x1, y1, z0); line(x1, y1, z0, x0, y1, z0); line(x0, y1, z0, x0, y0, z0)
            line(x0, y0, z1, x1, y0, z1); line(x1, y0, z1, x1, y1, z1); line(x1, y1, z1, x0, y1, z1); line(x0, y1, z1, x0, y0, z1)
            line(x0, y0, z0, x0, y0, z1); line(x1, y0, z0, x1, y0, z1); line(x1, y1, z0, x1, y1, z1); line(x0, y1, z0, x0, y1, z1)
        }

        fun addHand(x: Float, y: Float, z: Float, rx: Float = 0.3f) {
            val r = 0.052f
            val h = 0.17f
            val segs = 8
            val cx = baseX + x; val cy = baseY + y; val cz = baseZ + z
            val cosR = cos(rx); val sinR = sin(rx)

            fun rot(lx: Float, ly: Float, lz: Float): FloatArray {
                return floatArrayOf(lx, ly * cosR - lz * sinR, ly * sinR + lz * cosR)
            }

            for (k in 0 until segs) {
                val a0 = (k.toFloat() / segs) * 2f * Math.PI.toFloat()
                val a1 = ((k + 1).toFloat() / segs) * 2f * Math.PI.toFloat()
                val x0 = cos(a0) * r; val z0 = sin(a0) * r
                val x1 = cos(a1) * r; val z1 = sin(a1) * r

                val p00 = rot(x0, -h / 2f, z0)
                val p01 = rot(x0, h / 2f, z0)
                val p10 = rot(x1, -h / 2f, z1)
                val p11 = rot(x1, h / 2f, z1)

                val col = ColorPalette.paperBelly
                putSolidVert(cx + p00[0], cy + p00[1], cz + p00[2], col)
                putSolidVert(cx + p10[0], cy + p10[1], cz + p10[2], col)
                putSolidVert(cx + p11[0], cy + p11[1], cz + p11[2], col)

                putSolidVert(cx + p00[0], cy + p00[1], cz + p00[2], col)
                putSolidVert(cx + p11[0], cy + p11[1], cz + p11[2], col)
                putSolidVert(cx + p01[0], cy + p01[1], cz + p01[2], col)

                putLineVert(cx + p00[0], cy + p00[1], cz + p00[2], ColorPalette.ink)
                putLineVert(cx + p01[0], cy + p01[1], cz + p01[2], ColorPalette.ink)
                putLineVert(cx + p00[0], cy + p00[1], cz + p00[2], ColorPalette.ink)
                putLineVert(cx + p10[0], cy + p10[1], cz + p10[2], ColorPalette.ink)
            }
        }

        when (game.wIdx) {
            0 -> { // Rifle with red holographic sight & hands
                addVmBox(0f, 0f, -0.1f, 0.09f, 0.11f, 0.55f, ColorPalette.shadeLight)
                addVmBox(0f, 0.02f, -0.55f, 0.045f, 0.045f, 0.5f, ColorPalette.shade)
                addVmBox(0f, -0.15f, 0f, 0.06f, 0.22f, 0.09f, ColorPalette.inkDeep)
                addVmBox(0f, -0.03f, 0.3f, 0.07f, 0.1f, 0.26f, ColorPalette.shadeLight)
                // Sight mount
                addVmBox(0f, 0.11f, -0.14f, 0.02f, 0.06f, 0.02f, ColorPalette.ink)
                // Red Holo sight square
                val s = 0.062f
                val hx = baseX; val hy = baseY + 0.16f; val hz = baseZ - 0.14f
                putLineVert(hx - s, hy - s, hz, ColorPalette.red); putLineVert(hx + s, hy - s, hz, ColorPalette.red)
                putLineVert(hx + s, hy - s, hz, ColorPalette.red); putLineVert(hx + s, hy + s, hz, ColorPalette.red)
                putLineVert(hx + s, hy + s, hz, ColorPalette.red); putLineVert(hx - s, hy + s, hz, ColorPalette.red)
                putLineVert(hx - s, hy + s, hz, ColorPalette.red); putLineVert(hx - s, hy - s, hz, ColorPalette.red)
                // Red concentric circle reticle
                for (k in 0 until 8) {
                    val a0 = (k.toFloat() / 8) * 2f * Math.PI.toFloat()
                    val a1 = ((k + 1).toFloat() / 8) * 2f * Math.PI.toFloat()
                    putLineVert(hx + cos(a0) * 0.034f, hy + sin(a0) * 0.034f, hz, ColorPalette.red)
                    putLineVert(hx + cos(a1) * 0.034f, hy + sin(a1) * 0.034f, hz, ColorPalette.red)
                }
                // Solid center red dot
                addVmBox(0f, 0.16f, -0.139f, 0.012f, 0.012f, 0.005f, ColorPalette.redBright)
                // Player hands
                addHand(0f, -0.11f, -0.3f, 0.3f)
                addHand(0f, -0.16f, 0.08f, 0.5f)
            }
            1 -> { // Shotgun & hands
                addVmBox(0f, 0f, -0.05f, 0.08f, 0.1f, 0.42f, ColorPalette.shadeLight)
                addVmBox(0f, 0.03f, -0.6f, 0.05f, 0.05f, 0.9f, ColorPalette.shade)
                addVmBox(0f, -0.03f, -0.62f, 0.075f, 0.075f, 0.24f, ColorPalette.inkDeep)
                addVmBox(0f, -0.05f, 0.28f, 0.07f, 0.11f, 0.28f, ColorPalette.shadeLight)
                addHand(0f, -0.11f, -0.62f, 0.2f)
                addHand(0f, -0.14f, 0.2f, 0.5f)
            }
            2 -> { // Revolver & hand
                addVmBox(0f, 0f, 0f, 0.06f, 0.08f, 0.2f, ColorPalette.shadeLight)
                addVmBox(0f, 0.02f, -0.3f, 0.035f, 0.035f, 0.42f, ColorPalette.shade)
                addVmBox(0f, 0f, -0.06f, 0.08f, 0.08f, 0.12f, ColorPalette.inkDeep)
                addVmBox(0f, -0.11f, 0.1f, 0.05f, 0.16f, 0.07f, ColorPalette.shadeLight)
                addHand(0f, -0.11f, 0.08f, 0.4f)
            }
            3 -> { // Sniper & hands
                addVmBox(0f, 0f, -0.15f, 0.07f, 0.09f, 0.7f, ColorPalette.shadeLight)
                addVmBox(0f, 0.02f, -0.9f, 0.032f, 0.032f, 1.1f, ColorPalette.shade)
                addVmBox(0f, 0.11f, -0.2f, 0.055f, 0.055f, 0.34f, ColorPalette.inkDeep)
                addVmBox(0f, -0.04f, 0.32f, 0.06f, 0.1f, 0.3f, ColorPalette.shadeLight)
                addHand(0f, -0.11f, -0.45f, 0.25f)
                addHand(0f, -0.13f, 0.24f, 0.5f)
            }
            4 -> { // Katana & hand
                addVmBox(0f, 0.02f, -0.62f, 0.016f, 0.05f, 1.15f, ColorPalette.paperBelly)
                addVmBox(0f, 0f, -0.06f, 0.13f, 0.025f, 0.06f, ColorPalette.inkDeep)
                addVmBox(0f, -0.01f, 0.1f, 0.035f, 0.035f, 0.3f, ColorPalette.shade)
                addHand(0.02f, -0.06f, 0.08f, 0.4f)
            }
        }

        // Dual-plane rotated muzzle flash
        if (game.kick > 0.45f && game.wIdx != 4) {
            val (mx, my, mz) = when (game.wIdx) {
                0 -> Triple(0f, 0.02f, -0.82f)
                1 -> Triple(0f, 0.03f, -1.06f)
                2 -> Triple(0f, 0.02f, -0.52f)
                3 -> Triple(0f, 0.02f, -1.46f)
                else -> Triple(0f, 0f, 0f)
            }
            val fx = baseX + mx; val fy = baseY + my; val fz = baseZ + mz
            val rot1 = (game.time * 43f) % (2f * Math.PI.toFloat())
            val w1 = 0.38f; val h1 = 0.24f
            val c1 = cos(rot1); val s1 = sin(rot1)
            fun fv(ox: Float, oy: Float, col: FloatArray) {
                val rx = ox * c1 - oy * s1
                val ry = ox * s1 + oy * c1
                putSolidVert(fx + rx, fy + ry, fz, col)
            }
            fv(-w1/2, -h1/2, ColorPalette.orange); fv(w1/2, -h1/2, ColorPalette.orange); fv(w1/2, h1/2, ColorPalette.orange)
            fv(-w1/2, -h1/2, ColorPalette.orange); fv(w1/2, h1/2, ColorPalette.orange); fv(-w1/2, h1/2, ColorPalette.orange)

            val rot2 = rot1 + 1.1f
            val w2 = 0.28f; val h2 = 0.16f
            val c2 = cos(rot2); val s2 = sin(rot2)
            fun fv2(ox: Float, oy: Float, col: FloatArray) {
                val rx = ox * c2 - oy * s2
                val ry = ox * s2 + oy * c2
                putSolidVert(fx + rx + 0.02f, fy + ry + 0.01f, fz + 0.005f, col)
            }
            fv2(-w2/2, -h2/2, ColorPalette.yellow); fv2(w2/2, -h2/2, ColorPalette.yellow); fv2(w2/2, h2/2, ColorPalette.yellow)
            fv2(-w2/2, -h2/2, ColorPalette.yellow); fv2(w2/2, h2/2, ColorPalette.yellow); fv2(-w2/2, h2/2, ColorPalette.yellow)
        }

        // Slash arc crescent
        if (game.slashT > 0f) {
            val segs = 10
            val r0 = 0.5f; val r1 = 0.65f
            val arcZ = -1.4f
            for (i in 0 until segs) {
                val a0 = -0.7f + (i.toFloat() / segs) * 1.5f
                val a1 = -0.7f + ((i + 1).toFloat() / segs) * 1.5f
                val c = ColorPalette.redBright
                putSolidVert(cos(a0) * r0, sin(a0) * r0 - 0.1f, arcZ, c)
                putSolidVert(cos(a1) * r0, sin(a1) * r0 - 0.1f, arcZ, c)
                putSolidVert(cos(a0) * r1, sin(a0) * r1 - 0.1f, arcZ, c)

                putSolidVert(cos(a1) * r0, sin(a1) * r0 - 0.1f, arcZ, c)
                putSolidVert(cos(a1) * r1, sin(a1) * r1 - 0.1f, arcZ, c)
                putSolidVert(cos(a0) * r1, sin(a0) * r1 - 0.1f, arcZ, c)
            }
        }

        val solidVertCount = sIdx / 7
        if (solidVertCount > 0) {
            dynSolidBuffer.position(0)
            dynSolidBuffer.put(dynSolidFloats, 0, sIdx)

            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
            GLES20.glPolygonOffset(1f, 1f)

            GLES20.glEnableVertexAttribArray(aPosLoc)
            dynSolidBuffer.position(0)
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 7 * 4, dynSolidBuffer)
            GLES20.glEnableVertexAttribArray(aColLoc)
            dynSolidBuffer.position(3)
            GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 7 * 4, dynSolidBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, solidVertCount)
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        }

        val lineVertCount = lIdx / 7
        if (lineVertCount > 0) {
            dynLineBuffer.position(0)
            dynLineBuffer.put(dynLineFloats, 0, lIdx)

            GLES20.glEnableVertexAttribArray(aPosLoc)
            dynLineBuffer.position(0)
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, 7 * 4, dynLineBuffer)
            GLES20.glEnableVertexAttribArray(aColLoc)
            dynLineBuffer.position(3)
            GLES20.glVertexAttribPointer(aColLoc, 4, GLES20.GL_FLOAT, false, 7 * 4, dynLineBuffer)

            GLES20.glLineWidth(2.4f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineVertCount)
        }
    }

    private fun buildStaticWorld() {
        val solidVerts = ArrayList<Float>(100000)
        val lineVerts = ArrayList<Float>(100000)

        fun putSolid(x: Float, y: Float, z: Float, c: FloatArray) {
            solidVerts.add(x); solidVerts.add(y); solidVerts.add(z)
            solidVerts.add(c[0]); solidVerts.add(c[1]); solidVerts.add(c[2]); solidVerts.add(c[3])
        }

        fun putLine(x: Float, y: Float, z: Float, c: FloatArray) {
            lineVerts.add(x); lineVerts.add(y); lineVerts.add(z)
            lineVerts.add(c[0]); lineVerts.add(c[1]); lineVerts.add(c[2]); lineVerts.add(c[3])
        }

        val scratch = FloatArray(4096)

        for (c in game.colliders) {
            val w = c.max.x - c.min.x; val h = c.max.y - c.min.y; val d = c.max.z - c.min.z
            val cx = (c.max.x + c.min.x) / 2f; val cy = (c.max.y + c.min.y) / 2f; val cz = (c.max.z + c.min.z) / 2f

            val x0 = c.min.x; val x1 = c.max.x
            val y0 = c.min.y; val y1 = c.max.y
            val z0 = c.min.z; val z1 = c.max.z

            val isCraneBoom = cy > 18f && cz < -25f
            val isGround = cy < 0f

            val cX = if (isCraneBoom) ColorPalette.orange else ColorPalette.shadeLight
            val cZ = if (isCraneBoom) ColorPalette.orange else ColorPalette.shade
            val cTop = if (isCraneBoom) ColorPalette.orange else if (isGround) ColorPalette.paper else ColorPalette.paper2
            val cBottom = if (isCraneBoom) ColorPalette.orange else ColorPalette.shade

            fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx2: Float, cy2: Float, cz2: Float, dx: Float, dy: Float, dz: Float, color: FloatArray) {
                putSolid(ax, ay, az, color); putSolid(bx, by, bz, color); putSolid(cx2, cy2, cz2, color)
                putSolid(ax, ay, az, color); putSolid(cx2, cy2, cz2, color); putSolid(dx, dy, dz, color)
            }

            // 6 faces with proper orientation
            quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, cZ)       // +Z
            quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, cZ)       // -Z
            quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, cX)       // +X
            quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, cX)       // -X
            quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, cTop)     // +Y
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, cBottom)  // -Y

            // Ink edges
            val n = Sketch.boxEdges(cx, cy, cz, w, h, d, scratch, 0)
            var i = 0
            while (i < n) {
                putLine(scratch[i], scratch[i + 1], scratch[i + 2], ColorPalette.ink)
                putLine(scratch[i + 3], scratch[i + 4], scratch[i + 5], ColorPalette.ink)
                i += 6
            }
        }

        // Crane cables & hook
        putLine(32f, 23f, -32f, ColorPalette.ink); putLine(13f, 19.8f, -32f, ColorPalette.ink)
        putLine(32f, 23f, -32f, ColorPalette.ink); putLine(41f, 19.8f, -32f, ColorPalette.ink)
        putLine(14f, 19f, -32f, ColorPalette.inkDeep); putLine(14f, 12.5f, -32f, ColorPalette.inkDeep)
        val hook = Sketch.cross(14f, 12.2f, -32f, 0.5f)
        var i = 0
        while (i < hook.size) {
            putLine(hook[i], hook[i + 1], hook[i + 2], ColorPalette.orange)
            putLine(hook[i + 3], hook[i + 4], hook[i + 5], ColorPalette.orange)
            i += 6
        }

        // Sky sun & clouds
        val sx = -30f; val sy = 34f; val sz = -46f
        val circ = Sketch.circlePairs(3f, 14, 0.08f)
        i = 0
        while (i < circ.size) {
            putLine(sx + circ[i], sy + circ[i + 1], sz + circ[i + 2], ColorPalette.ink)
            putLine(sx + circ[i + 3], sy + circ[i + 4], sz + circ[i + 5], ColorPalette.ink)
            i += 6
        }
        for (r in 0 until 8) {
            val a = (r / 8.0) * 2.0 * Math.PI
            val c0 = cos(a).toFloat(); val s0 = sin(a).toFloat()
            putLine(sx + c0 * 3.6f, sy + s0 * 3.6f, sz, ColorPalette.ink)
            putLine(sx + c0 * 4.6f, sy + s0 * 4.6f, sz, ColorPalette.ink)
        }
        for (cc in listOf(floatArrayOf(20f, 30f, -46f), floatArrayOf(40f, 26f, -45f), floatArrayOf(-8f, 27f, -46f))) {
            for (off in listOf(floatArrayOf(-1.5f, 0f, 1.5f), floatArrayOf(0.4f, 0.35f, 2f), floatArrayOf(2.2f, 0f, 1.3f))) {
                val c2 = Sketch.circlePairs(off[2], 11, 0.08f, cc[0] + off[0], cc[1] + off[1], cc[2])
                i = 0
                while (i < c2.size) {
                    putLine(c2[i], c2[i + 1], c2[i + 2], ColorPalette.ink)
                    putLine(c2[i + 3], c2[i + 4], c2[i + 5], ColorPalette.ink)
                    i += 6
                }
            }
        }

        // Upload to VBOs
        staticSolidCount = solidVerts.size / 7
        val sBuf = ByteBuffer.allocateDirect(solidVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (v in solidVerts) sBuf.put(v)
        sBuf.position(0)

        val vboIds = IntArray(2)
        GLES20.glGenBuffers(2, vboIds, 0)
        staticSolidVBO = vboIds[0]
        staticLineVBO = vboIds[1]

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, staticSolidVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, solidVerts.size * 4, sBuf, GLES20.GL_STATIC_DRAW)

        staticLineCount = lineVerts.size / 7
        val lBuf = ByteBuffer.allocateDirect(lineVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (v in lineVerts) lBuf.put(v)
        lBuf.position(0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, staticLineVBO)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, lineVerts.size * 4, lBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vsSource)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val err = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            throw RuntimeException("GL Program link failed: $err")
        }
        return prog
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val err = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("GL Shader compile failed: $err")
        }
        return shader
    }
}

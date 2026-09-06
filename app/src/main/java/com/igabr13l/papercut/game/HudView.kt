package com.igabr13l.papercut.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Everything the user sees on top of the GL canvas: HUD, touch controls
 * (joystick + buttons) and menu/pause/gameover screens. All input lands here.
 */
class HudView(context: Context, val game: Game, val sfx: Sfx) : View(context) {

    val ink = Color.argb(255, 46, 61, 168)
    val red = Color.argb(255, 200, 40, 60)
    val green = Color.argb(255, 61, 138, 75)
    val yellow = Color.argb(255, 184, 160, 19)
    val orange = Color.argb(255, 217, 126, 16)
    val paper = Color.argb(255, 244, 241, 230)

    val textBig = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 34f; isFakeBoldText = true }
    val textMid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 24f }
    val textSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 18f }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 110f; isFakeBoldText = true }
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = ink }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val btnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(140, 255, 255, 255) }
    val btnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = ink }
    val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 250, 247, 238) }
    val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 90f; color = red }

    var scaleF = 1f
    var btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textAlign = Paint.Align.CENTER; textSize = 22f }

    abstract class Btn(val label: String, val hold: Boolean) {
        val rect = RectF()
        abstract fun tap()
        fun down() { if (hold) press(true) else tap() }
        fun up() { if (hold) press(false) }
        open fun press(down: Boolean) {}
    }

    var fireBtn: Btn
    var aimBtn: Btn
    var jumpBtn: Btn
    var grappleBtn: Btn
    var reloadBtn: Btn
    var dashBtn: Btn
    var pauseBtn: Btn
    var weaponBtns: List<Btn>
    var playBtn: Btn
    var resumeBtn: Btn
    var restartBtn: Btn
    var allBtns: List<Btn>

    init {
        fun mk(label: String, hold: Boolean, action: (Boolean) -> Unit): Btn = object : Btn(label, hold) {
            override fun tap() { action(true); if (!hold) sfx.click() }
            override fun press(down: Boolean) { action(down) }
        }
        fireBtn = mk("FUEGO", true) { down -> game.input.fire = down; if (down) game.input.firePressed = true }
        aimBtn = mk("MIRA", true) { down -> game.input.aim = down }
        jumpBtn = mk("SALTO", false) { game.input.jumpQ = true }
        grappleBtn = mk("GANCHO", false) { game.input.grappleQ = true }
        reloadBtn = mk("REC", false) { game.input.reloadQ = true }
        dashBtn = mk("DASH", false) { game.input.dashQ = true }
        pauseBtn = mk("PAUSA", false) { game.pause() }
        weaponBtns = listOf("RIF", "SHG", "REV", "SNP", "KAT").mapIndexed { i, name ->
            object : Btn(name, false) {
                override fun tap() { game.input.swapQ = i; sfx.click() }
            }
        }
        playBtn = mk("JUGAR", false) { game.start() }
        resumeBtn = mk("SEGUIR", false) { game.resume() }
        restartBtn = mk("REINTENTAR", false) { game.restart() }
        allBtns = listOf(fireBtn, aimBtn, jumpBtn, grappleBtn, reloadBtn, dashBtn, pauseBtn) + weaponBtns + listOf(playBtn, resumeBtn, restartBtn)
    }

    // joystick state
    var joyId = -1; var joyOx = 0f; var joyOy = 0f; var joyX = 0f; var joyY = 0f
    var lookId = -1; var lookX = 0f; var lookY = 0f
    var joyR = 60f * 2f

    fun layoutControls(w: Int, h: Int) {
        scaleF = w / 1550f.coerceAtLeast(1f) * 1f
        joyR = h * 0.085f
        val s = (w / 1100f).coerceIn(0.75f, 1.6f)
        btnText.textSize = 18f * s
        fun r(btn: Btn, cx: Float, cy: Float, rw: Float, rh: Float) {
            btn.rect.set(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2)
        }
        val right = w - 20f
        val bottom = h - 20f
        r(fireBtn, right - 70f * s, bottom - 70f * s, 120f * s, 120f * s)
        r(aimBtn, right - 200f * s, bottom - 60f * s, 90f * s, 90f * s)
        r(jumpBtn, right - 80f * s, bottom - 190f * s, 95f * s, 95f * s)
        r(grappleBtn, right - 200f * s, bottom - 165f * s, 85f * s, 85f * s)
        r(reloadBtn, right - 290f * s, bottom - 60f * s, 80f * s, 80f * s)
        r(dashBtn, right - 290f * s, bottom - 165f * s, 85f * s, 85f * s)
        r(pauseBtn, w - 45f * s, 40f * s, 80f * s, 50f * s)
        for ((i, wb) in weaponBtns.withIndex()) {
            r(wb, w - 46f * s, h * 0.16f + i * 46f * s, 78f * s, 40f * s)
        }
        r(playBtn, w / 2f, h * 0.72f, 320f, 84f)
        r(resumeBtn, w / 2f - 110f, h * 0.68f, 180f, 72f)
        r(restartBtn, w / 2f + 120f, h * 0.68f, 200f, 72f)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        layoutControls(w, h)
    }

    fun redraw() = postInvalidate()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        when (game.phase) {
            0 -> drawMenu(canvas, w, h)
            1 -> drawPlaying(canvas, w, h)
            2 -> { drawPlaying(canvas, w, h); drawPanelOverlay(canvas, w, h) }
            3 -> { drawPlaying(canvas, w, h); drawOver(canvas, w, h) }
        }
        if (game.hitFlash > 0.01f && game.phase == 1) {
            val old = vignette.alpha
            vignette.alpha = (game.hitFlash * 200).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, h, vignette.apply { this.alpha = (game.hitFlash * 140).toInt() })
            vignette.alpha = old
        }
    }

    fun drawBtn(canvas: Canvas, b: Btn, color: Int = ink, active: Boolean = false) {
        canvas.drawRoundRect(b.rect, 16f, 16f, btnFill)
        if (active) { fillPaint.color = Color.argb(70, 46, 61, 168); canvas.drawRoundRect(b.rect, 16f, 16f, fillPaint) }
        btnStroke.color = color
        canvas.drawRoundRect(b.rect, 16f, 16f, btnStroke)
        btnText.color = color
        val tp = b.rect.centerY() + btnText.textSize * 0.35f
        canvas.drawText(b.label, b.rect.centerX(), tp, btnText)
    }

    fun drawMenu(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, panelFill.apply { alpha = 200 })
        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.color = ink
        canvas.drawText("PAPERCUT", w / 2f, h * 0.26f, titlePaint)
        textMid.textAlign = Paint.Align.CENTER
        canvas.drawText("shooter de oleadas a boli · arena de obra en cuaderno rayado", w / 2f, h * 0.33f, textMid)
        textSmall.textAlign = Paint.Align.CENTER
        val lines = listOf(
            "PULGAR IZQ: joystick para moverse · a fondo = sprint",
            "PULGAR DER: desliza para mirar · FUEGO dispara",
            "MIRA apunta/zoom · BLOQ bloquea con la katana",
            "SALTO · GANCHO engancha y hace yank · REC recarga",
            "Cada 4 oleadas: THE DOODLER. Bloquea su tinta.",
        )
        for ((i, l) in lines.withIndex()) canvas.drawText(l, w / 2f, h * (0.42f + i * 0.045f), textSmall)
        drawBtn(canvas, playBtn, red)
    }

    fun drawPanelOverlay(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, panelFill.apply { alpha = 190 })
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("PAUSA", w / 2f, h * 0.4f, titlePaint)
        drawBtn(canvas, resumeBtn)
        drawBtn(canvas, restartBtn, red)
        // reposition restart label when paused (rect shared with gameover)
    }

    fun drawOver(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, panelFill.apply { alpha = 220 })
        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.color = red
        canvas.drawText("TE BORRARON DE LA PÁGINA", w / 2f, h * 0.3f, titlePaint.apply { textSize = 64f })
        titlePaint.textSize = 110f
        textMid.textAlign = Paint.Align.CENTER
        canvas.drawText("SCORE ${game.score}   ·   OLEADA ${game.wave}   ·   KILLS ${game.kills}   ·   mejor: ${game.bestWave}", w / 2f, h * 0.45f, textMid)
        restartBtn.rect.set(w / 2f - 110f, h * 0.6f, w / 2f + 110f, h * 0.6f + 80f)
        drawBtn(canvas, restartBtn, red)
    }

    fun drawPlaying(canvas: Canvas, w: Float, h: Float) {
        val g = game
        // score / wave
        textBig.textAlign = Paint.Align.LEFT
        canvas.drawText("SCORE ${g.score}", 24f, 54f, textBig)
        canvas.drawText("${g.kills} kills", 24f, 84f, textSmall)
        textBig.textAlign = Paint.Align.RIGHT
        canvas.drawText("WAVE ${g.wave}", w - 100f, 54f, textBig)
        canvas.drawText("${g.enemiesLeft()} enemies left", w - 100f, 84f, textSmall)
        textBig.textAlign = Paint.Align.LEFT

        // kill feed
        textSmall.textAlign = Paint.Align.RIGHT
        for ((i, f) in g.feed.withIndex()) {
            textSmall.color = f.color
            canvas.drawText(f.text, w - 100f, 120f + i * 26f, textSmall)
        }
        textSmall.color = ink

        // boss bar
        g.boss?.let { b ->
            textMid.textAlign = Paint.Align.CENTER
            textMid.color = red
            canvas.drawText("THE DOODLER", w / 2f, 44f, textMid)
            textMid.color = ink
            val bw = w * 0.4f
            val bx = w / 2f - bw / 2f
            val by = 56f
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + 18f), 8f, 8f, btnFill)
            fillPaint.color = red
            canvas.drawRoundRect(RectF(bx, by, bx + bw * (b.hp / b.maxHp).coerceIn(0f, 1f), by + 18f), 8f, 8f, fillPaint)
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + 18f), 8f, 8f, barPaint)
        }

        // banner
        g.bannerTitle?.let { t ->
            titlePaint.textAlign = Paint.Align.CENTER
            titlePaint.textSize = 56f
            canvas.drawText(t, w / 2f, h * 0.3f, titlePaint)
            titlePaint.textSize = 110f
            textMid.textAlign = Paint.Align.CENTER
            g.bannerSub?.let { canvas.drawText(it, w / 2f, h * 0.3f + 40f, textMid) }
        }

        // floating world texts (projected)
        textMid.textAlign = Paint.Align.CENTER
        for (f in g.floats) {
            val scr = project(f.pos) ?: continue
            val age = g.time - f.born
            textMid.color = f.color
            textMid.alpha = ((1f - age / 1.25f) * 255).toInt().coerceIn(0, 255)
            canvas.drawText(f.text, scr[0], scr[1] - age * 70f, textMid)
            textMid.alpha = 255
        }
        textMid.color = ink

        // hint
        if (g.phase == 1) {
            textSmall.textAlign = Paint.Align.CENTER
            val hint = g.hints[(g.hintT.toInt() / 9).mod(g.hints.size)]
            canvas.drawText(hint, w / 2f, h * 0.86f, textSmall)
        }

        // crosshair
        if (!g.isZooming()) {
            val cx = w / 2f; val cy = h / 2f
            val gap = 7f + g.crosshairSpread() * 0.55f
            fillPaint.color = ink
            canvas.drawCircle(cx, cy, 3f, fillPaint)
            val cl = floatArrayOf(cx, cy - gap - 16f, cx, cy - gap, cx, cy + gap, cx, cy + gap + 16f, cx - gap - 16f, cy, cx - gap, cy, cx + gap, cy, cx + gap + 16f, cy)
            canvas.drawLine(cl[0], cl[1], cl[2], cl[3], barPaint)
            canvas.drawLine(cl[4], cl[5], cl[6], cl[7], barPaint)
            canvas.drawLine(cl[8], cl[9], cl[10], cl[11], barPaint)
            canvas.drawLine(cl[12], cl[13], cl[14], cl[15], barPaint)
        } else {
            // sniper scope
            canvas.drawCircle(w / 2f, h / 2f, h * 0.42f, barPaint)
            canvas.drawLine(w / 2f - h * 0.42f, h / 2f, w / 2f + h * 0.42f, h / 2f, barPaint)
            canvas.drawLine(w / 2f, h / 2f - h * 0.42f, w / 2f, h / 2f + h * 0.42f, barPaint)
            fillPaint.color = red
            canvas.drawCircle(w / 2f, h / 2f, 5f, fillPaint)
        }

        // hp + ammo (bottom-left)
        val hpW = 220f
        val hpX = 24f; val hpY = h - 90f
        textSmall.textAlign = Paint.Align.LEFT
        canvas.drawText("HP", hpX, hpY + 14f, textSmall)
        canvas.drawRoundRect(RectF(hpX + 36f, hpY, hpX + 36f + hpW, hpY + 16f), 8f, 8f, btnFill)
        fillPaint.color = if (g.player.hp > 30) ink else red
        canvas.drawRoundRect(RectF(hpX + 36f, hpY, hpX + 36f + hpW * (g.player.hp / 100f).coerceIn(0f, 1f), hpY + 16f), 8f, 8f, fillPaint)
        canvas.drawRoundRect(RectF(hpX + 36f, hpY, hpX + 36f + hpW, hpY + 16f), 8f, 8f, barPaint)
        textBig.textSize = 40f
        val wst = g.weapons[g.wIdx]
        if (g.wIdx == 4) canvas.drawText("∞", hpX + 40f, hpY + 60f, textBig)
        else canvas.drawText("${wst.mag}/${wst.reserve}", hpX + 40f, hpY + 60f, textBig)
        textBig.textSize = 34f
        if (g.wIdx != 4 && wst.mag == 0) {
            textSmall.color = red
            canvas.drawText("REC — reload!", hpX + 40f, hpY + 84f, textSmall)
            textSmall.color = ink
        }

        // slash meter
        if (g.wIdx == 4) {
            val sx = hpX + 250f; val syTop = hpY - 40f; val sh = 80f
            canvas.drawRoundRect(RectF(sx, syTop, sx + 14f, syTop + sh), 6f, 6f, btnFill)
            fillPaint.color = yellow
            val sh2 = sh * (1f - (g.slashCd.coerceAtLeast(0f)) / 3f)
            canvas.drawRoundRect(RectF(sx, syTop + sh - sh2, sx + 14f, syTop + sh), 6f, 6f, fillPaint)
            canvas.drawRoundRect(RectF(sx, syTop, sx + 14f, syTop + sh), 6f, 6f, barPaint)
        }

        // touch controls
        // joystick
        if (joyId != -1) {
            btnStroke.color = ink
            canvas.drawCircle(joyOx, joyOy, joyR, btnFill)
            canvas.drawCircle(joyOx, joyOy, joyR, btnStroke)
            fillPaint.color = Color.argb(90, 46, 61, 168)
            canvas.drawCircle(joyX, joyY, joyR * 0.45f, fillPaint)
            canvas.drawCircle(joyX, joyY, joyR * 0.45f, btnStroke)
        }
        val showDash = g.wIdx == 4
        for (wb in weaponBtns) drawBtn(canvas, wb, ink, active = game.wIdx == weaponBtns.indexOf(wb))
        if (showDash) drawBtn(canvas, dashBtn)
        drawBtn(canvas, reloadBtn)
        drawBtn(canvas, grappleBtn)
        drawBtn(canvas, aimBtn, ink, active = g.input.aim)
        drawBtn(canvas, jumpBtn)
        drawBtn(canvas, fireBtn, red, active = g.input.fire)
        drawBtn(canvas, pauseBtn, red)
    }

    fun project(world: Vec3): FloatArray? {
        val clip = FloatArray(4)
        Mat4.transform(game.lastVP, world.x, world.y, world.z, clip)
        if (clip[3] <= 0.01f) return null
        val nx = clip[0] / clip[3]; val ny = clip[1] / clip[3]
        return floatArrayOf((nx * 0.5f + 0.5f) * width, (0.5f - ny * 0.5f) * height)
    }

    /* ---------------- touch ---------------- */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pointerCount = e.pointerCount
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                handleDown(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until pointerCount) {
                    val id = e.getPointerId(i)
                    if (id == joyId) updateJoy(e.getX(i), e.getY(i))
                    else if (id == lookId) {
                        game.input.lookDX += e.getX(i) - lookX
                        game.input.lookDY += e.getY(i) - lookY
                        lookX = e.getX(i); lookY = e.getY(i)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = if (e.actionMasked == MotionEvent.ACTION_UP) 0 else e.actionIndex
                handleUp(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_CANCEL -> {
                joyId = -1; lookId = -1
                game.input.moveX = 0f; game.input.moveY = 0f
                for (b in allBtns) if (b.hold) b.up()
            }
        }
        return true
    }

    fun handleDown(id: Int, x: Float, y: Float) {
        // buttons first
        for (b in allBtns) {
            if (b.rect.contains(x, y) && visibleBtn(b)) {
                pressedBtns[id] = b
                b.down()
                return
            }
        }
        if (game.phase != 1) return
        if (x < width * 0.45f && joyId == -1) {
            joyId = id; joyOx = x; joyOy = y; joyX = x; joyY = y
        } else if (lookId == -1) {
            lookId = id; lookX = x; lookY = y
        }
    }

    val pressedBtns = HashMap<Int, Btn>()

    fun visibleBtn(b: Btn): Boolean = when (b) {
        playBtn -> game.phase == 0
        resumeBtn -> game.phase == 2
        restartBtn -> game.phase == 2 || game.phase == 3
        dashBtn -> game.wIdx == 4
        else -> game.phase == 1
    }

    fun handleUp(id: Int, x: Float, y: Float) {
        val b = pressedBtns.remove(id)
        if (b != null) { b.up(); return }
        if (id == joyId) {
            joyId = -1
            game.input.moveX = 0f; game.input.moveY = 0f
        } else if (id == lookId) lookId = -1
    }

    fun updateJoy(x: Float, y: Float) {
        var dx = x - joyOx; var dy = y - joyOy
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len > joyR) { dx = dx / len * joyR; dy = dy / len * joyR }
        joyX = joyOx + dx; joyY = joyOy + dy
        game.input.moveX = dx / joyR
        game.input.moveY = -dy / joyR
    }
}

package com.igabr13l.papercut.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

    val doodleBold: Typeface = try { Typeface.create("casual", Typeface.BOLD) } catch (_: Throwable) { Typeface.DEFAULT_BOLD }
    val doodleNormal: Typeface = try { Typeface.create("casual", Typeface.NORMAL) } catch (_: Throwable) { Typeface.DEFAULT }

    val textBig = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 34f; typeface = doodleBold }
    val textMid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 24f; typeface = doodleNormal }
    val textSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 18f; typeface = doodleNormal }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 110f; typeface = doodleBold }
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3.5f; color = ink }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val btnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(140, 255, 255, 255) }
    val btnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = ink }
    val panelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(220, 250, 247, 238) }
    val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 90f; color = red }

    val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.8f; color = ink; strokeCap = Paint.Cap.ROUND }
    val cornerPaintThin = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; color = Color.argb(140, 46, 61, 168); strokeCap = Paint.Cap.ROUND }
    val tallyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.4f; color = ink; strokeCap = Paint.Cap.ROUND }
    val tallyCross = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.6f; color = ink; strokeCap = Paint.Cap.ROUND }
    val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = ink }

    var scaleF = 1f
    var btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textAlign = Paint.Align.CENTER; textSize = 22f; typeface = doodleBold }

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

    // notebook paper overlay (blue rules + red margin + vignette), like the web version
    var vignetteShaderW = 0; var vignetteShaderH = 0
    val paperRule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 62, 82, 190); strokeWidth = 1.6f }
    val paperMargin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 214, 69, 69); strokeWidth = 2.6f }
    val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawPaperOverlay(canvas: Canvas, w: Float, h: Float) {
        val gap = h * 0.05f
        var y = gap
        while (y < h) { canvas.drawLine(0f, y, w, y, paperRule); y += gap }
        canvas.drawLine(w * 0.045f, 0f, w * 0.045f, h, paperMargin)
        if (vignetteShaderW != width || vignetteShaderH != height) {
            vignetteShaderW = width; vignetteShaderH = height
            vignettePaint.shader = android.graphics.RadialGradient(
                w / 2f, h * 0.45f, h * 0.8f,
                intArrayOf(Color.TRANSPARENT, Color.argb(70, 110, 100, 70)),
                floatArrayOf(0.55f, 1f), android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

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

    fun drawCornerDoodles(canvas: Canvas, w: Float, h: Float) {
        val s = 52f
        // Top-Left
        canvas.drawLine(14f, 14f + s, 16f, 16f, cornerPaint)
        canvas.drawLine(16f, 16f, 14f + s, 14f, cornerPaint)
        canvas.drawLine(20f, 18f + s * 0.72f, 22f, 22f, cornerPaintThin)
        canvas.drawLine(22f, 22f, 18f + s * 0.72f, 20f, cornerPaintThin)

        // Top-Right
        canvas.drawLine(w - 14f, 14f + s, w - 16f, 16f, cornerPaint)
        canvas.drawLine(w - 16f, 16f, w - 14f - s, 14f, cornerPaint)
        canvas.drawLine(w - 20f, 18f + s * 0.72f, w - 22f, 22f, cornerPaintThin)
        canvas.drawLine(w - 22f, 22f, w - 18f - s * 0.72f, 20f, cornerPaintThin)

        // Bottom-Left
        canvas.drawLine(14f, h - 14f - s, 16f, h - 16f, cornerPaint)
        canvas.drawLine(16f, h - 16f, 14f + s, h - 14f, cornerPaint)
        canvas.drawLine(20f, h - 18f - s * 0.72f, 22f, h - 22f, cornerPaintThin)
        canvas.drawLine(22f, h - 22f, 18f + s * 0.72f, h - 20f, cornerPaintThin)

        // Bottom-Right
        canvas.drawLine(w - 14f, h - 14f - s, w - 16f, h - 16f, cornerPaint)
        canvas.drawLine(w - 16f, h - 16f, w - 14f - s, h - 14f, cornerPaint)
        canvas.drawLine(w - 20f, h - 18f - s * 0.72f, w - 22f, h - 22f, cornerPaintThin)
        canvas.drawLine(w - 22f, h - 22f, w - 18f - s * 0.72f, h - 20f, cornerPaintThin)
    }

    fun drawTally(canvas: Canvas, x: Float, y: Float, n: Int) {
        val maxGroups = minOf(6, (n + 4) / 5)
        val groupW = 22f
        val tallyH = 16f
        for (g in 0 until maxGroups) {
            val count = minOf(5, n - g * 5)
            val gx = x + g * (groupW + 10f)
            for (i in 0 until minOf(4, count)) {
                val lx = gx + i * (groupW / 4f)
                canvas.drawLine(lx, y, lx + 0.5f, y + tallyH, tallyPaint)
            }
            if (count == 5) {
                canvas.drawLine(gx - 2f, y + tallyH - 2f, gx + groupW - 1f, y + 2f, tallyCross)
            }
        }
        if (n > 30) {
            canvas.drawText("+${n - 30}", x + maxGroups * (groupW + 10f), y + tallyH - 2f, textSmall)
        }
    }

    fun drawBtn(canvas: Canvas, b: Btn, color: Int = ink, active: Boolean = false) {
        val isFire = b === fireBtn
        val isAction = b === jumpBtn || b === aimBtn || b === grappleBtn || b === reloadBtn || b === dashBtn
        val isWeapon = weaponBtns.contains(b)

        if (isFire) {
            val cx = b.rect.centerX()
            val cy = b.rect.centerY()
            val r = b.rect.width() / 2f
            fillPaint.color = if (active) Color.argb(90, 200, 40, 60) else Color.argb(45, 200, 40, 60)
            canvas.drawCircle(cx, cy, r, fillPaint)
            btnStroke.color = red
            btnStroke.strokeWidth = 4f
            canvas.drawCircle(cx, cy, r, btnStroke)
            btnText.color = red
            btnText.textSize = 24f * scaleF
            val tp = cy + btnText.textSize * 0.35f
            canvas.drawText(b.label, cx, tp, btnText)
        } else if (isAction) {
            val cx = b.rect.centerX()
            val cy = b.rect.centerY()
            val r = b.rect.width() / 2f
            canvas.drawCircle(cx, cy, r, btnFill)
            if (active) {
                fillPaint.color = Color.argb(80, 46, 61, 168)
                canvas.drawCircle(cx, cy, r, fillPaint)
            }
            btnStroke.color = color
            btnStroke.strokeWidth = 3f
            canvas.drawCircle(cx, cy, r, btnStroke)
            btnText.color = color
            btnText.textSize = 17f * scaleF
            val tp = cy + btnText.textSize * 0.35f
            canvas.drawText(b.label, cx, tp, btnText)
        } else {
            // Weapon tabs or menu buttons (rounded doodle pills)
            canvas.drawRoundRect(b.rect, 20f, 20f, btnFill)
            if (active) {
                fillPaint.color = Color.argb(70, 46, 61, 168)
                canvas.drawRoundRect(b.rect, 20f, 20f, fillPaint)
            }
            btnStroke.color = color
            btnStroke.strokeWidth = 3f
            canvas.drawRoundRect(b.rect, 20f, 20f, btnStroke)
            btnText.color = color
            btnText.textSize = if (isWeapon) 16f * scaleF else 22f * scaleF
            val tp = b.rect.centerY() + btnText.textSize * 0.35f
            canvas.drawText(b.label, b.rect.centerX(), tp, btnText)
        }
    }

    fun drawMenu(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, panelFill.apply { alpha = 200 })
        drawCornerDoodles(canvas, w, h)
        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.color = ink
        canvas.drawText("PAPERCUT", w / 2f, h * 0.26f, titlePaint)
        // Wavy scribble underline under title
        val uw = 280f
        var ux = w / 2f - uw / 2f
        val uy = h * 0.275f
        while (ux < w / 2f + uw / 2f) {
            canvas.drawLine(ux, uy, ux + 10f, uy - 3f, btnStroke)
            canvas.drawLine(ux + 10f, uy - 3f, ux + 20f, uy, btnStroke)
            ux += 20f
        }
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
        drawCornerDoodles(canvas, w, h)
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("PAUSA", w / 2f, h * 0.4f, titlePaint)
        drawBtn(canvas, resumeBtn)
        drawBtn(canvas, restartBtn, red)
    }

    fun drawOver(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, panelFill.apply { alpha = 220 })
        drawCornerDoodles(canvas, w, h)
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
        drawPaperOverlay(canvas, w, h)
        drawCornerDoodles(canvas, w, h)
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
        val hpW = 200f
        val hpX = 24f; val hpY = h - 96f
        textSmall.textAlign = Paint.Align.LEFT
        canvas.drawText("HP", hpX, hpY + 14f, textSmall)
        val barRect = RectF(hpX + 36f, hpY, hpX + 36f + hpW, hpY + 16f)
        canvas.drawRoundRect(barRect, 8f, 8f, btnFill)
        val fillW = hpW * (g.player.hp / 100f).coerceIn(0f, 1f)
        if (fillW > 0f) {
            canvas.save()
            canvas.clipRect(hpX + 36f, hpY, hpX + 36f + fillW, hpY + 16f)
            hatchPaint.color = if (g.player.hp > 30) ink else red
            var hx = hpX + 36f - 16f
            while (hx < hpX + 36f + fillW + 16f) {
                canvas.drawLine(hx, hpY + 16f, hx + 16f, hpY, hatchPaint)
                hx += 8f
            }
            canvas.restore()
        }
        canvas.drawRoundRect(barRect, 8f, 8f, barPaint)

        // Ammo + Tally marks
        textBig.textSize = 38f
        val wst = g.weapons[g.wIdx]
        if (g.wIdx == 4) canvas.drawText("∞", hpX + 40f, hpY + 54f, textBig)
        else {
            canvas.drawText("${wst.mag}/${wst.reserve}", hpX + 40f, hpY + 54f, textBig)
            drawTally(canvas, hpX + 40f, hpY + 62f, wst.mag)
        }
        textBig.textSize = 34f
        if (g.wIdx != 4 && wst.mag == 0) {
            textSmall.color = red
            canvas.drawText("REC — reload!", hpX + 40f, hpY + 98f, textSmall)
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
                        game.input.addLook(e.getX(i) - lookX, e.getY(i) - lookY)
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

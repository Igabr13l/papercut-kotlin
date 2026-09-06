package com.igabr13l.papercut.game

import kotlin.math.min
import kotlin.random.Random

/* Types */
class Collider(val min: Vec3, val max: Vec3)

class Enemy(
    var scale: Float, var hp: Float, val maxHp: Float,
    val kind: Int, // 0 grunt, 1 shooter, 2 boss
) {
    var pos = Vec3()
    var facing = 0f
    var state = 0 // 0 spawn, 1 chase, 2 windup, 3 attack, 4 stun, 5 yank, 6 dead
    var t = 0.5f
    var vel = Vec3()
    var grounded = false
    var flash = 0f
    var yankTimer = 0f
    var atkCd = 1f + Random.nextFloat()
    var phase = Random.nextFloat() * 9f
    var deadT = 0f
    var summoned = false
    var walkPhase = Random.nextFloat() * 10f
    fun dead() = state == 6
}

class Proj(val pos: Vec3, val vel: Vec3, var deflected: Boolean, var life: Float, val dmg: Float, val owner: Enemy?)

class Particle(val obj: Vec3, val vel: Vec3, var life: Float, val max: Float, val grav: Float, val size: Float, val color: FloatArray)

class Pickup(val obj: Vec3, val kind: Int, var t: Float) // 0 hp, 1 ammo

class Barrel(val pos: Vec3, var hp: Float, var alive: Boolean)

class FloatText(val pos: Vec3, val text: String, val color: Int, var born: Float)
class FeedItem(val text: String, val color: Int, var born: Float)

class WeaponDef(
    val name: String, val mag: Int, val reserve: Int, val cd: Float, val dmg: Float,
    val spread: Float, val auto: Boolean, val head: Float, val pellets: Int, val reload: Float,
)

val WEAPON_DEFS = listOf(
    WeaponDef("RIFLE", 30, 150, 0.10f, 16f, 0.013f, true, 2.2f, 1, 1.4f),
    WeaponDef("SHOTGUN", 6, 48, 0.78f, 11f, 0.09f, false, 1.6f, 9, 2.2f),
    WeaponDef("REVOLVER", 6, 60, 0.17f, 44f, 0.006f, false, 2.5f, 1, 1.5f),
    WeaponDef("SNIPER", 5, 30, 1.15f, 170f, 0.0f, false, 3f, 1, 2.4f),
    WeaponDef("KATANA", 0, 0, 0.38f, 70f, 0f, false, 1.5f, 0, 0f),
)

const val HEADSHOT = 2f; const val SLICED = 1.5f; const val YANKED = 1.5f; const val BLOCKED = 2f; const val AIRBORNE = 2.5f

/* Touch input state, fed by HudView */
class Input {
    var moveX = 0f; var moveY = 0f          // joystick, -1..1
    var lookDX = 0f; var lookDY = 0f        // accumulated pixels, consumed each frame
    var fire = false; var aim = false
    var firePressed = false
    var jumpQ = false; var dashQ = false; var grappleQ = false; var reloadQ = false
    var swapQ = -1
    fun clearPerFrame() { lookDX = 0f; lookDY = 0f; firePressed = false; jumpQ = false; dashQ = false; grappleQ = false; reloadQ = false; swapQ = -1 }
}

class WeaponState(def: WeaponDef) {
    val def = def
    var mag = def.mag
    var reserve = def.reserve
    var cdT = 0f
    var reloadT = 0f
}

class Game {
    // phases
    var phase = 0 // 0 menu, 1 playing, 2 paused, 3 over
    var sfx: Sfx? = null

    val input = Input()
    var time = 0f

    val colliders = ArrayList<Collider>()
    val enemies = ArrayList<Enemy>()
    val projs = ArrayList<Proj>()
    val parts = ArrayList<Particle>()
    val pickups = ArrayList<Pickup>()
    val barrels = ArrayList<Barrel>()
    var barrelSpots = listOf(floatArrayOf(-4f, 2f), floatArrayOf(14f, 8f), floatArrayOf(-18f, -6f), floatArrayOf(26f, -14f), floatArrayOf(6f, -10f), floatArrayOf(-28f, 12f))

    val player = PlayerState()
    var shake = 0f; var hitFlash = 0f; var kick = 0f; var bobT = 0f
    var fovT = 75f; var fovCur = 75f

    val weapons = WEAPON_DEFS.map { WeaponState(it) }
    var wIdx = 0
    var slashCd = 0f; var slashT = 0f; var dashT = 0f; var dashDir = Vec3()
    var grapplePoint: Vec3? = null; var grappleT = 0f; var grappleCd = 0f

    var score = 0; var wave = 0; var kills = 0; var shots = 0; var hits = 0; var bestWave = 0
    var waveActive = false; var interT = 1.6f; var toSpawn = 0; var spawnT = 0f; var bossDue = false
    var boss: Enemy? = null
    val floats = ArrayList<FloatText>()
    val feed = ArrayList<FeedItem>()
    var hintT = 0f
    var bannerTitle: String? = null; var bannerSub: String? = null; var bannerUntil = 0f

    var lastVP = FloatArray(16)
    var screenW = 1; var screenH = 1

    val hints = listOf(
        "swipe 1-5 weapons · the shotgun ends heavies",
        "hold BLOQ with the katana to block & return ink bolts",
        "GANCHO hooks ledges and yanks scribbles to you",
        "headshots pay double · jump-slash pays x2.5",
        "orange barrels explode · feed them to the doodler",
        "katana DASH: charged dash-slash through crowds",
    )

    class PlayerState {
        val pos = Vec3(0f, 0f, 26f)
        val vel = Vec3()
        var yaw = 0f; var pitch = 0f
        var grounded = false; var hp = 100f; var coyote = 0f
    }

    init { buildWorld() }

    /* ---------------- world ---------------- */
    fun addBox(cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float, collide: Boolean = true) {
        if (collide) colliders.add(Collider(Vec3(cx - w / 2, cy - h / 2, cz - d / 2), Vec3(cx + w / 2, cy + h / 2, cz + d / 2)))
    }

    fun stairs(x: Float, z0: Float, dz: Float, y0: Float, n: Int, w: Float) {
        for (i in 0 until n) {
            val top = y0 + 0.4f * (i + 1)
            val h = top - (y0 - 0.3f)
            addBox(x, top - h / 2, z0 + dz * i, w, h, 1.15f)
        }
    }

    fun buildWorld() {
        addBox(0f, -0.5f, 0f, 96f, 1f, 96f) // ground
        addBox(0f, 4.5f, -47.5f, 96f, 9f, 1f)
        addBox(0f, 4.5f, 47.5f, 96f, 9f, 1f)
        addBox(-47.5f, 4.5f, 0f, 1f, 9f, 96f)
        addBox(47.5f, 4.5f, 0f, 1f, 9f, 96f)
        // building A (NW): two open floors
        for (pp in listOf(floatArrayOf(-31f, -26f), floatArrayOf(-17f, -26f), floatArrayOf(-31f, -14f), floatArrayOf(-17f, -14f))) {
            addBox(pp[0], 2f, pp[1], 0.9f, 4f, 0.9f)
            addBox(pp[0], 6f, pp[1], 0.9f, 4f, 0.9f)
        }
        addBox(-24f, 4f, -20f, 18f, 0.5f, 14f)
        addBox(-24f, 8f, -20f, 18f, 0.5f, 14f)
        stairs(-13.5f, -4f, -1.05f, 0f, 10, 3.2f)
        stairs(-31f, -14f, -1.05f, 4.25f, 10, 2.6f)
        // building B (SE)
        for (pp in listOf(floatArrayOf(15f, 11f), floatArrayOf(29f, 11f), floatArrayOf(15f, 25f), floatArrayOf(29f, 25f))) addBox(pp[0], 2.25f, pp[1], 0.9f, 4.5f, 0.9f)
        addBox(22f, 4.5f, 18f, 16f, 0.5f, 16f)
        stairs(12.5f, 0f, 1.05f, 0f, 11, 3f)
        // central tower
        for (pp in listOf(floatArrayOf(-2.5f, -6.5f), floatArrayOf(2.5f, -6.5f), floatArrayOf(-2.5f, -1.5f), floatArrayOf(2.5f, -1.5f))) addBox(pp[0], 4f, pp[1], 0.8f, 8f, 0.8f)
        addBox(0f, 8f, -4f, 8f, 0.5f, 7f)
        // bridge tower -> east walkway
        addBox(21f, 8f, -4f, 34f, 0.4f, 3f)
        for (px in listOf(10f, 21f, 32f)) addBox(px, 4f, -4f, 0.7f, 8f, 0.7f)
        // east elevated walkway
        addBox(40f, 8f, 0f, 4f, 0.4f, 40f)
        for (pz in -16..16 step 8) addBox(40f, 4f, pz.toFloat(), 0.8f, 8f, 0.8f)
        // bridge A floor2 -> tower
        addBox(-8f, 8f, -20f, 14f, 0.4f, 3f)
        // cover blocks
        addBox(-6f, 1.1f, 6f, 6f, 0.7f, 0.7f)
        addBox(8f, 1.1f, -2f, 0.7f, 0.7f, 6f)
        addBox(2f, 1.1f, 14f, 6f, 0.7f, 0.7f)
        addBox(-10f, 1f, 10f, 2f, 2f, 2f)
        addBox(12f, 1f, -12f, 2f, 2f, 2f)
        addBox(-2f, 1f, -16f, 2f, 2f, 2f)
        addBox(-38f, 1.5f, 20f, 3f, 3f, 3f)
        addBox(36f, 1.5f, 30f, 3f, 3f, 3f)
    }

    fun resetBarrels() {
        for (b in barrels) b.alive = false
        barrels.clear()
        for (s in barrelSpots) barrels.add(Barrel(Vec3(s[0], 0.7f, s[1]), 40f, true))
    }

    /* ---------------- flow ---------------- */
    fun start() { reset(); phase = 1 }
    fun resume() { phase = 1 }
    fun pause() {
        if (phase != 1) return
        phase = 2
        input.fire = false; input.aim = false; input.moveX = 0f; input.moveY = 0f
    }
    fun restart() { reset(); phase = 1 }

    fun reset() {
        enemies.clear(); projs.clear(); parts.clear(); pickups.clear()
        resetBarrels()
        player.pos.set(0f, 0f, 26f); player.vel.set(0f, 0f, 0f); player.yaw = 0f; player.pitch = 0f; player.hp = 100f
        for (w in weapons) { w.mag = w.def.mag; w.reserve = w.def.reserve; w.cdT = 0f; w.reloadT = 0f }
        wIdx = 0
        score = 0; kills = 0; shots = 0; hits = 0
        wave = 0; waveActive = false; interT = 1.6f; toSpawn = 0; bossDue = false; boss = null
        slashCd = 0f; grapplePoint = null
        floats.clear(); feed.clear(); hintT = 0f; hitFlash = 0f; shake = 0f
        banner("WAVE 1 INCOMING", "grab the pen — survive the notebook")
    }

    fun banner(title: String, sub: String) { bannerTitle = title; bannerSub = sub; bannerUntil = time + 2.4f }

    fun switchWeapon(n: Int) {
        if (n < 0 || n > 4 || n == wIdx) return
        wIdx = n
        weapons[n].reloadT = 0f
    }

    fun startReload() {
        val w = weapons[wIdx]
        if (wIdx == 4 || w.reloadT > 0 || w.reserve <= 0 || w.mag >= w.def.mag) return
        w.reloadT = w.def.reload
        sfx?.reload()
    }

    /* ---------------- helpers ---------------- */
    fun rayBox(o: Vec3, d: Vec3, c: Collider, maxD: Float): Float? {
        var tmin = 0f; var tmax = maxD
        for (axis in 0..2) {
            val dv = axisOf(d, axis); val ov = axisOf(o, axis)
            val mn = axisOf(c.min, axis); val mx = axisOf(c.max, axis)
            if (kotlin.math.abs(dv) < 1e-8f) { if (ov < mn || ov > mx) return null; continue }
            var t0 = (mn - ov) / dv; var t1 = (mx - ov) / dv
            if (t0 > t1) { val tmp = t0; t0 = t1; t1 = tmp }
            tmin = maxOf(tmin, t0); tmax = minOf(tmax, t1)
            if (tmin > tmax) return null
        }
        return tmin
    }

    fun axisOf(v: Vec3, axis: Int): Float = when (axis) { 0 -> v.x; 1 -> v.y; else -> v.z }

    fun raySphere(o: Vec3, d: Vec3, cx: Float, cy: Float, cz: Float, r: Float, maxD: Float): Float? {
        val ox = o.x - cx; val oy = o.y - cy; val oz = o.z - cz
        val b = ox * d.x + oy * d.y + oz * d.z
        val q = ox * ox + oy * oy + oz * oz - r * r
        val h = b * b - q
        if (h < 0) return null
        val sq = kotlin.math.sqrt(h)
        val t = -b - sq
        val tt = if (t > 0) t else -b + sq
        return if (tt > 0 && tt < maxD) tt else null
    }

    class HitResult(
        val kind: Int, // 0 enemy, 1 barrel, 2 world
        val enemy: Enemy?, val barrel: Barrel?, val head: Boolean,
        val point: Vec3, val dist: Float, val normal: Vec3,
    )

    fun aimDir(spread: Float): Vec3 {
        // forward = camera -Z
        val cp = kotlin.math.cos(player.pitch); val sp = kotlin.math.sin(player.pitch)
        val cy = kotlin.math.cos(player.yaw); val sy = kotlin.math.sin(player.yaw)
        val d = Vec3(-sy * cp, sp, -cy * cp)
        if (spread > 0) {
            val r = Vec3(kotlin.math.cos(player.yaw), 0f, -kotlin.math.sin(player.yaw))
            val u = Vec3(sy * sp, cp, cy * sp) // up approx: r x fwd
            d.addScaled(r, (Random.nextFloat() - 0.5f) * spread * 2f)
            d.addScaled(u, (Random.nextFloat() - 0.5f) * spread * 2f)
            d.norm()
        }
        return d
    }

    fun hitscan(dir: Vec3, maxD: Float = 140f): HitResult? {
        val o = eyePos()
        var best: HitResult? = null
        for (e in enemies) {
            if (e.dead()) continue
            val p = e.pos
            val headT = raySphere(o, dir, p.x, p.y + 1.62f * e.scale, p.z, 0.32f * e.scale + 0.06f, maxD)
            if (headT != null && (best == null || headT < best.dist)) best = HitResult(0, e, null, true, o.copy().addScaled(dir, headT), headT, Vec3())
            val torT = raySphere(o, dir, p.x, p.y + 1.0f * e.scale, p.z, 0.5f * e.scale, maxD)
            if (torT != null && (best == null || torT < best.dist)) best = HitResult(0, e, null, false, o.copy().addScaled(dir, torT), torT, Vec3())
        }
        for (b in barrels) {
            if (!b.alive) continue
            val t = raySphere(o, dir, b.pos.x, b.pos.y, b.pos.z, 0.62f, maxD)
            if (t != null && (best == null || t < best.dist)) best = HitResult(1, null, b, false, o.copy().addScaled(dir, t), t, Vec3())
        }
        for (c in colliders) {
            val t = rayBox(o, dir, c, maxD)
            if (t != null && (best == null || t < best.dist)) {
                val point = o.copy().addScaled(dir, t)
                val ctr = c.min.copy().add(c.max).scale(0.5f)
                val ext = c.max.copy().sub(c.min).scale(0.5f)
                val rel = point.copy().sub(ctr)
                val n = Vec3()
                if (kotlin.math.abs(rel.x / ext.x) > kotlin.math.abs(rel.y / ext.y) && kotlin.math.abs(rel.x / ext.x) > kotlin.math.abs(rel.z / ext.z)) n.x = kotlin.math.sign(rel.x)
                else if (kotlin.math.abs(rel.y / ext.y) > kotlin.math.abs(rel.z / ext.z)) n.y = kotlin.math.sign(rel.y)
                else n.z = kotlin.math.sign(rel.z)
                best = HitResult(2, null, null, false, point, t, n)
            }
        }
        return best
    }

    fun eyePos(): Vec3 = Vec3(player.pos.x, player.pos.y + 1.6f, player.pos.z)

    fun moveEntity(pos: Vec3, vel: Vec3, r: Float, h: Float, dt: Float, stepUp: Float): Boolean {
        var grounded = false
        fun overlapXZ(x: Float, z: Float, y: Float): List<Collider> {
            val out = ArrayList<Collider>()
            for (c in colliders) {
                if (x + r > c.min.x && x - r < c.max.x && z + r > c.min.z && z - r < c.max.z && y < c.max.y - 0.02f && y + h > c.min.y + 0.02f) out.add(c)
            }
            return out
        }
        val nx = pos.x + vel.x * dt
        var hit = overlapXZ(nx, pos.z, pos.y)
        if (hit.isNotEmpty()) {
            val top = hit.maxOf { it.max.y }
            if (top - pos.y <= stepUp && top > pos.y) { pos.y = top + 0.001f; pos.x = nx } else vel.x = 0f
        } else pos.x = nx
        val nz = pos.z + vel.z * dt
        hit = overlapXZ(pos.x, nz, pos.y)
        if (hit.isNotEmpty()) {
            val top = hit.maxOf { it.max.y }
            if (top - pos.y <= stepUp && top > pos.y) { pos.y = top + 0.001f; pos.z = nz } else vel.z = 0f
        } else pos.z = nz
        val py = pos.y
        pos.y += vel.y * dt
        for (c in colliders) {
            if (pos.x + r > c.min.x && pos.x - r < c.max.x && pos.z + r > c.min.z && pos.z - r < c.max.z) {
                if (vel.y <= 0 && py >= c.max.y - 0.08f && pos.y <= c.max.y) { pos.y = c.max.y; vel.y = 0f; grounded = true }
                else if (vel.y > 0 && py + h <= c.min.y + 0.08f && pos.y + h >= c.min.y) { pos.y = c.min.y - h - 0.01f; vel.y = 0f }
            }
        }
        pos.x = pos.x.coerceIn(-46.5f, 46.5f)
        pos.z = pos.z.coerceIn(-46.5f, 46.5f)
        return grounded
    }

    /* ---------------- fx ---------------- */
    fun burst(point: Vec3, color: FloatArray, n: Int, size: Float = 0.16f, speed: Float = 6f) {
        for (i in 0 until n) {
            if (parts.size > 200) parts.removeAt(0)
            val v = Vec3(Random.nextFloat() - 0.5f, Random.nextFloat() * 0.9f, Random.nextFloat() - 0.5f).norm().scale(speed * (0.4f + Random.nextFloat()))
            parts.add(Particle(point.copy(), v, 0.5f + Random.nextFloat() * 0.4f, 0.9f, 14f, size * (0.5f + Random.nextFloat()), color))
        }
    }

    fun floatText(world: Vec3, text: String, color: Int) {
        floats.add(FloatText(world, text, color, time))
        if (floats.size > 14) floats.removeAt(0)
    }

    /* ---------------- combat ---------------- */
    fun damageEnemy(e: Enemy, dmg: Float, head: Boolean, point: Vec3, tags: MutableList<String>) {
        if (e.dead()) return
        val mult = if (head) WEAPON_DEFS[wIdx].head else 1f
        e.hp -= dmg * mult
        e.flash = 1f
        hits++
        sfx?.hit()
        burst(point, Sketch.RED, if (head) 7 else 4, 0.14f, 5f)
        if (e.hp <= 0) killEnemy(e, if (head) { tags.add("HEADSHOT"); tags } else tags, tags.contains("SLICED"))
    }

    fun killEnemy(e: Enemy, tagsIn: MutableList<String>, melee: Boolean) {
        if (e.dead()) return
        val tags = tagsIn
        if (melee && !player.grounded) tags.add("AIRBORNE")
        if (e.yankTimer > 0 && !tags.contains("YANKED")) tags.add("YANKED")
        val uniq = tags.distinct()
        var mult = 1f
        for (t in uniq) mult *= when (t) {
            "HEADSHOT" -> HEADSHOT; "SLICED" -> SLICED; "YANKED" -> YANKED; "BLOCKED" -> BLOCKED; "AIRBORNE" -> AIRBORNE; else -> 1f
        }
        val base = if (e.kind == 2) 1500f else 100f
        val pts = (base * mult).toInt()
        score += pts
        kills++
        val p = e.pos
        val label = (if (uniq.isNotEmpty()) uniq.joinToString(" ") else "KILL") + " +$pts"
        feed.add(FeedItem(label, if (uniq.isNotEmpty()) colorOf(uniq[0]) else 0xFF2E3DA8.toInt(), time))
        if (feed.size > 5) feed.removeAt(0)
        burst(p.copy().add(Vec3(0f, 1f * e.scale, 0f)), Sketch.RED, if (e.kind == 2) 40 else 12, 0.22f * e.scale, 7f)
        for (i in 0 until (if (e.kind == 2) 6 else 3)) {
            val v = Vec3((Random.nextFloat() - 0.5f) * 7f, 4f + Random.nextFloat() * 4f, (Random.nextFloat() - 0.5f) * 7f)
            parts.add(Particle(p.copy().add(Vec3(0f, 1.2f * e.scale, 0f)), v, 1.3f, 1.3f, 16f, 0.13f * e.scale, Sketch.SHADE_LIGHT))
        }
        burst(p.copy().add(Vec3(0f, 0.05f, 0f)), Sketch.RED, 8, 0.5f * e.scale, 2f)
        sfx?.splat()
        e.state = 6; e.deadT = 0f
        if (e.kind == 2) {
            boss = null
            banner("THE DOODLER ERASED", "+1500 · the page is quiet... for now")
            shake = min(1f, shake + 0.7f)
        } else if (Random.nextFloat() < 0.2f) dropPickup(p)
    }

    fun colorOf(tag: String): Int = when (tag) {
        "HEADSHOT", "BLOCKED" -> 0xFFB8A013.toInt()
        "SLICED", "AIRBORNE" -> 0xFFC47A12.toInt()
        "YANKED" -> 0xFF3D8A4B.toInt()
        else -> 0xFF2E3DA8.toInt()
    }

    fun dropPickup(p: Vec3) {
        val kind = if (player.hp < 65) 0 else if (Random.nextFloat() < 0.5f) 0 else 1
        pickups.add(Pickup(p.copy().add(Vec3(0f, 0.6f, 0f)), kind, 0f))
    }

    fun fire() {
        val w = weapons[wIdx]
        w.mag--; w.cdT = w.def.cd
        kick = 1f
        sfx?.shot(wIdx)
        shake = min(1f, shake + (if (wIdx == 1) 0.35f else if (wIdx == 3) 0.4f else 0.12f))
        shots++
        val zoom = wIdx == 3 && input.aim
        val spread = w.def.spread * (if (zoom) 0.1f else if (input.aim) 0.6f else 1f) + (if (player.grounded) 0f else 0.02f) + player.vel.len() * 0.0015f
        for (i in 0 until maxOf(1, w.def.pellets)) {
            val dir = aimDir(spread)
            val hit = hitscan(dir)
            if (hit == null) continue
            when (hit.kind) {
                0 -> damageEnemy(hit.enemy!!, w.def.dmg, hit.head, hit.point, ArrayList())
                1 -> { hit.barrel!!.hp -= w.def.dmg; if (hit.barrel.hp <= 0) explodeBarrel(hit.barrel) }
                else -> burst(hit.point, Sketch.INK_DEEP, 2, 0.07f, 3f)
            }
        }
    }

    fun slash(dash: Boolean) {
        val w = weapons[4]
        w.cdT = if (dash) 0.3f else w.def.cd
        slashT = 0.2f
        if (dash) shake = min(1f, shake + 0.2f)
        val fwd = aimDir(0f)
        val origin = eyePos()
        for (e in enemies) {
            if (e.dead()) continue
            val to = e.pos.copy().add(Vec3(0f, 1f * e.scale, 0f)).sub(origin)
            val dist = to.len()
            if (dist < (if (dash) 4.2f else 3.0f) && to.norm().dot(fwd) > (if (dash) 0.3f else 0.45f)) {
                damageEnemy(e, if (dash) 95f else w.def.dmg, false, origin.copy().addScaled(fwd, dist), arrayListOf("SLICED"))
            }
        }
        for (b in barrels) {
            if (!b.alive) continue
            val to = b.pos.copy().sub(origin)
            if (to.len() < 3f && to.norm().dot(fwd) > 0.45f) { b.hp -= 999f; explodeBarrel(b) }
        }
        for (p in projs) {
            if (p.deflected) continue
            val to = p.pos.copy().sub(origin)
            if (to.len() < 2.4f && to.norm().dot(fwd) > 0.3f) {
                burst(p.pos, Sketch.GREEN, 5, 0.1f, 4f)
                p.life = 0f
                score += 15
            }
        }
    }

    fun explodeBarrel(b: Barrel) {
        if (!b.alive) return
        b.alive = false
        sfx?.explode()
        shake = min(1f, shake + 0.5f)
        burst(b.pos, Sketch.ORANGE, 22, 0.3f, 10f)
        burst(b.pos, Sketch.RED, 14, 0.25f, 8f)
        for (e in enemies) {
            if (e.dead()) continue
            val d = e.pos.distTo(b.pos)
            if (d < 6f) damageEnemy(e, 130f * (1f - d / 7f), false, e.pos.copy().add(Vec3(0f, 1f, 0f)), ArrayList())
        }
        val pd = player.pos.distTo(b.pos)
        if (pd < 5f) damagePlayer((30f * (1f - pd / 5f)).toInt(), b.pos, "BOOM")
    }

    fun damagePlayer(d: Int, src: Vec3?, label: String = "INK BOLT") {
        if (phase != 1) return
        if (src != null) floatText(src.copy(), "$label -$d", 0xFFC8283C.toInt())
        player.hp -= d
        sfx?.hurt()
        hitFlash = 1f
        shake = min(1f, shake + 0.35f)
        if (player.hp <= 0) {
            player.hp = 0f
            phase = 3
            sfx?.over()
            bestWave = maxOf(bestWave, wave)
        }
    }

    fun tryGrapple() {
        if (grappleCd > 0) return
        grappleCd = 0.35f
        val dir = aimDir(0f)
        val o = eyePos()
        var bestE: Enemy? = null; var bestD = 34f
        for (e in enemies) {
            if (e.dead() || e.kind == 2) continue
            val p = e.pos
            val t = raySphere(o, dir, p.x, p.y + 1f * e.scale, p.z, 0.8f * e.scale, bestD)
            if (t != null && t < bestD) { bestD = t; bestE = e }
        }
        val be = bestE
        if (be != null) {
            be.state = 5; be.t = 0f
            sfx?.grapple()
            floatText(be.pos.copy().add(Vec3(0f, 2f, 0f)), "YANK!", 0xFF3D8A4B.toInt())
            return
        }
        val hit = hitscan(dir, 48f)
        if (hit != null && hit.kind == 2) {
            grapplePoint = hit.point
        }
    }

    fun spawnProj(from: Vec3, dir: Vec3, speed: Float, dmg: Float, owner: Enemy?, color: FloatArray = Sketch.SHADE_LIGHT) {
        projs.add(Proj(from.copy(), dir.copy().scale(speed), false, 5f, dmg, owner))
    }

    fun spawnEnemy(kind: Int, at: Vec3? = null): Enemy {
        val scale = if (kind == 2) 3.1f else if (kind == 1) 1.05f else 1f
        var p = at
        if (p == null) {
            val spots = listOf(
                floatArrayOf(-44f, -44f), floatArrayOf(44f, 44f), floatArrayOf(-44f, 44f), floatArrayOf(44f, -44f),
                floatArrayOf(-44f, 0f), floatArrayOf(44f, 0f), floatArrayOf(0f, -44f), floatArrayOf(0f, 44f),
            )
            for (tries in 0 until 12) {
                val s = spots[Random.nextInt(spots.size)]
                val cand = Vec3(s[0] + (Random.nextFloat() - 0.5f) * 8f, 0f, s[1] + (Random.nextFloat() - 0.5f) * 8f)
                if (cand.distTo(player.pos) > 22f) { p = cand; break }
            }
            if (p == null) p = Vec3(44f, 0f, 44f)
        }
        val hp = if (kind == 2) 1300f + wave * 120f else if (kind == 1) 55f else 65f
        val e = Enemy(scale, hp, hp, kind)
        e.pos = p
        enemies.add(e)
        if (kind == 2) { boss = e; banner("THE DOODLER", "block boss — block his ink, slice his scribbles") }
        return e
    }

    /* ---------------- waves ---------------- */
    fun waveUpdate(dt: Float) {
        if (!waveActive) {
            interT -= dt
            if (interT <= 0) {
                wave++
                waveActive = true
                toSpawn = min(18, 4 + wave * 2)
                bossDue = wave % 4 == 0
                spawnT = 0.5f
                if (!bossDue) banner("WAVE $wave", "$toSpawn scribbles incoming")
                sfx?.wave()
            }
            return
        }
        val alive = enemies.count { !it.dead() }
        if (bossDue && boss == null && alive < 8) { spawnEnemy(2); bossDue = false; toSpawn = maxOf(toSpawn, 4) }
        if (toSpawn > 0 && alive < 10) {
            spawnT -= dt
            if (spawnT <= 0) {
                spawnT = maxOf(0.35f, 1.1f - wave * 0.05f)
                toSpawn--
                spawnEnemy(if (Random.nextFloat() < min(0.5f, 0.18f + wave * 0.04f)) 1 else 0)
            }
        }
        if (toSpawn <= 0 && alive == 0) {
            waveActive = false
            interT = 3.5f
            score += 200 * wave
            sfx?.wave()
            banner("WAVE $wave CLEAR", "+${200 * wave} bonus · breathe, reload, re-ink")
        }
    }

    /* ---------------- update ---------------- */
    fun update(dt: Float) {
        val P = player
        val w = weapons[wIdx]
        w.cdT -= dt; grappleCd -= dt; slashCd -= dt; slashT -= dt
        hitFlash = maxOf(0f, hitFlash - dt * 2.2f)
        shake = maxOf(0f, shake - dt * 2.6f)
        kick = maxOf(0f, kick - dt * 7f)
        for (ww in weapons) {
            if (ww.reloadT > 0) {
                ww.reloadT -= dt
                if (ww.reloadT <= 0) {
                    val need = ww.def.mag - ww.mag
                    val take = minOf(need, ww.reserve)
                    ww.mag += take; ww.reserve -= take
                }
            }
        }
        // look input
        P.yaw -= input.lookDX * 0.0032f
        P.pitch = (P.pitch - input.lookDY * 0.0032f).coerceIn(-1.5f, 1.5f)

        val zooming = wIdx == 3 && input.aim
        val blocking = wIdx == 4 && input.aim
        fovT = if (zooming) 32f else if (input.aim && wIdx != 4) 62f else 75f
        fovCur += (fovT - fovCur) * min(1f, dt * 12f)

        // movement
        val fwd = Vec3(-kotlin.math.sin(P.yaw), 0f, -kotlin.math.cos(P.yaw))
        val right = Vec3(kotlin.math.cos(P.yaw), 0f, -kotlin.math.sin(P.yaw))
        val wish = Vec3()
        if (input.moveX != 0f || input.moveY != 0f) {
            wish.addScaled(fwd, input.moveY)
            wish.addScaled(right, input.moveX)
        }
        if (wish.len2() > 0) wish.norm()
        val sprint = kotlin.math.hypot(input.moveX.toDouble(), input.moveY.toDouble()) > 0.92 && !zooming && !blocking
        val speed = (if (sprint) 10.2f else 7.4f) * (if (zooming) 0.55f else 1f) * (if (blocking) 0.45f else 1f)
        val accel = if (P.grounded) 60f else 16f
        P.vel.x += (wish.x * speed - P.vel.x) * min(1f, accel * dt * 0.16f)
        P.vel.z += (wish.z * speed - P.vel.z) * min(1f, accel * dt * 0.16f)
        P.vel.y -= 22f * dt
        if (input.jumpQ && (P.grounded || P.coyote > 0)) {
            P.vel.y = 8.8f; P.grounded = false; P.coyote = 0f
            sfx?.jump()
            grapplePoint = null
        }
        // dash slash
        if (input.dashQ && wIdx == 4 && slashCd <= 0f) {
            slashCd = 3f
            dashT = 0.24f
            dashDir.set(fwd)
            slash(true)
        }
        if (dashT > 0f) {
            dashT -= dt
            P.vel.x = dashDir.x * 26f
            P.vel.z = dashDir.z * 26f
            P.vel.y = maxOf(P.vel.y, 0f)
        }
        // grapple: tap hooks, tap again releases
        if (input.grappleQ) {
            if (grapplePoint != null) grapplePoint = null
            else tryGrapple()
        }
        if (input.reloadQ) startReload()
        if (input.swapQ >= 0) switchWeapon(input.swapQ)
        // grapple pull
        val gp = grapplePoint
        if (gp != null) {
            grappleT += dt
            val eye = P.pos.copy().add(Vec3(0f, 1.6f, 0f))
            val dir = gp.copy().sub(eye)
            val dist = dir.len()
            if (dist < 2.4f || grappleT > 2f) {
                if (dist < 3.2f) P.vel.y = maxOf(P.vel.y, 6.5f)
                grapplePoint = null
            } else {
                dir.norm().scale(30f)
                val k = min(1f, dt * 10f)
                P.vel.x += (dir.x - P.vel.x) * k
                P.vel.y += (dir.y - P.vel.y) * k
                P.vel.z += (dir.z - P.vel.z) * k
                P.grounded = false
            }
        }
        val wasGrounded = P.grounded
        P.grounded = moveEntity(P.pos, P.vel, 0.45f, 1.7f, dt, 0.55f)
        if (P.grounded) P.coyote = 0.12f else P.coyote -= dt
        if (P.pos.y < -8) { P.pos.set(0f, 2f, 26f); P.vel.set(0f, 0f, 0f); damagePlayer(15, P.pos.copy(), "FALL") }
        bobT += dt * (if (P.grounded) P.vel.len() * 1.4f else 0f)
        // firing
        val trigger = input.fire && (w.def.auto || input.firePressed)
        if (wIdx == 4) {
            if (trigger && w.cdT <= 0 && dashT <= 0f) slash(false)
        } else if (trigger && w.cdT <= 0 && w.reloadT <= 0) {
            if (w.mag > 0) fire()
            else { w.cdT = 0.3f; if (input.firePressed) startReload() }
        }
        input.clearPerFrame()

        updateEnemies(dt)
        updateProjs(dt, blocking)
        updateParts(dt)
        updatePickups(dt)
        waveUpdate(dt)
        floats.removeAll { time - it.born > 1.25f }
        feed.removeAll { time - it.born > 3.2f }
        hintT += dt
        if (bannerTitle != null && time > bannerUntil) { bannerTitle = null; bannerSub = null }
    }

    private fun updateEnemies(dt: Float) {
        val P = player
        val eye = P.pos.copy().add(Vec3(0f, 1.4f, 0f))
        val it = enemies.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.flash = maxOf(0f, e.flash - dt * 5f)
            e.yankTimer = maxOf(0f, e.yankTimer - dt)
            val pos = e.pos
            if (e.state == 6) {
                e.deadT += dt
                if (e.deadT > 1.5f) it.remove()
                continue
            }
            val toP = eye.copy().sub(pos); toP.y = 0f
            val dist = toP.len()
            toP.norm()
            e.facing = kotlin.math.atan2(toP.x, toP.z)
            e.t -= dt
            e.atkCd -= dt
            e.walkPhase += dt
            val spd = (if (e.kind == 2) 3.1f else if (e.kind == 1) 3.4f else 4.6f + min(2f, wave * 0.08f)) * (if (e.state == 4) 0f else 1f)
            when (e.state) {
                0 -> { if (e.t <= 0) e.state = 1 }
                5 -> {
                    val dir = eye.copy().sub(pos)
                    val d = dir.len()
                    dir.norm()
                    e.vel.set(dir.scale(26f))
                    if (d < 1.6f) {
                        damageEnemy(e, 30f, false, pos.copy().add(Vec3(0f, 1.2f * e.scale, 0f)), arrayListOf("YANKED"))
                        if (!e.dead()) { e.state = 4; e.t = 1.1f; e.yankTimer = 2.5f }
                        shake = min(1f, shake + 0.2f)
                    }
                }
                4 -> {
                    e.vel.scale(maxOf(0f, 1 - dt * 6f))
                    if (e.t <= 0) e.state = 1
                }
                2 -> {
                    e.vel.scale(maxOf(0f, 1 - dt * 8f))
                    if (e.t <= 0) {
                        e.state = 3; e.t = 0.35f
                        if (e.kind == 1 || e.kind == 2) {
                            val from = pos.copy().add(Vec3(0f, 1.35f * e.scale, 0f))
                            if (e.kind == 2) {
                                if (dist < 5) { damagePlayer(25, eye, "SLAM"); burst(eye, Sketch.RED, 8, 0.2f, 5f); shake = min(1f, shake + 0.4f) }
                                else bossAttack(from)
                            } else {
                                val dir = eye.copy().add(Vec3(0f, 0.2f, 0f)).sub(from).norm()
                                dir.x += (Random.nextFloat() - 0.5f) * 0.08f; dir.y += (Random.nextFloat() - 0.5f) * 0.06f; dir.z += (Random.nextFloat() - 0.5f) * 0.08f
                                spawnProj(from, dir.norm(), 20f, 9f, e)
                            }
                        } else if (dist < 2.6) {
                            damagePlayer(12, eye, "SLAM")
                            burst(eye, Sketch.RED, 6, 0.15f, 4f)
                        }
                    }
                }
                3 -> { if (e.t <= 0) { e.state = 1; e.atkCd = if (e.kind == 2) 1.6f else 1.4f + Random.nextFloat() } }
                else -> { // chase
                    val move = toP.copy()
                    if (e.kind == 1) {
                        if (dist < 9) move.scale(-1f)
                        else if (dist < 16) { move.set(-toP.z, 0f, toP.x); move.scale(if (kotlin.math.sin(e.phase) > 0) 1f else -1f) }
                    }
                    if (e.kind == 2 && !e.summoned && e.hp < e.maxHp * 0.5f) {
                        e.summoned = true
                        for (k in 0 until 3) spawnEnemy(0, pos.copy().add(Vec3((Random.nextFloat() - 0.5f) * 6f, 0f, (Random.nextFloat() - 0.5f) * 6f)))
                        banner("THE DOODLER SUMMONS", "scribble minions join the page")
                    }
                    e.vel.x += (move.x * spd - e.vel.x) * min(1f, dt * 8f)
                    e.vel.z += (move.z * spd - e.vel.z) * min(1f, dt * 8f)
                    e.vel.y -= 22f * dt
                    for (o in enemies) {
                        if (o === e || o.dead()) continue
                        val d2 = o.pos.distTo(pos)
                        if (d2 < 1.2f && d2 > 0.01f) {
                            val push = pos.copy().sub(o.pos); push.y = 0f; push.norm().scale((1.2f - d2) * 6f)
                            e.vel.x += push.x * dt * 8f; e.vel.z += push.z * dt * 8f
                        }
                    }
                    val inRange = if (e.kind == 0) dist < 2.4f else if (e.kind == 2) dist < 24f else dist < 26f && dist > 6f
                    if (e.atkCd <= 0 && inRange && los(pos.copy().add(Vec3(0f, 1.5f * e.scale, 0f)), eye)) {
                        e.state = 2; e.t = if (e.kind == 2) 0.6f else 0.4f
                    }
                }
            }
            if (e.state != 5) e.grounded = moveEntity(pos, e.vel, 0.4f * e.scale, 1.8f * e.scale, dt, 0.5f * e.scale)
            else pos.addScaled(e.vel, dt)
            if (pos.y < -7) {
                if (e.yankTimer > 0) damageEnemy(e, 9999f, false, pos.copy(), arrayListOf("YANKED"))
                else killEnemy(e, ArrayList(), false)
            }
        }
    }

    private fun bossAttack(from: Vec3) {
        val e = boss ?: return
        val eye = player.pos.copy().add(Vec3(0f, 1.4f, 0f))
        e.phase = (e.phase + 1f) % 3f
        val base = eye.copy().sub(from).norm()
        when (e.phase.toInt()) {
            0 -> for (i in -2..2) {
                val d = base.copy()
                rotateY(d, i * 0.14f)
                spawnProj(from, d, 18f, 12f, e, Sketch.RED)
            }
            1 -> for (i in 0 until 12) {
                val a = (i.toFloat() / 12f) * 2f * kotlin.math.PI.toFloat()
                spawnProj(from, Vec3(kotlin.math.cos(a), 0.12f, kotlin.math.sin(a)), 14f, 12f, e, Sketch.RED)
            }
            else -> for (i in 0 until 3) {
                val d = base.copy()
                d.y += 0.06f * i
                d.x += (Random.nextFloat() - 0.5f) * 0.05f; d.z += (Random.nextFloat() - 0.5f) * 0.05f
                spawnProj(from, d.norm(), 24f, 12f, e, Sketch.RED)
            }
        }
    }

    fun rotateY(v: Vec3, ang: Float) {
        val c = kotlin.math.cos(ang); val s = kotlin.math.sin(ang)
        val nx = v.x * c + v.z * s
        val nz = -v.x * s + v.z * c
        v.x = nx; v.z = nz
    }

    fun los(a: Vec3, b: Vec3): Boolean {
        val dir = b.copy().sub(a)
        val dist = dir.len()
        dir.norm()
        for (c in colliders) {
            val t = rayBox(a, dir, c, dist)
            if (t != null && t < dist - 0.3f) return false
        }
        return true
    }

    private fun updateProjs(dt: Float, blocking: Boolean) {
        val eye = player.pos.copy().add(Vec3(0f, 1.3f, 0f))
        val fwd = aimDir(0f)
        val it = projs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            p.pos.addScaled(p.vel, dt)
            var gone = p.life <= 0
            if (!gone && !p.deflected) {
                val toMe = p.pos.copy().sub(eye)
                if (toMe.len() < 2.2f && blocking && p.vel.dot(fwd) < 0) {
                    val back = if (p.owner != null && !p.owner.dead())
                        p.owner.pos.copy().add(Vec3(0f, 1f * p.owner.scale, 0f)).sub(p.pos).norm().scale(34f)
                    else p.vel.copy().scale(-1f).norm().scale(34f)
                    p.vel.set(back)
                    p.deflected = true
                    sfx?.block()
                    score += 10
                    floatText(p.pos.copy(), "BLOCK", 0xFF3D8A4B.toInt())
                    kick = min(1f, kick + 0.4f)
                } else if (p.pos.distTo(eye) < 0.75f) {
                    damagePlayer(p.dmg.toInt(), p.pos.copy())
                    burst(p.pos, Sketch.RED, 6, 0.14f, 4f)
                    gone = true
                }
            } else if (!gone && p.deflected) {
                for (e in enemies) {
                    if (e.dead()) continue
                    val c = e.pos.copy().add(Vec3(0f, 1.1f * e.scale, 0f))
                    if (p.pos.distTo(c) < 0.9f * e.scale) {
                        damageEnemy(e, 60f, false, p.pos.copy(), arrayListOf("BLOCKED"))
                        gone = true
                        break
                    }
                }
            }
            if (!gone) {
                val step = p.vel.copy().norm()
                val prev = p.pos.copy().addScaled(p.vel, -dt)
                for (c in colliders) {
                    val t = rayBox(prev, step, c, p.vel.len() * dt + 0.2f)
                    if (t != null) { gone = true; break }
                }
            }
            if (gone) it.remove()
        }
    }

    private fun updateParts(dt: Float) {
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0) { it.remove(); continue }
            p.vel.y -= p.grav * dt
            p.obj.addScaled(p.vel, dt)
        }
    }

    private fun updatePickups(dt: Float) {
        val it = pickups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.t += dt
            if (p.obj.distTo(player.pos.copy().add(Vec3(0f, 1f, 0f))) < 1.5f) {
                if (p.kind == 0) { player.hp = min(100f, player.hp + 30f); sfx?.pickup(); floatText(p.obj.copy(), "+30 HP", 0xFF3D8A4B.toInt()) }
                else {
                    weapons[0].reserve += 90; weapons[1].reserve += 24; weapons[2].reserve += 30; weapons[3].reserve += 15
                    floatText(p.obj.copy(), "AMMO REFILL", 0xFFB8A013.toInt())
                }
                it.remove()
            }
        }
    }

    fun menuCamera(dt: Float) {
        time += dt
        val a = time * 0.12f
        menuEye.set(kotlin.math.cos(a) * 52f, 24f + kotlin.math.sin(time * 0.3f) * 2f, kotlin.math.sin(a) * 52f)
    }

    val menuEye = Vec3(52f, 24f, 0f)

    fun enemiesLeft(): Int = toSpawn + enemies.count { !it.dead() }
    fun isZooming(): Boolean = wIdx == 3 && input.aim
    fun crosshairSpread(): Float =
        weapons[wIdx].def.spread * 260f + (if (player.grounded) 0f else 10f) +
            (if (player.vel.len() > 2f) 6f else 0f) + (if (wIdx == 4) 8f else 0f)
}

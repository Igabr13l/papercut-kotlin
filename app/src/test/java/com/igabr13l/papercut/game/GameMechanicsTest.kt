package com.igabr13l.papercut.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI

class GameMechanicsTest {

    private lateinit var game: Game
    private val eps = 1e-3f

    @Before
    fun setUp() {
        game = Game()
        game.start() // phase = 1, wave = 0
    }

    @Test
    fun testWeaponDefinitionsAndSwitching() {
        assertEquals(5, game.weapons.size)
        assertEquals("RIFLE", game.weapons[0].def.name)
        assertEquals("SHOTGUN", game.weapons[1].def.name)
        assertEquals("REVOLVER", game.weapons[2].def.name)
        assertEquals("SNIPER", game.weapons[3].def.name)
        assertEquals("KATANA", game.weapons[4].def.name)

        // Initial weapon is Rifle
        assertEquals(0, game.wIdx)
        assertEquals(30, game.weapons[0].mag)
        assertEquals(150, game.weapons[0].reserve)

        // Switch to Shotgun
        game.switchWeapon(1)
        assertEquals(1, game.wIdx)

        // Invalid switch ignored
        game.switchWeapon(9)
        assertEquals(1, game.wIdx)
        game.switchWeapon(-1)
        assertEquals(1, game.wIdx)
    }

    @Test
    fun testFiringAndReloading() {
        val rifle = game.weapons[0]
        assertEquals(30, rifle.mag)

        // Fire once
        game.input.fire = true
        game.input.firePressed = true
        game.update(0.016f)

        assertEquals(29, rifle.mag)
        assertEquals(1, game.shots)
        assertTrue(rifle.cdT > 0f)
        game.input.fire = false

        // Empty mag manually to test reload
        rifle.mag = 0
        game.startReload()
        assertTrue(rifle.reloadT > 0f)

        // Fast-forward past reload time
        val reloadTime = rifle.def.reload
        game.update(reloadTime + 0.1f)

        assertEquals(0f, rifle.reloadT, eps)
        assertEquals(rifle.def.mag, rifle.mag)
        assertEquals(150 - 30, rifle.reserve)

        // Katana cannot be reloaded
        game.switchWeapon(4)
        game.startReload()
        assertEquals(0f, game.weapons[4].reloadT, eps)
    }

    @Test
    fun testDamageAndHeadshotMultiplier() {
        val grunt = game.spawnEnemy(0, Vec3(0f, 0f, 20f))
        val initialHp = grunt.hp

        // Body shot with Revolver (dmg = 44, head = 2.5)
        game.switchWeapon(2)
        game.damageEnemy(grunt, 44f, head = false, Vec3(0f, 1f, 20f), arrayListOf())
        assertEquals(initialHp - 44f, grunt.hp, eps)
        assertEquals(1f, grunt.flash, eps)
        assertEquals(1, game.hits)

        // Headshot on new grunt
        val grunt2 = game.spawnEnemy(0, Vec3(0f, 0f, 20f))
        game.damageEnemy(grunt2, 44f, head = true, Vec3(0f, 1.62f, 20f), arrayListOf())
        // Multiplier is 2.5, so damage is 44 * 2.5 = 110 (lethal to 65hp grunt)
        assertTrue(grunt2.dead())
    }

    @Test
    fun testStyleMultipliersAndScoring() {
        val initialScore = game.score

        val grunt = game.spawnEnemy(0, Vec3(0f, 0f, 20f))
        // Kill with HEADSHOT and AIRBORNE tags
        val tags = arrayListOf("HEADSHOT", "AIRBORNE")
        game.killEnemy(grunt, tags, melee = true)

        assertTrue(grunt.dead())
        assertEquals(1, game.kills)

        // Expected score: base (100) * HEADSHOT(2.0) * AIRBORNE(2.5) = 500
        val expectedScore = initialScore + (100f * 2.0f * 2.5f).toInt()
        assertEquals(expectedScore, game.score)
    }

    @Test
    fun testKatanaMeleeSlash() {
        game.switchWeapon(4) // Katana
        val grunt = game.spawnEnemy(0, Vec3(0f, 0f, 24f)) // In front of player (pos = (0, 0, 26))
        game.player.pitch = 0f
        game.player.yaw = 0f // Facing -Z towards (0, 0, 24)

        game.slash(dash = false)

        assertTrue("Grunt should take katana slash damage", grunt.hp < grunt.maxHp)
    }

    @Test
    fun testKatanaProjectileDeflection() {
        val grunt = game.spawnEnemy(1, Vec3(0f, 0f, 10f))
        val eye = game.player.pos.copy().add(Vec3(0f, 1.3f, 0f))

        // Spawn a projectile moving towards player's eye
        val projPos = eye.copy().add(Vec3(0f, 0f, 1.5f)) // 1.5m in front of eye
        val projVel = Vec3(0f, 0f, -20f) // moving in -Z (towards player)
        game.spawnProj(projPos, Vec3(0f, 0f, 1f), -20f, 15f, grunt)

        val proj = game.projs.first()

        // Player blocks with Katana
        game.switchWeapon(4)
        game.input.aim = true
        game.player.pitch = 0f
        game.player.yaw = (PI).toFloat() // Facing +Z (towards projPos)

        val initialScore = game.score
        val initialHp = game.player.hp

        game.update(0.016f)

        // Projectile should be deflected, player takes NO damage
        assertTrue("Projectile should be deflected", proj.deflected)
        assertEquals(initialHp, game.player.hp, eps)
        assertTrue("Player should gain score for blocking", game.score > initialScore)
    }

    @Test
    fun testGrappleYankEnemy() {
        val grunt = game.spawnEnemy(0, Vec3(0f, 0f, 15f)) // In front of player
        game.player.pitch = 0f
        game.player.yaw = 0f // Facing -Z

        game.tryGrapple()

        // Grunt should enter yank state (5)
        assertEquals(5, grunt.state)
        assertEquals(0f, grunt.t, eps)
    }

    @Test
    fun testWaveProgressionAndBossSpawn() {
        assertEquals(0, game.wave)
        assertFalse(game.waveActive)

        // Fast forward through wave 1 intermission
        game.update(2.0f)
        assertEquals(1, game.wave)
        assertTrue(game.waveActive)
        assertTrue("Wave 1 should have enemies to spawn", game.toSpawn > 0)
        assertFalse("Wave 1 is not a boss wave", game.bossDue)

        // Set wave to 4 to verify THE DOODLER (boss) spawn
        game.wave = 3
        game.waveActive = false
        game.interT = 0.1f
        game.update(0.2f) // triggers wave 4

        assertEquals(4, game.wave)
        assertTrue("Wave 4 must flag bossDue", game.bossDue || game.boss != null)

        // Let update run to spawn the boss
        game.update(0.1f)
        assertNotNull("The Doodler boss must be spawned on Wave 4", game.boss)
        assertEquals(2, game.boss!!.kind)
    }

    @Test
    fun testBarrelExplosionDamage() {
        val barrel = game.barrels.first()
        val grunt = game.spawnEnemy(0, barrel.pos.copy().add(Vec3(1f, 0f, 0f))) // 1m from barrel

        game.explodeBarrel(barrel)

        assertFalse(barrel.alive)
        assertTrue("Enemy near barrel must take explosion damage", grunt.hp < grunt.maxHp)
    }

    @Test
    fun testTracersCreationAndLifecycle() {
        assertEquals(0, game.tracers.size)
        // Fire rifle
        game.fire()
        assertTrue("Tracer should be added when weapon fires", game.tracers.isNotEmpty())
        val initialLife = game.tracers.first().life
        assertTrue(initialLife > 0f)

        // Age tracers past lifetime (0.07s)
        game.update(0.1f)
        assertEquals("Expired tracers should be cleaned up", 0, game.tracers.size)
    }

    @Test
    fun testPickupCollectionAndBobbing() {
        // Drop HP pickup near player
        val initialHp = 50f
        game.player.hp = initialHp
        val pickupPos = game.player.pos.copy().add(Vec3(0f, 0.5f, 0f))
        game.pickups.add(Pickup(pickupPos.copy(), 0, 0f)) // HP pickup

        // Update to trigger pickup collection
        game.update(0.016f)

        assertEquals("Player should receive 30 HP", initialHp + 30f, game.player.hp, eps)
        assertEquals("Pickup should be consumed and removed", 0, game.pickups.size)

        // Ammo pickup test
        val rifle = game.weapons[0]
        val initialReserve = rifle.reserve
        val ammoPos = game.player.pos.copy().add(Vec3(0f, 0.5f, 0f))
        game.pickups.add(Pickup(ammoPos.copy(), 1, 0f)) // Ammo pickup

        game.update(0.016f)
        assertEquals("Rifle reserve should increase by 90", initialReserve + 90, rifle.reserve)
        assertEquals(0, game.pickups.size)

        // Bobbing test for distant pickup
        val farPos = Vec3(20f, 1f, 20f)
        val farPickup = Pickup(farPos.copy(), 0, 0f)
        game.pickups.add(farPickup)
        val originalY = farPickup.obj.y
        game.update(0.1f)
        assertNotEquals("Pickup should bob in y position", originalY, farPickup.obj.y, eps)
    }

    @Test
    fun testDeadEnemySinking() {
        val grunt = game.spawnEnemy(0, Vec3(0f, 0f, 10f))
        game.killEnemy(grunt, arrayListOf(), melee = false)

        assertEquals(6, grunt.state)
        val yBefore = grunt.pos.y

        // After deadT > 0.7s, enemy should sink into the paper
        game.update(0.8f)
        assertTrue("Dead enemy should sink into the notebook paper", grunt.pos.y < yBefore)

        // After deadT > 1.5s, enemy should be removed completely
        game.update(1.0f)
        assertFalse("Dead enemy should be removed after 1.5s", game.enemies.contains(grunt))
    }

    @Test
    fun testThreadSafeInputAccumulation() {
        game.input.addLook(15f, -25f)
        game.input.addLook(5f, 10f)

        assertEquals(20f, game.input.consumeLookX(), eps)
        assertEquals(-15f, game.input.consumeLookY(), eps)

        // Second consume should return 0 (consumed)
        assertEquals(0f, game.input.consumeLookX(), eps)
        assertEquals(0f, game.input.consumeLookY(), eps)
    }
}

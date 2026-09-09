package com.igabr13l.papercut.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class CollisionAndPhysicsTest {

    private lateinit var game: Game
    private val eps = 1e-3f

    @Before
    fun setUp() {
        game = Game()
    }

    @Test
    fun testRaySphereHitAndMiss() {
        val o = Vec3(0f, 0f, 0f)
        val d = Vec3(0f, 0f, 1f) // pointing along +Z
        val cx = 0f; val cy = 0f; val cz = 10f; val r = 2f

        // Direct hit at center of sphere at distance 10, radius 2 -> hit at distance 8
        val hit = game.raySphere(o, d, cx, cy, cz, r, 50f)
        assertNotNull(hit)
        assertEquals(8f, hit!!, eps)

        // Miss to the right
        val dMiss = Vec3(1f, 0f, 0f)
        val miss = game.raySphere(o, dMiss, cx, cy, cz, r, 50f)
        assertNull(miss)

        // Sphere behind ray origin
        val dBack = Vec3(0f, 0f, -1f)
        val behind = game.raySphere(o, dBack, cx, cy, cz, r, 50f)
        assertNull(behind)

        // Max distance cut off
        val tooFar = game.raySphere(o, d, cx, cy, cz, r, 5f)
        assertNull(tooFar)
    }

    @Test
    fun testRayBoxIntersection() {
        // Box centered at (0, 0, 10), dimensions 4 x 4 x 4 (min = (-2, -2, 8), max = (2, 2, 12))
        val box = Collider(Vec3(-2f, -2f, 8f), Vec3(2f, 2f, 12f))

        // Ray from (0, 0, 0) along +Z -> hits front face (z = 8) at distance 8
        val hitZ = game.rayBox(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 1f), box, 50f)
        assertNotNull(hitZ)
        assertEquals(8f, hitZ!!, eps)

        // Ray from (10, 0, 10) along -X -> hits +X face (x = 2) at distance 8
        val hitX = game.rayBox(Vec3(10f, 0f, 10f), Vec3(-1f, 0f, 0f), box, 50f)
        assertNotNull(hitX)
        assertEquals(8f, hitX!!, eps)

        // Ray from (0, 10, 10) along -Y -> hits top face (y = 2) at distance 8
        val hitY = game.rayBox(Vec3(0f, 10f, 10f), Vec3(0f, -1f, 0f), box, 50f)
        assertNotNull(hitY)
        assertEquals(8f, hitY!!, eps)

        // Ray that misses the box
        val miss = game.rayBox(Vec3(5f, 5f, 0f), Vec3(0f, 0f, 1f), box, 50f)
        assertNull(miss)
    }

    @Test
    fun testAimDirCalculation() {
        game.player.pitch = 0f
        game.player.yaw = 0f
        val fwd0 = game.aimDir(0f)
        assertEquals(0f, fwd0.x, eps)
        assertEquals(0f, fwd0.y, eps)
        assertEquals(-1f, fwd0.z, eps)

        // Looking 90 degrees right (yaw = pi/2)
        game.player.yaw = (PI / 2).toFloat()
        val fwd90 = game.aimDir(0f)
        assertEquals(-1f, fwd90.x, eps)
        assertEquals(0f, fwd90.y, eps)
        assertEquals(0f, fwd90.z, eps)

        // Looking up 45 degrees
        game.player.yaw = 0f
        game.player.pitch = (PI / 4).toFloat()
        val fwdUp = game.aimDir(0f)
        assertEquals(0f, fwdUp.x, eps)
        assertTrue(fwdUp.y > 0.7f)
        assertTrue(fwdUp.z < -0.7f)
    }

    @Test
    fun testMoveEntityFlatGround() {
        val pos = Vec3(0f, 0f, 0f)
        val vel = Vec3(5f, 0f, 0f)
        val grounded = game.moveEntity(pos, vel, 0.45f, 1.7f, 0.1f, 0.55f)
        assertEquals(0.5f, pos.x, eps)
        assertEquals(0f, pos.z, eps)
        assertTrue(grounded) // ground box is at y = [-1, 0]
    }

    @Test
    fun testMoveEntityWallCollision() {
        // Ground is at y in [-1, 0]. Add a barrier box at x in [2, 4], y in [0, 3], z in [-5, 5]
        game.colliders.clear()
        game.addBox(0f, -0.5f, 0f, 96f, 1f, 96f) // ground
        game.addBox(3f, 1.5f, 0f, 2f, 3f, 10f)   // wall from x=2 to x=4

        val pos = Vec3(1f, 0f, 0f)
        val vel = Vec3(20f, 0f, 0f) // trying to move fast into the wall at x=2
        val r = 0.45f

        // Move 0.1s: with dt=0.1, vel.x*dt = 2.0. nx = 3.0.
        // nx + r = 3.45 > 2.0 and nx - r = 2.55 < 4.0 -> overlaps collider!
        // Wall top is 3.0, pos.y is 0, stepUp is 0.55 -> wall is too high to step up!
        game.moveEntity(pos, vel, r, 1.7f, 0.1f, 0.55f)

        // Should be stopped at old pos.x and vel.x zeroed
        assertEquals(1f, pos.x, eps)
        assertEquals(0f, vel.x, eps)
    }

    @Test
    fun testMoveEntityStepUpStairs() {
        game.colliders.clear()
        game.addBox(0f, -0.5f, 0f, 96f, 1f, 96f) // ground y in [-1, 0]
        // Step box: x in [1, 3], y in [0, 0.4], z in [-5, 5]. Step height = 0.4 (<= stepUp 0.55)
        game.addBox(2f, 0.2f, 0f, 2f, 0.4f, 10f)

        val pos = Vec3(0.8f, 0f, 0f)
        val vel = Vec3(10f, 0f, 0f) // moving towards step
        val r = 0.45f

        // Moving horizontally into step should automatically step up onto top (y = 0.4 + 0.001)
        game.moveEntity(pos, vel, r, 1.7f, 0.1f, 0.55f)

        assertTrue("Should step up above 0.4", pos.y >= 0.4f)
        assertTrue("Should have advanced forward", pos.x > 0.8f)
    }

    @Test
    fun testMoveEntityGravityAndLanding() {
        game.colliders.clear()
        game.addBox(0f, -0.5f, 0f, 96f, 1f, 96f) // ground top = 0

        val pos = Vec3(0f, 5f, 0f) // falling from height 5
        val vel = Vec3(0f, -10f, 0f)

        var grounded = false
        // Simulate falling over several frames
        for (i in 0 until 10) {
            vel.y -= 22f * 0.05f // gravity
            grounded = game.moveEntity(pos, vel, 0.45f, 1.7f, 0.05f, 0.55f)
            if (grounded) break
        }

        assertTrue("Entity must land on the ground", grounded)
        assertEquals("Entity Y position must snap to ground level (y=0)", 0f, pos.y, eps)
        assertEquals("Vertical velocity must reset to 0 upon landing", 0f, vel.y, eps)
    }

    @Test
    fun testArenaBoundaries() {
        val pos = Vec3(46f, 0f, 0f)
        val vel = Vec3(50f, 0f, 0f) // trying to run outside the arena (+X > 46.5)
        game.moveEntity(pos, vel, 0.45f, 1.7f, 0.1f, 0.55f)
        assertTrue("Entity must be clamped within arena X bounds", pos.x <= 46.5f)
    }
}

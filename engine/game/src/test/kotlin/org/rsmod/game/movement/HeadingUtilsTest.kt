package org.rsmod.game.movement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.map.Direction

class HeadingUtilsTest {
    @Test
    fun `angleToFineDelta uses full circle for intercardinal headings`() {
        val speed = 64
        val east = HeadingUtils.angleToFineDelta(Direction.East.angle, speed)
        val ese = HeadingUtils.angleToFineDelta(Direction.East.angle + 128, speed)
        val se = HeadingUtils.angleToFineDelta(Direction.SouthEast.angle, speed)
        val sse = HeadingUtils.angleToFineDelta(Direction.SouthEast.angle + 128, speed)
        val south = HeadingUtils.angleToFineDelta(Direction.South.angle, speed)

        assertEquals(speed to 0, east)
        assertEquals(0 to -speed, south)
        assertNotEquals(se, ese)
        assertNotEquals(south, sse)
        assertNotEquals(east, ese)
    }

    @Test
    fun `angleFromCoordDelta matches direction angles for cardinals`() {
        assertEquals(Direction.East.angle, HeadingUtils.angleFromCoordDelta(dx = 1, dz = 0))
        assertEquals(Direction.South.angle, HeadingUtils.angleFromCoordDelta(dx = 0, dz = -1))
        assertEquals(Direction.North.angle, HeadingUtils.angleFromCoordDelta(dx = 0, dz = 1))
        assertEquals(Direction.West.angle, HeadingUtils.angleFromCoordDelta(dx = -1, dz = 0))
    }
}

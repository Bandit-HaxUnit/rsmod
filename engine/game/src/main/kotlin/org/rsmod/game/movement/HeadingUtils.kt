package org.rsmod.game.movement

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/** 2048-angle helpers shared by sailing and movement handlers. */
public object HeadingUtils {
    /** Scale factor for [net.rsprot.protocol.game.incoming.misc.user.SetHeading] values (0-15). */
    public const val PACKED_HEADING_SCALE: Int = 128

    private const val ANGLE_UNITS: Int = 2048

    /** Matches [org.rsmod.game.map.Direction.angleBetween] unit conversion. */
    private const val UNITS_PER_RADIAN: Double = ANGLE_UNITS / (2.0 * Math.PI)

    /** Converts a [SetHeading] packet value (0-15) to 2048-angle units. */
    public fun packedHeadingToAngle(heading: Int): Int =
        normalizeAngle(heading * PACKED_HEADING_SCALE)

    /** Maps a coord delta to a 2048-angle (supports all 16 helm headings). */
    public fun angleFromCoordDelta(dx: Int, dz: Int): Int {
        if (dx == 0 && dz == 0) {
            return Direction.South.angle
        }
        return (atan2(-dx.toDouble(), -dz.toDouble()) * UNITS_PER_RADIAN).roundToInt() and
            (ANGLE_UNITS - 1)
    }

    /** Maps a click target to a 2048-angle heading. */
    public fun headingFromCoordDelta(source: CoordGrid, target: CoordGrid): Int =
        angleFromCoordDelta(target.x - source.x, target.z - source.z)

    public fun normalizeAngle(angle: Int): Int = ((angle % ANGLE_UNITS) + ANGLE_UNITS) % ANGLE_UNITS

    public fun reverseAngle(angle: Int): Int = normalizeAngle(angle + 1024)

    public fun shortestAngleDelta(from: Int, to: Int): Int {
        val delta = normalizeAngle(to) - normalizeAngle(from)
        return when {
            delta > 1024 -> delta - ANGLE_UNITS
            delta < -1024 -> delta + ANGLE_UNITS
            else -> delta
        }
    }

    /**
     * Signed turn delta for world-entity rotation while under sail.
     *
     * Prefers the clockwise arc when it is at most half a circle (≤1024 units); otherwise turns
     * counter-clockwise. Reversing course (e.g. north→south) therefore traces a ~180° clockwise
     * arc on the water instead of spinning the long way or taking the west-side semicircle.
     */
    public fun turnAngleDelta(from: Int, to: Int): Int {
        val clockwise = (normalizeAngle(to) - normalizeAngle(from) + ANGLE_UNITS) % ANGLE_UNITS
        if (clockwise == 0) {
            return 0
        }
        return if (clockwise <= 1024) clockwise else clockwise - ANGLE_UNITS
    }

    /** Maps a 2048-angle to a single-tile delta (8-way). */
    public fun angleToTileDelta(angle: Int): Pair<Int, Int> {
        val direction = closestDirection(angle)
        return direction.xOff to direction.zOff
    }

    /** Returns a tile [CoordGrid] [distance] steps along [heading] from [source]. */
    public fun headingFaceTarget(source: CoordGrid, heading: Int, distance: Int = 3): CoordGrid {
        val (dx, dz) = angleToTileDelta(heading)
        return source.translate(dx * distance, dz * distance)
    }

    /**
     * Maps a 2048-angle to a sub-tile fine delta for smooth world-entity movement.
     *
     * Uses the full angle circle so 16-point helm headings (e.g. ESE, SSE) produce distinct
     * movement vectors, not just the nearest 8-way direction.
     */
    public fun angleToFineDelta(angle: Int, speed: Int): Pair<Int, Int> {
        if (speed == 0) {
            return 0 to 0
        }
        val radians = normalizeAngle(angle) * Math.PI / 1024.0
        val fineDx = (-sin(radians) * speed).roundToInt()
        val fineDz = (-cos(radians) * speed).roundToInt()
        return fineDx to fineDz
    }

    /** Maps a 2048-angle to the nearest [Direction] (256-unit sectors). */
    private fun closestDirection(angle: Int): Direction {
        val sector = ((normalizeAngle(angle) + 128) and 2047) / 256
        return when (sector) {
            0 -> Direction.South
            1 -> Direction.SouthWest
            2 -> Direction.West
            3 -> Direction.NorthWest
            4 -> Direction.North
            5 -> Direction.NorthEast
            6 -> Direction.East
            else -> Direction.SouthEast
        }
    }
}

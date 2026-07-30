package org.rsmod.content.skills.sailing

import org.rsmod.content.skills.sailing.BoatContent.FINE_UNITS_PER_TILE
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.movement.HeadingUtils
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Root-world collision checks and hull footprint updates for player-owned boats. */
internal object BoatCollision {
    fun canStep(boat: WorldEntity, collision: CollisionFlagMap, fineDx: Int, fineDz: Int): Boolean {
        if (fineDx == 0 && fineDz == 0) {
            return true
        }
        val before = footprintTiles(boat, boat.rootCoord)
        val (afterRoot, _, _) = candidatePosition(boat, fineDx, fineDz)
        val after = footprintTiles(boat, afterRoot, boat.pendingAngle ?: boat.angle)
        for (tile in after - before) {
            val flags = collision[tile.x, tile.z, boat.projectedLevel]
            if (flags and CollisionFlag.BLOCK_WALK != 0) {
                return false
            }
        }
        return true
    }

    fun syncHullCollision(boat: WorldEntity, collision: CollisionFlagMap) {
        val angle = boat.pendingAngle ?: boat.angle
        val newFootprint = footprintTiles(boat, boat.rootCoord, angle)
        val oldFootprint = boat.hullCollisionTiles.orEmpty()

        for (tile in oldFootprint - newFootprint) {
            if (collision.isZoneAllocated(tile.x, tile.z, boat.projectedLevel)) {
                collision.remove(tile.x, tile.z, boat.projectedLevel, CollisionFlag.BLOCK_WALK)
            }
        }
        for (tile in newFootprint - oldFootprint) {
            collision.allocateIfAbsent(tile.x, tile.z, boat.projectedLevel)
            collision.add(tile.x, tile.z, boat.projectedLevel, CollisionFlag.BLOCK_WALK)
        }

        boat.hullCollisionTiles =
            if (newFootprint.isEmpty()) {
                null
            } else {
                newFootprint.toMutableSet()
            }
    }

    fun clearHullCollision(boat: WorldEntity, collision: CollisionFlagMap) {
        val footprint = boat.hullCollisionTiles.orEmpty()
        for (tile in footprint) {
            if (collision.isZoneAllocated(tile.x, tile.z, boat.projectedLevel)) {
                collision.remove(tile.x, tile.z, boat.projectedLevel, CollisionFlag.BLOCK_WALK)
            }
        }
        boat.hullCollisionTiles = null
    }

    private fun candidatePosition(
        boat: WorldEntity,
        fineDx: Int,
        fineDz: Int,
    ): Triple<CoordGrid, Int, Int> {
        var fineX = boat.subFineX + fineDx
        var fineZ = boat.subFineZ + fineDz
        var root = boat.rootCoord

        if (fineX < 0) {
            val tiles = (-fineX + FINE_UNITS_PER_TILE - 1) / FINE_UNITS_PER_TILE
            root = root.translate(-tiles, 0)
            fineX += tiles * FINE_UNITS_PER_TILE
        } else if (fineX >= FINE_UNITS_PER_TILE) {
            val tiles = fineX / FINE_UNITS_PER_TILE
            root = root.translate(tiles, 0)
            fineX -= tiles * FINE_UNITS_PER_TILE
        }

        if (fineZ < 0) {
            val tiles = (-fineZ + FINE_UNITS_PER_TILE - 1) / FINE_UNITS_PER_TILE
            root = root.translate(0, -tiles)
            fineZ += tiles * FINE_UNITS_PER_TILE
        } else if (fineZ >= FINE_UNITS_PER_TILE) {
            val tiles = fineZ / FINE_UNITS_PER_TILE
            root = root.translate(0, tiles)
            fineZ -= tiles * FINE_UNITS_PER_TILE
        }

        return Triple(root, fineX, fineZ)
    }

    private fun footprintTiles(
        boat: WorldEntity,
        root: CoordGrid,
        angle: Int = boat.angle,
    ): Set<CoordGrid> {
        val rotation = footprintRotation(angle)
        val width = boat.sizeX * ZoneGrid.LENGTH
        val length = boat.sizeZ * ZoneGrid.LENGTH
        return buildSet {
            for (localX in 0 until width) {
                for (localZ in 0 until length) {
                    val (rotX, rotZ) = rotateLocalOffset(rotation, localX, localZ, width, length)
                    add(root.translate(rotX, rotZ))
                }
            }
        }
    }

    /** Maps a 2048-angle to a 0..3 footprint rotation. */
    private fun footprintRotation(angle: Int): Int =
        (HeadingUtils.normalizeAngle(angle) / 512) and 3

    private fun rotateLocalOffset(
        rotation: Int,
        localX: Int,
        localZ: Int,
        width: Int,
        length: Int,
    ): Pair<Int, Int> {
        val widthExcl = width - 1
        val lengthExcl = length - 1
        return when (rotation) {
            1 -> localZ to (widthExcl - localX)
            2 -> (widthExcl - localX) to (lengthExcl - localZ)
            3 -> (lengthExcl - localZ) to localX
            else -> localX to localZ
        }
    }
}

package org.rsmod.content.skills.sailing

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.sailing.BoatContent.FINE_UNITS_PER_TILE
import org.rsmod.content.skills.sailing.BoatContent.FULL_SAIL_FINE_SPEED
import org.rsmod.content.skills.sailing.BoatContent.HALF_SAIL_FINE_SPEED
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_HALF
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_NAVIGATING
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_REVERSE
import org.rsmod.content.skills.sailing.BoatContent.TURN_FINE_SPEED
import org.rsmod.content.skills.sailing.BoatContent.TURN_RATE
import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.movement.HeadingUtils
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Per-tick sailing movement for player-owned boat world entities. */
internal object BoatMovement {
    fun tick(boat: WorldEntity, owner: Player, collision: CollisionFlagMap) {
        val moveMode = owner.vars[sailing_varbits.sidepanel_boat_move_mode]
        val underSail = owner.vars[sailing_varbits.sidepanel_sail_button_toggled] != 0
        if (
            !underSail ||
                moveMode !in setOf(MOVE_MODE_HALF, MOVE_MODE_NAVIGATING, MOVE_MODE_REVERSE)
        ) {
            syncSpawnVarbits(boat, owner)
            BoatCollision.syncHullCollision(boat, collision)
            return
        }

        val currentAngle = boat.pendingAngle ?: boat.angle
        val targetHeading =
            if (moveMode == MOVE_MODE_REVERSE) {
                HeadingUtils.reverseAngle(boat.navigationHeading)
            } else {
                boat.navigationHeading
            }

        val angleDelta = HeadingUtils.turnAngleDelta(currentAngle, targetHeading)
        if (angleDelta != 0) {
            val step = angleDelta.coerceIn(-TURN_RATE, TURN_RATE)
            val newAngle = HeadingUtils.normalizeAngle(currentAngle + step)
            boat.pendingAngle = newAngle
            val (fineDx, fineDz) =
                HeadingUtils.angleToFineDelta(newAngle, scaledSpeed(boat, TURN_FINE_SPEED))
            stepFine(boat, collision, fineDx, fineDz)
            syncSpawnVarbits(boat, owner)
            BoatCollision.syncHullCollision(boat, collision)
            return
        }

        boat.pendingAngle?.let {
            boat.angle = it
            boat.pendingAngle = null
        }

        val speed =
            when (moveMode) {
                MOVE_MODE_HALF -> scaledSpeed(boat, HALF_SAIL_FINE_SPEED)
                else -> scaledSpeed(boat, FULL_SAIL_FINE_SPEED)
            }
        val (fineDx, fineDz) = HeadingUtils.angleToFineDelta(targetHeading, speed)
        stepFine(boat, collision, fineDx, fineDz)
        syncSpawnVarbits(boat, owner)
        BoatCollision.syncHullCollision(boat, collision)
    }

    private fun scaledSpeed(boat: WorldEntity, baseSpeed: Int): Int =
        baseSpeed * boat.sailSpeedMultiplier.coerceAtLeast(1)

    fun syncSpawnVarbits(boat: WorldEntity, owner: Player) {
        VarPlayerIntMapSetter.set(
            owner,
            sailing_varbits.boat_spawned_angle,
            boat.pendingAngle ?: boat.angle,
        )
        VarPlayerIntMapSetter.set(owner, sailing_varbits.boat_spawned_finex, boat.subFineX)
        VarPlayerIntMapSetter.set(owner, sailing_varbits.boat_spawned_finez, boat.subFineZ)
    }

    private fun stepFine(
        boat: WorldEntity,
        collision: CollisionFlagMap,
        fineDx: Int,
        fineDz: Int,
    ): Boolean {
        if (fineDx == 0 && fineDz == 0) {
            return false
        }
        if (!BoatCollision.canStep(boat, collision, fineDx, fineDz)) {
            return false
        }

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

        boat.rootCoord = root
        boat.subFineX = fineX
        boat.subFineZ = fineZ
        return true
    }
}

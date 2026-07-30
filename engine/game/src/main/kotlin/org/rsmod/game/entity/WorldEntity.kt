package org.rsmod.game.entity

import org.rsmod.game.entity.PathingEntity.Companion.INVALID_SLOT
import org.rsmod.game.entity.worldentity.NoopWorldEntityInfo
import org.rsmod.game.entity.worldentity.WorldEntityInfoProtocol
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid

public class WorldEntity(
    public val typeId: Int,
    public var rootCoord: CoordGrid,
    public val avatar: WorldEntityAvatar = WorldEntityAvatar(),
) {
    public var slotId: Int = INVALID_SLOT

    /** Player slot that owns this entity; 0 if unowned. */
    public var ownerPlayerSlot: Int = 0

    /** Instance size in zones (8 tiles per zone). */
    public var sizeX: Int = 2
    public var sizeZ: Int = 2

    /** South-western zone of the instance in the root world. */
    public var southWestZoneX: Int = rootCoord.x shr 3
    public var southWestZoneZ: Int = rootCoord.z shr 3

    public var minLevel: Int = 0
    public var maxLevel: Int = 3

    /** Level at which the hull is projected in the root world. */
    public var projectedLevel: Int = rootCoord.level

    /** Default level for entities inside the instance. */
    public var activeLevel: Int = 1

    public var angle: Int = 0

    /** Desired travel heading while navigating (2048-angle units). */
    public var navigationHeading: Int = angle

    /** Sub-tile fine offset within the root tile (0–128, centre = 64). */
    public var subFineX: Int = FINE_TILE_SIZE / 2

    public var subFineZ: Int = FINE_TILE_SIZE / 2

    public var pendingAngle: Int? = null
    public var pendingCoordJump: Boolean = false

    /** Dev/testing multiplier applied to sail and turn fine speeds (1 = normal). */
    public var sailSpeedMultiplier: Int = 1

    /** Root-world tiles currently blocked by this entity's hull collision footprint. */
    public var hullCollisionTiles: MutableSet<CoordGrid>? = null

    public var infoProtocol: WorldEntityInfoProtocol
        get() = avatar.infoProtocol
        set(value) {
            avatar.infoProtocol = value
        }

    /** Fine pivot of the entity centre in the root world (rsprot `WorldEntityAvatar` convention). */
    public val fineX: Int
        get() = rootCoord.x * FINE_TILE_SIZE + subFineX

    public val fineZ: Int
        get() = rootCoord.z * FINE_TILE_SIZE + subFineZ

    /** Returns `true` if [coords] falls within this entity's instance zones (any level). */
    public fun containsInstanceCoords(coords: CoordGrid): Boolean {
        val zoneX = coords.x shr 3
        val zoneZ = coords.z shr 3
        return zoneX >= southWestZoneX &&
            zoneX < southWestZoneX + sizeX &&
            zoneZ >= southWestZoneZ &&
            zoneZ < southWestZoneZ + sizeZ
    }

    public fun disableAvatar() {
        infoProtocol.disable()
        infoProtocol = NoopWorldEntityInfo
    }

    public companion object {
        public const val FINE_TILE_SIZE: Int = 128

        private fun entityCenterFineOffset(sizeZones: Int): Int =
            ((sizeZones * ZoneGrid.LENGTH - 1) * FINE_TILE_SIZE) / 2
    }
}

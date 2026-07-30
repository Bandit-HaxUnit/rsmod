package org.rsmod.content.skills.sailing

import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

/** Shared constants and helpers for player-owned sailing boats (The Pandemonium raft). */
internal object BoatContent {
    /** World entity config type for the starter raft (`worldentity=(id=1)` in trace). */
    const val WORLDENTITY_TYPE_RAFT = 1

    /**
     * South-west tile of the boat hull while moored at the Port Sarim sailing dock. The live trace
     * reports the entity centered at (3074.5, 2987.5, 0) with an 8x8 footprint.
     */
    val PORT_SARIM_MOORING = CoordGrid(x = 3071, z = 2984, level = 0)

    /** Tile west of the Port Sarim gangplank after disembarking (`0_47_46_61_43` in live trace). */
    val PORT_SARIM_GANGPLANK_DISEMBARK = CoordGrid(x = 3069, z = 2987, level = 0)

    /** Port Sarim dock gangplank used for board/disembark (`0_47_46_62_43` in live trace). */
    val PORT_SARIM_GANGPLANK = CoordGrid(x = 3070, z = 2987, level = 0)

    /** Facing angle while moored at spawn (`sailing_boat_spawned_angle` on first board). */
    const val PORT_SARIM_MOORING_ANGLE = 1024

    const val INSTANCE_BASE_ZONE_X = 480
    const val INSTANCE_BASE_ZONE_Z = 807
    const val INSTANCE_ZONE_SPACING = 4
    const val INSTANCE_GRID_COLUMNS = 32

    /** Entities inside boat instances are placed on level 1. */
    const val DECK_LEVEL = 1

    /** Walkable deck lane of the 1x3 raft, relative to the instance zone. */
    const val DECK_X = 3
    const val DECK_MIN_Z = 2
    const val DECK_MAX_Z = 5

    /** Boarding drops the player on the helm tile (z offset 4 in the live trace). */
    const val BOARDING_Z = 4

    const val ROLE_CAPTAIN = 10
    const val BOAT_TYPE_PANDEMONIUM_RAFT = 8110
    /** Sidepanel move mode while navigating from the helm (trace tick 6752). */
    const val MOVE_MODE_NAVIGATING = 2
    /** Sidepanel move mode while moored aboard (login-aboard trace). */
    const val MOVE_MODE_MOORED = 4
    /** Sidepanel move mode: half sail forward (trace `if_buttonx` sub=2). */
    const val MOVE_MODE_HALF = 1
    /** Sidepanel move mode: reverse (trace `if_buttonx` sub=1). */
    const val MOVE_MODE_REVERSE = 3
    /** Sidepanel move mode: stopped / not under sail. */
    const val MOVE_MODE_STOPPED = 0

    /** Angle units per tick while turning to face heading (trace: 640→512 in one tick). */
    const val TURN_RATE = 128

    /** Sub-tile units per root tile (matches `WorldEntity.FINE_TILE_SIZE`). */
    const val FINE_UNITS_PER_TILE = 128

    /** Sub-tile speed per tick at full sail (trace: ~64 units/tick, one tile ≈ 2 ticks). */
    const val FULL_SAIL_FINE_SPEED = 64

    /** Sub-tile speed per tick at half sail (trace: ~32 units/tick, one tile ≈ 4 ticks). */
    const val HALF_SAIL_FINE_SPEED = 32

    /** Sub-tile slide per tick while turning (trace tick 2227: finex 64→0). */
    const val TURN_FINE_SPEED = 64

    const val FINE_CENTRE = FINE_UNITS_PER_TILE / 2
    const val FACILITY_HOTSPOT_RAFT = 15
    /** `sailing_boat_facility_lockedin` while at the helm (trace tick 6751). */
    const val FACILITY_LOCKED_HELM = 3
    const val RAFT_HP = 20
    const val HELM_STATUS_FREE = 1
    const val HELM_STATUS_NAVIGATING = 2
    const val RAFT_REPAIR_KITS = 10

    /** Clientscript run when entering/leaving helm control (trace ticks 6752 / 6758). */
    const val CS2_SAILING_HELM_UPDATE = 8778

    /** Clientscripts run when opening the sailing sidepanel (board + login-aboard trace). */
    const val CS2_SAILING_SIDEPANEL_INIT = 8776
    const val CS2_SAILING_SIDEPANEL_MODE = 915

    /** CS2 948 duration for gangplank board/disembark fades (live trace uses 15). */
    const val BOARD_FADE_CLIENT_DURATION = 15

    val WorldEntity.boardingCoord: CoordGrid
        get() =
            CoordGrid(
                x = southWestZoneX * ZoneGrid.LENGTH + DECK_X,
                z = southWestZoneZ * ZoneGrid.LENGTH + BOARDING_Z,
                level = activeLevel,
            )

    fun instanceZone(playerSlot: Int): ZoneKey {
        val column = (playerSlot - 1) % INSTANCE_GRID_COLUMNS
        val row = (playerSlot - 1) / INSTANCE_GRID_COLUMNS
        return ZoneKey(
            x = INSTANCE_BASE_ZONE_X + column * INSTANCE_ZONE_SPACING,
            z = INSTANCE_BASE_ZONE_Z + row * INSTANCE_ZONE_SPACING,
            level = DECK_LEVEL,
        )
    }

    fun Player.isAboardOwnedBoat(): Boolean =
        vars[sailing_varbits.boarded_boat] != 0 ||
            vars[sailing_varbits.player_is_on_player_boat] != 0
}

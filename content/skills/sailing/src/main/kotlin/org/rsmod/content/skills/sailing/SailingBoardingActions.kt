package org.rsmod.content.skills.sailing

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.content.skills.sailing.BoatContent.BOAT_TYPE_PANDEMONIUM_RAFT
import org.rsmod.content.skills.sailing.BoatContent.CS2_SAILING_HELM_UPDATE
import org.rsmod.content.skills.sailing.BoatContent.CS2_SAILING_SIDEPANEL_INIT
import org.rsmod.content.skills.sailing.BoatContent.CS2_SAILING_SIDEPANEL_MODE
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_MOORED
import org.rsmod.content.skills.sailing.BoatContent.DECK_LEVEL
import org.rsmod.content.skills.sailing.BoatContent.DECK_MAX_Z
import org.rsmod.content.skills.sailing.BoatContent.DECK_MIN_Z
import org.rsmod.content.skills.sailing.BoatContent.DECK_X
import org.rsmod.content.skills.sailing.BoatContent.FACILITY_HOTSPOT_RAFT
import org.rsmod.content.skills.sailing.BoatContent.HELM_STATUS_FREE
import org.rsmod.content.skills.sailing.BoatContent.PORT_SARIM_GANGPLANK_DISEMBARK
import org.rsmod.content.skills.sailing.BoatContent.PORT_SARIM_MOORING
import org.rsmod.content.skills.sailing.BoatContent.PORT_SARIM_MOORING_ANGLE
import org.rsmod.content.skills.sailing.BoatContent.RAFT_HP
import org.rsmod.content.skills.sailing.BoatContent.RAFT_REPAIR_KITS
import org.rsmod.content.skills.sailing.BoatContent.ROLE_CAPTAIN
import org.rsmod.content.skills.sailing.BoatContent.WORLDENTITY_TYPE_RAFT
import org.rsmod.content.skills.sailing.BoatContent.boardingCoord
import org.rsmod.content.skills.sailing.BoatContent.instanceZone
import org.rsmod.content.skills.sailing.configs.sailing_components
import org.rsmod.content.skills.sailing.configs.sailing_interfaces
import org.rsmod.content.skills.sailing.configs.sailing_locs
import org.rsmod.content.skills.sailing.configs.sailing_seqs
import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.content.skills.sailing.configs.sailing_varps
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Shared board/disembark logic for gameplay scripts and dev commands. */
class SailingBoardingActions
@Inject
constructor(
    private val worldEntityRepo: WorldEntityRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val locTypes: LocTypeList,
    private val collision: CollisionFlagMap,
    private val helmActions: SailingHelmActions,
) {
    suspend fun ProtectedAccess.boardOwnedBoat(withFade: Boolean = true) {
        if (withFade) {
            beginGangplankBoardFade()
            delay(1)
        }
        boardOwnedBoatCore()
        if (withFade) {
            delay(1)
            endGangplankBoardFade()
            delay(1)
            closeFadeOverlay(cycles = 1)
        }
    }

    private fun ProtectedAccess.boardOwnedBoatCore() {
        val boat = worldEntityRepo.findByOwner(player.slotId) ?: spawnMooredBoat(player.slotId)
        logger.info {
            "Boarding sailing boat for '${player.username}': slot=${player.slotId}, " +
                "boatIndex=${boat.slotId}, deck=${boat.boardingCoord}, root=${boat.rootCoord}, " +
                "swZone=(${boat.southWestZoneX}, ${boat.southWestZoneZ})"
        }
        spam("You board your boat.")
        applyBoardedState(boat)
        telejump(boat.boardingCoord)
        player.pendingAboardWorldEntityRebuild = true
        ifOpenOverlay(sailing_interfaces.sidepanel, components.toplevel_target_side0)
        ifSetEvents(sailing_components.facilities_content_clicklayer, 0..2, IfEvent.Op1)
    }

    /** Live trace tick 7: fade to black, hide minimap, clear HP hud overlay. */
    private fun ProtectedAccess.beginGangplankBoardFade() {
        minimapHideMap()
        clearHealthHud()
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = BoatContent.BOARD_FADE_CLIENT_DURATION,
        )
    }

    /** Live trace tick 9: fade back in and restore minimap. */
    private fun ProtectedAccess.endGangplankBoardFade() {
        minimapReset()
        clearHealthHud()
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = BoatContent.BOARD_FADE_CLIENT_DURATION,
        )
    }

    fun ProtectedAccess.disembarkOwnedBoat(boat: WorldEntity) {
        logger.info { "Disembarking sailing boat for '${player.username}'." }
        player.avatar.boatHelmHeadingMode = false
        if (vars[sailing_varbits.sidepanel_player_at_helm] != 0) {
            resetHelmVisuals(boat.boardingCoord)
            runClientScript(CS2_SAILING_HELM_UPDATE, "", 1, "", 1)
            helmActions.clearHelmInteractionMode(player, boat)
        }
        spam("You disembark at the Pandemonium.")
        clearBoardedState()
        ifCloseSub(sailing_interfaces.sidepanel)
        ifOpenOverlay(interfaces.combat_interface, components.toplevel_target_side0)
        telejump(PORT_SARIM_GANGPLANK_DISEMBARK)
    }

    fun spawnMooredBoat(
        playerSlot: Int,
        instanceZone: ZoneKey = instanceZone(playerSlot),
    ): WorldEntity {
        buildRaftDeck(instanceZone)
        val boat =
            WorldEntity(typeId = WORLDENTITY_TYPE_RAFT, rootCoord = PORT_SARIM_MOORING).apply {
                ownerPlayerSlot = playerSlot
                sizeX = 1
                sizeZ = 1
                projectedLevel = PORT_SARIM_MOORING.level
                activeLevel = DECK_LEVEL
                southWestZoneX = instanceZone.x
                southWestZoneZ = instanceZone.z
                angle = PORT_SARIM_MOORING_ANGLE
                navigationHeading = angle
                subFineX = BoatContent.FINE_CENTRE
                subFineZ = BoatContent.FINE_CENTRE
            }
        worldEntityRepo.add(boat)
        BoatCollision.syncHullCollision(boat, collision)
        logger.info {
            "Spawned sailing boat world entity: owner=$playerSlot, index=${boat.slotId}, " +
                "root=${boat.rootCoord}, swZone=(${boat.southWestZoneX}, " +
                "${boat.southWestZoneZ}), deck=${boat.boardingCoord}"
        }
        return boat
    }

    fun ProtectedAccess.printBoatInfo(boat: WorldEntity) {
        player.mes(
            "boat slot=${boat.slotId} root=${boat.rootCoord} fine=(${boat.subFineX}, " +
                "${boat.subFineZ}) angle=${boat.angle} heading=${boat.navigationHeading} " +
                "projected=${boat.projectedLevel} active=${boat.activeLevel} " +
                "swZone=(${boat.southWestZoneX}, ${boat.southWestZoneZ}) " +
                "moveMode=${vars[sailing_varbits.sidepanel_boat_move_mode]} " +
                "atHelm=${vars[sailing_varbits.sidepanel_player_at_helm]} " +
                "speedMult=${boat.sailSpeedMultiplier} " +
                "helmMode=${player.avatar.boatHelmHeadingMode}"
        )
    }

    private fun ProtectedAccess.applyBoardedState(boat: WorldEntity) {
        vars[sailing_varbits.boarded_boat] = 1
        vars[sailing_varbits.boarded_boat_world] = boat.slotId
        vars[sailing_varbits.player_is_on_player_boat] = 1
        vars[sailing_varbits.boat_spawned] = 1
        vars[sailing_varbits.boat_spawned_angle] = boat.angle
        vars[sailing_varbits.boat_spawned_finex] = boat.subFineX
        vars[sailing_varbits.boat_spawned_finez] = boat.subFineZ
        vars[sailing_varbits.boarded_boat_last_dock] = 1
        vars[sailing_varbits.preloaded_anims] = 1

        vars[sailing_varbits.sidepanel_player_role] = ROLE_CAPTAIN
        vars[sailing_varps.sidepanel_boat_type] = BOAT_TYPE_PANDEMONIUM_RAFT
        vars[sailing_varbits.sidepanel_players_on_board_total] = 1
        vars[sailing_varbits.sidepanel_facility_hotspot0] = FACILITY_HOTSPOT_RAFT
        vars[sailing_varbits.sidepanel_boat_hp_max] = RAFT_HP
        vars[sailing_varbits.sidepanel_boat_hp] = RAFT_HP
        vars[sailing_varbits.sidepanel_helm_status] = HELM_STATUS_FREE
        vars[sailing_varbits.sidepanel_repairkits] = RAFT_REPAIR_KITS
        vars[sailing_varbits.sidepanel_visible] = 1
        vars[sailing_varbits.sidepanel_visible_from_combat_tab] = 1
        vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_MOORED

        runClientScript(CS2_SAILING_SIDEPANEL_INIT, player.displayName, 1, "", 1)
        runClientScript(CS2_SAILING_SIDEPANEL_MODE, 0)
    }

    private fun ProtectedAccess.clearBoardedState() {
        vars[sailing_varbits.boarded_boat] = 0
        vars[sailing_varbits.boarded_boat_world] = 0
        vars[sailing_varbits.player_is_on_player_boat] = 0
        vars[sailing_varbits.boat_facility_lockedin] = 0
        vars[sailing_varbits.sidepanel_visible] = 0
        vars[sailing_varbits.sidepanel_players_on_board_total] = 0
        vars[sailing_varbits.sidepanel_facility_hotspot0] = 0
        vars[sailing_varbits.sidepanel_helm_status] = 0
        vars[sailing_varbits.sidepanel_player_at_helm] = 0
        vars[sailing_varbits.sidepanel_boat_move_mode] = 0
        vars[sailing_varbits.sidepanel_sail_button_toggled] = 0
        vars[sailing_varbits.sidepanel_repairkits] = 0
        vars[sailing_varbits.sidepanel_boat_hp_max] = 0
        vars[sailing_varbits.sidepanel_boat_hp] = 0
        vars[sailing_varps.sidepanel_boat_type] = -1
    }

    private fun ProtectedAccess.resetHelmVisuals(helmCoords: CoordGrid) {
        val helm = deckLoc(helmCoords, sailing_locs.steering_kandarin_1x3_wood) ?: return
        locAnim(worldRepo, helm, sailing_seqs.helm_inactive)
    }

    private fun ProtectedAccess.deckLoc(coords: CoordGrid, type: LocType): BoundLocInfo? {
        val loc = locRepo.findExact(coords, type) ?: return null
        return BoundLocInfo(loc, locTypes[type])
    }

    private fun buildRaftDeck(zone: ZoneKey) {
        val base = zone.toCoords()
        for (dx in 0 until ZoneGrid.LENGTH) {
            for (dz in 0 until ZoneGrid.LENGTH) {
                collision.allocateIfAbsent(base.x + dx, base.z + dz, base.level)
                val deckLane = dx == DECK_X && dz in DECK_MIN_Z..DECK_MAX_Z
                if (!deckLane) {
                    collision.add(base.x + dx, base.z + dz, base.level, CollisionFlag.BLOCK_WALK)
                }
            }
        }

        fun spawn(dx: Int, dz: Int, type: LocType, shape: LocShape) {
            val coords = base.translate(dx, dz)
            locRepo.add(coords, type, Int.MAX_VALUE, LocAngle.West, shape)
        }

        spawn(DECK_X, 2, sailing_locs.cargo_hold_regular_raft, LocShape.CentrepieceStraight)
        spawn(DECK_X, 3, sailing_locs.sail_kandarin_1x3_wood, LocShape.CentrepieceStraight)
        spawn(DECK_X, 4, sailing_locs.steering_kandarin_1x3_wood, LocShape.CentrepieceStraight)
        spawn(DECK_X, 5, sailing_locs.sail_kandarin_1x3_linen, LocShape.CentrepieceStraight)

        for (dx in 2..4) {
            for (dz in DECK_MIN_Z..DECK_MAX_Z) {
                spawn(dx, dz, sailing_locs.invisible_nonblocking, LocShape.GroundDecor)
            }
        }

        spawn(3, 2, sailing_locs.bgsound_ocean_water_loop, LocShape.GroundDecor)
        spawn(4, 3, sailing_locs.randomsound_ocean_gulls, LocShape.GroundDecor)
        spawn(4, 4, sailing_locs.randomsound_ocean_crashing_waves, LocShape.GroundDecor)
    }

    private companion object {
        val logger = InlineLogger()
    }
}

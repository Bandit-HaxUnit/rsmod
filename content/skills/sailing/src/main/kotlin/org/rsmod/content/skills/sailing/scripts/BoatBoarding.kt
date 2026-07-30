package org.rsmod.content.skills.sailing.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.components
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.skills.sailing.BoatContent.BOAT_TYPE_PANDEMONIUM_RAFT
import org.rsmod.content.skills.sailing.BoatContent.CS2_SAILING_SIDEPANEL_INIT
import org.rsmod.content.skills.sailing.BoatContent.CS2_SAILING_SIDEPANEL_MODE
import org.rsmod.content.skills.sailing.BoatContent.FACILITY_HOTSPOT_RAFT
import org.rsmod.content.skills.sailing.BoatContent.HELM_STATUS_FREE
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_MOORED
import org.rsmod.content.skills.sailing.BoatContent.RAFT_HP
import org.rsmod.content.skills.sailing.BoatContent.RAFT_REPAIR_KITS
import org.rsmod.content.skills.sailing.BoatContent.ROLE_CAPTAIN
import org.rsmod.content.skills.sailing.BoatContent.boardingCoord
import org.rsmod.content.skills.sailing.BoatContent.isAboardOwnedBoat
import org.rsmod.content.skills.sailing.SailingBoardingActions
import org.rsmod.content.skills.sailing.SailingHelmActions
import org.rsmod.content.skills.sailing.configs.sailing_components
import org.rsmod.content.skills.sailing.configs.sailing_interfaces
import org.rsmod.content.skills.sailing.configs.sailing_locs
import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.content.skills.sailing.configs.sailing_varps
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Boards and disembarks the player on their own boat ("The Pandemonium" starter raft) via the
 * gangplank, using world entities.
 *
 * Live trace (`rsprox-logs/real_game_sailing_logs_jag_coords.txt`):
 * - Board from dock: **`oploc2_v2`** on gangplank 59836
 * - Disembark aboard: **`oploc1_v2`** on the dock gangplank 59836 (no deck copy)
 */
class BoatBoarding
@Inject
constructor(
    private val worldEntityRepo: WorldEntityRepository,
    private val boardingActions: SailingBoardingActions,
    private val helmActions: SailingHelmActions,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(sailing_locs.gangplank_the_pandemonium) { disembarkFromGangplank() }
        onOpLoc2(sailing_locs.gangplank_the_pandemonium) { boardFromGangplank() }
        onEvent<SessionStateEvent.MapPrepare> { player.restoreOwnedBoat() }
        onPlayerLogin { player.restoreOwnedBoat() }
    }

    private suspend fun ProtectedAccess.boardFromGangplank() {
        val boat = worldEntityRepo.findByOwner(player.slotId)
        val onDeck = boat?.containsInstanceCoords(player.coords) == true
        if (onDeck) {
            return
        }
        if (player.isAboardOwnedBoat() && boat != null) {
            telejump(boat.boardingCoord)
            return
        }
        with(boardingActions) { boardOwnedBoat() }
    }

    private fun ProtectedAccess.disembarkFromGangplank() {
        val boat = worldEntityRepo.findByOwner(player.slotId) ?: return
        if (!player.isAboardOwnedBoat()) {
            return
        }
        with(boardingActions) { disembarkOwnedBoat(boat) }
    }

    private fun Player.restoreOwnedBoat() {
        if (!isAboardOwnedBoat()) {
            return
        }

        val existing = worldEntityRepo.findByOwner(slotId)
        if (existing != null) {
            logger.info {
                "Reused sailing boat for '$username' during login: slot=$slotId, " +
                    "boatIndex=${existing.slotId}, coords=$coords, root=${existing.rootCoord}, " +
                    "swZone=(${existing.southWestZoneX}, ${existing.southWestZoneZ})"
            }
            restoreBoardingClientState(existing)
            return
        }

        val stale = worldEntityRepo.findByInstanceCoords(coords)
        if (stale != null) {
            logger.info {
                "Replacing stale sailing boat during login for '$username': " +
                    "oldOwner=${stale.ownerPlayerSlot}, newOwner=$slotId, " +
                    "oldIndex=${stale.slotId}, coords=$coords"
            }
            worldEntityRepo.del(stale)
        }

        val instanceZone = ZoneKey.from(coords)
        val boat = boardingActions.spawnMooredBoat(slotId, instanceZone = instanceZone)
        logger.info {
            "Restored sailing boat for '$username' during login: slot=$slotId, " +
                "boatIndex=${boat.slotId}, coords=$coords, instanceZone=$instanceZone, " +
                "root=${boat.rootCoord}"
        }
        restoreBoardingClientState(boat)
    }

    private fun Player.restoreBoardingClientState(boat: WorldEntity) {
        VarPlayerIntMapSetter.set(this, sailing_varbits.boarded_boat, 1)
        VarPlayerIntMapSetter.set(this, sailing_varbits.boarded_boat_world, boat.slotId)
        VarPlayerIntMapSetter.set(this, sailing_varbits.player_is_on_player_boat, 1)
        VarPlayerIntMapSetter.set(this, sailing_varbits.boat_spawned, 1)
        VarPlayerIntMapSetter.set(this, sailing_varbits.boat_spawned_angle, boat.angle)
        VarPlayerIntMapSetter.set(this, sailing_varbits.boat_spawned_finex, boat.subFineX)
        VarPlayerIntMapSetter.set(this, sailing_varbits.boat_spawned_finez, boat.subFineZ)
        VarPlayerIntMapSetter.set(this, sailing_varbits.boarded_boat_last_dock, 1)
        VarPlayerIntMapSetter.set(this, sailing_varbits.preloaded_anims, 1)

        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_player_role, ROLE_CAPTAIN)
        VarPlayerIntMapSetter.set(
            this,
            sailing_varps.sidepanel_boat_type,
            BOAT_TYPE_PANDEMONIUM_RAFT,
        )
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_players_on_board_total, 1)
        VarPlayerIntMapSetter.set(
            this,
            sailing_varbits.sidepanel_facility_hotspot0,
            FACILITY_HOTSPOT_RAFT,
        )
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_boat_hp_max, RAFT_HP)
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_boat_hp, RAFT_HP)
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_helm_status, HELM_STATUS_FREE)
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_repairkits, RAFT_REPAIR_KITS)
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_visible, 1)
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_visible_from_combat_tab, 1)
        VarPlayerIntMapSetter.set(this, sailing_varbits.sidepanel_boat_move_mode, MOVE_MODE_MOORED)

        runClientScript(CS2_SAILING_SIDEPANEL_INIT, displayName, 1, "", 1)
        runClientScript(CS2_SAILING_SIDEPANEL_MODE, 0)
        ifOpenOverlay(sailing_interfaces.sidepanel, components.toplevel_target_side0, eventBus)
        ifSetEvents(sailing_components.facilities_content_clicklayer, 0..2, IfEvent.Op1)

        if (vars[sailing_varbits.sidepanel_player_at_helm] != 0) {
            avatar.boatHelmHeadingMode = true
            helmActions.applyHelmInteractionMode(this, boat)
        }
    }

    private companion object {
        val logger = InlineLogger()
    }
}

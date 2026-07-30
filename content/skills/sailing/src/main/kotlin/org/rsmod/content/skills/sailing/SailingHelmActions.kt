package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import org.rsmod.api.player.output.InteractionMode
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.content.skills.sailing.BoatContent.CS2_SAILING_HELM_UPDATE
import org.rsmod.content.skills.sailing.BoatContent.CS2_SAILING_SIDEPANEL_MODE
import org.rsmod.content.skills.sailing.BoatContent.FACILITY_LOCKED_HELM
import org.rsmod.content.skills.sailing.BoatContent.HELM_STATUS_FREE
import org.rsmod.content.skills.sailing.BoatContent.HELM_STATUS_NAVIGATING
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_HALF
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_MOORED
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_NAVIGATING
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_REVERSE
import org.rsmod.content.skills.sailing.BoatContent.MOVE_MODE_STOPPED
import org.rsmod.content.skills.sailing.configs.sailing_components
import org.rsmod.content.skills.sailing.configs.sailing_locs
import org.rsmod.content.skills.sailing.configs.sailing_seqs
import org.rsmod.content.skills.sailing.configs.sailing_synths
import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.movement.HeadingUtils
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid

/** Shared helm enter/stop logic for gameplay scripts and dev commands. */
class SailingHelmActions
@Inject
constructor(
    private val worldEntityRepo: WorldEntityRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val locTypes: LocTypeList,
) {
    suspend fun ProtectedAccess.enterHelmNavigation() {
        val boat = worldEntityRepo.findByOwner(player.slotId) ?: return
        val helmCoords = player.coords

        boat.navigationHeading = boat.angle
        player.avatar.boatHelmHeadingMode = true

        vars[sailing_varbits.boat_facility_lockedin] = FACILITY_LOCKED_HELM
        ifSetEvents(sailing_components.facilities_content_clicklayer, 0..2, IfEvent.Op1)
        anim(sailing_seqs.human_helm_active)
        playHelmActiveAnims(helmCoords)
        runClientScript(CS2_SAILING_SIDEPANEL_MODE, 0)
        applyHelmInteractionMode(player, boat)
        faceSquare(HeadingUtils.headingFaceTarget(player.coords, boat.navigationHeading))
        soundSynth(sailing_synths.helm_enter)

        delay(1)

        if (vars[sailing_varbits.sidepanel_boat_move_mode] == MOVE_MODE_STOPPED) {
            vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_MOORED
        }
        vars[sailing_varbits.sidepanel_player_at_helm] = 1
        vars[sailing_varbits.sidepanel_helm_status] = HELM_STATUS_NAVIGATING
        runClientScript(CS2_SAILING_HELM_UPDATE, "", 0, player.displayName, 1)
    }

    suspend fun ProtectedAccess.stopNavigatingFromHelm() {
        val boat = worldEntityRepo.findByOwner(player.slotId) ?: return
        val helmCoords = player.coords

        player.avatar.boatHelmHeadingMode = false
        vars[sailing_varbits.boat_facility_lockedin] = 0
        resetHelmVisuals(helmCoords)
        clearHelmInteractionMode(player, boat)
        faceSquare(helmCoords)
        resetAnim()
        soundSynth(sailing_synths.helm_exit)

        delay(1)

        vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_STOPPED
        vars[sailing_varbits.sidepanel_player_at_helm] = 0
        vars[sailing_varbits.sidepanel_helm_status] = HELM_STATUS_FREE
        vars[sailing_varbits.sidepanel_sail_button_toggled] = 0
        runClientScript(CS2_SAILING_HELM_UPDATE, "", 0, "", 1)
    }

    fun ProtectedAccess.toggleSailSpeed(comsub: Int) {
        if (vars[sailing_varbits.sidepanel_player_at_helm] == 0) {
            return
        }

        val helmCoords = player.coords
        when (comsub) {
            0 -> toggleFullSail(helmCoords)
            1 -> toggleReverseSail(helmCoords)
            2 -> toggleHalfSail(helmCoords)
        }
    }

    fun clearHelmInteractionMode(player: Player, boat: WorldEntity) {
        InteractionMode.resetBoatInteractionMode(player, boat.slotId)
        InteractionMode.restoreDefaultWalkMode(player)
    }

    fun applyHelmInteractionMode(player: Player, boat: WorldEntity) {
        InteractionMode.setPlayerHeadingMode(player)
        InteractionMode.setBoatWalkMode(player, boat.slotId)
    }

    fun applyHeadingClick(player: Player, boat: WorldEntity, dest: CoordGrid) {
        boat.navigationHeading = HeadingUtils.headingFromCoordDelta(player.coords, dest)
    }

    fun applySetHeading(boat: WorldEntity, heading: Int) {
        boat.navigationHeading = HeadingUtils.packedHeadingToAngle(heading)
    }

    suspend fun ProtectedAccess.enableSailIfNeededForHeading() {
        if (vars[sailing_varbits.sidepanel_player_at_helm] == 0) {
            return
        }
        val moveMode = vars[sailing_varbits.sidepanel_boat_move_mode]
        if (moveMode != MOVE_MODE_MOORED && moveMode != MOVE_MODE_STOPPED) {
            return
        }
        delay(1)
        vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_NAVIGATING
        vars[sailing_varbits.sidepanel_sail_button_toggled] = 1
        playFullSailAnims(player.coords)
        playHelmLoopAnims(player.coords)
    }

    fun ProtectedAccess.setSailMode(mode: String) {
        if (vars[sailing_varbits.sidepanel_player_at_helm] == 0) {
            mes("You must be at the helm.")
            return
        }
        val helmCoords = player.coords
        when (mode.lowercase()) {
            "full" -> {
                vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_NAVIGATING
                vars[sailing_varbits.sidepanel_sail_button_toggled] = 1
                playFullSailAnims(helmCoords)
                playHelmLoopAnims(helmCoords)
            }
            "half" -> {
                vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_HALF
                vars[sailing_varbits.sidepanel_sail_button_toggled] = 1
                playHalfSailAnims(helmCoords)
                playHelmLoopAnims(helmCoords)
            }
            "reverse" -> {
                vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_REVERSE
                vars[sailing_varbits.sidepanel_sail_button_toggled] = 1
                playReverseSailAnims(helmCoords)
                playHelmLoopAnims(helmCoords)
            }
            "stop" -> {
                val moveMode = vars[sailing_varbits.sidepanel_boat_move_mode]
                vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_STOPPED
                vars[sailing_varbits.sidepanel_sail_button_toggled] = 0
                when (moveMode) {
                    MOVE_MODE_NAVIGATING -> playFullToDownAnims(helmCoords)
                    MOVE_MODE_HALF -> playHalfToDownAnims(helmCoords)
                    else -> playSailRestAnims(helmCoords)
                }
            }
            else -> mes("Use: full, half, reverse, or stop")
        }
    }

    private fun ProtectedAccess.toggleFullSail(helmCoords: CoordGrid) {
        val moveMode = vars[sailing_varbits.sidepanel_boat_move_mode]
        if (moveMode == MOVE_MODE_NAVIGATING) {
            stopSail(helmCoords, moveMode)
            return
        }
        vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_NAVIGATING
        vars[sailing_varbits.sidepanel_sail_button_toggled] = 1
        playFullSailAnims(helmCoords)
        playHelmLoopAnims(helmCoords)
    }

    private fun ProtectedAccess.toggleReverseSail(helmCoords: CoordGrid) {
        val moveMode = vars[sailing_varbits.sidepanel_boat_move_mode]
        if (moveMode == MOVE_MODE_REVERSE) {
            mes("The boat is already reversing.")
            return
        }
        vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_REVERSE
        vars[sailing_varbits.sidepanel_sail_button_toggled] = 1
        playReverseSailAnims(helmCoords)
        playHelmLoopAnims(helmCoords)
    }

    private fun ProtectedAccess.toggleHalfSail(helmCoords: CoordGrid) {
        val moveMode = vars[sailing_varbits.sidepanel_boat_move_mode]
        if (moveMode == MOVE_MODE_HALF) {
            stopSail(helmCoords, moveMode)
            return
        }
        vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_HALF
        vars[sailing_varbits.sidepanel_sail_button_toggled] = 1
        playHalfSailAnims(helmCoords)
        playHelmLoopAnims(helmCoords)
    }

    private fun ProtectedAccess.stopSail(helmCoords: CoordGrid, fromMode: Int) {
        vars[sailing_varbits.sidepanel_boat_move_mode] = MOVE_MODE_STOPPED
        vars[sailing_varbits.sidepanel_sail_button_toggled] = 0
        when (fromMode) {
            MOVE_MODE_NAVIGATING -> playFullToDownAnims(helmCoords)
            MOVE_MODE_HALF -> playHalfToDownAnims(helmCoords)
            else -> playSailRestAnims(helmCoords)
        }
    }

    private fun ProtectedAccess.playHelmActiveAnims(helmCoords: CoordGrid) {
        deckLoc(helmCoords, sailing_locs.steering_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.helm_active)
        }
    }

    private fun ProtectedAccess.playHelmLoopAnims(helmCoords: CoordGrid) {
        anim(sailing_seqs.human_helm_active_loop)
        deckLoc(helmCoords, sailing_locs.steering_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.helm_active_loop)
        }
    }

    private fun ProtectedAccess.playFullSailAnims(helmCoords: CoordGrid) {
        deckLoc(helmCoords.translateZ(-1), sailing_locs.sail_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_wood_down_to_full)
        }
        deckLoc(helmCoords.translateZ(1), sailing_locs.sail_kandarin_1x3_linen)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_linen_down_to_full_offset)
        }
        soundSynth(sailing_synths.sail_raise)
    }

    private fun ProtectedAccess.playHalfSailAnims(helmCoords: CoordGrid) {
        deckLoc(helmCoords.translateZ(-1), sailing_locs.sail_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_wood_down_to_half)
        }
        deckLoc(helmCoords.translateZ(1), sailing_locs.sail_kandarin_1x3_linen)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_linen_down_to_half_offset)
        }
        soundSynth(sailing_synths.sail_raise)
    }

    private fun ProtectedAccess.playReverseSailAnims(helmCoords: CoordGrid) {
        playSailRestAnims(helmCoords)
    }

    private fun ProtectedAccess.playFullToDownAnims(helmCoords: CoordGrid) {
        deckLoc(helmCoords.translateZ(-1), sailing_locs.sail_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_wood_full_to_down)
        }
        deckLoc(helmCoords.translateZ(1), sailing_locs.sail_kandarin_1x3_linen)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_linen_full_to_down_offset)
        }
        soundSynth(sailing_synths.sail_lower)
    }

    private fun ProtectedAccess.playHalfToDownAnims(helmCoords: CoordGrid) {
        deckLoc(helmCoords.translateZ(-1), sailing_locs.sail_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_wood_half_to_down)
        }
        deckLoc(helmCoords.translateZ(1), sailing_locs.sail_kandarin_1x3_linen)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_linen_half_to_down_offset)
        }
        soundSynth(sailing_synths.sail_lower)
    }

    private fun ProtectedAccess.playSailRestAnims(helmCoords: CoordGrid) {
        deckLoc(helmCoords.translateZ(-1), sailing_locs.sail_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_wood_down)
        }
        deckLoc(helmCoords.translateZ(1), sailing_locs.sail_kandarin_1x3_linen)?.let {
            locAnim(worldRepo, it, sailing_seqs.sail_linen_down_offset)
        }
    }

    private fun ProtectedAccess.resetHelmVisuals(helmCoords: CoordGrid) {
        deckLoc(helmCoords, sailing_locs.steering_kandarin_1x3_wood)?.let {
            locAnim(worldRepo, it, sailing_seqs.helm_inactive)
        }
        playSailRestAnims(helmCoords)
    }

    private fun ProtectedAccess.deckLoc(coords: CoordGrid, type: LocType): BoundLocInfo? {
        val loc = locRepo.findExact(coords, type) ?: return null
        return BoundLocInfo(loc, locTypes[type])
    }
}

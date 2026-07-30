package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.api.script.onCommand
import org.rsmod.content.skills.sailing.BoatContent.FINE_UNITS_PER_TILE
import org.rsmod.content.skills.sailing.BoatContent.boardingCoord
import org.rsmod.content.skills.sailing.BoatContent.isAboardOwnedBoat
import org.rsmod.content.skills.sailing.BoatMovement
import org.rsmod.content.skills.sailing.SailingBoardingActions
import org.rsmod.content.skills.sailing.SailingHelmActions
import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.movement.HeadingUtils
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Dev commands for manual sailing iteration and testing. */
class SailingDevCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldEntityRepo: WorldEntityRepository,
    private val boardingActions: SailingBoardingActions,
    private val helmActions: SailingHelmActions,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("boardboat") {
            desc = "Board your moored raft without using the gangplank"
            cheat(::boardBoat)
        }
        onCommand("disembarkboat") {
            desc = "Disembark from your boat"
            cheat(::disembarkBoat)
        }
        onCommand("boatinfo") {
            desc = "Print owned boat state"
            cheat(::boatInfo)
        }
        onCommand("boatroot") {
            desc = "Set boat hull root coord"
            invalidArgs = "Use as ::boatroot x z [level]"
            cheat(::boatRoot)
        }
        onCommand("boatfine") {
            desc = "Set boat sub-tile fine offset"
            invalidArgs = "Use as ::boatfine x z (0-128)"
            cheat(::boatFine)
        }
        onCommand("boatlevel") {
            desc = "Set boat projected or active level"
            invalidArgs = "Use as ::boatlevel projected|active <level>"
            cheat(::boatLevel)
        }
        onCommand("helm") {
            desc = "Enter helm navigation mode"
            cheat(::enterHelm)
        }
        onCommand("stophelm") {
            desc = "Stop helm navigation mode"
            cheat(::stopHelm)
        }
        onCommand("sail") {
            desc = "Set sail mode: full|half|reverse|stop"
            invalidArgs = "Use as ::sail full|half|reverse|stop"
            cheat(::sail)
        }
        onCommand("boatangle") {
            desc = "Set boat angle (0-2047)"
            invalidArgs = "Use as ::boatangle <angle>"
            cheat(::boatAngle)
        }
        onCommand("boatspeed") {
            desc = "Set boat sail speed multiplier (1 = normal)"
            invalidArgs = "Use as ::boatspeed <multiplier>"
            cheat(::boatSpeed)
        }
    }

    private fun boardBoat(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                if (player.isAboardOwnedBoat()) {
                    worldEntityRepo.findByOwner(player.slotId)?.let { telejump(it.boardingCoord) }
                    mes("You are already aboard.")
                    return@launch
                }
                with(boardingActions) { boardOwnedBoat(withFade = false) }
            }
        }

    private fun disembarkBoat(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                val boat = worldEntityRepo.findByOwner(player.slotId)
                if (boat == null || !player.isAboardOwnedBoat()) {
                    mes("You are not aboard a boat.")
                    return@launch
                }
                with(boardingActions) { disembarkOwnedBoat(boat) }
            }
        }

    private fun boatInfo(cheat: Cheat) =
        with(cheat) {
            val boat =
                worldEntityRepo.findByOwner(player.slotId)
                    ?: run {
                        player.mes("No owned boat found.")
                        return@with
                    }
            protectedAccess.launch(player) { with(boardingActions) { printBoatInfo(boat) } }
        }

    private fun boatRoot(cheat: Cheat) =
        with(cheat) {
            val boat =
                worldEntityRepo.findByOwner(player.slotId)
                    ?: run {
                        player.mes("No owned boat found.")
                        return@with
                    }
            if (args.size < 2) {
                player.mes("Use as ::boatroot x z [level]")
                return@with
            }
            val x = args[0].toIntOrNull()
            val z = args[1].toIntOrNull()
            val level = args.getOrNull(2)?.toIntOrNull() ?: boat.rootCoord.level
            if (x == null || z == null) {
                player.mes("Invalid coordinates.")
                return@with
            }
            boat.rootCoord = CoordGrid(x, z, level)
            player.mes("Set boat root to ${boat.rootCoord}.")
        }

    private fun boatFine(cheat: Cheat) =
        with(cheat) {
            val boat =
                worldEntityRepo.findByOwner(player.slotId)
                    ?: run {
                        player.mes("No owned boat found.")
                        return@with
                    }
            if (args.size < 2) {
                player.mes("Use as ::boatfine x z (0-128)")
                return@with
            }
            val fineX = args[0].toIntOrNull()
            val fineZ = args[1].toIntOrNull()
            if (
                fineX == null ||
                    fineZ == null ||
                    fineX !in 0..FINE_UNITS_PER_TILE ||
                    fineZ !in 0..FINE_UNITS_PER_TILE
            ) {
                player.mes("Fine offsets must be 0-${FINE_UNITS_PER_TILE}.")
                return@with
            }
            boat.subFineX = fineX
            boat.subFineZ = fineZ
            protectedAccess.launch(player) { BoatMovement.syncSpawnVarbits(boat, player) }
            player.mes("Set boat fine offset to ($fineX, $fineZ).")
        }

    private fun boatLevel(cheat: Cheat) =
        with(cheat) {
            val boat =
                worldEntityRepo.findByOwner(player.slotId)
                    ?: run {
                        player.mes("No owned boat found.")
                        return@with
                    }
            if (args.size < 2) {
                player.mes("Use as ::boatlevel projected|active <level>")
                return@with
            }
            val level = args[1].toIntOrNull()
            if (level == null) {
                player.mes("Invalid level.")
                return@with
            }
            when (args[0].lowercase()) {
                "projected" -> {
                    boat.projectedLevel = level
                    player.mes("Set boat projectedLevel to $level.")
                }
                "active" -> {
                    boat.activeLevel = level
                    player.mes("Set boat activeLevel to $level.")
                }
                else -> player.mes("Use projected or active.")
            }
        }

    private fun enterHelm(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                if (!player.isAboardOwnedBoat()) {
                    mes("You must be aboard your boat.")
                    return@launch
                }
                with(helmActions) { enterHelmNavigation() }
            }
        }

    private fun stopHelm(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                if (!player.isAboardOwnedBoat()) {
                    mes("You must be aboard your boat.")
                    return@launch
                }
                with(helmActions) { stopNavigatingFromHelm() }
            }
        }

    private fun sail(cheat: Cheat) =
        with(cheat) {
            if (args.isEmpty()) {
                player.mes("Use as ::sail full|half|reverse|stop")
                return@with
            }
            protectedAccess.launch(player) {
                if (!player.isAboardOwnedBoat()) {
                    mes("You must be aboard your boat.")
                    return@launch
                }
                with(helmActions) { setSailMode(args[0]) }
            }
        }

    private fun boatAngle(cheat: Cheat) =
        with(cheat) {
            val boat =
                worldEntityRepo.findByOwner(player.slotId)
                    ?: run {
                        player.mes("No owned boat found.")
                        return@with
                    }
            val angle = args.getOrNull(0)?.toIntOrNull()
            if (angle == null) {
                player.mes("Use as ::boatangle <angle>")
                return@with
            }
            boat.angle = HeadingUtils.normalizeAngle(angle)
            boat.navigationHeading = boat.angle
            protectedAccess.launch(player) {
                BoatMovement.syncSpawnVarbits(boat, player)
                VarPlayerIntMapSetter.set(player, sailing_varbits.boat_spawned_angle, boat.angle)
            }
            player.mes("Set boat angle to ${boat.angle}.")
        }

    private fun boatSpeed(cheat: Cheat) =
        with(cheat) {
            val boat =
                worldEntityRepo.findByOwner(player.slotId)
                    ?: run {
                        player.mes("No owned boat found.")
                        return@with
                    }
            val multiplier = args.getOrNull(0)?.toIntOrNull()
            if (multiplier == null || multiplier < 1) {
                player.mes("Use as ::boatspeed <multiplier> (integer >= 1)")
                return@with
            }
            boat.sailSpeedMultiplier = multiplier
            player.mes("Set boat sail speed multiplier to $multiplier.")
        }
}

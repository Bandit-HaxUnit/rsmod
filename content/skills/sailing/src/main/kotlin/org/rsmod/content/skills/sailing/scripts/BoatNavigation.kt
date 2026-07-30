package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.content.skills.sailing.BoatContent.isAboardOwnedBoat
import org.rsmod.content.skills.sailing.SailingHelmActions
import org.rsmod.content.skills.sailing.configs.sailing_components
import org.rsmod.content.skills.sailing.configs.sailing_locs
import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Helm navigation and sidepanel sail controls for player-owned boats.
 *
 * Live trace (`rsprox-logs/more_real_game_sailing_logs.txt`):
 * - **`oploc1_v2`** on helm enters heading mode (move_mode=4, helm_status=2)
 * - Second **`oploc1_v2`** on helm stops navigating (move_mode=0, helm_status=1)
 * - **`if_buttonx`** on `facilities_content_clicklayer` sub=0/1/2 toggles sail speed
 */
class BoatNavigation
@Inject
constructor(
    private val helmActions: SailingHelmActions,
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldEntityRepo: WorldEntityRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(sailing_locs.steering_kandarin_1x3_wood) { toggleHelmNavigation() }
        onIfOverlayButton(sailing_components.facilities_content_clicklayer) { clickSailSpeed() }
        onEvent<PlayerMovementEvent.BoatHeadingClick> { handleHeadingClick() }
        onEvent<PlayerMovementEvent.BoatSetHeading> { handleSetHeading() }
    }

    private fun PlayerMovementEvent.BoatHeadingClick.handleHeadingClick() {
        val boat = worldEntityRepo.findByOwner(player.slotId) ?: return
        helmActions.applyHeadingClick(player, boat, dest)
        protectedAccess.launch(player) {
            with(helmActions) { enableSailIfNeededForHeading() }
        }
    }

    private fun PlayerMovementEvent.BoatSetHeading.handleSetHeading() {
        val boat = worldEntityRepo.findByOwner(player.slotId) ?: return
        helmActions.applySetHeading(boat, heading)
        protectedAccess.launch(player) {
            with(helmActions) { enableSailIfNeededForHeading() }
        }
    }

    private fun IfOverlayButton.clickSailSpeed() {
        protectedAccess.launch(player) { toggleSailSpeed(comsub) }
    }

    private suspend fun ProtectedAccess.toggleHelmNavigation() {
        if (!player.isAboardOwnedBoat()) {
            return
        }
        if (vars[sailing_varbits.sidepanel_player_at_helm] != 0) {
            with(helmActions) { stopNavigatingFromHelm() }
        } else {
            with(helmActions) { enterHelmNavigation() }
        }
    }

    private fun ProtectedAccess.toggleSailSpeed(comsub: Int) {
        if (!player.isAboardOwnedBoat()) {
            return
        }
        with(helmActions) { toggleSailSpeed(comsub) }
    }
}

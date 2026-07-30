package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.sailing.BoatContent.isAboardOwnedBoat
import org.rsmod.content.skills.sailing.BoatMovement
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Advances player-owned boat world entities each tick while under sail. */
class BoatMovementScript
@Inject
constructor(
    private val worldEntityList: WorldEntityList,
    private val playerList: PlayerList,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { processBoatMovement() }
    }

    private fun processBoatMovement() {
        for (entity in worldEntityList) {
            val ownerSlot = entity.ownerPlayerSlot
            if (ownerSlot == 0) {
                continue
            }
            val owner = playerList[ownerSlot] ?: continue
            if (!owner.isAboardOwnedBoat()) {
                continue
            }
            BoatMovement.tick(entity, owner, collision)
        }
    }
}

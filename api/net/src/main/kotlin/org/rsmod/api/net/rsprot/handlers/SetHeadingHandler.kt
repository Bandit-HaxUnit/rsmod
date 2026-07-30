package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.misc.user.SetHeading
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

class SetHeadingHandler
@Inject
constructor(private val eventBus: EventBus, private val worldEntityReg: WorldEntityRegistry) :
    MessageHandler<SetHeading> {
    override fun handle(player: Player, message: SetHeading) {
        if (player.isDelayed || !player.avatar.boatHelmHeadingMode) {
            return
        }
        if (worldEntityReg.findByOwner(player.slotId) == null) {
            return
        }
        val heading = message.heading
        if (heading !in 0..15) {
            return
        }
        eventBus.publish(PlayerMovementEvent.BoatSetHeading(player, heading))
    }
}

package org.rsmod.api.player.events

import org.rsmod.events.KeyedEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.walktrig.WalkTriggerType
import org.rsmod.map.CoordGrid

public class PlayerMovementEvent {
    public class WalkTrigger(
        public val player: Player,
        triggerType: WalkTriggerType,
        override val id: Long = triggerType.id.toLong(),
    ) : KeyedEvent

    /** Map click while the player is at the helm in heading interaction mode. */
    public class BoatHeadingClick(public val player: Player, public val dest: CoordGrid) : UnboundEvent

    /**
     * [SetHeading] packet while at the helm in heading interaction mode.
     *
     * [heading] is the raw client value (0-15); multiply by 128 for 2048-angle units.
     */
    public class BoatSetHeading(public val player: Player, public val heading: Int) : UnboundEvent
}

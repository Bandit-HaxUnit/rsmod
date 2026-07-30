package org.rsmod.api.player.output

import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Updates client-side world map state via [worldmap_transmitdata] (cs2 1749).
 *
 * The script sets `%varcint188` (`worldmap_youarehere`) and `%varcint401` (`worldmap_gravestone`)
 * from the given coordinates.
 */
public object WorldMap {
    public const val TRANSMIT_DATA_CS2: Int = 1749

    private val lastPositionBySlot = mutableMapOf<Int, Int>()

    public fun transmit(
        player: Player,
        position: CoordGrid,
        gravestone: CoordGrid = CoordGrid.NULL,
    ) {
        player.runClientScript(TRANSMIT_DATA_CS2, position.packed, gravestone.packed)
        lastPositionBySlot[player.slotId] = position.packed
    }

    public fun clearPlayer(player: Player) {
        lastPositionBySlot.remove(player.slotId)
    }

    public fun shouldRetransmit(player: Player): Boolean {
        return lastPositionBySlot[player.slotId] != player.coords.packed
    }
}

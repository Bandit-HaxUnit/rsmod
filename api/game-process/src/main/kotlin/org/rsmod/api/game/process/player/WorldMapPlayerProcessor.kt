package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.output.WorldMap
import org.rsmod.game.entity.Player

public class WorldMapPlayerProcessor @Inject constructor() {
    public fun process(player: Player) {
        if (!player.ui.containsOverlay(interfaces.worldmap)) {
            WorldMap.clearPlayer(player)
            return
        }
        if (!WorldMap.shouldRetransmit(player)) {
            return
        }
        WorldMap.transmit(player, player.coords)
    }
}

package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.misc.client.ResetInteractionMode
import net.rsprot.protocol.game.outgoing.misc.client.SetInteractionMode
import org.rsmod.game.entity.Player

/**
 * Sends client interaction-mode packets for sailing helm navigation.
 *
 * rsprot tile modes: 0=disabled, 1=walk, 2=heading. Entity modes: 0=disabled, 1=enabled,
 * 2=examine-only. [DEFAULT_WORLD_ID] (-2) targets the player's default world; positive ids
 * target a world entity index.
 */
public object InteractionMode {
    /** Default world — used for player interaction mode (`type=default` in RSProx). */
    public const val DEFAULT_WORLD_ID: Int = -2

    public const val TILE_DISABLED: Int = 0
    public const val TILE_WALK: Int = 1
    public const val TILE_HEADING: Int = 2

    public const val ENTITY_DISABLED: Int = 0
    public const val ENTITY_ENABLED: Int = 1

    public fun setPlayerHeadingMode(player: Player) {
        player.client.write(
            SetInteractionMode(DEFAULT_WORLD_ID, TILE_HEADING, ENTITY_ENABLED),
        )
    }

    public fun setPlayerWalkMode(player: Player) {
        player.client.write(SetInteractionMode(DEFAULT_WORLD_ID, TILE_WALK, ENTITY_ENABLED))
    }

    public fun setBoatWalkMode(player: Player, boatIndex: Int) {
        player.client.write(SetInteractionMode(boatIndex, TILE_WALK, ENTITY_ENABLED))
    }

    public fun resetBoatInteractionMode(player: Player, boatIndex: Int) {
        player.client.write(ResetInteractionMode(boatIndex))
    }

    /** Restores default walk mode after helm navigation (live trace sends walk twice). */
    public fun restoreDefaultWalkMode(player: Player) {
        setPlayerWalkMode(player)
        setPlayerWalkMode(player)
    }
}

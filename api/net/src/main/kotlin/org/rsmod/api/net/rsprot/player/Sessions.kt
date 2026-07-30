package org.rsmod.api.net.rsprot.player

import net.rsprot.protocol.api.Session
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

data class SessionStart(val player: Player, val session: Session<Player>) : UnboundEvent

/** Published right after [SessionStart]; sends map rebuild, player info, and zone data. */
data class SessionLoginFlush(val player: Player) : UnboundEvent

/** Published after login scripts finish; flushes remaining init packets to the client. */
data class SessionLoginFinish(val player: Player) : UnboundEvent

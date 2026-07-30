package org.rsmod.game.client

import org.rsmod.game.entity.Player

public interface Client<S, T> {
    public fun close()

    public fun write(message: T)

    public fun read(player: Player)

    public fun flush()

    public fun flushHighPriority()

    public fun unregister(service: S, player: Player)
}

public interface ClientCycle {
    /**
     * When `true`, [flush] emits zone updates in rsprot-correct order (interleaved with info
     * packets). When `false`, zone updates are sent in a separate pass after [flush].
     */
    public val managesZoneUpdateFlush: Boolean
        get() = false

    public fun update(player: Player)

    public fun flush(player: Player)

    public fun release()
}

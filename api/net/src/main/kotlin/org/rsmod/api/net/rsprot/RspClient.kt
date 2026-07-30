package org.rsmod.api.net.rsprot

import com.github.michaelbull.logging.InlineLogger
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

class RspClient(private val session: Session<Player>, internal val infos: Infos) :
    Client<NetworkService<Player>, OutgoingGameMessage> {
    internal var logOutgoingPackets: Boolean = false

    override fun close() {
        session.requestClose()
    }

    override fun write(message: OutgoingGameMessage) {
        if (logOutgoingPackets) {
            logger.debug { "Login outgoing: ${message.javaClass.simpleName}" }
        }
        session.queue(message)
    }

    override fun read(player: Player) {
        session.processIncomingPackets(player)
    }

    override fun flush() {
        session.flush()
    }

    override fun flushHighPriority() {
        session.discardLowPriorityCategoryPackets()
        session.flush()
    }

    override fun unregister(service: NetworkService<Player>, player: Player) {
        service.infoProtocols.dealloc(infos)
    }

    private companion object {
        private val logger = InlineLogger()
    }
}

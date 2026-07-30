package org.rsmod.api.net.rsprot

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.protocol.api.NetworkService
import org.rsmod.api.game.process.player.PlayerBuildAreaProcessor
import org.rsmod.api.game.process.player.PlayerRegionProcessor
import org.rsmod.api.game.process.player.PlayerZoneUpdateProcessor
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.WorldEntityList

class LoginNetworkCompleter
@Inject
constructor(
    private val buildAreas: PlayerBuildAreaProcessor,
    private val regions: PlayerRegionProcessor,
    private val zoneUpdates: PlayerZoneUpdateProcessor,
    private val playerList: PlayerList,
    private val worldEntities: WorldEntityList,
) {
    fun complete(player: Player, service: NetworkService<Player>) {
        val client = player.client as? RspClient
        if (client == null) {
            logger.warn {
                "Login network completion skipped: client is not RspClient (player=$player)"
            }
            return
        }

        val cycle = player.clientCycle as? RspCycle
        if (cycle == null) {
            logger.warn {
                "Login network completion skipped: clientCycle is not RspCycle (player=$player)"
            }
            return
        }

        try {
            (client as RspClient).logOutgoingPackets = true
            regions.process(player)
            buildAreas.process(player)
            zoneUpdates.computeEnclosedBuffers()
            logger.info {
                "Sending login map/info for '${player.username}' " +
                    "(slot=${player.slotId}, coords=${player.coords}, buildArea=${player.buildArea})"
            }

            cycle.update(player)
            service.infoProtocols.update()
            cycle.queueLoginAboardRebuild(player, worldEntities)
            cycle.flush(player)
            flushOtherInfoProtocols(excludeSlot = player.slotId)
            client.flush()

            logger.info { "Login map/info sent for '${player.username}'" }
        } catch (e: Exception) {
            logger.error(e) { "Login map/info failed for '${player.username}'" }
            throw e
        } finally {
            (client as? RspClient)?.logOutgoingPackets = false
        }
    }

    fun finish(player: Player, service: NetworkService<Player>) {
        val client = player.client as? RspClient ?: return
        val cycle = player.clientCycle as? RspCycle ?: return
        try {
            (client as RspClient).logOutgoingPackets = true
            cycle.update(player)
            service.infoProtocols.update()
            cycle.flush(player)
            flushOtherInfoProtocols(excludeSlot = player.slotId)
            MiscOutput.serverTickEnd(player)
            client.flush()
            logger.info { "Login init packets sent for '${player.username}'" }
        } catch (e: Exception) {
            logger.error(e) { "Login init flush failed for '${player.username}'" }
            throw e
        } finally {
            (client as RspClient).logOutgoingPackets = false
        }
    }

    /**
     * Global info updates recalculate packets for every connected player. Flush any other online
     * players so their previously calculated packets are not dropped.
     */
    private fun flushOtherInfoProtocols(excludeSlot: Int) {
        for (online in playerList) {
            if (online.loggingOut || online.slotId == excludeSlot) {
                continue
            }
            val cycle = online.clientCycle as? RspCycle ?: continue
            cycle.flush(online)
        }
    }

    private companion object {
        private val logger = InlineLogger()
    }
}

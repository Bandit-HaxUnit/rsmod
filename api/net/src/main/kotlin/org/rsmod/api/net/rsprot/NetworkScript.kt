package org.rsmod.api.net.rsprot

import jakarta.inject.Inject
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.common.RSProtConstants
import net.rsprot.protocol.common.client.OldSchoolClientType
import org.rsmod.api.core.Build
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.player.PlayerZoneUpdateProcessor
import org.rsmod.api.net.rsprot.player.SessionLoginFinish
import org.rsmod.api.net.rsprot.player.SessionLoginFlush
import org.rsmod.api.net.rsprot.player.SessionStart
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.script.onEvent
import org.rsmod.game.MapClock
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.worldentity.WorldEntityStateEvents
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class NetworkScript
@Inject
constructor(
    private val mapClock: MapClock,
    private val service: NetworkService<Player>,
    private val objTypes: ObjTypeList,
    private val regionReg: RegionRegistry,
    private val worldEntityReg: WorldEntityRegistry,
    private val worldEntityList: WorldEntityList,
    private val zoneUpdates: PlayerZoneUpdateProcessor,
    private val loginCompleter: LoginNetworkCompleter,
) : PluginScript() {
    override fun ScriptContext.startup() {
        check(RSProtConstants.REVISION == Build.MAJOR) {
            "RSProt and RSMod have mismatching revision builds! " +
                "(rsmod=${Build.MAJOR}, rsprot=${RSProtConstants.REVISION})"
        }
        onEvent<GameLifecycle.Startup> { initService() }
        onEvent<GameLifecycle.UpdateInfo> { updateService() }
        onEvent<SessionStart> { startSession() }
        onEvent<SessionLoginFlush> { loginCompleter.complete(player, service) }
        onEvent<SessionLoginFinish> { loginCompleter.finish(player, service) }
        onEvent<SessionStateEvent.Delete> { closeSession() }
        onEvent<NpcStateEvents.Create> { createNpcAvatar(npc) }
        onEvent<NpcStateEvents.Delete> { deleteNpcAvatar(npc) }
        onEvent<WorldEntityStateEvents.Create> { createWorldEntityAvatar(entity) }
        onEvent<WorldEntityStateEvents.Delete> { deleteWorldEntityAvatar(entity) }
    }

    private fun initService() {
        service.setCommunicationThread(Thread.currentThread())
    }

    private fun updateService() {
        service.infoProtocols.update()
    }

    @Suppress("UNCHECKED_CAST")
    private fun SessionStart.startSession() {
        val slot = player.slotId

        val infos = service.infoProtocols.alloc(slot, OldSchoolClientType.DESKTOP)

        val client = RspClient(session, infos) as Client<Any, Any>
        val cycle =
            RspCycle(session, infos, objTypes, regionReg, worldEntityList, zoneUpdates)

        player.client = client
        player.clientCycle = cycle

        cycle.init(player)
    }

    private fun SessionStateEvent.Delete.closeSession() {
        val ownedBoat = worldEntityReg.findByOwner(player.slotId)
        val aboardOwnedBoat =
            ownedBoat != null && ownedBoat.containsInstanceCoords(player.coords)
        if (!aboardOwnedBoat) {
            worldEntityReg.delAllOwnedBy(player.slotId)
        }
        val client = player.client as? RspClient ?: return
        client.unregister(service, player)
    }

    private fun createNpcAvatar(npc: Npc) {
        val rspAvatar =
            service.npcAvatarFactory.alloc(
                index = npc.slotId,
                id = npc.id,
                level = npc.level,
                x = npc.x,
                z = npc.z,
                spawnCycle = mapClock.cycle,
                direction = npc.respawnDir.id,
            )
        npc.infoProtocol = RspNpcInfo(rspAvatar)
    }

    private fun deleteNpcAvatar(npc: Npc) {
        val infoProtocol = npc.avatar.infoProtocol
        if (infoProtocol is RspNpcInfo) {
            service.npcAvatarFactory.release(infoProtocol.rspAvatar)
        }
    }

    private fun createWorldEntityAvatar(entity: WorldEntity) {
        logger.info {
            "Creating world entity avatar: index=${entity.slotId}, type=${entity.typeId}, " +
                "owner=${entity.ownerPlayerSlot}, root=${entity.rootCoord}, " +
                "swZone=(${entity.southWestZoneX}, ${entity.southWestZoneZ}), " +
                "size=${entity.sizeX}x${entity.sizeZ}, projectedLevel=${entity.projectedLevel}, " +
                "activeLevel=${entity.activeLevel}, angle=${entity.angle}"
        }
        val rspAvatar =
            service.worldEntityAvatarFactory.alloc(
                index = entity.slotId,
                id = entity.typeId,
                ownerIndex = entity.ownerPlayerSlot,
                sizeX = entity.sizeX,
                sizeZ = entity.sizeZ,
                southWestZoneX = entity.southWestZoneX,
                southWestZoneZ = entity.southWestZoneZ,
                minLevel = entity.minLevel,
                maxLevel = entity.maxLevel,
                fineX = entity.fineX,
                fineZ = entity.fineZ,
                projectedLevel = entity.projectedLevel,
                activeLevel = entity.activeLevel,
                angle = entity.angle,
            )
        entity.infoProtocol = RspWorldEntityInfo(rspAvatar)
    }

    private fun deleteWorldEntityAvatar(entity: WorldEntity) {
        val infoProtocol = entity.avatar.infoProtocol
        if (infoProtocol is RspWorldEntityInfo) {
            service.worldEntityAvatarFactory.release(infoProtocol.rspAvatar)
        }
    }

    private companion object {
        private val logger = com.github.michaelbull.logging.InlineLogger()
    }
}

package org.rsmod.api.game.process.player

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import jakarta.inject.Inject
import java.util.ArrayList
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.worldentity.SetActiveWorldV2
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.message.ZoneProt
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.registry.zone.ZoneUpdateTransformer
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.api.utils.zone.SharedZoneEnclosedBuffers
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

public class PlayerZoneUpdateProcessor
@Inject
constructor(
    private val updates: ZoneUpdateMap,
    private val locReg: LocRegistry,
    private val objReg: ObjRegistry,
    private val enclosedBuffers: SharedZoneEnclosedBuffers,
    private val worldEntities: WorldEntityList,
) {
    public fun computeEnclosedBuffers() {
        enclosedBuffers.computeSharedBuffers()
    }

    public fun process(player: Player) {
        player.processZoneUpdates()
    }

    public fun processRootWorldZones(player: Player) {
        val plan = player.computeZoneUpdatePlan()
        val context = RootZoneWorldContext()
        if (plan.zoneMoved) {
            processNewVisibleZonesForContext(player, plan.buildArea, plan.newRootZones, context)
            player.processVisibleZoneUpdatesForContext(plan.buildArea, plan.oldRootZones, context)
        } else {
            player.processVisibleZoneUpdatesForContext(
                plan.buildArea,
                player.visibleZoneKeys.filterRootZones(plan.aboard),
                context,
            )
        }
    }

    public fun processDynamicWorldZones(player: Player, entityIndex: Int) {
        val plan = player.computeZoneUpdatePlan()
        val entity = worldEntities[entityIndex] ?: return
        if (plan.aboard?.slotId != entity.slotId) {
            return
        }
        val context = DynamicZoneWorldContext(entity)
        if (plan.zoneMoved) {
            processNewVisibleZonesForContext(player, plan.buildArea, plan.newEntityZones, context)
            player.processVisibleZoneUpdatesForContext(plan.buildArea, plan.oldEntityZones, context)
        } else {
            player.processVisibleZoneUpdatesForContext(
                plan.buildArea,
                player.visibleZoneKeys.filterEntityZones(plan.aboard),
                context,
            )
        }
        context.finish()
    }

    public fun finalizeZoneUpdates(player: Player) {
        val plan = player.computeZoneUpdatePlan()
        player.applyVisibleZoneKeys(plan.allVisibleZones)
        player.lastProcessedZone = plan.currZone
    }

    public fun clearEnclosedBuffers() {
        enclosedBuffers.clear()
    }

    public fun clearPendingZoneUpdates() {
        updates.clear()
    }

    private fun Player.processZoneUpdates() {
        val currZone = ZoneKey.from(coords)
        val visibleZones = visibleZoneKeys
        val prevZone = lastProcessedZone
        val buildArea = buildArea

        val aboard = worldEntities.firstOrNull { it.containsInstanceCoords(coords) }
        val anchorZone = if (aboard != null) ZoneKey.from(aboard.rootCoord) else currZone

        if (currZone != prevZone) {
            val currZones =
                anchorZone.computeVisibleNeighbouringZones().filterWithinBuildArea(buildArea)
            if (aboard != null) {
                currZones.addAll(aboard.instanceZones())
            }

            val newZones = IntArrayList(currZones).apply { removeAll(visibleZones) }
            processNewVisibleZones(buildArea, newZones, aboard)

            refreshVisibleZoneKeys(currZones)

            val oldZones = IntArrayList(currZones).apply { removeAll(newZones) }
            processVisibleZoneUpdates(buildArea, oldZones, aboard)
        } else {
            processVisibleZoneUpdates(buildArea, visibleZones, aboard)
        }

        lastProcessedZone = currZone
    }

    private fun Player.computeZoneUpdatePlan(): ZoneUpdatePlan {
        val buildArea = buildArea
        val currZone = ZoneKey.from(coords)
        val prevZone = lastProcessedZone
        val aboard = worldEntities.firstOrNull { it.containsInstanceCoords(coords) }
        val anchorZone = if (aboard != null) ZoneKey.from(aboard.rootCoord) else currZone
        val rootVisibleZones =
            anchorZone.computeVisibleNeighbouringZones().filterWithinBuildArea(buildArea)
        val entityVisibleZones = aboard?.instanceZones() ?: IntArrayList(0)
        val allVisibleZones =
            IntArrayList(rootVisibleZones.size + entityVisibleZones.size).apply {
                addAll(rootVisibleZones)
                addAll(entityVisibleZones)
            }
        val zoneMoved = currZone != prevZone
        val newZones =
            if (zoneMoved) {
                IntArrayList(allVisibleZones).apply { removeAll(visibleZoneKeys) }
            } else {
                IntArrayList(0)
            }
        val oldZones =
            if (zoneMoved) {
                IntArrayList(allVisibleZones).apply { removeAll(newZones) }
            } else {
                IntArrayList(0)
            }
        return ZoneUpdatePlan(
            buildArea = buildArea,
            aboard = aboard,
            currZone = currZone,
            zoneMoved = zoneMoved,
            allVisibleZones = allVisibleZones,
            newRootZones = newZones.filterRootZones(aboard),
            newEntityZones = newZones.filterEntityZones(aboard),
            oldRootZones = oldZones.filterRootZones(aboard),
            oldEntityZones = oldZones.filterEntityZones(aboard),
        )
    }

    private fun Player.processNewVisibleZones(
        buildArea: CoordGrid,
        zones: IntList,
        aboard: WorldEntity?,
    ) {
        val worldContext = ZoneWorldContext(this, aboard)
        for (zone in zones.intIterator()) {
            val key = ZoneKey(zone)
            val zoneBase = key.toCoords()
            val relativeBase = worldContext.prepare(zoneBase, buildArea)
            sendZoneResetUpdate(relativeBase, zoneBase)
            sendZonePersistentUpdates(relativeBase, zoneBase, key)
        }
        worldContext.finish()
    }

    private fun processNewVisibleZonesForContext(
        player: Player,
        buildArea: CoordGrid,
        zones: IntList,
        worldContext: ZoneUpdateContext,
    ) {
        for (zone in zones.intIterator()) {
            val key = ZoneKey(zone)
            val zoneBase = key.toCoords()
            val relativeBase = worldContext.prepare(zoneBase, buildArea)
            player.sendZoneResetUpdate(relativeBase, zoneBase)
            player.sendZonePersistentUpdates(relativeBase, zoneBase, key)
        }
    }

    private fun Player.processVisibleZoneUpdates(
        buildArea: CoordGrid,
        currZones: List<Int>,
        aboard: WorldEntity?,
    ) {
        val worldContext = ZoneWorldContext(this, aboard)
        for (zone in currZones) {
            val zoneKey = ZoneKey(zone)
            val zoneBase = zoneKey.toCoords()
            val relativeBase = worldContext.prepare(zoneBase, buildArea)
            sendZoneSharedEnclosedUpdates(relativeBase, zoneKey, zoneBase)
            sendZonePlayerEnclosedUpdates(relativeBase, zoneKey, zoneBase)
        }
        worldContext.finish()
    }

    private fun Player.processVisibleZoneUpdatesForContext(
        buildArea: CoordGrid,
        currZones: List<Int>,
        worldContext: ZoneUpdateContext,
    ) {
        for (zone in currZones) {
            val zoneKey = ZoneKey(zone)
            val zoneBase = zoneKey.toCoords()
            val relativeBase = worldContext.prepare(zoneBase, buildArea)
            sendZoneSharedEnclosedUpdates(relativeBase, zoneKey, zoneBase)
            sendZonePlayerEnclosedUpdates(relativeBase, zoneKey, zoneBase)
        }
    }

    private fun Player.sendZoneResetUpdate(relativeBase: CoordGrid, zoneBase: CoordGrid) {
        val deltaX = zoneBase.x - relativeBase.x
        val deltaZ = zoneBase.z - relativeBase.z
        val message = UpdateZoneFullFollows(deltaX, deltaZ, zoneBase.level)
        client.write(message)
    }

    private fun Player.sendZonePersistentUpdates(
        relativeBase: CoordGrid,
        zoneBase: CoordGrid,
        zone: ZoneKey,
    ) {
        val spawnedLocs = locReg.findAllSpawned(zone)
        sendPersistentLocs(spawnedLocs)

        val spawnedObjs = objReg.findAll(zone)
        sendPersistentObjs(relativeBase, zoneBase, spawnedObjs, observerUUID)
    }

    private fun Player.sendPersistentLocs(locs: Sequence<LocInfo>) {
        for (loc in locs) {
            val prot = ZoneUpdateTransformer.toPersistentLocChange(loc)
            client.write(prot)
        }
    }

    private fun Player.sendPersistentObjs(
        relativeBase: CoordGrid,
        zoneBase: CoordGrid,
        objs: Sequence<Obj>,
        observerId: Long?,
    ) {
        val enclosedUpdates = ArrayList<ZoneProt>()
        for (obj in objs) {
            val prot = ZoneUpdateTransformer.toPersistentObjAdd(obj, observerId) ?: continue
            enclosedUpdates += prot
        }
        if (enclosedUpdates.isNotEmpty()) {
            sendZonePlayerEnclosedUpdates(relativeBase, zoneBase, enclosedUpdates)
        }
    }

    private fun Player.refreshVisibleZoneKeys(zones: IntList) {
        applyVisibleZoneKeys(zones)
    }

    private fun Player.applyVisibleZoneKeys(zones: IntList) {
        visibleZoneKeys.clear()
        visibleZoneKeys.addAll(zones)
    }

    private fun Player.sendZonePlayerEnclosedUpdates(
        relativeBase: CoordGrid,
        zone: ZoneKey,
        zoneBase: CoordGrid,
    ) {
        val zoneUpdates = updates[zone] ?: return
        check(zoneUpdates.isNotEmpty) { "`updates` for zone should not be empty: $zone" }
        val playerSpecific = zoneUpdates.toPlayerSpecificEnclosed(observerUUID)
        sendZonePlayerEnclosedUpdates(relativeBase, zoneBase, playerSpecific)
    }

    private fun Player.sendZonePlayerEnclosedUpdates(
        relativeBase: CoordGrid,
        zoneBase: CoordGrid,
        enclosedUpdates: List<ZoneProt>,
    ) {
        if (enclosedUpdates.isEmpty()) {
            return
        }
        val buffer = enclosedBuffers.computeBufferForClient(OldSchoolClientType.DESKTOP, enclosedUpdates)
        val deltaX = zoneBase.x - relativeBase.x
        val deltaZ = zoneBase.z - relativeBase.z
        val message = UpdateZonePartialEnclosed(deltaX, deltaZ, zoneBase.level, buffer)
        client.write(message)
    }

    private fun Player.sendZoneSharedEnclosedUpdates(
        relativeBase: CoordGrid,
        zone: ZoneKey,
        zoneBase: CoordGrid,
    ) {
        val enclosed = enclosedBuffers[zone] ?: return
        val buffer = enclosed[OldSchoolClientType.DESKTOP] ?: return
        val deltaX = zoneBase.x - relativeBase.x
        val deltaZ = zoneBase.z - relativeBase.z
        val prot = UpdateZonePartialEnclosed(deltaX, deltaZ, zoneBase.level, buffer)
        client.write(prot)
    }

    private fun Iterable<ZoneProt>.toPlayerSpecificEnclosed(observerId: Long?): List<ZoneProt> {
        val enclosed = ArrayList<ZoneProt>()
        for (update in this) {
            val prot =
                (update as? ZoneUpdateTransformer.PartialFollowsZoneProt)
                    ?.toEnclosed(observerId)
            if (prot != null) {
                enclosed += prot
            }
        }
        return enclosed
    }

    private fun ZoneUpdateTransformer.PartialFollowsZoneProt.toEnclosed(
        observerId: Long?,
    ): ZoneProt? =
        when (this) {
            is ZoneUpdateTransformer.ObjPrivateZoneProt ->
                if (isVisibleTo(observerId)) backing else null
            is ZoneUpdateTransformer.ObjReveal ->
                if (observerId == obj.receiverId) null else backing
            else -> backing
        }

    private fun ZoneKey.computeVisibleNeighbouringZones(): IntList {
        val zones = IntArrayList(ZONE_VIEW_TOTAL_COUNT)
        for (x in -ZONE_VIEW_RADIUS..ZONE_VIEW_RADIUS) {
            for (z in -ZONE_VIEW_RADIUS..ZONE_VIEW_RADIUS) {
                val zone = translate(x, z)
                zones.add(zone.packed)
            }
        }
        return zones
    }

    private fun IntList.filterWithinBuildArea(buildArea: CoordGrid): IntList {
        val zones = IntArrayList(size)
        forEach { zone ->
            val zoneBase = ZoneKey(zone).toCoords()
            val deltaX = zoneBase.x - buildArea.x
            val deltaZ = zoneBase.z - buildArea.z
            val viewable = deltaX in BUILD_AREA_BOUNDS && deltaZ in BUILD_AREA_BOUNDS
            if (viewable) {
                zones.add(zone)
            }
        }
        return zones
    }

    private fun IntList.filterRootZones(aboard: WorldEntity?): IntList {
        if (aboard == null) {
            return this
        }
        val zones = IntArrayList(size)
        forEach { zone ->
            if (!aboard.containsInstanceZone(ZoneKey(zone))) {
                zones.add(zone)
            }
        }
        return zones
    }

    private fun IntList.filterEntityZones(aboard: WorldEntity?): IntList {
        if (aboard == null) {
            return IntArrayList(0)
        }
        val zones = IntArrayList(size)
        forEach { zone ->
            if (aboard.containsInstanceZone(ZoneKey(zone))) {
                zones.add(zone)
            }
        }
        return zones
    }

    private fun WorldEntity.instanceZones(): IntList {
        val zones = IntArrayList(sizeX * sizeZ)
        for (dx in 0 until sizeX) {
            for (dz in 0 until sizeZ) {
                zones.add(ZoneKey(southWestZoneX + dx, southWestZoneZ + dz, activeLevel).packed)
            }
        }
        return zones
    }

    private fun WorldEntity.containsInstanceZone(zone: ZoneKey): Boolean {
        return zone.x in southWestZoneX until southWestZoneX + sizeX &&
            zone.z in southWestZoneZ until southWestZoneZ + sizeZ &&
            zone.level == activeLevel
    }

    private fun WorldEntity.dynamicZoneBase(): CoordGrid =
        CoordGrid(
            x = southWestZoneX * ZoneGrid.LENGTH,
            z = southWestZoneZ * ZoneGrid.LENGTH,
            level = activeLevel,
        )

    private interface ZoneUpdateContext {
        fun prepare(zoneBase: CoordGrid, buildArea: CoordGrid): CoordGrid

        fun finish() {}
    }

    private class RootZoneWorldContext : ZoneUpdateContext {
        override fun prepare(zoneBase: CoordGrid, buildArea: CoordGrid): CoordGrid = buildArea
    }

    private class DynamicZoneWorldContext(private val entity: WorldEntity) : ZoneUpdateContext {
        override fun prepare(zoneBase: CoordGrid, buildArea: CoordGrid): CoordGrid =
            CoordGrid(
                x = entity.southWestZoneX * ZoneGrid.LENGTH,
                z = entity.southWestZoneZ * ZoneGrid.LENGTH,
                level = entity.activeLevel,
            )
    }

    private class ZoneWorldContext(private val player: Player, private val aboard: WorldEntity?) :
        ZoneUpdateContext {
        private var activeWorld: Int? = null

        override fun prepare(zoneBase: CoordGrid, buildArea: CoordGrid): CoordGrid {
            if (aboard == null) {
                return buildArea
            }
            val zoneKey = ZoneKey.from(zoneBase)
            val entityZone =
                zoneKey.x in aboard.southWestZoneX until aboard.southWestZoneX + aboard.sizeX &&
                    zoneKey.z in aboard.southWestZoneZ until aboard.southWestZoneZ + aboard.sizeZ &&
                    zoneKey.level == aboard.activeLevel
            val desiredWorld = if (entityZone) aboard.slotId else ROOT_WORLD
            if (activeWorld != desiredWorld) {
                if (entityZone) {
                    player.client.write(
                        SetActiveWorldV2(
                            SetActiveWorldV2.DynamicWorldType(aboard.slotId, aboard.activeLevel),
                        ),
                    )
                } else {
                    player.client.write(SetActiveWorldV2.getRoot(zoneBase.level))
                }
                activeWorld = desiredWorld
            }
            return if (entityZone) {
                CoordGrid(
                    x = aboard.southWestZoneX * ZoneGrid.LENGTH,
                    z = aboard.southWestZoneZ * ZoneGrid.LENGTH,
                    level = aboard.activeLevel,
                )
            } else {
                buildArea
            }
        }

        override fun finish() {
            if (aboard != null && activeWorld != null && activeWorld != ROOT_WORLD) {
                player.client.write(SetActiveWorldV2.getRoot(player.coords.level))
            }
        }
    }

    private data class ZoneUpdatePlan(
        val buildArea: CoordGrid,
        val aboard: WorldEntity?,
        val currZone: ZoneKey,
        val zoneMoved: Boolean,
        val allVisibleZones: IntList,
        val newRootZones: IntList,
        val newEntityZones: IntList,
        val oldRootZones: IntList,
        val oldEntityZones: IntList,
    )

    public companion object {
        private const val ROOT_WORLD: Int = -1

        public const val ZONE_VIEW_RADIUS: Int = 3
        public const val ZONE_VIEW_TOTAL_COUNT: Int =
            (2 * ZONE_VIEW_RADIUS + 1) * (2 * ZONE_VIEW_RADIUS + 1)

        public val BUILD_AREA_BOUNDS: IntRange = 0 until BuildAreaUtils.SIZE
    }
}

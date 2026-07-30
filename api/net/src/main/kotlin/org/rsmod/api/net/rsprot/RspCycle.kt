package org.rsmod.api.net.rsprot

import com.github.michaelbull.logging.InlineLogger
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcInfoPacket
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo
import net.rsprot.protocol.game.outgoing.info.util.BuildArea
import net.rsprot.protocol.game.outgoing.info.util.PacketResult
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.map.RebuildRegionV2
import net.rsprot.protocol.game.outgoing.map.RebuildWorldEntityV4
import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.api.config.refs.baseanimsets
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.player.PlayerZoneUpdateProcessor
import org.rsmod.api.player.righthand
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.game.client.ClientCycle
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class RspCycle(
    private val session: Session<Player>,
    private val infos: Infos,
    private val objTypes: ObjTypeList,
    private val regions: RegionRegistry,
    private val worldEntities: WorldEntityList,
    private val zoneUpdates: PlayerZoneUpdateProcessor,
) : ClientCycle {
    override val managesZoneUpdateFlush: Boolean = true

    private var knownCoords: CoordGrid = CoordGrid.ZERO

    private var knownBuildArea: CoordGrid = CoordGrid.NULL

    private var knownCachedSpeed: MoveSpeed = MoveSpeed.Stationary

    private var knownFaceEntity: Int? = -1

    private var knownRegionUid: Int? = null

    private var cachedRegionZoneProvider: RebuildRegionV2.RebuildRegionZoneProvider? = null

    private var knownActiveWorldCount: Int = 0

    private val playerInfo
        get() = infos.playerInfo

    private val playerExtendedInfo: PlayerAvatarExtendedInfo
        get() = playerInfo.avatar.extendedInfo

    private val worldId: Int
        get() = 0

    fun init(player: Player) {
        player.updateCoords()
        // A player logging in aboard a world entity must have their login map anchored on the
        // entity's root-world coord - their own coords point at instance land, which has no
        // static map to build from. (Same rule as `PlayerBuildAreaProcessor` and
        // `updateRootInfoBuildArea`.)
        val aboard = worldEntities.firstOrNull { it.containsInstanceCoords(player.coords) }
        val anchor = aboard?.rootCoord ?: player.coords
        infos.updateRootBuildAreaCenteredOnPlayer(anchor.x, anchor.z)
        player.queueRebuildLogin(anchor)
    }

    private fun Player.queueRebuildLogin(anchor: CoordGrid) {
        val zoneX = anchor.x shr 3
        val zoneZ = anchor.z shr 3
        logger.info {
            "Queueing RebuildLogin for '$username' " +
                "(zoneX=$zoneX, zoneZ=$zoneZ, worldId=$worldId, coords=$coords)"
        }
        val rebuild = RebuildLoginV2(zoneX, zoneZ, worldId, playerInfo)
        session.queue(rebuild)
    }

    override fun update(player: Player) {
        player.updateMoveSpeed()
        player.updateCoords()
        player.rebuildArea()
        player.updateRootInfoBuildArea()
        player.applyExactMove()
        player.applyPublicMessage()
        player.applyFace()
        player.applyAnim()
        player.applySpotanims()
        player.applySay()
        player.applyHeadbars()
        player.applyHitmarks()
        player.syncAppearance(objTypes)
    }

    override fun flush(player: Player) {
        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets

        var pendingAboardRebuild = player.pendingAboardWorldEntityRebuild

        val activeWorldCount = infoPackets.activeWorlds.size
        if (activeWorldCount != knownActiveWorldCount) {
            logger.info {
                "Active world count changed for '${player.username}': " +
                    "$knownActiveWorldCount -> $activeWorldCount (coords=${player.coords}, " +
                    "buildArea=${player.buildArea})"
            }
            knownActiveWorldCount = activeWorldCount
        }

        session.queue(rootPackets.activeWorld)
        session.queue(rootPackets.npcUpdateOrigin)
        session.queuePacketResult(rootPackets.worldEntityInfo)
        session.queuePacketResult(rootPackets.playerInfo)
        session.queueNpcInfoPacket(rootPackets.npcInfo)

        // Root-world zone updates while the root active world is still set (see rsprot README).
        zoneUpdates.processRootWorldZones(player)

        for (world in infoPackets.activeWorlds) {
            session.queue(world.activeWorld)

            val forceAboardRebuild =
                pendingAboardRebuild && player.isAboardWorldEntity(world.worldId)
            if (world.added || forceAboardRebuild) {
                queueWorldEntityRebuild(player, world.worldId)
                if (forceAboardRebuild || (pendingAboardRebuild && world.added)) {
                    pendingAboardRebuild = false
                }
            }

            session.queue(world.npcUpdateOrigin)
            session.queueNpcInfoPacket(world.npcInfo)

            // Dynamic-world zone updates for this entity; active world was just set above.
            zoneUpdates.processDynamicWorldZones(player, world.worldId)
        }

        player.pendingAboardWorldEntityRebuild = pendingAboardRebuild

        zoneUpdates.finalizeZoneUpdates(player)

        // Ensure subsequent packets target the root world (e.g. inv/stat updates this tick).
        session.queue(rootPackets.activeWorld)
    }

    override fun release() {
        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets

        releasePacketResult(rootPackets.worldEntityInfo)
        releasePacketResult(rootPackets.playerInfo)
        releaseNpcInfoPacket(rootPackets.npcInfo)
        for (world in infoPackets.activeWorlds) {
            releaseNpcInfoPacket(world.npcInfo)
        }
    }

    /**
     * Queues a `REBUILD_WORLDENTITY` for a world entity that entered this player's high resolution
     * view this cycle. Must be sent while that entity's dynamic active world is set (see rsprot
     * README). The client uses it to construct the entity's zones, which are then rendered at the
     * entity's root-world position.
     *
     * Raft entities copy hull geometry from the canonical template zone via [BoatTemplateZones].
     * Dynamic deck locs (helm, sails, etc.) are delivered via zone updates targeted at the
     * entity's dynamic world (see `PlayerZoneUpdateProcessor`).
     */
    private fun queueWorldEntityRebuild(player: Player, index: Int) {
        val entity = worldEntities[index] ?: return
        logger.info {
            "Queueing RebuildWorldEntity for '${player.username}': " +
                "index=$index, type=${entity.typeId}, owner=${entity.ownerPlayerSlot}, " +
                "root=${entity.rootCoord}, swZone=(${entity.southWestZoneX}, " +
                "${entity.southWestZoneZ}), size=${entity.sizeX}x${entity.sizeZ}, " +
                "projectedLevel=${entity.projectedLevel}, activeLevel=${entity.activeLevel}, " +
                "angle=${entity.angle}"
        }
        session.queue(entity.createRebuild())
    }

    private fun WorldEntity.createRebuild(): RebuildWorldEntityV4 {
        val templateProvider = BoatTemplateZones.zoneProvider(typeId)
        val zoneProvider =
            templateProvider
                ?: object : RebuildWorldEntityV4.RebuildWorldEntityZoneProvider {
                    override fun provide(zoneX: Int, zoneZ: Int, level: Int): RebuildRegionZone? =
                        null
                }
        return RebuildWorldEntityV4(
            southWestZoneX * 8,
            southWestZoneZ * 8,
            sizeX,
            sizeZ,
            zoneProvider,
        )
    }

    /**
     * Requests a hull rebuild on the next [flush] while the player is aboard a world entity.
     * Rebuild is queued once inside the dynamic-world loop, immediately after
     * `SetActiveWorld`, matching rsprot's required packet order.
     */
    fun queueLoginAboardRebuild(player: Player, worldEntities: WorldEntityList) {
        val aboard =
            worldEntities.firstOrNull { it.containsInstanceCoords(player.coords) }
                ?: worldEntities.firstOrNull { it.ownerPlayerSlot == player.slotId }
                ?: return
        if (!player.isAboardWorldEntity(aboard.slotId)) {
            return
        }
        player.pendingAboardWorldEntityRebuild = true
    }

    private fun Player.isAboardWorldEntity(index: Int): Boolean {
        val entity = worldEntities[index] ?: return false
        return entity.containsInstanceCoords(coords) || entity.ownerPlayerSlot == slotId
    }

    private fun <T : OutgoingGameMessage> Session<Player>.queuePacketResult(
        result: PacketResult<T>
    ) {
        queue(result.getOrThrow())
    }

    private fun Session<Player>.queueNpcInfoPacket(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        queuePacketResult(result)
    }

    private fun <T : OutgoingGameMessage> releasePacketResult(result: PacketResult<T>) {
        result.getOrThrow().safeRelease()
    }

    private fun releaseNpcInfoPacket(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        result.getOrThrow().safeRelease()
    }

    private fun Player.updateMoveSpeed() {
        if (knownCachedSpeed != cachedMoveSpeed) {
            val extendedInfo = playerInfo.avatar.extendedInfo
            extendedInfo.setMoveSpeed(cachedMoveSpeed.steps)
            knownCachedSpeed = cachedMoveSpeed
        }
        val moveSpeed = resolvePendingMoveSpeed()
        if (moveSpeed != cachedMoveSpeed && coords != knownCoords) {
            val extendedInfo = playerInfo.avatar.extendedInfo
            extendedInfo.setTempMoveSpeed(moveSpeed.steps)
        }
    }

    private fun Player.resolvePendingMoveSpeed(): MoveSpeed =
        when {
            pendingTelejump -> MoveSpeed.Stationary
            pendingTeleport -> MoveSpeed.Walk
            pendingStepCount == 1 -> MoveSpeed.Walk
            pendingStepCount == 2 -> MoveSpeed.Run
            else -> moveSpeed
        }

    private fun Player.updateCoords() {
        infos.updateRootCoord(level, x, z)
        knownCoords = coords
    }

    /**
     * Guarantees the root build area rsprot's info protocols see is anchored on the world entity's
     * root coord while the player stands inside the entity's instance zones (e.g. a boat deck).
     *
     * rsprot's world-entity range check (`WorldEntityInfo.isWorldInRange`) requires the entity's
     * _root-world_ coord to fall inside the root build area it is given; `Infos`' own KDoc states
     * that a player aboard a world entity should have this build area correspond to the entity's
     * coordgrid in the root world. Without it, the world entity drops out of (or never enters) high
     * resolution: `getAddedWorldEntityIndices` never fires and no `REBUILD_WORLDENTITY` is sent.
     * (This - not the instance-zone address - was the root cause of the "boarding never queues
     * RebuildWorldEntity" bug observed when relocating the boat instance land.)
     *
     * [Player.buildArea] is already anchored the same way by `PlayerBuildAreaProcessor` while
     * aboard, so this normally re-pushes the same value [rebuildArea] derived from it; it runs
     * _after_ [rebuildArea] to keep the last word on the value rsprot sees for the tick either way.
     */
    private fun Player.updateRootInfoBuildArea() {
        val worldEntity = worldEntities.firstOrNull { it.containsInstanceCoords(coords) } ?: return
        val rootCoord = worldEntity.rootCoord
        infos.updateRootBuildAreaCenteredOnPlayer(rootCoord.x, rootCoord.z)
    }

    private fun Player.rebuildArea() {
        val recalcBuildArea = knownBuildArea != buildArea && buildArea != CoordGrid.NULL
        if (recalcBuildArea) {
            val zone = ZoneKey.from(buildArea)
            val area = BuildArea(zone.x, zone.z)
            infos.updateRootBuildArea(area)
        }

        if (!recalcBuildArea) {
            return
        }

        // Skip log-in rebuild as RebuildLogin is already sent.
        if (knownBuildArea == CoordGrid.NULL) {
            knownBuildArea = buildArea
            return
        }

        if (regionUid == null) {
            // No registered region backs the player's coords. Centre a REBUILD_NORMAL on the
            // build area; the client resolves the enclosing static map itself. Note that while
            // aboard a world entity the build area anchors on the entity's root coord (see
            // `PlayerBuildAreaProcessor`), so this rebuilds the real map around the entity - the
            // player's own (instance-land) coords are never used as a map anchor.
            val zone = ZoneKey.from(buildArea)
            val centerZoneX = zone.x + BuildAreaUtils.ZONE_VIEW_RADIUS
            val centerZoneZ = zone.z + BuildAreaUtils.ZONE_VIEW_RADIUS
            val rebuild = RebuildNormalV2(centerZoneX, centerZoneZ, worldId)
            knownBuildArea = buildArea
            knownRegionUid = null
            cachedRegionZoneProvider = null
            session.queue(rebuild)
            return
        }

        val region = regions[coords]

        // The player's region uid should be reassigned every cycle before calling this function,
        // as such we should expect the region to always be valid at this point.
        checkNotNull(region) { "Unexpected invalid region: uid=$regionUid, coords=$coords" }

        // TODO: When implementing `net` module properly, figure out what the best way would be to
        //  "invalidate" the `cachedRebuildRegion` if the region is somehow altered. This can
        //  happen in regions such as the Gauntlet. (If we decide to keep this as a cached value
        //  as opposed to reconstructing it every time)
        if (regionUid != knownRegionUid) {
            cachedRegionZoneProvider = createRegionZoneProvider(region)
            knownRegionUid = regionUid
        }

        val zoneProvider = cachedRegionZoneProvider ?: createRegionZoneProvider(region)
        infos.updateRootBuildAreaCenteredOnPlayer(x, z)
        val rebuild = RebuildRegionV2(x shr 3, z shr 3, true, zoneProvider)
        knownBuildArea = buildArea
        cachedRegionZoneProvider = zoneProvider
        session.queue(rebuild)
    }

    private fun createRegionZoneProvider(
        region: Region
    ): RebuildRegionV2.RebuildRegionZoneProvider {
        val regionZones = region.toZoneList()
        val rebuildZones =
            regionZones.associateWith { zone ->
                val copyZone = regions[zone]
                if (copyZone == RegionZoneCopy.NULL) {
                    return@associateWith null
                }
                RebuildRegionZone(
                    copyZone.normalX,
                    copyZone.normalZ,
                    copyZone.normalLevel,
                    copyZone.rotation,
                )
            }
        val zoneProvider =
            object : RebuildRegionV2.RebuildRegionZoneProvider {
                override fun provide(zoneX: Int, zoneZ: Int, level: Int): RebuildRegionZone? {
                    val zoneKey = ZoneKey(zoneX, zoneZ, level)
                    return rebuildZones[zoneKey]
                }
            }
        return zoneProvider
    }

    private fun Player.applyPublicMessage() {
        val message = publicMessage ?: return
        playerExtendedInfo.setChat(
            colour = message.colour,
            effects = message.effect,
            modicon = message.modIcon,
            autotyper = message.autoTyper,
            text = message.text,
            pattern = message.pattern,
        )
        publicMessage = null
    }

    private fun Player.applyFace() {
        val slot = faceEntity.entitySlot
        if (pendingFaceAngle != EntityFaceAngle.NULL) {
            playerExtendedInfo.setFaceAngle(pendingFaceAngle.intValue)
            knownFaceEntity = slot
            return
        }
        if (knownFaceEntity != slot) {
            playerExtendedInfo.setFacePathingEntity(slot)
            knownFaceEntity = slot
        }
    }

    private fun Player.applyAnim() {
        when (pendingSequence) {
            EntitySeq.NULL -> return
            EntitySeq.ZERO -> playerExtendedInfo.setSequence(-1, 0)
            else -> playerExtendedInfo.setSequence(pendingSequence.id, pendingSequence.delay)
        }
    }

    private fun Player.applySpotanims() {
        if (pendingSpotanims.isEmpty) {
            return
        }
        for (packed in pendingSpotanims.longIterator()) {
            val (id, delay, height, slot) = EntitySpotanim(packed)
            playerExtendedInfo.setSpotAnim(slot, id, delay, height)
        }
    }

    private fun Player.applySay() {
        val text = pendingSay ?: return
        playerExtendedInfo.setSay(text)
    }

    private fun Player.applyExactMove() {
        val move = pendingExactMove ?: return
        playerExtendedInfo.setExactMove(
            deltaX1 = move.deltaX1,
            deltaZ1 = move.deltaZ1,
            delay1 = move.clientDelay1,
            deltaX2 = move.deltaX2,
            deltaZ2 = move.deltaZ2,
            delay2 = move.clientDelay2,
            angle = move.direction,
        )
    }

    private fun Player.applyHeadbars() {
        for (packedHeadbar in activeHeadbars.longIterator()) {
            val headbar = Headbar(packedHeadbar)
            playerExtendedInfo.addHeadBar(
                sourceIndex = if (headbar.isNoSource) -1 else headbar.sourceSlot,
                selfType = headbar.self,
                otherType = if (headbar.isPrivate) -1 else headbar.public,
                startFill = headbar.startFill,
                endFill = headbar.endFill,
                startTime = headbar.startTime,
                endTime = headbar.endTime,
            )
        }
    }

    private fun Player.applyHitmarks() {
        for (packedHitmark in activeHitmarks.longIterator()) {
            val hitmark = Hitmark(packedHitmark)
            playerExtendedInfo.addHitMark(
                sourceIndex = if (hitmark.isNoSource) -1 else hitmark.sourceSlot,
                selfType = hitmark.self,
                sourceType = hitmark.source,
                otherType = if (hitmark.isPrivate) -1 else hitmark.public,
                value = hitmark.damage,
                delay = hitmark.delay,
            )
        }
    }

    private fun Player.syncAppearance(objTypes: ObjTypeList) {
        if (!appearance.rebuild) {
            return
        }
        if (!hasInitializedInventories()) {
            return
        }
        val info = playerExtendedInfo

        val colours = appearance.coloursSnapshot()
        for (i in colours.indices) {
            info.setColour(i, colours[i].toInt())
        }

        val identKit = appearance.identKitSnapshot()
        for (i in identKit.indices) {
            info.setIdentKit(i, identKit[i].toInt())
        }

        info.setName(displayName)
        info.setOverheadIcon(overheadIcon ?: -1)
        info.setSkullIcon(skullIcon ?: -1)
        info.setCombatLevel(combatLevel)
        info.setBodyType(appearance.bodyType)
        info.setPronoun(appearance.pronoun)
        info.setHidden(appearance.softHidden)

        info.setNameExtras(
            beforeName = appearance.namePrefix ?: "",
            afterName = appearance.nameSuffix ?: "",
            afterCombatLevel = appearance.combatLvlSuffix ?: "",
        )

        val bas = this.appearance.bas
        val weapon = this.righthand
        val transmog = this.transmog

        val readyAnim: Int
        val turnOnSpotAnim: Int
        val walkForwardAnim: Int
        val walkBackAnim: Int
        val walkLeftAnim: Int
        val walkRightAnim: Int
        val runningAnim: Int

        if (bas != null) {
            readyAnim = bas.readyAnim.id
            turnOnSpotAnim = bas.turnOnSpot.id
            walkForwardAnim = bas.walkForward.id
            walkBackAnim = bas.walkBack.id
            walkLeftAnim = bas.walkLeft.id
            walkRightAnim = bas.walkRight.id
            runningAnim = bas.running.id
        } else if (transmog != null) {
            readyAnim = transmog.readyAnim
            turnOnSpotAnim = transmog.turnBackAnim
            walkForwardAnim = transmog.walkAnim
            walkBackAnim = transmog.walkAnim
            walkLeftAnim = transmog.turnLeftAnim
            walkRightAnim = transmog.turnRightAnim
            runningAnim = transmog.runAnim
        } else if (weapon != null) {
            val type = objTypes[weapon]
            readyAnim = type.param(params.bas_readyanim).id
            turnOnSpotAnim = type.param(params.bas_turnonspot).id
            walkForwardAnim = type.param(params.bas_walk_f).id
            walkBackAnim = type.param(params.bas_walk_b).id
            walkLeftAnim = type.param(params.bas_walk_l).id
            walkRightAnim = type.param(params.bas_walk_r).id
            runningAnim = type.param(params.bas_running).id
        } else {
            val default = baseanimsets.human_default
            readyAnim = default.readyAnim.id
            turnOnSpotAnim = default.turnOnSpot.id
            walkForwardAnim = default.walkForward.id
            walkBackAnim = default.walkBack.id
            walkLeftAnim = default.walkLeft.id
            walkRightAnim = default.walkRight.id
            runningAnim = default.running.id
        }

        info.setTransmogrification(transmog?.id ?: -1)
        info.setBaseAnimationSet(
            readyAnim = readyAnim,
            turnAnim = turnOnSpotAnim,
            walkAnim = walkForwardAnim,
            walkAnimBack = walkBackAnim,
            walkAnimLeft = walkLeftAnim,
            walkAnimRight = walkRightAnim,
            runAnim = runningAnim,
        )

        for (wearpos in Wearpos.visibleWearpos) {
            val obj = worn[wearpos.slot]
            if (obj == null) {
                info.setWornObj(wearpos.slot, -1, -1, -1)
                continue
            }
            val objType = objTypes[obj]
            info.setWornObj(wearpos.slot, obj.id, objType.wearpos2, objType.wearpos3)
        }
    }

    private companion object {
        private val logger = InlineLogger()
    }
}

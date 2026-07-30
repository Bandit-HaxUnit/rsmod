package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.utils.map.BuildAreaUtils
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.map.zone.ZoneKey

public class PlayerBuildAreaProcessor
@Inject
constructor(private val worldEntities: WorldEntityList) {
    public fun process(player: Player) {
        player.processBuildAreaChange()
    }

    private fun Player.processBuildAreaChange() {
        // While standing inside a world entity's instance zones (e.g. a boat deck), the build
        // area anchors on the _entity's_ root-world coord instead of the player's own
        // (instance-land) coords: the client keeps the real map around the entity loaded (a live
        // capture's RuneLite overlay showed the Port Sarim map regions resident while aboard) and
        // cannot host the entity's own source zones inside its root build area. The deck's
        // content is delivered separately, targeted at the entity's dynamic world - see
        // `PlayerZoneUpdateProcessor`.
        val worldEntity = worldEntities.firstOrNull { it.containsInstanceCoords(coords) }
        if (worldEntity != null) {
            buildArea = BuildAreaUtils.calculateBuildArea(ZoneKey.from(worldEntity.rootCoord))
            return
        }
        val rebuildBuildArea = BuildAreaUtils.requiresNewBuildArea(this)
        if (rebuildBuildArea) {
            enterBuildArea()
        }
    }

    private fun Player.enterBuildArea() {
        buildArea = BuildAreaUtils.calculateBuildArea(ZoneKey.from(coords))
    }
}

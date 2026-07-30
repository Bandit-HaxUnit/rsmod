package org.rsmod.api.repo.worldentity

import jakarta.inject.Inject
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.worldentity.isSuccess
import org.rsmod.game.entity.WorldEntity
import org.rsmod.map.CoordGrid

public class WorldEntityRepository @Inject constructor(private val registry: WorldEntityRegistry) {
    public fun add(entity: WorldEntity) {
        val add = registry.add(entity)
        check(add.isSuccess()) { "Failed to add world entity. (result=$add, entity=$entity)" }
    }

    public fun del(entity: WorldEntity) {
        val delete = registry.del(entity)
        check(delete.isSuccess()) { "Failed to delete world entity. (result=$delete, entity=$entity)" }
    }

    public fun get(slot: Int): WorldEntity? = registry.get(slot)

    public fun findByOwner(playerSlot: Int): WorldEntity? = registry.findByOwner(playerSlot)

    public fun findByInstanceCoords(coords: CoordGrid): WorldEntity? =
        registry.findByInstanceCoords(coords)

    public fun delAllOwnedBy(playerSlot: Int): Int = registry.delAllOwnedBy(playerSlot)

    public fun delAll(): Int = registry.delAll()
}

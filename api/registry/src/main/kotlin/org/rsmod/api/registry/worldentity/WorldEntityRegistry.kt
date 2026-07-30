package org.rsmod.api.registry.worldentity

import jakarta.inject.Inject
import org.rsmod.events.EventBus
import org.rsmod.game.entity.PathingEntity.Companion.INVALID_SLOT
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.worldentity.WorldEntityStateEvents
import org.rsmod.map.CoordGrid

public class WorldEntityRegistry
@Inject
constructor(
    private val entityList: WorldEntityList,
    private val eventBus: EventBus,
) {
    public fun get(slot: Int): WorldEntity? = entityList[slot]

    public fun findByOwner(playerSlot: Int): WorldEntity? =
        entityList.firstOrNull { it.ownerPlayerSlot == playerSlot }

    public fun findAllByOwner(playerSlot: Int): List<WorldEntity> =
        entityList.filter { it.ownerPlayerSlot == playerSlot }

    public fun findByInstanceCoords(coords: CoordGrid): WorldEntity? =
        entityList.firstOrNull { it.containsInstanceCoords(coords) }

    public fun delAllOwnedBy(playerSlot: Int): Int {
        val owned = findAllByOwner(playerSlot)
        owned.forEach { del(it) }
        return owned.size
    }

    public fun delAll(): Int {
        val all = entityList.toList()
        all.forEach { del(it) }
        return all.size
    }

    public fun add(entity: WorldEntity): WorldEntityRegistryResult.Add {
        val slot = entityList.nextFreeSlot() ?: return WorldEntityRegistryResult.Add.NoAvailableSlot
        entityList[slot] = entity
        entity.slotId = slot
        eventBus.publish(WorldEntityStateEvents.Create(entity))
        return WorldEntityRegistryResult.Add.Success
    }

    public fun del(entity: WorldEntity): WorldEntityRegistryResult.Delete {
        val slot = entity.slotId
        if (slot == INVALID_SLOT) {
            return WorldEntityRegistryResult.Delete.UnexpectedSlot
        } else if (entityList[slot] != entity) {
            return WorldEntityRegistryResult.Delete.ListSlotMismatch(entityList[slot])
        }
        entityList.remove(slot)
        eventBus.publish(WorldEntityStateEvents.Delete(entity))
        entity.slotId = INVALID_SLOT
        entity.disableAvatar()
        return WorldEntityRegistryResult.Delete.Success
    }
}

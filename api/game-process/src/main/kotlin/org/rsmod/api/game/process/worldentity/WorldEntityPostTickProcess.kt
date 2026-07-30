package org.rsmod.api.game.process.worldentity

import jakarta.inject.Inject
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList

public class WorldEntityPostTickProcess
@Inject
constructor(
    private val entityList: WorldEntityList,
    private val registry: WorldEntityRegistry,
    private val exceptionHandler: GameExceptionHandler,
) {
    public fun process() {
        for (entity in entityList) {
            entity.tryOrDelete {
                updateProtocolInfo()
                cleanUpPendingUpdates()
            }
        }
    }

    private fun WorldEntity.updateProtocolInfo() {
        if (!infoProtocol.isActive()) {
            return
        }

        val jump = pendingCoordJump
        pendingCoordJump = false
        infoProtocol.updateCoord(
            level = projectedLevel,
            fineX = fineX,
            fineZ = fineZ,
            angle = pendingAngle ?: angle,
            jump = jump,
        )

        val pending = pendingAngle
        if (pending != null && pending != angle) {
            infoProtocol.updateAngle(pending)
        }
    }

    private fun WorldEntity.cleanUpPendingUpdates() {
        pendingAngle?.let { angle = it }
        pendingAngle = null
        pendingCoordJump = false
    }

    private inline fun WorldEntity.tryOrDelete(block: WorldEntity.() -> Unit) =
        try {
            block(this)
        } catch (e: Exception) {
            registry.del(this)
            exceptionHandler.handle(e) { "Error processing post-tick for world entity: $this." }
        } catch (e: NotImplementedError) {
            registry.del(this)
            exceptionHandler.handle(e) { "Error processing post-tick for world entity: $this." }
        }
}

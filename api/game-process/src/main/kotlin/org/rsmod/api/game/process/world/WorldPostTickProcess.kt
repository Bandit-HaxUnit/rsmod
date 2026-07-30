package org.rsmod.api.game.process.world

import jakarta.inject.Inject
import org.rsmod.api.game.process.npc.NpcPostTickProcess
import org.rsmod.api.game.process.worldentity.WorldEntityPostTickProcess
import org.rsmod.api.repo.EntityLifecycleProcess

public class WorldPostTickProcess
@Inject
constructor(
    private val worldEntityPostTick: WorldEntityPostTickProcess,
    private val npcPostTick: NpcPostTickProcess,
    private val entityLifecycle: EntityLifecycleProcess,
    private val update: WorldUpdateProcess,
) {
    public fun process() {
        worldEntityPostTick.process()
        npcPostTick.process()
        entityLifecycle.process()
        update.process()
    }
}

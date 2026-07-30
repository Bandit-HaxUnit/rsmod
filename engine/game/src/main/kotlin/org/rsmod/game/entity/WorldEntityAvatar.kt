package org.rsmod.game.entity

import org.rsmod.game.entity.worldentity.NoopWorldEntityInfo
import org.rsmod.game.entity.worldentity.WorldEntityInfoProtocol

public class WorldEntityAvatar(
    public var infoProtocol: WorldEntityInfoProtocol = NoopWorldEntityInfo
)

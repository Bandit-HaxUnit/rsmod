package org.rsmod.api.net.rsprot

import net.rsprot.protocol.game.outgoing.info.worldentityinfo.WorldEntityAvatar
import org.rsmod.game.entity.worldentity.WorldEntityInfoProtocol

class RspWorldEntityInfo(val rspAvatar: WorldEntityAvatar) : WorldEntityInfoProtocol {
    private var active: Boolean = true

    override fun updateCoord(level: Int, fineX: Int, fineZ: Int, angle: Int, jump: Boolean) {
        rspAvatar.updateCoord(level, fineX, fineZ, jump)
        rspAvatar.updateAngle(angle)
    }

    override fun updateAngle(angle: Int) {
        rspAvatar.updateAngle(angle)
    }

    override fun disable() {
        active = false
        rspAvatar.setSpecific(false)
    }

    override fun isActive(): Boolean = active
}

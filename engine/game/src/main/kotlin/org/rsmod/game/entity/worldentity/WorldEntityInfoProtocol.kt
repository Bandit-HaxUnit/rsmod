package org.rsmod.game.entity.worldentity

public interface WorldEntityInfoProtocol {
    public fun updateCoord(level: Int, fineX: Int, fineZ: Int, angle: Int, jump: Boolean)

    public fun updateAngle(angle: Int)

    public fun disable()

    public fun isActive(): Boolean
}

public data object NoopWorldEntityInfo : WorldEntityInfoProtocol {
    override fun updateCoord(level: Int, fineX: Int, fineZ: Int, angle: Int, jump: Boolean) {}

    override fun updateAngle(angle: Int) {}

    override fun disable() {}

    override fun isActive(): Boolean = false
}

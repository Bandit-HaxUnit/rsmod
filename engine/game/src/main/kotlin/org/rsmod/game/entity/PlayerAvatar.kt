package org.rsmod.game.entity

public class PlayerAvatar : PathingEntityAvatar(size = 1) {
    public var name: String = ""

    /** While true, map clicks set boat heading instead of walking (sailing helm mode). */
    public var boatHelmHeadingMode: Boolean = false
}

package org.rsmod.game.entity

/** World entity indices are in range 1..4095 in the client protocol. */
public class WorldEntityList : EntityList<WorldEntity>(capacity = 4096, slotPadding = 1)

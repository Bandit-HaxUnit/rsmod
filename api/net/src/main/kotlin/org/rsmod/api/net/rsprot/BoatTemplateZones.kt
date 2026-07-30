package org.rsmod.api.net.rsprot

import net.rsprot.protocol.game.outgoing.map.RebuildWorldEntityV4
import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone

/**
 * Serves map geometry for player-owned raft world entities from the canonical template map square
 * at region (60, 100) / zone (480, 807).
 *
 * Confirmed directly from a real-game `build_area` capture (`rebuild_worldentity_v4` +
 * `build_area` debug lines) on login-aboard:
 * ```
 * source=(0_60_100_0_56), dest=(0_251_107_0_0), rotation=0
 * source=(1_60_100_0_56), dest=(1_251_107_0_0), rotation=0
 * source=(2_60_100_0_56), dest=(2_251_107_0_0), rotation=0
 * source=(3_60_100_0_56), dest=(3_251_107_0_0), rotation=0
 * ```
 * All four destination levels source from the *same* zone (480, 807) — only the level varies, and
 * it always matches between source and destination (dest level N reads from source level N). The
 * destination zone itself (251, 107 in this capture) is the live server's own per-player
 * instance-land allocation, unrelated to ours — only the source side is canonical.
 *
 * As of this writing our local cache has no baked data for this square (renders as empty void via
 * `::telezone 480 807 1`) — that's a cache-completeness gap, not a coordinate bug. An earlier
 * attempt substituted a nearby static model gallery at zone (484, 807) / level 0 to get *something*
 * to render; that made the hull visible but broke walkability, because that location's surrounding
 * scene carries its own baked collision (rocks/terrain) that gets copied along with the visuals and
 * overrides the walkable-lane collision `buildRaftDeck()`/`BoatCollision` set up server-side. Do not
 * reuse that zone as a substitute source without confirming it's collision-clean.
 */
internal object BoatTemplateZones {
    const val RAFT_WORLD_ENTITY_TYPE: Int = 1

    private const val TEMPLATE_ZONE_X: Int = 480
    private const val TEMPLATE_ZONE_Z: Int = 807

    fun zoneProvider(typeId: Int): RebuildWorldEntityV4.RebuildWorldEntityZoneProvider? {
        if (typeId != RAFT_WORLD_ENTITY_TYPE) {
            return null
        }
        return RebuildWorldEntityV4.RebuildWorldEntityZoneProvider { _, _, level ->
            RebuildRegionZone(TEMPLATE_ZONE_X, TEMPLATE_ZONE_Z, level, rotation = 0)
        }
    }
}

package org.rsmod.api.cache.map

import org.openrs2.buffer.use
import org.openrs2.cache.Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.map.area.MapAreaDecoder
import org.rsmod.api.cache.map.loc.MapLocListDecoder
import org.rsmod.api.cache.map.npc.MapNpcListDecoder
import org.rsmod.api.cache.map.obj.MapObjListDecoder
import org.rsmod.api.cache.map.tile.MapTileDecoder
import org.rsmod.api.cache.util.InlineByteBuf
import org.rsmod.api.cache.util.readOrNull
import org.rsmod.api.cache.util.toInlineBuf
import org.rsmod.map.square.MapSquareKey

public data class MapGroupFileLayout(
    val map: Int,
    val loc: Int,
    val npc: Int?,
    val obj: Int?,
    val area: Int?,
)

public object MapGroupFileLayoutDetector {
    private val defaultSample = MapSquareKey(50, 50)

    public fun detect(cache: Cache, sample: MapSquareKey = defaultSample): MapGroupFileLayout {
        val group = sample.id
        var map = -1
        var loc = -1
        var npc = -1
        var obj = -1
        var area = -1

        for (file in 0 until 5) {
            val backing =
                cache.readOrNull(Js5Archives.MAPS, group, file)?.use { it.toInlineBuf().backing }
                    ?: continue
            when {
                map == -1 && decodes(backing, MapTileDecoder::decode) -> map = file
                loc == -1 && decodes(backing, MapLocListDecoder::decode) -> loc = file
                area == -1 && decodes(backing, MapAreaDecoder::decode) -> area = file
                npc == -1 && decodes(backing, MapNpcListDecoder::decode) -> npc = file
                obj == -1 && decodes(backing, MapObjListDecoder::decode) -> obj = file
            }
        }

        check(map >= 0 && loc >= 0) {
            "Unable to detect rev-237 map group layout from sample square $sample"
        }

        return MapGroupFileLayout(
            map = map,
            loc = loc,
            npc = npc.takeIf { it >= 0 },
            obj = obj.takeIf { it >= 0 },
            area = area.takeIf { it >= 0 },
        )
    }

    public fun <T> decodes(backing: ByteArray, decode: (InlineByteBuf) -> T): Boolean =
        runCatching { decode(InlineByteBuf(backing)) }.isSuccess
}

package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.seq.HashedSeqType

typealias sailing_seqs = SailingSeqs

object SailingSeqs : SeqReferences() {
    val human_helm_active = find("human_sailing_alpha_helm_raft01_active01")
    val human_helm_active_loop =
        HashedSeqType(
            startHash = null,
            internalName = "human_sailing_alpha_helm_raft01_active01_loop",
            internalId = 13_341,
        )

    val helm_active = find("sailing_alpha_helm_raft01_active01")
    val helm_active_loop =
        HashedSeqType(
            startHash = null,
            internalName = "sailing_alpha_helm_raft01_active01_loop",
            internalId = 13_336,
        )
    val helm_inactive = find("sailing_alpha_helm_raft01_inactive01")

    val sail_wood_down = find("sailing_boat_sail_kandarin_1x3_down")
    val sail_linen_down_offset = find("sailing_boat_sail_kandarin_1x3_down_offset")

    val sail_wood_down_to_full = find("sailing_boat_sail_kandarin_1x3_down_to_full")
    val sail_linen_down_to_full_offset = find("sailing_boat_sail_kandarin_1x3_down_to_full_offset")

    val sail_wood_down_to_half = find("sailing_boat_sail_kandarin_1x3_down_to_half")
    val sail_linen_down_to_half_offset = find("sailing_boat_sail_kandarin_1x3_down_to_half_offset")

    val sail_wood_full_to_down = find("sailing_boat_sail_kandarin_1x3_full_to_down")
    val sail_linen_full_to_down_offset = find("sailing_boat_sail_kandarin_1x3_full_to_down_offset")

    val sail_wood_half_to_down = find("sailing_boat_sail_kandarin_1x3_half_to_down")
    val sail_linen_half_to_down_offset = find("sailing_boat_sail_kandarin_1x3_half_to_down_offset")
}

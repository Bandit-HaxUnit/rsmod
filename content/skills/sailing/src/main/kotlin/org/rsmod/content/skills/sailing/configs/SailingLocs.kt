package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.type.refs.loc.LocReferences

typealias sailing_locs = SailingLocs

object SailingLocs : LocReferences() {
    val gangplank_the_pandemonium = find("sailing_gangplank_the_pandemonium")

    val steering_kandarin_1x3_wood = find("sailing_boat_steering_kandarin_1x3_wood")
    val sail_kandarin_1x3_wood = find("sailing_boat_sail_kandarin_1x3_wood")
    val sail_kandarin_1x3_linen = find("sailing_boat_sail_kandarin_1x3_linen")
    val cargo_hold_regular_raft = find("sailing_boat_cargo_hold_regular_raft")

    val invisible_nonblocking = find("invisible_type0_nonblocking")
    val randomsound_ocean_gulls = find("randomsound_ardent_ocean_gulls")
    val randomsound_ocean_crashing_waves = find("randomsound_ardent_ocean_crashing_waves")
    val bgsound_ocean_water_loop = find("bgsound_sailing_ocean_water_loop_01")
}

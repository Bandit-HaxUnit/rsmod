package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.type.refs.varp.VarpReferences

typealias sailing_varps = SailingVarps

object SailingVarps : VarpReferences() {
    val sidepanel_boat_type = find("sailing_sidepanel_boat_type")
}

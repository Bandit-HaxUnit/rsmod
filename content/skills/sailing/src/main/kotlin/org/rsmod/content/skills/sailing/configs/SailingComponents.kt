package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.type.refs.comp.ComponentReferences

typealias sailing_components = SailingComponents

object SailingComponents : ComponentReferences() {
    /** Sidepanel sail speed buttons (`if_buttonx` trace sub=0/1/2). */
    val facilities_content_clicklayer = find("sailing_sidepanel:facilities_content_clicklayer")
}

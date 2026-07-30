package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.type.refs.interf.InterfaceReferences

typealias sailing_interfaces = SailingInterfaces

object SailingInterfaces : InterfaceReferences() {
    val sidepanel = find("sailing_sidepanel")
}

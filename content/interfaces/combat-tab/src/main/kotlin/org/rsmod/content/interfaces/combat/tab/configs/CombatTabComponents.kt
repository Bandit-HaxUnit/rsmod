package org.rsmod.content.interfaces.combat.tab.configs

import org.rsmod.api.type.refs.comp.ComponentReferences

typealias combat_components = CombatTabComponents

object CombatTabComponents : ComponentReferences() {
    val stance1 = find("combat_interface:0", 311653829278247770)
    val stance2 = find("combat_interface:1", 4684467431532907852)
    val stance3 = find("combat_interface:2", 4131728010349991345)
    val stance4 = find("combat_interface:3", 467180306506721033)
    val auto_retaliate = find("combat_interface:retaliate", 2493258517564384991)
    val special_attack = find("combat_interface:special_attack", 6633356982973521316)

    val special_attack_orb = find("orbs:specbutton", 8522710415849917018)
}

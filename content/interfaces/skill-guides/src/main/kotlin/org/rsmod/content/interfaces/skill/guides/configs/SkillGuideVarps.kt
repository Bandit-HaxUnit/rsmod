package org.rsmod.content.interfaces.skill.guides.configs

import org.rsmod.api.type.refs.varp.VarpReferences

typealias guide_varps = SkillGuideVarps

object SkillGuideVarps : VarpReferences() {
    val selected_skill = find("skill_guide_v2")
}

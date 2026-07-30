package org.rsmod.content.interfaces.skill.guides

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.skill.guides.configs.guide_components
import org.rsmod.content.interfaces.skill.guides.configs.guide_enums
import org.rsmod.content.interfaces.skill.guides.configs.guide_interfaces
import org.rsmod.content.interfaces.skill.guides.configs.guide_varps
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.enums.EnumTypeMapResolver
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SkillGuideScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val enumResolver: EnumTypeMapResolver,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val mappedTabButtons = enumResolver[guide_enums.open_buttons].filterValuesNotNull()
        for ((button, skill) in mappedTabButtons) {
            onIfOverlayButton(button) { player.selectGuide(skill) }
        }

        onIfOverlayButton(guide_components.close_button) { player.closeGuide() }
    }

    private fun Player.selectGuide(skill: Int) {
        ifClose(eventBus)
        protectedAccess.launch(this) { openGuide(skill) }
    }

    private fun Player.openGuide(skill: Int) {
        selectedSkill = skill
        ifOpenOverlay(guide_interfaces.skill_guide_v2, eventBus)
    }

    private fun Player.closeGuide() {
        ifCloseSub(guide_interfaces.skill_guide_v2, eventBus)
    }
}

private var Player.selectedSkill by intVarp(guide_varps.selected_skill)

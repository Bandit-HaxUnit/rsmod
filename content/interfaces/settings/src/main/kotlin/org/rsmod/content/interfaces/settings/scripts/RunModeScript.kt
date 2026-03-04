package org.rsmod.content.interfaces.settings.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.interfaces.settings.configs.setting_components
import org.rsmod.content.interfaces.settings.configs.setting_queues
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.comp.HashedComponentType
import org.rsmod.game.ui.Component
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RunModeScript
@Inject
constructor(private val eventBus: EventBus, private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    override fun ScriptContext.startup() {
        val registeredRunTogglePackedIds = hashSetOf<Int>()
        registerRunToggle(setting_components.runbutton_orb, registeredRunTogglePackedIds)
        registerRunToggle(setting_components.runbutton_orb_nomap, registeredRunTogglePackedIds)
        registerRunToggle(setting_components.runbutton_orb_osm, registeredRunTogglePackedIds)
        registerRunToggle(setting_components.runbutton_orb_osm_nomap, registeredRunTogglePackedIds)
        registerRunTogglePacked(160, 28, registeredRunTogglePackedIds)
        registerRunTogglePacked(895, 25, registeredRunTogglePackedIds)
        registerRunTogglePacked(897, 24, registeredRunTogglePackedIds)
        registerRunTogglePacked(898, 25, registeredRunTogglePackedIds)
        registerRunToggle(setting_components.runmode, registeredRunTogglePackedIds)
        onPlayerQueue(setting_queues.runmode_toggle) { toggleRun() }
    }

    private fun ScriptContext.registerRunToggle(
        component: ComponentType,
        registeredPackedIds: MutableSet<Int>,
    ) {
        if (!registeredPackedIds.add(component.packed)) {
            return
        }
        onIfOverlayButton(component) { player.selectRunToggle() }
    }

    private fun ScriptContext.registerRunTogglePacked(
        parent: Int,
        child: Int,
        registeredPackedIds: MutableSet<Int>,
    ) {
        val component =
            HashedComponentType(
                startHash = null,
                internalName = "runmode_toggle_fallback_${parent}_$child",
                internalId = Component(parent, child).packed,
            )
        registerRunToggle(component, registeredPackedIds)
    }

    private fun Player.selectRunToggle() {
        if (setting_queues.runmode_toggle in queueList) {
            return
        }
        ifClose(eventBus)
        val toggled = protectedAccess.launch(this) { toggleRun() }
        if (!toggled) {
            strongQueue(setting_queues.runmode_toggle, 1)
        }
    }
}

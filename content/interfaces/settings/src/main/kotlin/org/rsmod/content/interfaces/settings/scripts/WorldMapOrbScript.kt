package org.rsmod.content.interfaces.settings.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.settings.configs.setting_components
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.comp.HashedComponentType
import org.rsmod.game.type.interf.HashedInterfaceType
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.ui.Component
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WorldMapOrbScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    private val worldMapInterface =
        HashedInterfaceType(
            startHash = null,
            internalName = "worldmap_fallback_595",
            internalId = 595,
        )

    override fun ScriptContext.startup() {
        val registeredOrbPackedIds = hashSetOf<Int>()
        registerWorldMapOrb(setting_components.worldmap_orb, registeredOrbPackedIds)
        registerWorldMapOrb(setting_components.worldmap_orb_nomap, registeredOrbPackedIds)
        registerWorldMapOrb(setting_components.worldmap_orb_osm, registeredOrbPackedIds)
        registerWorldMapOrb(setting_components.worldmap_orb_osm_nomap, registeredOrbPackedIds)

        // Known rev drift fallbacks from packet traces/symbols.
        registerWorldMapOrbPacked(160, 55, registeredOrbPackedIds)
        registerWorldMapOrbPacked(895, 53, registeredOrbPackedIds)
        registerWorldMapOrbPacked(897, 51, registeredOrbPackedIds)
        registerWorldMapOrbPacked(898, 56, registeredOrbPackedIds)

        val registeredClosePackedIds = hashSetOf<Int>()
        registerWorldMapClosePacked(595, 38, registeredClosePackedIds)
        registerWorldMapClosePacked(595, 40, registeredClosePackedIds)
    }

    private fun ScriptContext.registerWorldMapOrb(
        component: ComponentType,
        registeredPackedIds: MutableSet<Int>,
    ) {
        if (!registeredPackedIds.add(component.packed)) {
            return
        }
        onIfOverlayButton(component) {
            player.selectWorldMapOrb(op)
        }
    }

    private fun ScriptContext.registerWorldMapOrbPacked(
        parent: Int,
        child: Int,
        registeredPackedIds: MutableSet<Int>,
    ) {
        val component =
            HashedComponentType(
                startHash = null,
                internalName = "worldmap_orb_fallback_${parent}_$child",
                internalId = Component(parent, child).packed,
            )
        registerWorldMapOrb(component, registeredPackedIds)
    }

    private fun ScriptContext.registerWorldMapClosePacked(
        parent: Int,
        child: Int,
        registeredPackedIds: MutableSet<Int>,
    ) {
        val component =
            HashedComponentType(
                startHash = null,
                internalName = "worldmap_close_fallback_${parent}_$child",
                internalId = Component(parent, child).packed,
            )
        if (!registeredPackedIds.add(component.packed)) {
            return
        }
        onIfOverlayButton(component) { player.closeWorldMap() }
    }

    private fun Player.selectWorldMapOrb(op: IfButtonOp) {
        if (op != IfButtonOp.Op2 && op != IfButtonOp.Op1) {
            return
        }
        if (ui.containsOverlay(worldMapInterface)) {
            closeWorldMap()
        } else {
            ifOpenOverlay(worldMapInterface, eventBus)
        }
    }

    private fun Player.closeWorldMap() {
        ifCloseOverlay(worldMapInterface, eventBus)
    }
}

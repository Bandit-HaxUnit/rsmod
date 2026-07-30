package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.type.refs.varbit.VarBitReferences

typealias sailing_varbits = SailingVarBits

object SailingVarBits : VarBitReferences() {
    val boarded_boat = find("sailing_boarded_boat")
    val boarded_boat_world = find("sailing_boarded_boat_world")
    val player_is_on_player_boat = find("sailing_player_is_on_player_boat")
    val boat_facility_lockedin = find("sailing_boat_facility_lockedin")
    val preloaded_anims = find("sailing_preloaded_anims")
    val boat_spawned = find("sailing_boat_spawned")
    val boat_spawned_angle = find("sailing_boat_spawned_angle")
    val boat_spawned_finex = find("sailing_boat_spawned_finex")
    val boat_spawned_finez = find("sailing_boat_spawned_finez")
    val boarded_boat_last_dock = find("sailing_boarded_boat_last_dock")

    val sidepanel_visible = find("sailing_sidepanel_visible")
    val sidepanel_visible_from_combat_tab = find("sailing_sidepanel_visible_from_combat_tab")
    val sidepanel_player_role = find("sailing_sidepanel_player_role")
    val sidepanel_boat_move_mode = find("sailing_sidepanel_boat_move_mode")
    val sidepanel_sail_button_toggled = find("sailing_sidepanel_sail_button_toggled")
    val sidepanel_players_on_board_total = find("sailing_sidepanel_players_on_board_total")
    val sidepanel_facility_hotspot0 = find("sailing_sidepanel_facility_hotspot0")
    val sidepanel_boat_hp_max = find("sailing_sidepanel_boat_hp_max")
    val sidepanel_boat_hp = find("sailing_sidepanel_boat_hp")
    val sidepanel_helm_status = find("sailing_sidepanel_helm_status")
    val sidepanel_player_at_helm = find("sailing_sidepanel_player_at_helm")
    val sidepanel_repairkits = find("sailing_sidepanel_repairkits")
}

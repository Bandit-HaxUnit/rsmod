package org.rsmod.content.skills.sailing.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.sailing.configs.sailing_components
import org.rsmod.content.skills.sailing.configs.sailing_interfaces
import org.rsmod.content.skills.sailing.configs.sailing_locs
import org.rsmod.content.skills.sailing.configs.sailing_varbits
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.add
import org.rsmod.game.map.collision.remove
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

class BoatBoardingTest {
    /** Gangplank board uses a 3-tick fade (out → board → in → close). */
    private fun GameTestScope.boardViaGangplank(gangplank: BoundLocInfo) {
        player.opLoc2(gangplank)
        advance(ticks = 4)
    }

    @Test
    fun GameTestState.`board the pandemonium via gangplank op2`() =
        runGameTest(BoatBoarding::class, BoatNavigation::class) {
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            player.opLoc2(gangplank)
            advance(ticks = 2)
            assertMessageSent("You board your boat.")
            advance(ticks = 2)
            assertEquals(1, player.coords.level)
            assertEquals(1, player.vars[sailing_varbits.boarded_boat])
            assertEquals(1, player.vars[sailing_varbits.player_is_on_player_boat])
            assertEquals(1, player.vars[sailing_varbits.sidepanel_visible])
            assertEquals(4, player.vars[sailing_varbits.sidepanel_boat_move_mode])
            assertEquals(1, player.vars[sailing_varbits.preloaded_anims])
            assertEquals(1, player.vars[sailing_varbits.boat_spawned])
            assertEquals(64, player.vars[sailing_varbits.boat_spawned_finex])
            assertEquals(64, player.vars[sailing_varbits.boat_spawned_finez])
            assertTrue(player.vars[sailing_varbits.boarded_boat_world] > 0)

            assertNotNull(findLoc(player.coords, sailing_locs.steering_kandarin_1x3_wood))
            assertNotNull(
                findLoc(player.coords.translateZ(-1), sailing_locs.sail_kandarin_1x3_wood)
            )
            assertNotNull(
                findLoc(player.coords.translateZ(1), sailing_locs.sail_kandarin_1x3_linen)
            )
            assertNotNull(
                findLoc(player.coords.translateZ(-2), sailing_locs.cargo_hold_regular_raft)
            )
            assertNull(
                findLoc(player.coords.translateX(-1), sailing_locs.gangplank_the_pandemonium)
            )

            assertTrue(player.ui.containsOverlay(sailing_interfaces.sidepanel))
        }

    @Test
    fun GameTestState.`disembark via dock gangplank op1`() =
        runGameTest(BoatBoarding::class, BoatNavigation::class) {
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)

            player.opLoc1(gangplank)
            advance(ticks = 1)

            assertMessageSent("You disembark at the Pandemonium.")
            assertEquals(0, player.vars[sailing_varbits.boarded_boat])
            assertEquals(0, player.vars[sailing_varbits.player_is_on_player_boat])
            assertEquals(0, player.vars[sailing_varbits.sidepanel_helm_status])
            assertEquals(CoordGrid(3069, 2987, 0), player.coords)
            assertFalse(player.ui.containsOverlay(sailing_interfaces.sidepanel))
            assertTrue(player.ui.containsOverlay(interfaces.combat_interface))
        }

    @Test
    fun GameTestState.`helm navigate enters helm mode while aboard`() =
        runGameTest(BoatBoarding::class, BoatNavigation::class) {
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)

            val helmLoc = findLoc(player.coords, sailing_locs.steering_kandarin_1x3_wood)
            assertNotNull(helmLoc)

            val helm = BoundLocInfo(helmLoc, locTypes[sailing_locs.steering_kandarin_1x3_wood])
            player.opLoc1(helm)
            advance(ticks = 2)

            assertEquals(2, player.vars[sailing_varbits.sidepanel_helm_status])
            assertEquals(1, player.vars[sailing_varbits.sidepanel_player_at_helm])
            assertEquals(4, player.vars[sailing_varbits.sidepanel_boat_move_mode])
            assertEquals(0, player.vars[sailing_varbits.sidepanel_sail_button_toggled])
            assertTrue(player.avatar.boatHelmHeadingMode)
        }

    @Test
    fun GameTestState.`helm navigate stops when helm op1 again`() =
        runGameTest(BoatBoarding::class, BoatNavigation::class) {
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)

            val helmLoc = findLoc(player.coords, sailing_locs.steering_kandarin_1x3_wood)
            assertNotNull(helmLoc)
            val helm = BoundLocInfo(helmLoc, locTypes[sailing_locs.steering_kandarin_1x3_wood])

            player.opLoc1(helm)
            advance(ticks = 2)
            player.opLoc1(helm)
            advance(ticks = 2)

            assertEquals(1, player.vars[sailing_varbits.sidepanel_helm_status])
            assertEquals(0, player.vars[sailing_varbits.sidepanel_player_at_helm])
            assertEquals(0, player.vars[sailing_varbits.sidepanel_boat_move_mode])
            assertEquals(0, player.vars[sailing_varbits.sidepanel_sail_button_toggled])
            assertFalse(player.avatar.boatHelmHeadingMode)
        }

    @Test
    fun GameTestState.`full sail moves the boat world entity`() =
        runInjectedGameTest(
            WorldEntityRepository::class,
            scripts = arrayOf(BoatBoarding::class, BoatNavigation::class, BoatMovementScript::class),
        ) { worldEntityRepo ->
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)

            val boat =
                worldEntityRepo.get(player.vars[sailing_varbits.boarded_boat_world])
                    ?: error("boat missing")
            val startRoot = boat.rootCoord

            val helmLoc = findLoc(player.coords, sailing_locs.steering_kandarin_1x3_wood)
            assertNotNull(helmLoc)
            val helm = BoundLocInfo(helmLoc, locTypes[sailing_locs.steering_kandarin_1x3_wood])
            player.opLoc1(helm)
            advance(ticks = 2)

            player.ifButton(sailing_components.facilities_content_clicklayer, comsub = 0)
            advance(ticks = 1)

            assertEquals(2, player.vars[sailing_varbits.sidepanel_boat_move_mode])
            assertEquals(1, player.vars[sailing_varbits.sidepanel_sail_button_toggled])

            val level = boat.projectedLevel
            for (dx in 0 until 8) {
                for (dz in 1..16) {
                    val tile = startRoot.translate(dx, dz)
                    collision.allocateIfAbsent(tile.x, tile.z, level)
                    collision.remove(tile, CollisionFlag.BLOCK_WALK)
                }
            }

            advance(ticks = 12)

            assertNotEquals(startRoot, boat.rootCoord)
            assertFalse(boat.pendingCoordJump)
        }

    @Test
    fun GameTestState.`re-boarding reuses the same boat`() =
        runGameTest(BoatBoarding::class) {
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)
            val firstWorld = player.vars[sailing_varbits.boarded_boat_world]
            val firstDeck = player.coords
            assertTrue(firstWorld > 0)
            assertEquals(1, firstDeck.level)

            player.teleport(gangplankCoords.translateX(-1))
            player.opLoc1(gangplank)
            advance(ticks = 1)

            player.teleport(gangplankCoords.translateX(-1))
            boardViaGangplank(gangplank)

            assertEquals(firstWorld, player.vars[sailing_varbits.boarded_boat_world])
            assertEquals(firstDeck, player.coords)
        }

    @Test
    fun GameTestState.`login aboard restores boat world entity and client state`() =
        runGameTest(BoatBoarding::class) {
            val deckCoords = CoordGrid(x = 3843, z = 6460, level = 1)
            player.coords = deckCoords
            VarPlayerIntMapSetter.set(player, sailing_varbits.boarded_boat, 1)
            VarPlayerIntMapSetter.set(player, sailing_varbits.player_is_on_player_boat, 1)

            eventBus.publish(SessionStateEvent.MapPrepare(player))

            assertNotNull(findLoc(deckCoords, sailing_locs.steering_kandarin_1x3_wood))
            assertTrue(player.vars[sailing_varbits.boarded_boat_world] > 0)
            assertEquals(1, player.vars[sailing_varbits.boat_spawned])
            assertEquals(64, player.vars[sailing_varbits.boat_spawned_finex])
            assertEquals(64, player.vars[sailing_varbits.boat_spawned_finez])
            assertEquals(4, player.vars[sailing_varbits.sidepanel_boat_move_mode])
            assertEquals(1, player.vars[sailing_varbits.sidepanel_visible_from_combat_tab])
            assertEquals(1, player.vars[sailing_varbits.preloaded_anims])
            assertTrue(player.ui.containsOverlay(sailing_interfaces.sidepanel))
        }

    @Test
    fun GameTestState.`login aboard reuses existing boat slot`() =
        runGameTest(BoatBoarding::class) {
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)
            val boatWorld = player.vars[sailing_varbits.boarded_boat_world]

            eventBus.publish(SessionStateEvent.MapPrepare(player))

            assertEquals(boatWorld, player.vars[sailing_varbits.boarded_boat_world])
            assertNotNull(findLoc(player.coords, sailing_locs.steering_kandarin_1x3_wood))
        }

    @Test
    fun GameTestState.`set heading updates boat navigation heading at helm`() =
        runInjectedGameTest(
            WorldEntityRepository::class,
            scripts = arrayOf(BoatBoarding::class, BoatNavigation::class),
        ) { worldEntityRepo ->
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)

            val boat = worldEntityRepo.findByOwner(player.slotId) ?: error("boat missing")
            val helmLoc = findLoc(player.coords, sailing_locs.steering_kandarin_1x3_wood)
            assertNotNull(helmLoc)
            val helm = BoundLocInfo(helmLoc, locTypes[sailing_locs.steering_kandarin_1x3_wood])

            player.opLoc1(helm)
            advance(ticks = 2)

            player.setHeading(heading = 6)
            advance(ticks = 1)

            assertEquals(768, boat.navigationHeading)

            advance(ticks = 1)

            assertEquals(2, player.vars[sailing_varbits.sidepanel_boat_move_mode])
            assertEquals(1, player.vars[sailing_varbits.sidepanel_sail_button_toggled])
        }

    @Test
    fun GameTestState.`boat movement blocked by root-world collision`() =
        runInjectedGameTest(
            WorldEntityRepository::class,
            scripts = arrayOf(BoatBoarding::class, BoatNavigation::class, BoatMovementScript::class),
        ) { worldEntityRepo ->
            val gangplankCoords = CoordGrid(0, 50, 50, 34, 31)
            val gangplank = placeMapLoc(gangplankCoords, sailing_locs.gangplank_the_pandemonium)
            player.teleport(gangplankCoords.translateX(-1))

            boardViaGangplank(gangplank)

            val boat =
                worldEntityRepo.get(player.vars[sailing_varbits.boarded_boat_world])
                    ?: error("boat missing")

            val helmLoc = findLoc(player.coords, sailing_locs.steering_kandarin_1x3_wood)
            assertNotNull(helmLoc)
            val helm = BoundLocInfo(helmLoc, locTypes[sailing_locs.steering_kandarin_1x3_wood])
            player.opLoc1(helm)
            advance(ticks = 1)

            player.ifButton(sailing_components.facilities_content_clicklayer, comsub = 0)
            advance(ticks = 1)

            val openRoot = CoordGrid(0, 50, 50, 20, 20)
            boat.rootCoord = openRoot
            boat.subFineX = WorldEntity.FINE_TILE_SIZE / 2
            boat.subFineZ = WorldEntity.FINE_TILE_SIZE / 2
            boat.navigationHeading = 0
            VarPlayerIntMapSetter.set(player, sailing_varbits.boat_spawned_angle, boat.angle)
            VarPlayerIntMapSetter.set(player, sailing_varbits.boat_spawned_finex, boat.subFineX)
            VarPlayerIntMapSetter.set(player, sailing_varbits.boat_spawned_finez, boat.subFineZ)

            val level = boat.projectedLevel
            for (dx in -4 until 12) {
                for (dz in -4 until 12) {
                    val tile = openRoot.translate(dx, dz)
                    collision.allocateIfAbsent(tile.x, tile.z, level)
                    collision.remove(tile, CollisionFlag.BLOCK_WALK)
                }
            }
            for (dx in 0 until 8) {
                val block = openRoot.translate(dx, -1)
                collision.add(block, CollisionFlag.BLOCK_WALK)
            }
            val startRoot = boat.rootCoord

            advance(ticks = 16)

            assertEquals(startRoot, boat.rootCoord)
        }
}

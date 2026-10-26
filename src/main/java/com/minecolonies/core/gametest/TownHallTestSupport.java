package com.minecolonies.core.gametest;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.savedata.IServerColonySaveData;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.modules.TownHallAssignmentModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.core.util.ChunkDataHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * ZM: Shared set up of the Town Hall game tests: found a colony, register a building from a placed hut block, spawn villagers.
 */
final class TownHallTestSupport
{
    /**
     * Template with a flat stone floor (see data/minecolonies/structure/zm_flat_floor.nbt).
     */
    static final String FLOOR_TEMPLATE = "zm_flat_floor";

    /**
     * Display name of the default structure pack (the pack.json name, not the folder).
     */
    static final String PACK = "Minecolonies Original";

    private TownHallTestSupport()
    {
        // Helpers only.
    }

    /**
     * Found a colony whose Town Hall is placed at the given spot of the test structure.
     *
     * @param helper     the game test helper.
     * @param relative   the position of the Town Hall in the test structure.
     * @param townHallLv the level to give to the Town Hall.
     * @return the new colony.
     */
    static IColony foundColony(final GameTestHelper helper, final BlockPos relative, final int townHallLv)
    {
        final ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(relative);
        helper.setBlock(relative, ModBlocks.blockHutTownHall.defaultBlockState());
        // The colony is created through the save data, not through IColonyManager#createColony: that one needs a player, and the mock player of the game test framework fails
        // the login handlers of the mod (its fake connection refuses the mod payloads). A colony without owner is enough for these tests.
        final IColony colony = IServerColonySaveData.getOrComputeSaveData(level).createColony(level, "ZMGameTest", pos);
        colony.setStructurePack(PACK);
        colony.setName("ZMGameTest");
        ChunkDataHelper.claimColonyChunks(level, true, (Colony) colony, colony.getCenter());
        final IBuilding townHall = addBuilding(helper, colony, relative);
        helper.assertTrue(townHall instanceof BuildingTownHall, "the building must be a Town Hall");
        townHall.setBuildingLevel(townHallLv);
        return colony;
    }

    /**
     * Register an already placed hut block as a building of the colony.
     *
     * @param helper   the game test helper.
     * @param colony   the colony.
     * @param relative the position of the block in the test structure.
     * @return the registered building.
     */
    static IBuilding addBuilding(final GameTestHelper helper, final IColony colony, final BlockPos relative)
    {
        final BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        helper.assertTrue(blockEntity instanceof TileEntityColonyBuilding, "the hut block must have a colony building tile entity");
        final IBuilding building = colony.getServerBuildingManager().addNewBuilding((TileEntityColonyBuilding) blockEntity, helper.getLevel());
        helper.assertTrue(building != null, "the building must be registered");
        return building;
    }

    /**
     * Place a hut block, register it and make it a finished hut of level 1, through the same call the builder makes when an upgrade is done.
     *
     * @param helper   the game test helper.
     * @param colony   the colony.
     * @param block    the hut block.
     * @param relative the position in the test structure.
     * @return the finished hut.
     */
    static IBuilding addFinishedHut(final GameTestHelper helper, final IColony colony, final Block block, final BlockPos relative)
    {
        helper.setBlock(relative, block.defaultBlockState());
        final IBuilding hut = addBuilding(helper, colony, relative);
        hut.setBuildingLevel(1);
        hut.onUpgradeComplete(null, 1);
        return hut;
    }

    /**
     * Create a villager with an entity next to the Town Hall.
     *
     * @param helper the game test helper.
     * @param colony the colony.
     * @return the villager; ids grow with every call.
     */
    static ICitizenData spawnVillager(final GameTestHelper helper, final IColony colony)
    {
        final ICitizenData citizen = colony.getCitizenManager().createAndRegisterCivilianData();
        colony.getCitizenManager().spawnOrCreateCivilian(citizen, helper.getLevel(), List.of(colony.getServerBuildingManager().getTownHall().getPosition()), true);
        helper.assertTrue(citizen.getEntity().isPresent(), "the villager entity must be spawned");
        return citizen;
    }

    /**
     * The assignment module a building hosts for a job.
     *
     * @param helper   the game test helper.
     * @param building the Town Hall.
     * @param job      the job.
     * @return the module.
     */
    static TownHallAssignmentModule hostedModule(final GameTestHelper helper, final IBuilding building, final JobEntry job)
    {
        final TownHallAssignmentModule module = building.getModulesByType(TownHallAssignmentModule.class)
                                                  .stream()
                                                  .filter(m -> m.getJobEntry() == job)
                                                  .findFirst()
                                                  .orElse(null);
        helper.assertTrue(module != null, "the Town Hall must host an assignment module for " + job.getKey());
        return module;
    }
}

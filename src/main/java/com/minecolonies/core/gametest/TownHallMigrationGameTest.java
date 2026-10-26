package com.minecolonies.core.gametest;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.TownHallAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.jobs.JobLumberjack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ZM: A worker hosted by the Town Hall moves into the finished hut of its profession, unless the hut already has a worker.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TownHallMigrationGameTest
{
    /**
     * The hosted lumberjack moves into the lumberjack hut when the hut is finished; a second hosted lumberjack stays because the hut is occupied.
     *
     * @param helper the game test helper.
     */
    @GameTest(template = TownHallTestSupport.FLOOR_TEMPLATE, timeoutTicks = 600)
    public static void hostedLumberjackMovesToFinishedHut(final GameTestHelper helper)
    {
        final IColony colony = TownHallTestSupport.foundColony(helper, new BlockPos(4, 1, 4), 3);
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        final TownHallAssignmentModule hosted = TownHallTestSupport.hostedModule(helper, townHall, ModJobs.lumberjack.get());

        final ICitizenData first = TownHallTestSupport.spawnVillager(helper, colony);
        final ICitizenData second = TownHallTestSupport.spawnVillager(helper, colony);
        helper.assertTrue(hosted.assignCitizen(second) && hosted.assignCitizen(first), "both lumberjacks fit on level 3");

        final IBuilding hut = TownHallTestSupport.addFinishedHut(helper, colony, ModBlocks.blockHutLumberjack, new BlockPos(1, 1, 1));
        final WorkerBuildingModule hutModule = hut.getModulesByType(WorkerBuildingModule.class)
                                                 .stream()
                                                 .filter(m -> !(m instanceof TownHallAssignmentModule) && m.getJobEntry() == ModJobs.lumberjack.get())
                                                 .findFirst()
                                                 .orElse(null);
        helper.assertTrue(hutModule != null, "the lumberjack hut must have a worker module");

        // The finished hut pulled the lowest id, the other one stays because the hut is occupied now.
        helper.assertTrue(hutModule.getAssignedCitizen().contains(first), "the lowest id must have moved into the hut");
        helper.assertTrue(first.getWorkBuilding() == hut && first.getJob() instanceof JobLumberjack, "the moved lumberjack works for the hut");
        helper.assertTrue(!hosted.getAssignedCitizen().contains(first), "the Town Hall slot must be free again");
        helper.assertTrue(hosted.getAssignedCitizen().contains(second) && second.getWorkBuilding() == townHall, "the second lumberjack stays in the Town Hall");

        hutModule.onColonyTick(colony);
        helper.assertTrue(hutModule.getAssignedCitizen().size() == 1 && second.getWorkBuilding() == townHall, "an occupied hut must not pull another worker");

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(first.getJob() instanceof JobLumberjack && first.getWorkBuilding() == hut, "the moved lumberjack keeps its job in the hut");
            helper.assertTrue(second.getJob() instanceof JobLumberjack && second.getWorkBuilding() == townHall, "the hosted lumberjack keeps its job in the Town Hall");
            helper.succeed();
        });
    }
}

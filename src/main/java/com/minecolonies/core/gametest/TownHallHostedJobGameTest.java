package com.minecolonies.core.gametest;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.TownHallAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.jobs.JobFarmer;
import com.minecolonies.core.colony.jobs.JobLumberjack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ZM: Smoke test of the professions hosted by the Town Hall. It founds a colony with a level 1 Town Hall, gives a villager the lumberjack job from the Town Hall assignment module
 * and checks that the job survives 200 ticks (the worker AI calls setJob(null) when it rejects its workplace) and that the lumberjack AI was created with the Town Hall as
 * workplace. Run with the gameTestServer run configuration (queue task runGameTestServer). It is only registered outside production, see GameTestHooks.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TownHallHostedJobGameTest
{
    /**
     * Ticks the job must survive.
     */
    private static final int SURVIVE_TICKS = 200;

    /**
     * Found a colony with a level 1 Town Hall and host a lumberjack in it.
     *
     * @param helper the game test helper.
     */
    @GameTest(template = TownHallTestSupport.FLOOR_TEMPLATE, timeoutTicks = 600)
    public static void hostedLumberjackKeepsItsJob(final GameTestHelper helper)
    {
        final IColony colony = TownHallTestSupport.foundColony(helper, new BlockPos(4, 1, 4), 1);
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        final TownHallAssignmentModule module = TownHallTestSupport.hostedModule(helper, townHall, ModJobs.lumberjack.get());

        final ICitizenData citizen = TownHallTestSupport.spawnVillager(helper, colony);
        helper.assertTrue(module.assignCitizen(citizen), "the Town Hall must accept the lumberjack on level 1");
        helper.assertTrue(citizen.getJob() instanceof JobLumberjack, "the villager must have the lumberjack job right after the assignment");
        helper.assertTrue(citizen.getWorkBuilding() == townHall, "the work building must be the Town Hall");

        helper.runAfterDelay(SURVIVE_TICKS, () -> {
            helper.assertTrue(citizen.getJob() instanceof JobLumberjack, "the lumberjack job must still be there after " + SURVIVE_TICKS + " ticks (setJob(null) was called)");
            helper.assertTrue(citizen.getWorkBuilding() == townHall, "the work building must still be the Town Hall");
            helper.assertTrue(module.getAssignedCitizen().contains(citizen), "the Town Hall module must still list the villager");
            helper.assertTrue(citizen.getJob().getWorkerAI() != null, "the lumberjack AI must exist");
            // The AI state is logged so the report can show it; it depends on the time of day and the missing axe, so it is not asserted.
            Log.getLogger().info("ZM gametest: lumberjack AI {} in state {}", citizen.getJob().getWorkerAI().getClass().getSimpleName(), citizen.getJob().getWorkerAI().getState());
            helper.succeed();
        });
    }

    /**
     * Found a colony with a level 1 Town Hall and host a farmer in it. Same checks as for the lumberjack, plus the field module of the Town Hall: it must exist and not stay
     * claimed once the farmer is gone.
     *
     * @param helper the game test helper.
     */
    @GameTest(template = TownHallTestSupport.FLOOR_TEMPLATE, timeoutTicks = 600)
    public static void hostedFarmerKeepsItsJob(final GameTestHelper helper)
    {
        final IColony colony = TownHallTestSupport.foundColony(helper, new BlockPos(4, 1, 4), 1);
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        final TownHallAssignmentModule module = TownHallTestSupport.hostedModule(helper, townHall, ModJobs.farmer.get());
        helper.assertTrue(townHall.hasModule(BuildingModules.FARMER_FIELDS), "the Town Hall must host the farmer field module");
        helper.assertTrue(townHall.hasModule(BuildingModules.TOWNHALL_FARMER_WORKPLACE), "the Town Hall must host the farmer workplace module");

        final ICitizenData citizen = TownHallTestSupport.spawnVillager(helper, colony);
        helper.assertTrue(module.assignCitizen(citizen), "the Town Hall must accept the farmer on level 1");
        helper.assertTrue(citizen.getJob() instanceof JobFarmer, "the villager must have the farmer job right after the assignment");
        helper.assertTrue(citizen.getWorkBuilding() == townHall, "the work building must be the Town Hall");

        helper.runAfterDelay(SURVIVE_TICKS, () -> {
            helper.assertTrue(citizen.getJob() instanceof JobFarmer, "the farmer job must still be there after " + SURVIVE_TICKS + " ticks (setJob(null) was called)");
            helper.assertTrue(citizen.getWorkBuilding() == townHall, "the work building must still be the Town Hall");
            helper.assertTrue(module.getAssignedCitizen().contains(citizen), "the Town Hall module must still list the villager");
            helper.assertTrue(citizen.getJob().getWorkerAI() != null, "the farmer AI must exist");
            Log.getLogger().info("ZM gametest: farmer AI {} in state {}", citizen.getJob().getWorkerAI().getClass().getSimpleName(), citizen.getJob().getWorkerAI().getState());
            helper.succeed();
        });
    }
}

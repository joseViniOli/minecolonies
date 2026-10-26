package com.minecolonies.core.gametest;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.TownHallAssignmentModule;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.TownHallBuilderModule;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
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

    /**
     * Found a colony with a level 1 Town Hall, host a builder in it and give the colony a work order for a new hut. The builder must keep its job, the dispatch must treat the
     * Town Hall as a builder (the order is not refused with "builder necessary") and the Town Hall must claim the order. The build itself is not waited for.
     *
     * @param helper the game test helper.
     */
    @GameTest(template = TownHallTestSupport.FLOOR_TEMPLATE, timeoutTicks = 600)
    public static void hostedBuilderKeepsItsJobAndAcceptsOrders(final GameTestHelper helper)
    {
        final IColony colony = TownHallTestSupport.foundColony(helper, new BlockPos(4, 1, 4), 1);
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        final TownHallAssignmentModule module = TownHallTestSupport.hostedModule(helper, townHall, ModJobs.builder.get());
        helper.assertTrue(townHall.hasModule(BuildingModules.TOWNHALL_BUILDER_WORKPLACE), "the Town Hall must host the builder workplace module");

        final ICitizenData citizen = TownHallTestSupport.spawnVillager(helper, colony);
        helper.assertTrue(module.assignCitizen(citizen), "the Town Hall must accept the builder on level 1");
        helper.assertTrue(citizen.getJob() instanceof JobBuilder, "the villager must have the builder job right after the assignment");
        helper.assertTrue(citizen.getWorkBuilding() == townHall, "the work building must be the Town Hall");

        // A hut that is placed but not built yet, as the player leaves it before asking for the build.
        final BlockPos hutSpot = new BlockPos(8, 1, 4);
        helper.setBlock(hutSpot, ModBlocks.blockHutLumberjack.defaultBlockState());
        final IBuilding hut = TownHallTestSupport.addBuilding(helper, colony, hutSpot);
        // The test world does not place the hut with a structure tool, so the blueprint path is set by hand (the order is created from it).
        hut.setBlueprintPath("fundamentals/lumberjack1.blueprint");
        final WorkOrderBuilding order = WorkOrderBuilding.create(WorkOrderType.BUILD, hut);
        helper.assertTrue(order.canBeResolved(colony, order.getTargetLevel()), "the dispatch must see the hosted builder (otherwise the player gets 'builder necessary')");
        helper.assertTrue(!order.tooFarFromAnyBuilder(colony, order.getTargetLevel()), "the hosted builder must count as near");
        helper.assertTrue(order.canBuild(townHall), "the Town Hall with a hosted builder must be able to build a level 1 hut");

        // "Reading from NBT" skips the check that the building site lies in chunks claimed by the colony, which the headless test world does not set up for the hut.
        colony.getWorkManager().addWorkOrder(order, true);
        helper.assertTrue(order.getID() != 0, "the work order must be registered");

        helper.runAfterDelay(SURVIVE_TICKS, () -> {
            helper.assertTrue(citizen.getJob() instanceof JobBuilder, "the builder job must still be there after " + SURVIVE_TICKS + " ticks (setJob(null) was called)");
            helper.assertTrue(citizen.getWorkBuilding() == townHall, "the work building must still be the Town Hall");
            helper.assertTrue(citizen.getJob().getWorkerAI() != null, "the builder AI must exist");

            // The colony tick of the work manager hands the order to a builder; call it once so the check does not depend on when the colony ticks.
            colony.getWorkManager().onColonyTick(colony);
            helper.assertTrue(order.getClaimedBy().equals(townHall.getID()), "the Town Hall must have claimed the work order, claimed by " + order.getClaimedBy());
            helper.assertTrue(TownHallBuilderModule.of(townHall).hasWorkOrder(), "the hosted builder state must hold the work order");
            helper.assertTrue(citizen.getJob() instanceof JobBuilder, "the builder job must survive claiming the order");
            Log.getLogger().info("ZM gametest: builder AI {} in state {}", citizen.getJob().getWorkerAI().getClass().getSimpleName(), citizen.getJob().getWorkerAI().getState());
            helper.succeed();
        });
    }
}

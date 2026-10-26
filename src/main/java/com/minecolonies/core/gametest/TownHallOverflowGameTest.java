package com.minecolonies.core.gametest;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.TownHallAssignmentModule;
import com.minecolonies.core.colony.jobs.JobLumberjack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ZM: Overflow rule of the Town Hall: when the slots shrink, the villager with the highest id loses the profession (and stays alive in the colony).
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TownHallOverflowGameTest
{
    /**
     * Two lumberjacks on a level 3 Town Hall (2 slots), the level falls to 1 (1 slot): the lower id keeps the job, both while the game runs and when the saved data is loaded in
     * the opposite order.
     *
     * @param helper the game test helper.
     */
    @GameTest(template = TownHallTestSupport.FLOOR_TEMPLATE, timeoutTicks = 400)
    public static void overflowDropsHighestId(final GameTestHelper helper)
    {
        final IColony colony = TownHallTestSupport.foundColony(helper, new BlockPos(4, 1, 4), 3);
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        final TownHallAssignmentModule module = TownHallTestSupport.hostedModule(helper, townHall, ModJobs.lumberjack.get());

        final ICitizenData low = TownHallTestSupport.spawnVillager(helper, colony);
        final ICitizenData high = TownHallTestSupport.spawnVillager(helper, colony);
        helper.assertTrue(low.getId() < high.getId(), "the ids must grow");

        // Assigned in the opposite order of the ids on purpose.
        helper.assertTrue(module.assignCitizen(high), "slot 1 of 2");
        helper.assertTrue(module.assignCitizen(low), "slot 2 of 2");
        helper.assertTrue(!module.assignCitizen(TownHallTestSupport.spawnVillager(helper, colony)), "a third lumberjack must not fit on level 3");

        // While the game runs.
        townHall.setBuildingLevel(1);
        module.onColonyTick(colony);
        helper.assertTrue(low.getJob() instanceof JobLumberjack && module.getAssignedCitizen().contains(low), "the lowest id keeps the profession");
        helper.assertTrue(high.getJob() == null && !module.getAssignedCitizen().contains(high), "the highest id loses the profession");
        helper.assertTrue(colony.getCitizenManager().getCivilian(high.getId()) != null && high.getEntity().isPresent() && high.getEntity().get().isAlive(),
          "the villager that lost the profession must stay alive in the colony");

        // When the saved data is loaded: saved as [high, low] on level 3, loaded on level 1.
        townHall.setBuildingLevel(3);
        helper.assertTrue(module.removeCitizen(low), "free the slots");
        helper.assertTrue(module.assignCitizen(high), "slot 1 of 2 again");
        helper.assertTrue(module.assignCitizen(low), "slot 2 of 2 again");
        final CompoundTag saved = new CompoundTag();
        module.serializeNBT(helper.getLevel().registryAccess(), saved);
        module.removeCitizen(high);
        module.removeCitizen(low);
        townHall.setBuildingLevel(1);
        module.deserializeNBT(helper.getLevel().registryAccess(), saved);
        helper.assertTrue(module.getAssignedCitizen().size() == 1 && module.getAssignedCitizen().contains(low) && low.getJob() instanceof JobLumberjack,
          "after the load the lowest id must hold the only slot");
        helper.assertTrue(high.getJob() == null, "after the load the highest id must have no job");
        helper.succeed();
    }
}

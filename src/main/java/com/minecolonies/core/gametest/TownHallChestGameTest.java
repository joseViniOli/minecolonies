package com.minecolonies.core.gametest;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.TownHallAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.TownHallChestModule;
import com.minecolonies.core.colony.jobs.JobLumberjack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;

/**
 * ZM: Tests of the shared colony chest of the Town Hall (see TownHallChestModule). Run with the gameTestServer run configuration (queue task runGameTestServer).
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TownHallChestGameTest
{
    /**
     * The chest has the capacity of a double chest, stores and gives back items, and the request system sees what is in it.
     *
     * @param helper the game test helper.
     */
    @GameTest(template = TownHallTestSupport.FLOOR_TEMPLATE, timeoutTicks = 200)
    public static void colonyChestStoresAndGivesBack(final GameTestHelper helper)
    {
        final IColony colony = TownHallTestSupport.foundColony(helper, new BlockPos(4, 1, 4), 1);
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        final TownHallChestModule chest = townHall.getModule(BuildingModules.TOWNHALL_COLONY_CHEST);
        helper.assertTrue(chest != null, "the Town Hall must host the colony chest module");
        helper.assertTrue(chest.ensureCapacity(), "the chest must grow to its capacity");
        helper.assertTrue(townHall.getTileEntity().getInventory().getSlots() >= TownHallChestModule.CHEST_SLOTS, "the chest must have at least " + TownHallChestModule.CHEST_SLOTS + " slots");

        helper.assertTrue(chest.deposit(new ItemStack(Items.OAK_LOG, 10)).isEmpty(), "ten logs must fit");
        helper.assertTrue(chest.count(stack -> stack.is(Items.OAK_LOG)) == 10, "the chest must count the ten logs");
        helper.assertTrue(InventoryUtils.hasBuildingEnoughElseCount(townHall, stack -> stack.is(Items.OAK_LOG), 1) >= 1, "the request system must see the logs as stock of the building");

        final ItemStack taken = chest.withdraw(stack -> stack.is(Items.OAK_LOG), 4);
        helper.assertTrue(taken.getCount() == 4 && taken.is(Items.OAK_LOG), "four logs must come out");
        helper.assertTrue(chest.count(stack -> stack.is(Items.OAK_LOG)) == 6, "six logs must stay in the chest");
        helper.assertTrue(chest.withdraw(stack -> stack.is(Items.STONE), 4).isEmpty(), "nothing matches stone");

        // More than the chest holds: the rest comes back, nothing is lost.
        final ItemStack rest = chest.deposit(new ItemStack(Items.COBBLESTONE, TownHallChestModule.CHEST_SLOTS * 64));
        helper.assertTrue(!rest.isEmpty(), "a full chest must hand back what did not fit");
        helper.succeed();
    }

    /**
     * A wooden axe donated to the chest ends up in the inventory of the hosted lumberjack, who otherwise waits in NEEDS_ITEM for a tool.
     *
     * @param helper the game test helper.
     */
    @GameTest(template = TownHallTestSupport.FLOOR_TEMPLATE, timeoutTicks = 900)
    public static void hostedLumberjackTakesAxeFromColonyChest(final GameTestHelper helper)
    {
        final IColony colony = TownHallTestSupport.foundColony(helper, new BlockPos(4, 1, 4), 1);
        final IBuilding townHall = colony.getServerBuildingManager().getTownHall();
        final TownHallChestModule chest = townHall.getModule(BuildingModules.TOWNHALL_COLONY_CHEST);
        helper.assertTrue(chest != null && chest.ensureCapacity(), "the Town Hall must host the colony chest");
        helper.assertTrue(chest.deposit(new ItemStack(Items.WOODEN_AXE)).isEmpty(), "the axe must fit");

        final TownHallAssignmentModule module = TownHallTestSupport.hostedModule(helper, townHall, ModJobs.lumberjack.get());
        final ICitizenData citizen = TownHallTestSupport.spawnVillager(helper, colony);
        helper.assertTrue(module.assignCitizen(citizen), "the Town Hall must accept the lumberjack on level 1");
        helper.assertTrue(citizen.getJob() instanceof JobLumberjack, "the villager must have the lumberjack job");

        helper.succeedWhen(() -> {
            helper.assertTrue(citizen.getJob() instanceof JobLumberjack && citizen.getWorkBuilding() == townHall, "the lumberjack must keep his job in the Town Hall");
            helper.assertTrue(citizen.getEntity().isPresent(), "the villager must have an entity");
            final boolean hasAxe = InventoryUtils.isEquipmentInItemHandler(citizen.getEntity().get().getInventoryCitizen(), ModEquipmentTypes.axe.get(), TOOL_LEVEL_WOOD_OR_GOLD,
              townHall.getMaxEquipmentLevel());
            helper.assertTrue(hasAxe, "the lumberjack must have taken the axe from the chest, AI state " + citizen.getJob().getWorkerAI().getState());
            Log.getLogger().info("ZM gametest: lumberjack took the axe from the colony chest, AI state {}", citizen.getJob().getWorkerAI().getState());
        });
    }
}

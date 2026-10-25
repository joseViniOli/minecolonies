package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.workerbuildings.ILumberjackWorkplace;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;

/**
 * ZM: Workplace data of a lumberjack hosted by the Town Hall. A hut has its own settings; the Town Hall has none for the lumberjack, so this module gives the AI fixed defaults
 * (replant, leave the leaves, no restricted area, hoe instead of shears) and keeps the tools of the worker in his inventory when he dumps it.
 * <p>
 * Limits of this first version: no marked work area (the lumberjack searches around the Town Hall like an unrestricted hut) and no nether tree growth boost.
 */
public class TownHallLumberjackModule extends AbstractBuildingModule implements ILumberjackWorkplace, IAltersRequiredItems
{
    /**
     * Default size of dynamic trees, equal to the default of the hut setting.
     */
    private static final int DEFAULT_DYNAMIC_TREES_SIZE = 5;

    @Override
    public boolean shouldReplant()
    {
        return true;
    }

    @Override
    public boolean shouldDefoliate()
    {
        return false;
    }

    @Override
    public boolean shouldRestrict()
    {
        return false;
    }

    @Nullable
    @Override
    public BlockPos getStartRestriction()
    {
        return null;
    }

    @Nullable
    @Override
    public BlockPos getEndRestriction()
    {
        return null;
    }

    @Override
    public boolean shouldUseShears()
    {
        return false;
    }

    @Override
    public int getDynamicTreesSize()
    {
        return DEFAULT_DYNAMIC_TREES_SIZE;
    }

    @Override
    public void addNetherTree(final BlockPos pos)
    {
        // The Town Hall does not speed up the growth of nether trees.
    }

    @Override
    public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        consumer.accept(stack -> ItemStackUtils.hasEquipmentLevel(stack, ModEquipmentTypes.axe.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel()), 1, true);
        consumer.accept(stack -> ItemStackUtils.hasEquipmentLevel(stack, ModEquipmentTypes.shears.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel()), 1, true);
        consumer.accept(stack -> ItemStackUtils.hasEquipmentLevel(stack, ModEquipmentTypes.hoe.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel()), 1, true);

        final ItemListModule saplingList = building.getModule(BuildingModules.ITEMLIST_SAPLING);
        if (saplingList == null)
        {
            return;
        }
        for (final ItemStorage sapling : IColonyManager.getInstance().getCompatibilityManager().getCopyOfSaplings())
        {
            if (!saplingList.isItemInList(sapling))
            {
                consumer.accept(stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(sapling.getItemStack(), stack), Constants.STACKSIZE, true);
            }
        }
    }
}

package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.buildings.workerbuildings.IFarmerWorkplace;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_CELL;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_PREV_POS;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_WORKING_OFFSET;

/**
 * ZM: Workplace data of a farmer hosted by the Town Hall. A farmer hut keeps the cursor of the field work in the building itself; the Town Hall has no such fields, so this module
 * holds them (and saves them under its own key). It asks for no fertilizer, because the Town Hall has no storage yet to deliver it to.
 * <p>
 * Limits of this first version: all farmers hosted by the Town Hall share one cursor and one set of fields. When the last hosted farmer is gone, the fields are given back so a
 * farmer hut can claim them.
 */
public class TownHallFarmerModule extends AbstractBuildingModule implements IFarmerWorkplace, IPersistentModule, ITickingModule, IAltersRequiredItems
{
    /**
     * The offset to work at relative to the scarecrow.
     */
    @Nullable
    private BlockPos workingOffset;

    /**
     * The previous position which has been worked at.
     */
    @Nullable
    private BlockPos prevPos;

    /**
     * The current index within the current field.
     */
    private int cell = -1;

    @Override
    public boolean requestFertilizer()
    {
        return false;
    }

    @Nullable
    @Override
    public BlockPos getWorkingOffset()
    {
        return workingOffset;
    }

    @Override
    public void setWorkingOffset(@Nullable final BlockPos blockPos)
    {
        this.workingOffset = blockPos;
    }

    @Override
    public int getCell()
    {
        return cell;
    }

    @Override
    public int setCell(final int i)
    {
        cell = i;
        return cell;
    }

    @Nullable
    @Override
    public BlockPos getPrevPos()
    {
        return prevPos;
    }

    @Override
    public void setPrevPos(@Nullable final BlockPos position)
    {
        this.prevPos = position;
    }

    /**
     * Give the fields back when no farmer is hosted any more, so they are not blocked for a farmer hut.
     *
     * @param colony the colony that ticks.
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (hasHostedFarmer())
        {
            return;
        }

        final BuildingExtensionsModule fields = building.getModule(BuildingModules.FARMER_FIELDS);
        if (fields == null)
        {
            return;
        }
        for (final IBuildingExtension extension : fields.getOwnedExtensions())
        {
            fields.freeExtension(extension);
        }
        workingOffset = null;
        prevPos = null;
        cell = -1;
    }

    /**
     * Whether a farmer is assigned to the Town Hall.
     *
     * @return true if so.
     */
    public boolean hasHostedFarmer()
    {
        final TownHallAssignmentModule work = building.getModule(BuildingModules.TOWNHALL_FARMER_WORK);
        return work != null && work.hasAssignedCitizen();
    }

    /**
     * The farmer keeps his hoe, his axe and the seeds of his fields when he dumps his inventory into the shared chest of the Town Hall, like the farmer of a hut does.
     *
     * @param consumer consumer that adds the items.
     */
    @Override
    public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        consumer.accept(stack -> ItemStackUtils.hasEquipmentLevel(stack, ModEquipmentTypes.hoe.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel()), 1, true);
        consumer.accept(stack -> ItemStackUtils.hasEquipmentLevel(stack, ModEquipmentTypes.axe.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel()), 1, true);

        final BuildingExtensionsModule fields = building.getModule(BuildingModules.FARMER_FIELDS);
        if (fields == null)
        {
            return;
        }
        for (final IBuildingExtension extension : fields.getOwnedExtensions())
        {
            if (extension instanceof final FarmField farmField && !farmField.getSeed().isEmpty())
            {
                consumer.accept(stack -> ItemStack.isSameItem(farmField.getSeed(), stack), 64, true);
            }
        }
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        cell = compound.contains(TAG_CELL) ? compound.getInt(TAG_CELL) : -1;
        workingOffset = compound.contains(TAG_WORKING_OFFSET) ? BlockPosUtil.read(compound, TAG_WORKING_OFFSET) : null;
        prevPos = compound.contains(TAG_PREV_POS) ? BlockPosUtil.read(compound, TAG_PREV_POS) : null;
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        compound.putInt(TAG_CELL, cell);
        if (workingOffset != null)
        {
            BlockPosUtil.write(compound, TAG_WORKING_OFFSET, workingOffset);
        }
        if (prevPos != null)
        {
            BlockPosUtil.write(compound, TAG_PREV_POS, prevPos);
        }
    }
}

package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.workerbuildings.IBuilderWorkplace;
import com.minecolonies.api.colony.buildings.workerbuildings.IStructureWorkplace;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.utils.BuilderBucket;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_FLUIDS_REMOVE;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_FLUIDS_REMOVE_POSITIONS;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_FLUIDS_REMOVE_Y;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_PROGRESS_POS;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_PROGRESS_STAGE;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_PURGED_MOBS;
import static com.minecolonies.core.colony.jobs.AbstractJobStructure.TAG_WORK_ORDER;

/**
 * ZM: Construction state of a builder hosted by the Town Hall. A builder hut keeps the claimed work order and the progress in the building itself; the Town Hall has no such
 * fields, so this module holds them (under its own NBT key) and forwards the resource list to the {@link BuildingResourcesModule} the Town Hall also hosts.
 * <p>
 * Limits of this first version: the Town Hall builds automatically (no manual mode), and all builders hosted by the Town Hall share one work order and one progress, so with two
 * of them both work on the same order. A builder only moves to a hut when he is idle (nothing is claimed).
 */
public class TownHallBuilderModule extends AbstractBuildingModule implements IBuilderWorkplace, IPersistentModule, IAltersRequiredItems, IBuildingEventsModule
{
    /**
     * Progress amount to mark the building dirty.
     */
    private static final int COUNT_TO_STORE_POS = 50;

    /**
     * Progress position of the builder.
     */
    @Nullable
    private BlockPos progressPos;

    /**
     * Progress stage of the builder.
     */
    @Nullable
    private BuildingProgressStage progressStage;

    /**
     * The progress counter of the builder.
     */
    private int progressCounter = 0;

    /**
     * All the fluids to be removed in fluids_remove.
     */
    private final Map<Integer, List<BlockPos>> fluidsToRemove = new LinkedHashMap<>();

    /**
     * The id of the current work order, 0 if there is none.
     */
    private int workOrderId;

    /**
     * Check if the builder purged mobs already on this day.
     */
    private boolean purgedMobsToday = false;

    /**
     * The construction state of a building.
     *
     * @param building the building the worker works for.
     * @return the building itself when it is a hut that keeps the state, the hosted module for the Town Hall, null if the building has none.
     */
    @Nullable
    public static IStructureWorkplace of(final IBuilding building)
    {
        if (building instanceof final IStructureWorkplace hut)
        {
            return hut;
        }
        return building.getModule(BuildingModules.TOWNHALL_BUILDER_WORKPLACE);
    }

    /**
     * The builder state of a building, see {@link #of(IBuilding)}.
     *
     * @param building the building the worker works for.
     * @return the building itself when it is a builder hut, the hosted module for the Town Hall, null if the building has none.
     */
    @Nullable
    public static IBuilderWorkplace builderOf(final IBuilding building)
    {
        if (building instanceof final IBuilderWorkplace hut)
        {
            return hut;
        }
        return building.getModule(BuildingModules.TOWNHALL_BUILDER_WORKPLACE);
    }

    /**
     * Reset the daily purge of mobs.
     */
    @Override
    public void onWakeUp()
    {
        purgedMobsToday = false;
    }

    private BuildingResourcesModule resources()
    {
        return building.getModule(BuildingModules.BUILDING_RESOURCES);
    }

    @Override
    public Map<String, BuildingBuilderResource> getNeededResources()
    {
        return resources().getNeededResources();
    }

    @Nullable
    @Override
    public BuilderBucket getRequiredResources()
    {
        return resources().getRequiredResources();
    }

    @Override
    public boolean hasResourceInBucket(final ItemStack stack)
    {
        final int hashCode = stack.getComponentsPatch().hashCode();
        final String key = stack.getDescriptionId() + "-" + hashCode;
        return getRequiredResources() != null && getRequiredResources().getResourceMap().containsKey(key);
    }

    @Override
    public void addNeededResource(@Nullable final ItemStack res, final int amount)
    {
        if (res != null)
        {
            final ItemStack copy = res.copy();
            copy.setCount(1);
            resources().addNeededResource(copy, amount);
            markDirty();
        }
    }

    @Override
    public void reduceNeededResource(final ItemStack res, final int amount)
    {
        resources().reduceNeededResource(res, amount);
        markDirty();
    }

    @Override
    public void resetNeededResources()
    {
        resources().resetNeededResources();
        markDirty();
    }

    @Override
    public boolean requiresResourceForBuilding(final ItemStack stack)
    {
        return resources().requiresResourceForBuilding(stack);
    }

    @Override
    public void setProgressPos(@Nullable final BlockPos blockPos, @Nullable final BuildingProgressStage stage)
    {
        this.progressPos = blockPos;
        if (progressCounter > COUNT_TO_STORE_POS || blockPos == null || stage != progressStage)
        {
            markDirty();
            progressCounter = 0;
        }
        else
        {
            progressCounter++;
        }
        this.progressStage = stage;
    }

    @Nullable
    @Override
    public Tuple<BlockPos, BuildingProgressStage> getProgress()
    {
        if (progressPos == null)
        {
            return null;
        }
        return new Tuple<>(progressPos, progressStage);
    }

    @Override
    public int getResourceBatchMultiplier()
    {
        return 1;
    }

    @Override
    public Map<Integer, List<BlockPos>> getFluidsToRemove()
    {
        return fluidsToRemove;
    }

    @Override
    public void checkOrRequestBucket(@Nullable final BuilderBucket requiredResources, final ICitizenData worker)
    {
        resources().checkOrRequestBucket(requiredResources, worker);
    }

    @Override
    public void nextStage()
    {
        resources().nextStage();
    }

    @Override
    public void setTotalStages(final int total)
    {
        resources().setTotalStages(total);
    }

    @Nullable
    @Override
    public BuilderBucket getNextBucket()
    {
        return resources().getNextBucket();
    }

    @Override
    public void onWorkOrderCancellation(final IWorkOrder workOrder)
    {
        if (workOrderId != workOrder.getID())
        {
            return;
        }
        for (final ICitizenData citizen : building.getAllAssignedCitizen())
        {
            if (citizen.getJob() instanceof final JobBuilder job)
            {
                building.cancelAllRequestsOfCitizenOrBuilding(citizen);
                if (job.getWorkerAI() instanceof final AbstractEntityAIStructureWithWorkOrder<?, ?> ai)
                {
                    ai.resetCurrentStructure();
                }
            }
        }

        setWorkOrder(null);
        resetNeededResources();
        setProgressPos(null, null);
        building.cancelAllRequestsOfCitizenOrBuilding(null);
    }

    @Override
    public boolean hasWorkOrder()
    {
        if (workOrderId == 0 || getWorkOrder() == null)
        {
            workOrderId = 0;
            return false;
        }
        return true;
    }

    @Nullable
    @Override
    public IBuilderWorkOrder getWorkOrder()
    {
        final IBuilderWorkOrder workOrder = building.getColony().getWorkManager().getWorkOrder(workOrderId, IBuilderWorkOrder.class);
        if (workOrder == null)
        {
            return null;
        }
        else if (!workOrder.getClaimedBy().equals(building.getID()))
        {
            workOrderId = 0;
            return null;
        }
        return workOrder;
    }

    @Override
    public void setWorkOrder(@Nullable final IWorkOrder order)
    {
        if (order == null)
        {
            workOrderId = 0;
            resetNeededResources();
        }
        else
        {
            workOrderId = order.getID();
        }
        markDirty();
    }

    @Override
    public void complete(final ICitizenData citizen)
    {
        getWorkOrder().onCompleted(building.getColony(), citizen);
        setWorkOrder(null);
    }

    /**
     * The Town Hall always takes orders on its own, there is no manual mode setting for the hosted builder.
     *
     * @return false.
     */
    @Override
    public boolean getManualMode()
    {
        return false;
    }

    @Override
    public boolean hasPurgedMobsToday()
    {
        return purgedMobsToday;
    }

    @Override
    public void setPurgedMobsToday(final boolean purgedMobsToday)
    {
        this.purgedMobsToday = purgedMobsToday;
    }

    /**
     * The builder keeps the resources of the current build and one tool of each kind (wood or gold) when he dumps his inventory.
     *
     * @param consumer consumer that adds the items.
     */
    @Override
    public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        for (final BuildingBuilderResource resource : resources().getNeededResources().values())
        {
            consumer.accept(stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(resource.getItemStack(), stack), resource.getAmount(), true);
        }
        keepTool(consumer, ModEquipmentTypes.pickaxe.get());
        keepTool(consumer, ModEquipmentTypes.shovel.get());
        keepTool(consumer, ModEquipmentTypes.axe.get());
        keepTool(consumer, ModEquipmentTypes.hoe.get());
        keepTool(consumer, ModEquipmentTypes.shears.get());
    }

    private void keepTool(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer, final EquipmentTypeEntry type)
    {
        consumer.accept(stack -> ItemStackUtils.hasEquipmentLevel(stack, type, TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel()), 1, true);
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        progressPos = null;
        progressStage = null;
        if (compound.contains(TAG_PROGRESS_POS))
        {
            progressPos = BlockPosUtil.read(compound, TAG_PROGRESS_POS);
            progressStage = BuildingProgressStage.values()[compound.getInt(TAG_PROGRESS_STAGE)];
        }

        fluidsToRemove.clear();
        if (compound.contains(TAG_FLUIDS_REMOVE))
        {
            final ListTag list = (ListTag) compound.get(TAG_FLUIDS_REMOVE);
            list.forEach(entry -> {
                final int y = ((CompoundTag) entry).getInt(TAG_FLUIDS_REMOVE_Y);
                final ListTag positions = (ListTag) ((CompoundTag) entry).get(TAG_FLUIDS_REMOVE_POSITIONS);
                final List<BlockPos> fluids = new ArrayList<>();
                for (int i = 0; i < positions.size(); i++)
                {
                    fluids.add(BlockPosUtil.readFromListNBT(positions, i));
                }
                fluidsToRemove.put(y, fluids);
            });
        }

        workOrderId = compound.getInt(TAG_WORK_ORDER);
        purgedMobsToday = compound.getBoolean(TAG_PURGED_MOBS);
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        if (progressPos != null && progressStage != null)
        {
            BlockPosUtil.write(compound, TAG_PROGRESS_POS, progressPos);
            compound.putInt(TAG_PROGRESS_STAGE, progressStage.ordinal());
        }

        final ListTag list = new ListTag();
        fluidsToRemove.forEach((y, fluids) -> {
            final CompoundTag entry = new CompoundTag();
            final ListTag positions = new ListTag();
            fluids.forEach(fluid -> BlockPosUtil.writeToListNBT(positions, fluid));
            entry.put(TAG_FLUIDS_REMOVE_POSITIONS, positions);
            entry.putInt(TAG_FLUIDS_REMOVE_Y, y);
            list.add(entry);
        });
        compound.put(TAG_FLUIDS_REMOVE, list);

        if (workOrderId != 0)
        {
            compound.putInt(TAG_WORK_ORDER, workOrderId);
        }
        compound.putBoolean(TAG_PURGED_MOBS, purgedMobsToday);
    }
}

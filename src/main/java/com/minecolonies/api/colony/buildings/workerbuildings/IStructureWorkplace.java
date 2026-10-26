package com.minecolonies.api.colony.buildings.workerbuildings;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.utils.BuilderBucket;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * ZM: The state of a construction job (current work order, progress in the structure, needed resources) that the structure worker AIs read and write. The builder and miner huts
 * implement it with fields of the building; the Town Hall provides it through a hosted module, so the AI no longer depends on the hut class.
 */
public interface IStructureWorkplace
{
    /**
     * Get the needed resources for the current build.
     *
     * @return the resources by key.
     */
    Map<String, BuildingBuilderResource> getNeededResources();

    /**
     * Get the bucket of resources for the current build step.
     *
     * @return the bucket, null if there is none.
     */
    @Nullable
    BuilderBucket getRequiredResources();

    /**
     * Check if the stack is part of the current bucket.
     *
     * @param stack the stack to check.
     * @return true if so.
     */
    boolean hasResourceInBucket(ItemStack stack);

    /**
     * Add a resource to the needed list.
     *
     * @param res    the resource.
     * @param amount the amount.
     */
    void addNeededResource(@Nullable ItemStack res, int amount);

    /**
     * Reduce a resource of the needed list.
     *
     * @param res    the resource.
     * @param amount the amount.
     */
    void reduceNeededResource(ItemStack res, int amount);

    /**
     * Reset the needed resources completely.
     */
    void resetNeededResources();

    /**
     * Check if the current construction requires a certain stack.
     *
     * @param stack the stack to test.
     * @return true if so.
     */
    boolean requiresResourceForBuilding(ItemStack stack);

    /**
     * Set the progress position of the worker.
     *
     * @param blockPos the last position, null to clear.
     * @param stage    the stage to set.
     */
    void setProgressPos(@Nullable BlockPos blockPos, @Nullable BuildingProgressStage stage);

    /**
     * Getter for the progress position.
     *
     * @return the current progress and stage, null if there is none.
     */
    @Nullable
    Tuple<BlockPos, BuildingProgressStage> getProgress();

    /**
     * Batch size to request for resources.
     *
     * @return the multiplier.
     */
    int getResourceBatchMultiplier();

    /**
     * The blocks to be removed in fluids_remove, by height.
     *
     * @return the modifiable map.
     */
    Map<Integer, List<BlockPos>> getFluidsToRemove();

    /**
     * Check or request if the contents of a specific batch are in the inventory of the building.
     *
     * @param requiredResources the bucket to check and request.
     * @param worker            the worker.
     */
    void checkOrRequestBucket(@Nullable BuilderBucket requiredResources, ICitizenData worker);

    /**
     * Go to the next stage.
     */
    void nextStage();

    /**
     * Set the total number of stages.
     *
     * @param total the total.
     */
    void setTotalStages(int total);

    /**
     * Return the next bucket to work on.
     *
     * @return the next bucket, null if none is available.
     */
    @Nullable
    BuilderBucket getNextBucket();

    /**
     * Handle the cancellation of a work order: reset requests and progress.
     *
     * @param workOrder the cancelled work order.
     */
    void onWorkOrderCancellation(IWorkOrder workOrder);

    /**
     * Whether a work order is claimed.
     *
     * @return true if so.
     */
    boolean hasWorkOrder();

    /**
     * The claimed work order, not cached.
     *
     * @return the work order, null if there is none.
     */
    @Nullable
    IBuilderWorkOrder getWorkOrder();

    /**
     * Set the work order.
     *
     * @param order the work order, null to clear.
     */
    void setWorkOrder(@Nullable IWorkOrder order);

    /**
     * Do the final completion when the current work is complete.
     *
     * @param citizen the worker that finished.
     */
    void complete(ICitizenData citizen);
}

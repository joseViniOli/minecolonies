package com.minecolonies.api.colony.buildings.workerbuildings;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * ZM: What the farmer AI needs to know about the place it works at: where it is in the current field and whether it asks for fertilizer. The farmer hut implements it, and the
 * Town Hall provides it through a hosted module, so the AI no longer depends on the hut class. The fields themselves are kept by a field module that both places have.
 */
public interface IFarmerWorkplace
{
    /**
     * Whether the farmer requests compost or bone meal.
     *
     * @return true if so.
     */
    boolean requestFertilizer();

    /**
     * The offset to work at relative to the scarecrow.
     *
     * @return the offset, null if the farmer has not started the field.
     */
    @Nullable
    BlockPos getWorkingOffset();

    /**
     * Set the offset to work at relative to the scarecrow.
     *
     * @param blockPos the offset, null to restart the field.
     */
    void setWorkingOffset(@Nullable BlockPos blockPos);

    /**
     * Get the current index within the current field.
     *
     * @return the index, -1 before the first cell.
     */
    int getCell();

    /**
     * Set the current index within the current field.
     *
     * @param i the index.
     * @return the value that was set.
     */
    int setCell(int i);

    /**
     * Get the previous position which has been worked at.
     *
     * @return the position, null if there is none.
     */
    @Nullable
    BlockPos getPrevPos();

    /**
     * Set the previous position which has been worked at.
     *
     * @param position the position, null to forget it.
     */
    void setPrevPos(@Nullable BlockPos position);
}

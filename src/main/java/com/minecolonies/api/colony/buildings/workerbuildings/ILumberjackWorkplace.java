package com.minecolonies.api.colony.buildings.workerbuildings;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * ZM: What the lumberjack AI needs to know about the place it works at. The lumberjack hut implements it with its settings, and the Town Hall provides it through a hosted module,
 * so the AI no longer depends on the hut class.
 */
public interface ILumberjackWorkplace
{
    /**
     * Whether the lumberjack should replant saplings.
     *
     * @return true if so.
     */
    boolean shouldReplant();

    /**
     * Whether the lumberjack should break all the leaves, not just the ones directly in the way.
     *
     * @return true if so.
     */
    boolean shouldDefoliate();

    /**
     * Whether the lumberjack only works inside a marked area.
     *
     * @return true if so, then {@link #getStartRestriction()} and {@link #getEndRestriction()} are set.
     */
    boolean shouldRestrict();

    /**
     * Start corner of the marked area.
     *
     * @return the position, null if there is no area.
     */
    @Nullable
    BlockPos getStartRestriction();

    /**
     * End corner of the marked area.
     *
     * @return the position, null if there is no area.
     */
    @Nullable
    BlockPos getEndRestriction();

    /**
     * Whether shears are used instead of a hoe for leaves and saplings.
     *
     * @return true if so.
     */
    boolean shouldUseShears();

    /**
     * Maximum size of dynamic trees (only relevant with the Dynamic Trees mod) that are harvested.
     *
     * @return the size.
     */
    int getDynamicTreesSize();

    /**
     * Remember a nether tree the lumberjack planted, so the place can speed up its growth. Places that do not do that may ignore it.
     *
     * @param pos the position of the planted fungus.
     */
    void addNetherTree(BlockPos pos);
}

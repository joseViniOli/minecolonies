package com.minecolonies.api.colony.buildings.workerbuildings;

/**
 * ZM: What the builder AI needs on top of the construction state: whether it takes work orders on its own and the daily purge of mobs at the build site. The builder hut
 * implements it, and the Town Hall provides it through a hosted module.
 */
public interface IBuilderWorkplace extends IStructureWorkplace
{
    /**
     * Whether the builder only builds the orders it is given, instead of taking them automatically.
     *
     * @return true if so.
     */
    boolean getManualMode();

    /**
     * Whether the builder already purged the mobs at the build site today.
     *
     * @return true if so.
     */
    boolean hasPurgedMobsToday();

    /**
     * Set whether the builder purged the mobs at the build site today.
     *
     * @param purgedMobsToday true if so.
     */
    void setPurgedMobsToday(boolean purgedMobsToday);
}

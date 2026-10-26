package com.minecolonies.core.colony.townhall;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.TownHallAssignmentModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * ZM: Who counts as a builder for the work order dispatch: a builder hut, or the Town Hall while it hosts a builder (a builder without a hut). The work order classes ask these
 * questions instead of checking for the hut class, so the Town Hall is accepted too.
 */
public final class HostedBuilders
{
    private HostedBuilders()
    {
        // Static helpers.
    }

    /**
     * Whether the building can be the workplace of a builder: a builder hut, or a building that hosts the builder (the Town Hall). It does not say that a builder is assigned.
     *
     * @param building the building.
     * @return true if so.
     */
    public static boolean isBuilderWorkplace(@Nullable final IBuilding building)
    {
        return building instanceof BuildingBuilder || (building != null && building.hasModule(BuildingModules.TOWNHALL_BUILDER_WORKPLACE));
    }

    /**
     * Whether a builder is assigned to a builder workplace.
     *
     * @param building the building.
     * @return true if so, false for other buildings.
     */
    public static boolean hasBuilder(@Nullable final IBuilding building)
    {
        if (building instanceof BuildingBuilder)
        {
            return !building.getAllAssignedCitizen().isEmpty();
        }
        return getHostedBuilder(building) != null;
    }

    /**
     * The builder hosted by a building, see {@link #isBuilderWorkplace(IBuilding)}.
     *
     * @param building the building.
     * @return the first builder of the Town Hall module, null if the building hosts none.
     */
    @Nullable
    public static ICitizenData getHostedBuilder(@Nullable final IBuilding building)
    {
        if (building == null || !building.hasModule(BuildingModules.TOWNHALL_BUILDER_WORK))
        {
            return null;
        }
        final TownHallAssignmentModule module = building.getModule(BuildingModules.TOWNHALL_BUILDER_WORK);
        return module == null ? null : module.getFirstCitizen();
    }

    /**
     * The first worker of a structure worker building: the worker of the hut, or the builder the Town Hall hosts.
     *
     * @param building the building.
     * @return the worker, null if there is none.
     */
    @Nullable
    public static ICitizenData getWorker(final IBuilding building)
    {
        if (building.hasModule(BuildingModules.TOWNHALL_BUILDER_WORKPLACE))
        {
            return getHostedBuilder(building);
        }
        final WorkerBuildingModule module = building.getModule(WorkerBuildingModule.class);
        return module == null ? null : module.getFirstCitizen();
    }

    /**
     * The level a builder workplace counts with in the level gate of the work orders: the level of the hut, or for the Town Hall the value of
     * {@link TownHallProfessions#hostedBuilderLevel(int)}.
     *
     * @param building the builder workplace.
     * @return the level.
     */
    public static int builderLevel(final IBuilding building)
    {
        if (building instanceof BuildingBuilder || !building.hasModule(BuildingModules.TOWNHALL_BUILDER_WORKPLACE))
        {
            return building.getBuildingLevel();
        }
        return TownHallProfessions.hostedBuilderLevel(building.getBuildingLevel());
    }
}

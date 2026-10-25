package com.minecolonies.core.colony.buildings.moduleviews;

import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.townhall.TownHallProfessions;

import java.util.Optional;

/**
 * ZM: Client side of the assignment module the Town Hall hosts. Villagers hired here work at the Town Hall until a hut of their profession takes them over.
 */
public class TownHallAssignmentModuleView extends WorkerBuildingModuleView
{
    /**
     * A villager hosted by the Town Hall in a job can be hired for the very same job in a hut, so the hire window of the hut lists them.
     *
     * @param jobEntry the job to check for.
     * @return true if it is the job of this module.
     */
    @Override
    public boolean canBeHiredAs(final JobEntry jobEntry)
    {
        return jobEntry == getJobEntry();
    }

    /**
     * Jobs of one profession (knight and ranger) share the slots, so they are full together.
     *
     * @return true if the profession has no free slot left.
     */
    @Override
    public boolean isFull()
    {
        if (!buildingView.allowsAssignment())
        {
            return true;
        }

        final Optional<TownHallProfessions.Profession> profession = TownHallProfessions.byJob(getJobEntry().getKey().getPath());
        if (profession.isEmpty())
        {
            return getAssignedCitizens().size() >= getMaxInhabitants();
        }

        int used = 0;
        for (final TownHallAssignmentModuleView view : buildingView.getModuleViews(TownHallAssignmentModuleView.class))
        {
            if (profession.get().getJobPaths().contains(view.getJobEntry().getKey().getPath()))
            {
                used += view.getAssignedCitizens().size();
            }
        }
        return used >= getMaxInhabitants();
    }
}

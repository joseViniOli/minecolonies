package com.minecolonies.core.colony.buildings.modules;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IBuildingWorkerModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.ICreatesResolversModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.resolvers.BuildingRequestResolver;
import com.minecolonies.core.colony.townhall.TownHallProfessions;
import com.minecolonies.core.util.AttributeModifierUtils;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.minecolonies.api.util.constant.CitizenConstants.SKILL_BONUS_ADD_NAME;

/**
 * ZM: Assignment module hosted by the Town Hall. It lets the player give a villager a profession from the Town Hall, without a hut. There is one module per job entry, each with
 * its own NBT key. The number of slots comes from {@link TownHallProfessions} and the Town Hall level.
 * <p>
 * Differences to {@link WorkerBuildingModule}:
 * <ul>
 * <li>It never hires on its own: the player decides who gets which profession.</li>
 * <li>It is full when the villagers of its profession (all jobs that share the slots, like knight and ranger) reach the limit. Only the lists of the Town Hall are counted, never
 * {@code getAllAssignedCitizen()}, so one occupied profession does not block the others.</li>
 * </ul>
 */
public class TownHallAssignmentModule extends WorkerBuildingModule implements IBuildingEventsModule, ITickingModule, IPersistentModule, IBuildingWorkerModule, ICreatesResolversModule
{
    /**
     * Create the module of one job.
     *
     * @param entry                the job of the module.
     * @param primary              the primary skill.
     * @param secondary            the secondary skill.
     * @param canWorkingDuringRain if the worker can work in the rain.
     */
    public TownHallAssignmentModule(final JobEntry entry, final Skill primary, final Skill secondary, final boolean canWorkingDuringRain)
    {
        super(entry, primary, secondary, canWorkingDuringRain, slotLimit(entry));
    }

    /**
     * Slots of a job at the level of the building it is hosted in.
     *
     * @param entry the job.
     * @return the function mapping the Town Hall to its slots for the job.
     */
    private static Function<IBuilding, Integer> slotLimit(final JobEntry entry)
    {
        final String jobPath = entry.getKey().getPath();
        return building -> TownHallProfessions.slotsForJob(jobPath, building.getBuildingLevel());
    }

    @Override
    public boolean isFull()
    {
        final Optional<TownHallProfessions.Profession> profession = TownHallProfessions.byJob(getJobEntry().getKey().getPath());
        if (profession.isEmpty())
        {
            return assignedCitizen.size() >= getModuleMax();
        }

        int used = 0;
        for (final TownHallAssignmentModule module : building.getModulesByType(TownHallAssignmentModule.class))
        {
            if (profession.get().getJobPaths().contains(module.getJobEntry().getKey().getPath()))
            {
                used += module.assignedCitizen.size();
            }
        }
        return used >= getModuleMax();
    }

    /**
     * The Town Hall never picks a jobless villager by itself: the player assigns the professions.
     *
     * @param colony the colony that ticks.
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        // Intentionally empty, see class comment.
    }

    @Override
    void onRemoval(final ICitizenData citizen)
    {
        super.onRemoval(citizen);
        final Optional<AbstractEntityCitizen> optCitizen = citizen.getEntity();
        optCitizen.ifPresent(entityCitizen -> AttributeModifierUtils.removeModifier(entityCitizen, SKILL_BONUS_ADD_NAME, Attributes.MOVEMENT_SPEED));
    }

    /**
     * Only the first module of the Town Hall creates the building resolver, the others would just register copies of it.
     *
     * @return the resolvers of this module.
     */
    @Override
    public List<IRequestResolver<?>> createResolvers()
    {
        if (building.getModule(TownHallAssignmentModule.class) != this)
        {
            return ImmutableList.of();
        }
        return ImmutableList.of(new BuildingRequestResolver(building.getRequester().getLocation(),
          building.getColony().getRequestManager().getFactoryController().getNewInstance(TypeConstants.ITOKEN)));
    }
}

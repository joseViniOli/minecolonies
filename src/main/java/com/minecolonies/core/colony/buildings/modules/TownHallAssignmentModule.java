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
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.resolvers.BuildingRequestResolver;
import com.minecolonies.core.colony.townhall.TownHallProfessions;
import com.minecolonies.core.util.AttributeModifierUtils;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
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
     * The Town Hall never picks a jobless villager by itself: the player assigns the professions. It only enforces the overflow rule, see {@link #enforceSlots()}.
     *
     * @param colony the colony that ticks.
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        enforceSlots();
    }

    /**
     * Overflow rule at load: when the saved villagers are more than the slots (the Town Hall level fell, the table changed), the ones with the lowest ids keep the profession and the
     * highest ids lose it, with a warning in the log. The result does not depend on the order the villagers were saved in.
     *
     * @param loaded the citizens listed in the saved data of this module.
     */
    @Override
    protected void assignLoadedCitizens(final List<ICitizenData> loaded)
    {
        final List<ICitizenData> sorted = new ArrayList<>(loaded);
        sorted.sort(Comparator.comparingInt(ICitizenData::getId));
        for (final ICitizenData citizen : sorted)
        {
            if (assignedCitizen.contains(citizen))
            {
                continue;
            }

            if (isFull())
            {
                if (citizen.getJob() != null && citizen.getJob().getJobRegistryEntry() == getJobEntry())
                {
                    warnOverflow(citizen);
                    // Only the job is removed, the villager stays alive in the colony.
                    citizen.setJob(null);
                }
                continue;
            }
            assignCitizen(citizen);
        }
    }

    /**
     * Overflow rule while the game runs: when a profession has more villagers than slots, the ones with the highest ids lose it, with a warning in the log. Villagers within the
     * slots are never touched. All modules of one profession (knight and ranger share their slots) are looked at together, by the first of them only.
     */
    private void enforceSlots()
    {
        final Optional<TownHallProfessions.Profession> profession = TownHallProfessions.byJob(getJobEntry().getKey().getPath());
        final List<TownHallAssignmentModule> group = new ArrayList<>();
        for (final TownHallAssignmentModule module : building.getModulesByType(TownHallAssignmentModule.class))
        {
            if (profession.isPresent() ? profession.get().getJobPaths().contains(module.getJobEntry().getKey().getPath()) : module == this)
            {
                group.add(module);
            }
        }
        if (group.isEmpty() || group.get(0) != this)
        {
            return;
        }

        final List<ICitizenData> workers = new ArrayList<>();
        for (final TownHallAssignmentModule module : group)
        {
            workers.addAll(module.assignedCitizen);
        }

        final int slots = getModuleMax();
        if (workers.size() <= slots)
        {
            return;
        }

        workers.sort(Comparator.comparingInt(ICitizenData::getId));
        for (final ICitizenData citizen : new ArrayList<>(workers.subList(slots, workers.size())))
        {
            warnOverflow(citizen);
            for (final TownHallAssignmentModule module : group)
            {
                if (module.assignedCitizen.contains(citizen))
                {
                    // Removes the job and frees the slot, the villager stays alive in the colony.
                    module.removeCitizen(citizen);
                    break;
                }
            }
        }
    }

    private void warnOverflow(final ICitizenData citizen)
    {
        Log.getLogger().warn("Town Hall of colony {} (level {}) has fewer {} slots than villagers: villager {} (id {}) loses the profession and stays in the colony.",
          building.getColony().getID(),
          building.getBuildingLevel(),
          getJobEntry().getKey().getPath(),
          citizen.getName(),
          citizen.getId());
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

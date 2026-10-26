package com.minecolonies.core.colony.buildings.modules;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.IBuildingWorkerModule;
import com.minecolonies.api.colony.buildings.modules.*;
import com.minecolonies.api.colony.buildings.workerbuildings.IStructureWorkplace;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.requestsystem.resolvers.BuildingRequestResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.PrivateWorkerCraftingProductionResolver;
import com.minecolonies.core.colony.requestsystem.resolvers.PrivateWorkerCraftingRequestResolver;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.util.BuildingUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.minecolonies.api.util.constant.NbtTagConstants.*;

/**
 * The worker module for citizen where they are assigned to if they work at it.
 */
public class WorkerBuildingModule extends AbstractAssignedCitizenModule
  implements IAssignsJob, IBuildingEventsModule, ITickingModule, IPersistentModule, IBuildingWorkerModule, ICreatesResolversModule
{
    /**
     * Module specific skills.
     */
    private final Skill primary;
    private final Skill secondary;

    /**
     * Job creator function.
     */
    private final JobEntry jobEntry;

    /**
     * Check if this worker by default can work in the rain.
     */
    private final boolean canWorkingDuringRain;

    /**
     * Max size in terms of assignees.
     */
    private final Function<IBuilding, Integer> sizeLimit;

    /**
     * Research requirement, default to null.
     */
    private ResourceLocation researchRequirement = null;

    public WorkerBuildingModule(
      final JobEntry entry,
      final Skill primary,
      final Skill secondary,
      final boolean canWorkingDuringRain,
      final Function<IBuilding, Integer> sizeLimit)
    {
        this.jobEntry = entry;
        this.primary = primary;
        this.secondary = secondary;
        this.canWorkingDuringRain = canWorkingDuringRain;
        this.sizeLimit = sizeLimit;
    }

    public WorkerBuildingModule(
        final JobEntry entry,
        final Skill primary,
        final Skill secondary,
        final boolean canWorkingDuringRain,
        final Function<IBuilding, Integer> sizeLimit,
        final ResourceLocation researchRequirement)
    {
        this.jobEntry = entry;
        this.primary = primary;
        this.secondary = secondary;
        this.canWorkingDuringRain = canWorkingDuringRain;
        this.sizeLimit = sizeLimit;
        this.researchRequirement = researchRequirement;
    }


    @Override
    public boolean assignCitizen(final ICitizenData citizen)
    {
        if (assignedCitizen.contains(citizen) || isFull() || citizen == null)
        {
            return false;
        }

        IJob job = citizen.getJob();
        if (job == null)
        {
            job = createJob(citizen);
        }

        if (!job.assignTo(this))
        {
            return false;
        }

        return super.assignCitizen(citizen);
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        if (compound.contains(TAG_WORKER))
        {
            final ListTag workersTagList = compound.getList(TAG_WORKER, Tag.TAG_COMPOUND);
            for (int i = 0; i < workersTagList.size(); ++i)
            {
                final ICitizenData data = building.getColony().getCitizenManager().getCivilian(workersTagList.getCompound(i).getInt(TAG_WORKER_ID));
                if (data != null && data.getJob() != null && data.getJob().getJobRegistryEntry() == jobEntry)
                {
                    assignCitizen(data);
                }
            }
        }
        else if (compound.contains(getModuleSerializationIdentifier()))
        {
            final CompoundTag jobCompound = compound.getCompound(jobEntry.getKey().toString());
            final int[] residentIds = jobCompound.getIntArray(TAG_WORKING_RESIDENTS);
            // ZM: collected and assigned through a hook, so the Town Hall can apply its overflow rule (see TownHallAssignmentModule)
            final List<ICitizenData> loaded = new ArrayList<>();
            for (final int citizenId : residentIds)
            {
                final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(citizenId);
                if (citizen != null)
                {
                    loaded.add(citizen);
                }
            }
            assignLoadedCitizens(loaded);
        }
        else
        {
            final int[] residentIds = compound.getIntArray(TAG_WORKING_RESIDENTS);
            // ZM: collected and assigned through a hook, so the Town Hall can apply its overflow rule (see TownHallAssignmentModule)
            final List<ICitizenData> loaded = new ArrayList<>();
            for (final int citizenId : residentIds)
            {
                final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(citizenId);
                if (citizen != null)
                {
                    loaded.add(citizen);
                }
            }
            assignLoadedCitizens(loaded);
        }
    }

    /**
     * ZM: Assign the citizens read from the NBT, in the stored order. A module may override it to decide who stays when there are more citizens than slots.
     *
     * @param loaded the citizens listed in the saved data of this module.
     */
    protected void assignLoadedCitizens(final List<ICitizenData> loaded)
    {
        for (final ICitizenData citizen : loaded)
        {
            assignCitizen(citizen);
        }
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        // ZM: a worker hosted by the Town Hall moves into a finished hut before anybody else is hired
        migrateHostedWorker();

        // If we have no active worker, grab one from the Colony
        if (!isFull() && BuildingUtils.canAutoHire(building, getHiringMode(), getJobEntry()) && (researchRequirement == null || colony.getResearchManager().getResearchEffects().getEffectStrength(researchRequirement) > 0))
        {
            final ICitizenData joblessCitizen = colony.getCitizenManager().getJoblessCitizen();
            if (joblessCitizen != null)
            {
                assignCitizen(joblessCitizen);
            }
        }
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        super.serializeNBT(provider, compound);
        if (!assignedCitizen.isEmpty())
        {
            final int[] residentIds = new int[assignedCitizen.size()];
            for (int i = 0; i < assignedCitizen.size(); ++i)
            {
                residentIds[i] = assignedCitizen.get(i).getId();
            }
            compound.putIntArray(TAG_WORKING_RESIDENTS, residentIds);
        }
    }

    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.serializeToView(buf);
        buf.writeById(IMinecoloniesAPI.getInstance().getJobRegistry()::getIdOrThrow, jobEntry);
        buf.writeInt(getPrimarySkill().ordinal());
        buf.writeInt(getSecondarySkill().ordinal());
        buf.writeBoolean(researchRequirement != null);
        if (researchRequirement != null)
        {
            buf.writeResourceLocation(researchRequirement);
        }
    }

    @Override
    void onAssignment(final ICitizenData citizen)
    {
        for (final AbstractCraftingBuildingModule module : building.getModulesByType(AbstractCraftingBuildingModule.class))
        {
            module.updateWorkerAvailableForRecipes();
        }
        citizen.getJob().onLevelUp();
    }

    @Override
    void onRemoval(final ICitizenData citizen)
    {
        if (citizen.getJob() != null)
        {
            citizen.getJob().onRemoval();
        }

        building.cancelAllRequestsOfCitizenOrBuilding(citizen);
        citizen.setVisibleStatus(null);
    }

    @Override
    public int getModuleMax()
    {
        return sizeLimit.apply(this.building);
    }

    @Override
    public void onUpgradeComplete(final int newLevel)
    {
        for (final Optional<AbstractEntityCitizen> entityCitizen : Objects.requireNonNull(getAssignedEntities()))
        {
            if (entityCitizen.isPresent() && entityCitizen.get().getCitizenJobHandler().getColonyJob() == null)
            {
                entityCitizen.get().getCitizenJobHandler().setModelDependingOnJob(null);
            }
        }
        building.getColony().getCitizenManager().calculateMaxCitizens();

        // ZM: the hut is finished, a worker hosted by the Town Hall moves in
        migrateHostedWorker();
    }

    /**
     * ZM: Move a worker of this profession that is hosted by the Town Hall into this hut, when the hut is finished and has no worker at all. A hut that already has an occupant is
     * left alone, the player moves workers by hand then. A hut set to manual or locked hiring is left alone as well. The hosted worker with the lowest id goes first. A hosted builder
     * only moves while idle, so it does not drop a work order it has claimed. The Town Hall slot is freed by {@link com.minecolonies.core.colony.jobs.AbstractJob#assignTo}.
     */
    private void migrateHostedWorker()
    {
        if (building instanceof BuildingTownHall || !building.canAssignCitizens() || isFull() || hasAssignedCitizen()
              || getHiringMode() == HiringMode.MANUAL || getHiringMode() == HiringMode.LOCKED
              || (researchRequirement != null && building.getColony().getResearchManager().getResearchEffects().getEffectStrength(researchRequirement) <= 0))
        {
            return;
        }

        final IBuilding townHall = building.getColony().getServerBuildingManager().getTownHall();
        if (townHall == null)
        {
            return;
        }

        ICitizenData candidate = null;
        for (final TownHallAssignmentModule hosted : townHall.getModulesByType(TownHallAssignmentModule.class))
        {
            if (!hosted.getJobEntry().equals(jobEntry))
            {
                continue;
            }

            for (final ICitizenData worker : hosted.getAssignedCitizen())
            {
                if (canMigrate(worker) && (candidate == null || worker.getId() < candidate.getId()))
                {
                    candidate = worker;
                }
            }
        }

        if (candidate != null && assignCitizen(candidate))
        {
            Log.getLogger().info("Colony {}: villager {} (id {}) moved from the Town Hall to the finished {} at {}.",
              building.getColony().getID(), candidate.getName(), candidate.getId(), jobEntry.getKey().getPath(), building.getPosition());
        }
    }

    /**
     * ZM: Whether a hosted worker may move now.
     *
     * @param worker the hosted worker.
     * @return false for a builder that is not idle.
     */
    private boolean canMigrate(final ICitizenData worker)
    {
        final IJob<?> job = worker.getJob();
        if (job == null)
        {
            return false;
        }
        if (job.getJobRegistryEntry() == ModJobs.builder.get())
        {
            // The work order is kept by the Town Hall while the builder is hosted, so a claimed order also keeps him there.
            final IStructureWorkplace state = worker.getWorkBuilding() == null ? null : TownHallBuilderModule.of(worker.getWorkBuilding());
            return (job.getWorkerAI() == null || job.isIdling()) && (state == null || !state.hasWorkOrder());
        }
        return true;
    }

    /**
     * Get the Job DisplayName
     */
    public String getJobDisplayName()
    {
        return Component.translatableEscape(jobEntry.getTranslationKey()).getString();
    }

    @NotNull
    @Override
    public IJob<?> createJob(final ICitizenData citizen)
    {
        return jobEntry.produceJob(citizen);
    }

    @Override
    public boolean canWorkDuringTheRain()
    {
        return building.getBuildingLevel() >= building.getMaxBuildingLevel() || canWorkingDuringRain;
    }

    @NotNull
    @Override
    public Skill getPrimarySkill()
    {
        return primary;
    }

    @NotNull
    @Override
    public Skill getSecondarySkill()
    {
        return secondary;
    }

    @Override
    public List<IRequestResolver<?>> createResolvers()
    {
        final ImmutableList.Builder<IRequestResolver<?>> builder = ImmutableList.builder();
        builder.add(new BuildingRequestResolver(building.getRequester().getLocation(), building.getColony().getRequestManager()
            .getFactoryController().getNewInstance(TypeConstants.ITOKEN)),
          new PrivateWorkerCraftingRequestResolver(building.getRequester().getLocation(), building.getColony().getRequestManager()
            .getFactoryController().getNewInstance(TypeConstants.ITOKEN), jobEntry),
          new PrivateWorkerCraftingProductionResolver(building.getRequester().getLocation(), building.getColony().getRequestManager()
            .getFactoryController().getNewInstance(TypeConstants.ITOKEN), jobEntry));
        return builder.build();
    }

    @Override
    public JobEntry getJobEntry()
    {
        return jobEntry;
    }

    @Override
    @Deprecated
    protected String getModuleSerializationIdentifier()
    {
        return jobEntry.getKey().toString();
    }
}

package com.minecolonies.core.colony.townhall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * ZM: Pure (no Minecraft types) table of the professions the Town Hall can assign without a hut: which Town Hall level unlocks each one, how many villagers each may have and
 * whether the matching worker AI already accepts the Town Hall as its workplace.
 * <p>
 * Slot rules (decided by the project Director):
 * <ul>
 * <li>Common professions: {@code 1 + level / 2}.</li>
 * <li>Builder: 1 slot on levels 1-2, 2 slots from level 3.</li>
 * <li>Guard (knight and ranger share one cap): {@code 1 + level}, unlocked on level 1.</li>
 * <li>Level 0 (Town Hall placed but not built) never has slots.</li>
 * </ul>
 * A profession with {@link Profession#isImplemented()} false always has 0 slots, so the player can never assign a worker whose AI would drop the job again.
 */
public final class TownHallProfessions
{
    /**
     * Highest Town Hall level.
     */
    public static final int MAX_LEVEL = 5;

    /**
     * Switch for the lumberjack, the first profession whose AI runs with the Town Hall as workplace (see EntityAIWorkLumberjack). Set to false to hide it again (0 slots) without
     * touching anything else.
     */
    public static final boolean HOSTED_LUMBERJACK_AI_READY = true;

    /**
     * Switch for the farmer: true when the farmer AI keeps its job with the Town Hall as workplace (see EntityAIWorkFarmer and the TownHallHostedJobGameTest). Set to false to hide
     * the profession again (0 slots, and the Town Hall stops claiming fields) without touching anything else.
     */
    public static final boolean HOSTED_FARMER_AI_READY = true;

    /**
     * Switch for the builder: true when the builder AI keeps its job with the Town Hall as workplace AND the work order dispatch accepts the Town Hall as a builder (see
     * EntityAIStructureBuilder, TownHallBuilderModule and the work order classes). Set to false to hide the profession again (0 slots) without touching anything else.
     */
    public static final boolean HOSTED_BUILDER_AI_READY = true;

    /**
     * Fields (scarecrows) a hosted farmer can work on, per Town Hall level: {@code level + 1}. A farmer hut allows as many fields as its own level; the Town Hall gives one more
     * because its farmers have no hut to upgrade yet. All farmers hosted by the Town Hall share these fields.
     */
    private static final int HOSTED_FARMER_EXTRA_FIELDS = 1;

    /**
     * Highest Town Hall level at which a builder without a hut still counts as level 1 in the work order level gate. From the next level on, the level of the Town Hall counts as
     * the level of the builder, like the level of a builder hut.
     */
    private static final int HOSTED_BUILDER_LOW_GATE_MAX_LEVEL = 2;

    /**
     * Base slots of every profession.
     */
    private static final int BASE_SLOTS = 1;

    /**
     * Common professions get one extra slot every this many Town Hall levels.
     */
    private static final int LEVELS_PER_EXTRA_SLOT = 2;

    /**
     * First Town Hall level on which the builder gets its second slot.
     */
    private static final int BUILDER_UPGRADE_LEVEL = 3;

    /**
     * Builder slots from {@link #BUILDER_UPGRADE_LEVEL} on.
     */
    private static final int BUILDER_UPGRADED_SLOTS = 2;

    /**
     * How the number of slots of a profession grows with the Town Hall level.
     */
    public enum SlotRule
    {
        /**
         * {@code 1 + level / 2}.
         */
        COMMON,
        /**
         * 1 on levels 1-2, 2 from level 3.
         */
        BUILDER,
        /**
         * {@code 1 + level}.
         */
        GUARD;

        /**
         * Slots of this rule at the given (already clamped, at least 1) level.
         *
         * @param level the Town Hall level.
         * @return the number of slots.
         */
        int slots(final int level)
        {
            switch (this)
            {
                case BUILDER:
                    return level >= BUILDER_UPGRADE_LEVEL ? BUILDER_UPGRADED_SLOTS : BASE_SLOTS;
                case GUARD:
                    return BASE_SLOTS + level;
                default:
                    return BASE_SLOTS + level / LEVELS_PER_EXTRA_SLOT;
            }
        }
    }

    /**
     * One assignable profession. A profession can be backed by several job entries that share the same slots (the guard is a knight or a ranger).
     */
    public static final class Profession
    {
        private final String       id;
        private final int          unlockLevel;
        private final SlotRule     rule;
        private final boolean      implemented;
        private final List<String> jobPaths;

        private Profession(final String id, final int unlockLevel, final SlotRule rule, final boolean implemented, final List<String> jobPaths)
        {
            this.id = id;
            this.unlockLevel = unlockLevel;
            this.rule = rule;
            this.implemented = implemented;
            this.jobPaths = Collections.unmodifiableList(new ArrayList<>(jobPaths));
        }

        /**
         * Profession id, equal to the ids used by the Zombie Moon side (for example "quarry_miner").
         *
         * @return the id.
         */
        public String getId()
        {
            return id;
        }

        /**
         * First Town Hall level that unlocks the profession.
         *
         * @return the level.
         */
        public int getUnlockLevel()
        {
            return unlockLevel;
        }

        /**
         * Whether the worker AI of this profession accepts the Town Hall as workplace.
         *
         * @return true if so.
         */
        public boolean isImplemented()
        {
            return implemented;
        }

        /**
         * Paths (in the minecolonies namespace) of the job entries that belong to this profession, for example "knight" and "ranger".
         *
         * @return the immutable list.
         */
        public List<String> getJobPaths()
        {
            return jobPaths;
        }

        /**
         * Slots of the profession at a Town Hall level; 0 while not implemented, not built or locked.
         *
         * @param thLevel the Town Hall level.
         * @return the number of villagers that may hold the profession (shared by all its jobs).
         */
        public int slots(final int thLevel)
        {
            if (!implemented || thLevel < unlockLevel)
            {
                return 0;
            }
            return rule.slots(Math.min(thLevel, MAX_LEVEL));
        }

        /**
         * Slots of the profession by its rule alone, as if its worker AI were ready. Used to compare the table with the shared data file.
         *
         * @param thLevel the Town Hall level.
         * @return the number of slots, 0 when the level is below the unlock level.
         */
        int slotsIgnoringImplementation(final int thLevel)
        {
            if (thLevel < unlockLevel)
            {
                return 0;
            }
            return rule.slots(Math.min(thLevel, MAX_LEVEL));
        }
    }

    /**
     * The Tier 1 professions, in the order they are registered.
     */
    private static final List<Profession> ALL = Collections.unmodifiableList(List.of(
      new Profession("lumberjack", 1, SlotRule.COMMON, HOSTED_LUMBERJACK_AI_READY, List.of("lumberjack")),
      new Profession("builder", 1, SlotRule.BUILDER, HOSTED_BUILDER_AI_READY, List.of("builder")),
      new Profession("farmer", 1, SlotRule.COMMON, HOSTED_FARMER_AI_READY, List.of("farmer")),
      new Profession("quarry_miner", 1, SlotRule.COMMON, false, List.of("quarrier")),
      new Profession("guard", 1, SlotRule.GUARD, false, List.of("knight", "ranger"))));

    private TownHallProfessions()
    {
        // Static table.
    }

    /**
     * All Tier 1 professions the table lists, implemented or not.
     *
     * @return the immutable list.
     */
    public static List<Profession> all()
    {
        return ALL;
    }

    /**
     * Find a profession by its id.
     *
     * @param id the profession id.
     * @return the profession, if listed.
     */
    public static Optional<Profession> byId(final String id)
    {
        return ALL.stream().filter(p -> p.id.equals(id)).findFirst();
    }

    /**
     * Find the profession a job entry belongs to.
     *
     * @param jobPath the path of the job entry in the minecolonies namespace (for example "lumberjack").
     * @return the profession, if listed.
     */
    public static Optional<Profession> byJob(final String jobPath)
    {
        return ALL.stream().filter(p -> p.jobPaths.contains(jobPath)).findFirst();
    }

    /**
     * Slots of the profession that the job belongs to, at a Town Hall level.
     *
     * @param jobPath the path of the job entry in the minecolonies namespace.
     * @param thLevel the Town Hall level.
     * @return the slots, 0 for jobs that are not listed.
     */
    public static int slotsForJob(final String jobPath, final int thLevel)
    {
        return byJob(jobPath).map(p -> p.slots(thLevel)).orElse(0);
    }

    /**
     * The level a builder hosted by a Town Hall counts with in the work order level gate (decided by the project Director): level 1 while the Town Hall is level 1 or 2, so he can
     * build level 1 huts and the Town Hall upgrade to level 2; from Town Hall level 3 the level of the Town Hall counts, as for a builder hut.
     *
     * @param thLevel the Town Hall level.
     * @return the level of the builder, 0 while the Town Hall is not built.
     */
    public static int hostedBuilderLevel(final int thLevel)
    {
        if (thLevel < 1)
        {
            return 0;
        }
        return thLevel <= HOSTED_BUILDER_LOW_GATE_MAX_LEVEL ? 1 : Math.min(thLevel, MAX_LEVEL);
    }

    /**
     * Maximum number of fields the farmers hosted by a Town Hall can work on.
     *
     * @param thLevel the Town Hall level.
     * @return the field limit, 0 while the farmer is not implemented or the Town Hall is not built.
     */
    public static int hostedFarmerFieldLimit(final int thLevel)
    {
        if (!HOSTED_FARMER_AI_READY || thLevel < 1)
        {
            return 0;
        }
        return Math.min(thLevel, MAX_LEVEL) + HOSTED_FARMER_EXTRA_FIELDS;
    }

    /**
     * Self check of the slot rules. The project has no JUnit on its test classpath, so this stays in the class and can be run on the compiled classes:
     * {@code java -cp build/classes/java/main com.minecolonies.core.colony.townhall.TownHallProfessions}. It throws when a rule is broken.
     *
     * @param args unused.
     */
    public static void main(final String[] args)
    {
        final List<String> problems = selfCheck();
        if (!problems.isEmpty())
        {
            throw new IllegalStateException(String.join("; ", problems));
        }
    }

    /**
     * Check the rules of the table against the values decided by the Director.
     *
     * @return the broken rules, empty if everything is right.
     */
    public static List<String> selfCheck()
    {
        final List<String> problems = new ArrayList<>();
        for (final Profession profession : ALL)
        {
            check(problems, profession.slots(0) == 0, profession.id + " has slots on level 0");
            check(problems, profession.unlockLevel == 1, profession.id + " is not unlocked on level 1");
            check(problems, profession.implemented || profession.slots(MAX_LEVEL) == 0, profession.id + " has slots while not implemented");
            check(problems, profession.slots(MAX_LEVEL + 3) == profession.slots(MAX_LEVEL), profession.id + " does not clamp the level");
        }

        final int[] common = {1, 2, 2, 3, 3};
        final int[] builder = {1, 1, 2, 2, 2};
        final int[] guard = {2, 3, 4, 5, 6};
        for (int level = 1; level <= MAX_LEVEL; level++)
        {
            check(problems, SlotRule.COMMON.slots(level) == common[level - 1], "common slots on level " + level);
            check(problems, SlotRule.BUILDER.slots(level) == builder[level - 1], "builder slots on level " + level);
            check(problems, SlotRule.GUARD.slots(level) == guard[level - 1], "guard slots on level " + level);
        }

        check(problems, ALL.size() == 5, "Tier 1 must list five professions");
        check(problems, byJob("knight").isPresent() && byJob("ranger").isPresent() && byJob("knight").equals(byJob("ranger")), "knight and ranger must share the guard profession");
        check(problems, byJob("quarrier").map(p -> p.id.equals("quarry_miner")).orElse(false), "quarrier must be the quarry_miner profession");
        check(problems, slotsForJob("baker", MAX_LEVEL) == 0, "unlisted jobs must have no slots");
        check(problems, available(0).isEmpty(), "level 0 must offer no profession");
        final int[] hostedBuilder = {1, 1, 3, 4, 5};
        for (int level = 1; level <= MAX_LEVEL; level++)
        {
            check(problems, hostedBuilderLevel(level) == hostedBuilder[level - 1], "hosted builder level on Town Hall level " + level);
        }
        check(problems, hostedBuilderLevel(0) == 0, "an unbuilt Town Hall has no hosted builder level");
        check(problems, hostedFarmerFieldLimit(0) == 0, "an unbuilt Town Hall must not claim fields");
        check(problems, !HOSTED_FARMER_AI_READY || (hostedFarmerFieldLimit(1) == 2 && hostedFarmerFieldLimit(MAX_LEVEL + 3) == MAX_LEVEL + 1), "hosted farmer fields are level + 1");
        return problems;
    }

    private static void check(final List<String> problems, final boolean condition, final String message)
    {
        if (!condition)
        {
            problems.add(message);
        }
    }

    /**
     * Profession ids that have slots at a Town Hall level.
     *
     * @param thLevel the Town Hall level.
     * @return the ids, in table order.
     */
    public static List<String> available(final int thLevel)
    {
        final List<String> result = new ArrayList<>();
        for (final Profession profession : ALL)
        {
            if (profession.slots(thLevel) > 0)
            {
                result.add(profession.id);
            }
        }
        return result;
    }
}

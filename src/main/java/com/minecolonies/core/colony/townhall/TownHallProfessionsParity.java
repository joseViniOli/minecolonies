package com.minecolonies.core.colony.townhall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ZM: Parity check between {@link TownHallProfessions} and the shared profession table of the Zombie Moon data (data only, copied to src/test/resources/zm as a fixture). The
 * project has no JUnit, so this is a plain main: {@code java -cp build/classes/java/main;<gson jar> com.minecolonies.core.colony.townhall.TownHallProfessionsParity [json path]}.
 * It exits with an error when the unlock level, the slots of any Town Hall level 0..5 or the job mapping of a profession differ.
 * <p>
 * The professions of the JSON that the Town Hall table does not list yet (cook, fisherman...) are only reported, they are another round of work. The {@code implemented} switch of
 * the Java table is ignored here, because it only hides a profession until its worker AI is ready, it is not part of the shared rules.
 */
public final class TownHallProfessionsParity
{
    /**
     * Default location of the fixture, relative to the project root.
     */
    private static final String DEFAULT_FIXTURE = "src/test/resources/zm/profession_unlocks.json";

    /**
     * Namespace prefix of the job ids of the JSON.
     */
    private static final String JOB_PREFIX = "minecolonies:";

    private TownHallProfessionsParity()
    {
        // Verification entry point only.
    }

    /**
     * Compare the Java table with the JSON file.
     *
     * @param args optional path of the JSON file.
     * @throws IOException if the file cannot be read.
     */
    public static void main(final String[] args) throws IOException
    {
        final Path path = Path.of(args.length > 0 ? args[0] : DEFAULT_FIXTURE);
        final List<String> report = new ArrayList<>();
        final List<String> problems;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            problems = compare(JsonParser.parseReader(reader).getAsJsonObject(), report);
        }

        report.forEach(line -> System.out.println("INFO  " + line));
        problems.forEach(line -> System.out.println("FAIL  " + line));
        System.out.println(problems.isEmpty() ? "PARITY OK" : "PARITY BROKEN: " + problems.size() + " difference(s)");
        if (!problems.isEmpty())
        {
            throw new IllegalStateException(String.join("; ", problems));
        }
    }

    /**
     * Compare the table with the parsed JSON.
     *
     * @param root   the parsed JSON file.
     * @param report receives one line for every profession that was compared or skipped.
     * @return the differences, empty if the table matches.
     */
    public static List<String> compare(final JsonObject root, final List<String> report)
    {
        final List<String> problems = new ArrayList<>();
        if (root.get("maxTownHallLevel").getAsInt() != TownHallProfessions.MAX_LEVEL)
        {
            problems.add("maxTownHallLevel: json " + root.get("maxTownHallLevel").getAsInt() + " java " + TownHallProfessions.MAX_LEVEL);
        }

        final Set<String> seen = new HashSet<>();
        for (final JsonElement element : root.getAsJsonArray("professions"))
        {
            final JsonObject json = element.getAsJsonObject();
            final String id = json.get("id").getAsString();
            final Optional<TownHallProfessions.Profession> optional = TownHallProfessions.byId(id);
            if (optional.isEmpty())
            {
                report.add(id + ": not in the Town Hall table (unlock level " + json.get("unlockLevel").getAsInt() + "), skipped");
                continue;
            }

            final TownHallProfessions.Profession profession = optional.get();
            seen.add(id);
            if (profession.getUnlockLevel() != json.get("unlockLevel").getAsInt())
            {
                problems.add(id + " unlock level: json " + json.get("unlockLevel").getAsInt() + " java " + profession.getUnlockLevel());
            }

            final JsonArray slots = json.getAsJsonArray("slotsByTownHallLevel");
            if (slots.size() != TownHallProfessions.MAX_LEVEL + 1)
            {
                problems.add(id + " slotsByTownHallLevel has " + slots.size() + " entries, expected " + (TownHallProfessions.MAX_LEVEL + 1));
            }
            final StringBuilder compared = new StringBuilder();
            for (int level = 0; level < slots.size(); level++)
            {
                final int expected = slots.get(level).getAsInt();
                final int actual = profession.slotsIgnoringImplementation(level);
                compared.append(actual).append(level < slots.size() - 1 ? "," : "");
                if (expected != actual)
                {
                    problems.add(id + " slots on Town Hall level " + level + ": json " + expected + " java " + actual);
                }
            }

            final Set<String> jsonJobs = new HashSet<>();
            for (final JsonElement job : json.getAsJsonArray("minecoloniesJobs"))
            {
                final String jobId = job.getAsString();
                jsonJobs.add(jobId.startsWith(JOB_PREFIX) ? jobId.substring(JOB_PREFIX.length()) : jobId);
            }
            if (!jsonJobs.equals(new HashSet<>(profession.getJobPaths())))
            {
                problems.add(id + " jobs: json " + jsonJobs + " java " + profession.getJobPaths());
            }

            report.add(id + ": unlock " + profession.getUnlockLevel() + ", slots by level [" + compared + "], jobs " + profession.getJobPaths() + " compared");
        }

        for (final TownHallProfessions.Profession profession : TownHallProfessions.all())
        {
            if (!seen.contains(profession.getId()))
            {
                problems.add(profession.getId() + " is in the Java table but not in the JSON");
            }
        }
        return problems;
    }
}

package com.newbulaco.showdown.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.newbulaco.showdown.CobblemonShowdown;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FormatManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FormatManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Format> formats = new ConcurrentHashMap<>();
    private final Path formatsDir;
    private WatchService watchService;
    private Thread watchThread;

    public FormatManager() {
        this.formatsDir = FMLPaths.CONFIGDIR.get()
                .resolve("cobblemon_showdown")
                .resolve("formats");

        try {
            Files.createDirectories(formatsDir);
            LOGGER.info("Formats directory: {}", formatsDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to create formats directory", e);
        }
    }

    public void initialize() {
        loadAllFormats();

        if (formats.isEmpty()) {
            LOGGER.info("No formats found, creating defaults...");
            createDefaultFormats();
        }

        startFileWatcher();
        LOGGER.info("Loaded {} format(s)", formats.size());
    }

    private void createDefaultFormats() {
        List<String> bannedPokemon = Arrays.asList(
                "mewtwo", "mew", "lugia", "ho_oh", "ho-oh", "celebi", "kyogre", "groudon",
                "rayquaza", "jirachi", "deoxys", "dialga", "palkia", "giratina",
                "phione", "manaphy", "darkrai", "shaymin", "arceus", "victini",
                "cobalion", "terrakion", "virizion", "tornadus", "thundurus",
                "reshiram", "zekrom", "landorus", "kyurem", "keldeo", "meloetta",
                "genesect", "xerneas", "yveltal", "zygarde", "diancie", "hoopa",
                "volcanion", "tapu_koko", "tapu_lele", "tapu_bulu", "tapu_fini",
                "cosmog", "cosmoem", "solgaleo", "lunala", "nihilego", "buzzwole",
                "pheromosa", "xurkitree", "celesteela", "kartana", "guzzlord",
                "necrozma", "magearna", "marshadow", "poipole", "naganadel",
                "stakataka", "blacephalon", "zeraora", "meltan", "melmetal",
                "zacian", "zamazenta", "eternatus", "kubfu", "urshifu", "zarude",
                "regieleki", "regidrago", "glastrier", "spectrier", "calyrex",
                "enamorus", "koraidon", "miraidon", "walking_wake", "iron_leaves",
                "annihilape", "espathra", "flutter_mane", "iron_bundle",
                "palafin", "ursaluna_bloodmoon", "darmanitan_galar", "chi-yu",
                "chien-pao", "ting-lu", "wo-chien"
        );

        List<String> bannedMoves = Arrays.asList(
                "baton_pass",
                "last_respects", "shed_tail",
                "sleep_powder", "spore", "hypnosis", "sing", "grass_whistle",
                "lovely_kiss", "dark_void", "yawn",
                "double_team", "minimize",
                "fissure", "guillotine", "horn_drill", "sheer_cold"
        );

        List<String> bannedAbilities = Arrays.asList(
                "arena_trap", "moody", "sand_veil", "shadow_tag", "snow_cloak"
        );

        List<String> bannedItems = Arrays.asList(
                "bright_powder", "kings_rock", "razor_fang", "lax_incense"
        );

        Format format4v4 = new Format("4v4 Smogon Singles");
        format4v4.setPartySize(4);
        format4v4.setDescription("Standard Smogon OU-style singles format with 4 Pokemon (Best of 1). Level scaled to 50.");
        format4v4.setBestOf(1);
        format4v4.setSetLevel(50);
        format4v4.setBattleTimer(true);
        format4v4.setTeamPreview(true);
        format4v4.setSpeciesClause(true);

        Format.FormatBans bans4v4 = new Format.FormatBans();
        bans4v4.setPokemon(new ArrayList<>(bannedPokemon));
        bans4v4.setMoves(new ArrayList<>(bannedMoves));
        bans4v4.setAbilities(new ArrayList<>(bannedAbilities));
        bans4v4.setItems(new ArrayList<>(bannedItems));
        format4v4.setBans(bans4v4);

        saveFormat("4v4_smogon_singles", format4v4);
        LOGGER.info("Created default format: 4v4 Smogon Singles");

        Format format4v4BO3 = new Format("4v4 Smogon Singles BO3");
        format4v4BO3.setPartySize(4);
        format4v4BO3.setDescription("Standard Smogon OU-style singles format with 4 Pokemon (Best of 3). Level scaled to 50.");
        format4v4BO3.setBestOf(3);
        format4v4BO3.setSetLevel(50);
        format4v4BO3.setBattleTimer(true);
        format4v4BO3.setTeamPreview(true);
        format4v4BO3.setSpeciesClause(true);

        Format.FormatBans bans4v4BO3 = new Format.FormatBans();
        bans4v4BO3.setPokemon(new ArrayList<>(bannedPokemon));
        bans4v4BO3.setMoves(new ArrayList<>(bannedMoves));
        bans4v4BO3.setAbilities(new ArrayList<>(bannedAbilities));
        bans4v4BO3.setItems(new ArrayList<>(bannedItems));
        format4v4BO3.setBans(bans4v4BO3);

        saveFormat("4v4_smogon_singles_bo3", format4v4BO3);
        LOGGER.info("Created default format: 4v4 Smogon Singles BO3");

        Format format6v6 = new Format("6v6 Smogon Singles");
        format6v6.setPartySize(6);
        format6v6.setDescription("Standard Smogon OU-style singles format with full 6 Pokemon teams (Best of 1). Level scaled to 50.");
        format6v6.setBestOf(1);
        format6v6.setSetLevel(50);
        format6v6.setBattleTimer(true);
        format6v6.setTeamPreview(true);
        format6v6.setSpeciesClause(true);

        Format.FormatBans bans6v6 = new Format.FormatBans();
        bans6v6.setPokemon(new ArrayList<>(bannedPokemon));
        bans6v6.setMoves(new ArrayList<>(bannedMoves));
        bans6v6.setAbilities(new ArrayList<>(bannedAbilities));
        bans6v6.setItems(new ArrayList<>(bannedItems));
        format6v6.setBans(bans6v6);

        saveFormat("6v6_smogon_singles", format6v6);
        LOGGER.info("Created default format: 6v6 Smogon Singles");

        Format format6v6BO3 = new Format("6v6 Smogon Singles BO3");
        format6v6BO3.setPartySize(6);
        format6v6BO3.setDescription("Standard Smogon OU-style singles format with full 6 Pokemon teams (Best of 3). Level scaled to 50.");
        format6v6BO3.setBestOf(3);
        format6v6BO3.setSetLevel(50);
        format6v6BO3.setBattleTimer(true);
        format6v6BO3.setTeamPreview(true);
        format6v6BO3.setSpeciesClause(true);

        Format.FormatBans bans6v6BO3 = new Format.FormatBans();
        bans6v6BO3.setPokemon(new ArrayList<>(bannedPokemon));
        bans6v6BO3.setMoves(new ArrayList<>(bannedMoves));
        bans6v6BO3.setAbilities(new ArrayList<>(bannedAbilities));
        bans6v6BO3.setItems(new ArrayList<>(bannedItems));
        format6v6BO3.setBans(bans6v6BO3);

        saveFormat("6v6_smogon_singles_bo3", format6v6BO3);
        LOGGER.info("Created default format: 6v6 Smogon Singles BO3");

        Format format3v3 = new Format("3v3 Singles");
        format3v3.setPartySize(3);
        format3v3.setDescription("Casual 3v3 singles format with no bans. Great for quick matches!");
        format3v3.setBestOf(1);
        format3v3.setSetLevel(50);
        format3v3.setBattleTimer(false);
        format3v3.setTeamPreview(false);
        format3v3.setSpeciesClause(false);

        saveFormat("3v3_singles", format3v3);
        LOGGER.info("Created default format: 3v3 Singles");
    }

    private void loadAllFormats() {
        formats.clear();
        File dir = formatsDir.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            LOGGER.warn("Formats directory does not exist: {}", formatsDir);
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try {
                loadFormat(file);
            } catch (Exception e) {
                LOGGER.error("Failed to load format from {}", file.getName(), e);
            }
        }
    }

    private void loadFormat(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            Format format = GSON.fromJson(reader, Format.class);

            if (format.getName() == null || format.getName().isEmpty()) {
                LOGGER.warn("Format in {} has no name, skipping", file.getName());
                return;
            }

            String formatId = sanitizeFormatId(file.getName().replace(".json", ""));
            formats.put(formatId, format);
            LOGGER.debug("Loaded format: {} from {}", format.getName(), file.getName());
        }
    }

    private boolean saveFormat(String formatId, Format format) {
        if (formatId == null || formatId.isEmpty()) {
            LOGGER.error("Cannot save format with null or empty ID");
            return false;
        }

        formatId = sanitizeFormatId(formatId);
        File file = formatsDir.resolve(formatId + ".json").toFile();

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(format, writer);
            formats.put(formatId, format);
            LOGGER.info("Saved format: {} to {}", format.getName(), file.getName());
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save format {}", formatId, e);
            return false;
        }
    }

    public Format getFormat(String formatId) {
        return formats.get(sanitizeFormatId(formatId));
    }

    public Map<String, Format> getAllFormats() {
        return Collections.unmodifiableMap(formats);
    }

    public Collection<String> getFormatIds() {
        return Collections.unmodifiableSet(formats.keySet());
    }

    public boolean hasFormat(String formatId) {
        return formats.containsKey(sanitizeFormatId(formatId));
    }

    public String exportFormat(String formatId) {
        Format format = getFormat(formatId);
        if (format == null) {
            return null;
        }
        return GSON.toJson(format);
    }

    private String sanitizeFormatId(String id) {
        return id.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
    }

    private void startFileWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            formatsDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            watchThread = new Thread(this::watchFiles, "FormatWatcher");
            watchThread.setDaemon(true);
            watchThread.start();

            LOGGER.info("Started format file watcher");
        } catch (IOException e) {
            LOGGER.error("Failed to start file watcher", e);
        }
    }

    private void watchFiles() {
        while (true) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (ClosedWatchServiceException e) {
                // shutdown closed the service while it was blocked in take(); exit cleanly
                // to prevent log spamming when the loop would otherwise spin on a closed service.
                LOGGER.info("Format file watcher stopped");
                return;
            } catch (InterruptedException e) {
                LOGGER.info("Format file watcher stopped");
                return;
            }

            try {
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (!filename.toString().endsWith(".json")) {
                        continue;
                    }

                    Path filePath = formatsDir.resolve(filename);
                    String formatId = sanitizeFormatId(filename.toString().replace(".json", ""));

                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        formats.remove(formatId);
                        LOGGER.info("Removed format due to file deletion: {}", formatId);
                    } else if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                               kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // small delay to ensure file is fully written
                        Thread.sleep(100);
                        try {
                            loadFormat(filePath.toFile());
                            LOGGER.info("Reloaded format: {}", formatId);
                        } catch (Exception e) {
                            LOGGER.error("Failed to reload format {}", formatId, e);
                        }
                    }
                }

                key.reset();
            } catch (Exception e) {
                LOGGER.error("Error in file watcher", e);
            }
        }
    }

    public void shutdown() {
        // close first so the watcher's blocking take() throws ClosedWatchServiceException and exits.
        // interrupt is a fallback in case close() is delayed.
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close watch service", e);
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }
}

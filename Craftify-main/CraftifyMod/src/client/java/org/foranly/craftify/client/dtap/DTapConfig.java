package org.foranly.craftify.client.dtap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Settings for the Mace D-taps feature and the nametag health bar ({@code config/larper-dtap.json}). */
public final class DTapConfig {

    public enum FirstWeapon { AUTO, BREACH_MACE, AXE, SWORD }

    public enum ElytraOff { AUTO, OFFHAND_CHESTPLATE, HOTBAR_CHESTPLATE, NONE }

    public enum HealthStyle { HEARTS, NUMBER, BOTH }

    /** Ping ranges (ms) used for per-ping gaps: &lt;50, 50-99, 100-149, 150+. */
    public static final int[] PING_BUCKET_START = {0, 50, 100, 150};

    public static final class TypeSettings {
        public boolean enabled = true;
        /** Ticks between the first hit and the second hit (20 ticks = 1 second). */
        public int gapTicks = 1;
        public boolean pingGapEnabled = false;
        public int[] pingGaps = {1, 1, 1, 1};
        public boolean returnToSlot = true;
        public FirstWeapon firstWeapon = FirstWeapon.AUTO;
        public ElytraOff elytraOff = ElytraOff.AUTO;
    }

    public static final class HealthBar {
        public boolean enabled = true;
        public HealthStyle style = HealthStyle.HEARTS;
        public boolean showSelf = false;
        public boolean includeAbsorption = true;
        public boolean playersOnly = true;
    }

    /** Auto D-tap: aims at one named player and fires once the requirements are met. */
    public static final class Auto {
        /** Only this player is ever targeted. Empty = auto mode does nothing. */
        public String targetName = "";
        public DTapType type = DTapType.ELYTRA;
        /** Blocks fallen before the slam counts (vanilla needs more than 1.5). */
        public int minFallBlocks = 2;
        public boolean aimEnabled = true;
        /** Share of the remaining angle turned each tick (100 = instant, lower = smoother). */
        public int aimSpeedPercent = 35;
        /** Show what auto mode is waiting for in the action bar. */
        public boolean showStatus = true;
    }

    public Map<String, TypeSettings> types = new LinkedHashMap<>();
    public Auto auto = new Auto();
    public HealthBar healthBar = new HealthBar();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("larper-dtap.json");
    private static DTapConfig instance;

    private DTapConfig() {
    }

    public static DTapConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static DTapConfig load() {
        DTapConfig cfg = null;
        if (Files.exists(FILE)) {
            try {
                cfg = GSON.fromJson(Files.readString(FILE), DTapConfig.class);
            } catch (IOException | RuntimeException e) {
                cfg = null;
            }
        }
        if (cfg == null) {
            cfg = new DTapConfig();
        }
        if (cfg.types == null) {
            cfg.types = new LinkedHashMap<>();
        }
        if (cfg.healthBar == null) {
            cfg.healthBar = new HealthBar();
        }
        if (cfg.auto == null) {
            cfg.auto = new Auto();
        }
        if (cfg.auto.type == null) {
            cfg.auto.type = DTapType.ELYTRA;
        }
        if (cfg.auto.targetName == null) {
            cfg.auto.targetName = "";
        }
        for (DTapType type : DTapType.values()) {
            TypeSettings s = cfg.types.computeIfAbsent(type.id, k -> new TypeSettings());
            if (s.pingGaps == null || s.pingGaps.length != PING_BUCKET_START.length) {
                s.pingGaps = new int[]{s.gapTicks, s.gapTicks, s.gapTicks, s.gapTicks};
            }
            if (s.firstWeapon == null) {
                s.firstWeapon = FirstWeapon.AUTO;
            }
            if (s.elytraOff == null) {
                s.elytraOff = ElytraOff.AUTO;
            }
        }
        // Sensible defaults for the follow-up slam: a single hit, no gap needed.
        return cfg;
    }

    public TypeSettings of(DTapType type) {
        return types.computeIfAbsent(type.id, k -> new TypeSettings());
    }

    public static int bucketOf(int pingMs) {
        int bucket = 0;
        for (int i = 0; i < PING_BUCKET_START.length; i++) {
            if (pingMs >= PING_BUCKET_START[i]) {
                bucket = i;
            }
        }
        return bucket;
    }

    public void save() {
        try {
            if (FILE.getParent() != null) {
                Files.createDirectories(FILE.getParent());
            }
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            // Best effort.
        }
    }
}

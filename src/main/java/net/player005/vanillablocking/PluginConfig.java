package net.player005.vanillablocking;

import net.player005.vanillablocking.BlockingFormula.Formula;
import net.player005.vanillablocking.item.BlockingItems;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads, migrates and exposes the plugin configuration.
 */
public final class PluginConfig {

    /**
     * Version of the bundled config.yml. Bump this whenever options are
     * added, removed or renamed, and (if needed) add a migration step in
     * {@link ConfigMigrator#rename}. On mismatch the user's config is
     * regenerated from the bundled defaults with all still-existing user
     * values carried over, and the old file is backed up.
     */
    public static final int CURRENT_VERSION = 3;

    /**
     * Which mechanism is used to make items block.
     */
    public enum StrategyChoice {
        /** Native blocking on 1.21.5+, the consumable trick below that. */
        AUTO,
        /** Always fake it with the consumable component. */
        CONSUMABLE,
        /** Always use the native component; refuses to load below 1.21.5. */
        BLOCKS_ATTACKS
    }

    /**
     * How the plugin decides whether a damage type can be blocked at all.
     */
    public enum CauseRule {
        /** The 1.8.9 rule, derived per hit: anything armour reduces. */
        ARMOR_APPLICABLE,
        /** Only the causes listed in {@code blockable-damage-causes}. */
        LIST
    }

    private final JavaPlugin plugin;

    private boolean enabled = true;
    private StrategyChoice strategy = StrategyChoice.AUTO;

    private Formula formula = Formula.LEGACY;
    private double multiplier = 0.5;
    private double maxReduction = -1;
    private double blockingAngle = 180;
    private CauseRule causeRule = CauseRule.ARMOR_APPLICABLE;
    private final Map<Material, Double> perItemMultiplier = new EnumMap<>(Material.class);

    private double knockbackMultiplier = 1.0;
    private double movementSpeedMultiplier = -1;
    private int blockDelayTicks = 0;
    private int durabilityCost = 0;

    private boolean blockHittingEnabled = true;
    private double blockHitDamageMultiplier = 1.0;
    private int interruptTicks = 10;
    private boolean blockHitFlicker = false;

    private boolean allowWithShield = false;
    private boolean allowOffhand = false;
    private boolean requirePermission = false;
    private boolean pvpOnly = false;
    private boolean allowUnsafeItems = false;
    private boolean respectWorldGuard = true;
    private boolean disableForBedrock = false;
    private final Set<GameMode> disabledGameModes = EnumSet.noneOf(GameMode.class);
    private final Set<String> disabledWorlds = new HashSet<>();

    private String feedbackSound = "";
    private float feedbackVolume = 1.0f;
    private float feedbackPitch = 1.0f;
    private boolean feedbackParticles = false;
    private boolean feedbackActionBar = false;

    private boolean ocmTooltipCompat = true;
    private boolean ocmWatchConfig = true;
    private int ocmWatchIntervalSeconds = 30;

    private boolean updateCheckerEnabled = true;
    private String updateCheckerProject = "vanilla-sword-blocking";
    private boolean metricsEnabled = true;

    private final Set<Material> blockableItems = EnumSet.noneOf(Material.class);
    private final Set<DamageCause> blockableCauses = EnumSet.noneOf(DamageCause.class);

    public PluginConfig(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * (Re)loads the config. Returns false if the file contains YAML errors,
     * in which case the previous settings (or the built-in defaults on the
     * first load) are kept and the user's file is left untouched.
     */
    public boolean load() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (Exception exception) {
            plugin.getSLF4JLogger().error("config.yml contains errors and could not be loaded - keeping the previous settings. Fix the file and run /vanillablocking reload.", exception);
            return false;
        }

        int version = config.getInt("config-version", 0);
        if (version != CURRENT_VERSION) {
            try {
                config = migrate(file, config, version);
                plugin.getSLF4JLogger().info("Updated config.yml from version {} to version {}.", version, CURRENT_VERSION);
            } catch (Exception exception) {
                plugin.getSLF4JLogger().error("Failed to update config.yml from version {} to version {} - keeping the old file, missing options use their defaults.", version, CURRENT_VERSION, exception);
            }
        }
        read(config);
        return true;
    }

    /**
     * Regenerates the config from the bundled defaults (keeping their
     * comments), carries over every user value whose option still exists,
     * and backs up the previous file.
     */
    private @NotNull YamlConfiguration migrate(@NotNull File file, @NotNull YamlConfiguration old, int oldVersion) throws IOException {
        File backup = new File(plugin.getDataFolder(), "config-backup-v" + oldVersion + ".yml");
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        plugin.getSLF4JLogger().info("Backed up old config to {}.", backup.getName());

        YamlConfiguration merged = ConfigMigrator.merge(old, loadBundledDefaults(), oldVersion, CURRENT_VERSION);
        merged.save(file);
        return merged;
    }

    private @NotNull YamlConfiguration loadBundledDefaults() throws IOException {
        InputStream resource = plugin.getResource("config.yml");
        if (resource == null) throw new IOException("config.yml is missing from the plugin jar");
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private void read(@NotNull YamlConfiguration config) {
        enabled = config.getBoolean("enabled", true);
        strategy = readEnum(config.getString("strategy", "auto"), StrategyChoice.class, StrategyChoice.AUTO, "strategy");

        formula = readEnum(config.getString("damage-reduction.formula", "legacy"), Formula.class, Formula.LEGACY, "damage-reduction.formula");
        multiplier = Math.max(0, config.getDouble("damage-reduction.multiplier", 0.5));
        maxReduction = config.getDouble("damage-reduction.max-reduction", -1);
        blockingAngle = clamp(config.getDouble("damage-reduction.blocking-angle", 180), 0, 180);
        causeRule = readEnum(config.getString("damage-reduction.cause-rule", "armor-applicable"), CauseRule.class, CauseRule.ARMOR_APPLICABLE, "damage-reduction.cause-rule");

        perItemMultiplier.clear();
        ConfigurationSection perItem = config.getConfigurationSection("damage-reduction.per-item");
        if (perItem != null) {
            for (String key : perItem.getKeys(false)) {
                Material material = item(key);
                if (material == null) {
                    warn("Ignoring unknown item '{}' in damage-reduction.per-item.", key);
                    continue;
                }
                perItemMultiplier.put(material, Math.max(0, perItem.getDouble(key)));
            }
        }

        knockbackMultiplier = Math.max(0, config.getDouble("knockback-multiplier", 1.0));
        double speed = config.getDouble("movement-speed-multiplier", -1);
        // Anything above 1 would make blocking faster than walking, which the
        // compensating attribute modifier would happily grant.
        movementSpeedMultiplier = speed < 0 ? -1 : Math.min(speed, 1.0);
        blockDelayTicks = Math.max(0, config.getInt("block-delay-ticks", 0));
        durabilityCost = Math.max(0, config.getInt("durability-cost", 0));

        blockHittingEnabled = config.getBoolean("block-hitting.enabled", true);
        blockHitDamageMultiplier = Math.max(0, config.getDouble("block-hitting.attack-damage-multiplier", 1.0));
        interruptTicks = Math.max(1, config.getInt("block-hitting.interrupt-ticks", 10));
        blockHitFlicker = config.getBoolean("block-hitting.visual-flicker", false);

        allowWithShield = config.getBoolean("restrictions.allow-with-shield", false);
        allowOffhand = config.getBoolean("restrictions.allow-offhand", false);
        requirePermission = config.getBoolean("restrictions.require-permission", false);
        pvpOnly = config.getBoolean("restrictions.pvp-only", false);
        allowUnsafeItems = config.getBoolean("restrictions.allow-unsafe-items", false);
        respectWorldGuard = config.getBoolean("restrictions.respect-worldguard-flag", true);
        disableForBedrock = config.getBoolean("restrictions.disable-for-bedrock", false);

        disabledWorlds.clear();
        for (String world : config.getStringList("restrictions.disabled-worlds")) {
            disabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }

        disabledGameModes.clear();
        for (String mode : config.getStringList("restrictions.disabled-gamemodes")) {
            try {
                disabledGameModes.add(GameMode.valueOf(mode.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                warn("Ignoring unknown gamemode '{}' in restrictions.disabled-gamemodes.", mode);
            }
        }

        feedbackSound = config.getString("feedback.sound", "");
        feedbackVolume = (float) config.getDouble("feedback.volume", 1.0);
        feedbackPitch = (float) config.getDouble("feedback.pitch", 1.0);
        feedbackParticles = config.getBoolean("feedback.particles", false);
        feedbackActionBar = config.getBoolean("feedback.action-bar", false);

        ocmTooltipCompat = config.getBoolean("oldcombatmechanics.tooltip-compat", true);
        ocmWatchConfig = config.getBoolean("oldcombatmechanics.watch-config", true);
        ocmWatchIntervalSeconds = Math.max(5, config.getInt("oldcombatmechanics.watch-interval-seconds", 30));

        updateCheckerEnabled = config.getBoolean("update-checker.enabled", true);
        updateCheckerProject = config.getString("update-checker.modrinth-project", "vanilla-sword-blocking");
        metricsEnabled = config.getBoolean("metrics", true);

        readBlockableItems(config);
        readBlockableCauses(config);
    }

    private void readBlockableItems(@NotNull YamlConfiguration config) {
        blockableItems.clear();
        Set<Material> requested = EnumSet.noneOf(Material.class);

        for (String entry : config.getStringList("blockable-items")) {
            if (entry.startsWith("#")) {
                Tag<Material> tag = itemTag(entry.substring(1));
                if (tag == null) {
                    warn("Ignoring unknown item tag '{}' in blockable-items.", entry);
                    continue;
                }
                requested.addAll(tag.getValues());
            } else {
                Material material = item(entry);
                if (material == null) {
                    warn("Ignoring unknown item '{}' in blockable-items.", entry);
                    continue;
                }
                requested.add(material);
            }
        }

        for (Material material : requested) {
            BlockingItems.Problem problem = BlockingItems.inspect(material);
            if (problem == BlockingItems.Problem.NONE) {
                blockableItems.add(material);
                continue;
            }
            if (problem.isFatal() && !allowUnsafeItems) {
                warn("Refusing to make {} blockable: {}. Set restrictions.allow-unsafe-items to true to override.",
                        material.getKey().asString(), BlockingItems.describe(problem));
                continue;
            }
            warn("Making {} blockable even though {}.", material.getKey().asString(), BlockingItems.describe(problem));
            blockableItems.add(material);
        }

        if (blockableItems.isEmpty()) {
            warn("blockable-items contains no usable entries - nothing will be able to block.");
        }
    }

    private void readBlockableCauses(@NotNull YamlConfiguration config) {
        blockableCauses.clear();
        for (String name : config.getStringList("blockable-damage-causes")) {
            try {
                blockableCauses.add(DamageCause.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                warn("Ignoring unknown damage cause '{}' in blockable-damage-causes.", name);
            }
        }
        if (causeRule == CauseRule.LIST && blockableCauses.isEmpty()) {
            warn("damage-reduction.cause-rule is 'list' but blockable-damage-causes is empty - no damage will be blocked.");
        }
    }

    private <T extends Enum<T>> T readEnum(@Nullable String raw, @NotNull Class<T> type, @NotNull T fallback, @NotNull String path) {
        if (raw == null || raw.isBlank()) return fallback;
        String name = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            warn("Unknown value '{}' for {}, falling back to '{}'.", raw, path, fallback.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            return fallback;
        }
    }

    private void warn(@NotNull String message, Object @NotNull ... args) {
        plugin.getSLF4JLogger().warn(message, args);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static @Nullable Tag<Material> itemTag(@NotNull String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        if (key == null) return null;
        return Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
    }

    private static @Nullable Material item(@NotNull String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        if (key == null) return null;
        Material material = Registry.MATERIAL.get(key);
        return material != null && material.isItem() ? material : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public @NotNull StrategyChoice strategy() {
        return strategy;
    }

    public boolean isActiveIn(@NotNull World world) {
        return enabled && !disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public boolean isBlockable(@NotNull DamageCause cause) {
        return blockableCauses.contains(cause);
    }

    public boolean isBlockableItem(@NotNull Material material) {
        return blockableItems.contains(material);
    }

    public @NotNull Set<Material> blockableItems() {
        return Set.copyOf(blockableItems);
    }

    public @NotNull CauseRule causeRule() {
        return causeRule;
    }

    public @NotNull Formula formula() {
        return formula;
    }

    public double multiplier() {
        return multiplier;
    }

    public double maxReduction() {
        return maxReduction;
    }

    public double blockingAngle() {
        return blockingAngle;
    }

    /**
     * Per-item override for the fraction of damage still taken, or a negative
     * value when the item uses the global formula.
     */
    public double perItemMultiplier(@NotNull Material material) {
        Double value = perItemMultiplier.get(material);
        return value == null ? -1 : value;
    }

    public boolean hasPerItemOverrides() {
        return !perItemMultiplier.isEmpty();
    }

    public boolean blockHittingEnabled() {
        return blockHittingEnabled;
    }

    public double blockHitDamageMultiplier() {
        return blockHitDamageMultiplier;
    }

    public int interruptTicks() {
        return interruptTicks;
    }

    public boolean blockHitFlicker() {
        return blockHitFlicker;
    }

    public boolean allowWithShield() {
        return allowWithShield;
    }

    public boolean allowOffhand() {
        return allowOffhand;
    }

    public boolean requirePermission() {
        return requirePermission;
    }

    public boolean pvpOnly() {
        return pvpOnly;
    }

    public boolean respectWorldGuard() {
        return respectWorldGuard;
    }

    public boolean disableForBedrock() {
        return disableForBedrock;
    }

    public boolean isGameModeDisabled(@NotNull GameMode mode) {
        return disabledGameModes.contains(mode);
    }

    public double knockbackMultiplier() {
        return knockbackMultiplier;
    }

    public double movementSpeedMultiplier() {
        return movementSpeedMultiplier;
    }

    public int blockDelayTicks() {
        return blockDelayTicks;
    }

    public int durabilityCost() {
        return durabilityCost;
    }

    public @NotNull String feedbackSound() {
        return feedbackSound == null ? "" : feedbackSound;
    }

    public float feedbackVolume() {
        return feedbackVolume;
    }

    public float feedbackPitch() {
        return feedbackPitch;
    }

    public boolean feedbackParticles() {
        return feedbackParticles;
    }

    public boolean feedbackActionBar() {
        return feedbackActionBar;
    }

    public boolean ocmTooltipCompat() {
        return ocmTooltipCompat;
    }

    public boolean ocmWatchConfig() {
        return ocmWatchConfig;
    }

    public int ocmWatchIntervalSeconds() {
        return ocmWatchIntervalSeconds;
    }

    public boolean updateCheckerEnabled() {
        return updateCheckerEnabled;
    }

    public @NotNull String updateCheckerProject() {
        return updateCheckerProject == null ? "" : updateCheckerProject;
    }

    public boolean metricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Applies the configured damage reduction to a pre-armor damage value.
     */
    public double applyBlocking(double damage, @NotNull Material blockingItem) {
        double override = perItemMultiplier(blockingItem);
        if (override >= 0) {
            return BlockingFormula.damageTakenCapped(Formula.MULTIPLIER, damage, override, maxReduction);
        }
        return BlockingFormula.damageTakenCapped(formula, damage, multiplier, maxReduction);
    }
}

package net.player005.vanillablocking;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Loads, migrates and exposes the plugin configuration.
 */
public final class PluginConfig {

    /**
     * Version of the bundled config.yml. Bump this whenever options are
     * added, removed or renamed, and (if needed) add a migration step in
     * {@link #migrate}. On mismatch the user's config is regenerated from
     * the bundled defaults with all still-existing user values carried
     * over, and the old file is backed up.
     */
    public static final int CURRENT_VERSION = 2;

    public enum Formula {LEGACY, MULTIPLIER}

    private final JavaPlugin plugin;

    private boolean enabled = true;
    private Formula formula = Formula.LEGACY;
    private double multiplier = 0.5;
    private double knockbackMultiplier = 1.0;
    private boolean blockHittingEnabled = true;
    private double blockHitDamageMultiplier = 1.0;
    private int interruptTicks = 10;
    private boolean allowWithShield = false;
    private boolean allowOffhand = false;
    private final Set<Material> blockableItems = EnumSet.noneOf(Material.class);
    private final Set<DamageCause> blockableCauses = EnumSet.noneOf(DamageCause.class);
    private final Set<String> disabledWorlds = new HashSet<>();

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

        YamlConfiguration defaults = loadBundledDefaults();

        // Per-version migration steps for renamed/restructured options go
        // here before values are carried over, e.g.:
        // if (oldVersion <= 2) old.set("new-key", old.get("old-key"));

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (key.equals("config-version")) continue;
            if (old.contains(key)) defaults.set(key, old.get(key));
        }
        defaults.set("config-version", CURRENT_VERSION);
        defaults.save(file);
        return defaults;
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

        String formulaName = config.getString("damage-reduction.formula", "legacy");
        switch (formulaName.toLowerCase(Locale.ROOT)) {
            case "legacy" -> formula = Formula.LEGACY;
            case "multiplier" -> formula = Formula.MULTIPLIER;
            default -> {
                plugin.getSLF4JLogger().warn("Unknown damage-reduction.formula '{}', falling back to 'legacy'.", formulaName);
                formula = Formula.LEGACY;
            }
        }

        multiplier = Math.max(0, config.getDouble("damage-reduction.multiplier", 0.5));
        knockbackMultiplier = Math.max(0, config.getDouble("knockback-multiplier", 1.0));

        blockHittingEnabled = config.getBoolean("block-hitting.enabled", true);
        blockHitDamageMultiplier = Math.max(0, config.getDouble("block-hitting.attack-damage-multiplier", 1.0));
        interruptTicks = Math.max(1, config.getInt("block-hitting.interrupt-ticks", 10));

        allowWithShield = config.getBoolean("restrictions.allow-with-shield", false);
        allowOffhand = config.getBoolean("restrictions.allow-offhand", false);

        disabledWorlds.clear();
        for (String world : config.getStringList("restrictions.disabled-worlds")) {
            disabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }

        blockableItems.clear();
        for (String entry : config.getStringList("blockable-items")) {
            if (entry.startsWith("#")) {
                Tag<Material> tag = itemTag(entry.substring(1));
                if (tag == null) {
                    plugin.getSLF4JLogger().warn("Ignoring unknown item tag '{}' in blockable-items.", entry);
                    continue;
                }
                blockableItems.addAll(tag.getValues());
            } else {
                Material material = item(entry);
                if (material == null) {
                    plugin.getSLF4JLogger().warn("Ignoring unknown item '{}' in blockable-items.", entry);
                    continue;
                }
                blockableItems.add(material);
            }
        }
        if (blockableItems.isEmpty()) {
            plugin.getSLF4JLogger().warn("blockable-items contains no valid entries - nothing will be able to block.");
        }

        blockableCauses.clear();
        for (String name : config.getStringList("blockable-damage-causes")) {
            try {
                blockableCauses.add(DamageCause.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getSLF4JLogger().warn("Ignoring unknown damage cause '{}' in blockable-damage-causes.", name);
            }
        }
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

    public boolean isActiveIn(@NotNull World world) {
        return enabled && !disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public boolean isBlockable(@NotNull DamageCause cause) {
        return blockableCauses.contains(cause);
    }

    public boolean isBlockableItem(@NotNull Material material) {
        return blockableItems.contains(material);
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

    public boolean allowWithShield() {
        return allowWithShield;
    }

    public boolean allowOffhand() {
        return allowOffhand;
    }

    public double knockbackMultiplier() {
        return knockbackMultiplier;
    }

    /**
     * Applies the configured damage reduction to a pre-armor damage value.
     */
    public double applyBlocking(double damage) {
        return switch (formula) {
            // 1.8.9 only blocked strictly positive damage
            case LEGACY -> damage > 0 ? (1.0 + damage) * 0.5 : damage;
            case MULTIPLIER -> damage * multiplier;
        };
    }
}

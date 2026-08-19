package net.player005.vanillablocking.ocm;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the bits of OldCombatMechanics' config the plugin needs, straight
 * from its config.yml - OCM exposes no API for this.
 * <p>
 * Everything here is a no-op when OCM is not installed.
 */
public final class OcmConfigReader {

    private static final String OCM_NAME = "OldCombatMechanics";

    private volatile boolean present;
    private volatile boolean enabled;
    private volatile Set<String> worlds = Set.of(); // lowercase world names
    private volatile Map<Material, Double> desiredDamage = Map.of();
    private volatile boolean swordBlockingModule;
    private volatile boolean attackCooldownDisabled;
    private volatile long lastModified;

    /**
     * Whether OldCombatMechanics is installed at all.
     */
    public boolean isPresent() {
        return present;
    }

    /**
     * Whether OCM's old-tool-damage module applies in this world, so item
     * tooltips need correcting to match.
     */
    public boolean isActiveIn(@NotNull World world) {
        if (!enabled) return false;
        if (worlds.isEmpty()) return true;
        return worlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    /**
     * The attack damage OCM gives this item, or null when it leaves it alone.
     */
    public @Nullable Double desiredDamage(@NotNull Material mat) {
        return desiredDamage.get(mat);
    }

    /**
     * Whether OCM's own sword-blocking module is on. It fakes blocking by
     * swapping the sword for a shield, so running it alongside this plugin
     * means two plugins fighting over the same right-click.
     */
    public boolean isSwordBlockingModuleEnabled() {
        return swordBlockingModule;
    }

    /**
     * Whether OCM removes the 1.9 attack cooldown. Block-hitting was
     * designed around 1.8 combat and feels wrong with the cooldown on.
     */
    public boolean isAttackCooldownDisabled() {
        return attackCooldownDisabled;
    }

    /**
     * Re-reads OCM's config only if the file changed since the last read.
     *
     * @return whether anything was re-read
     */
    public boolean reloadIfChanged() {
        File file = configFile();
        if (file == null) return false;
        long modified = file.lastModified();
        if (modified == lastModified) return false;
        reload();
        return true;
    }

    /**
     * Re-reads OCM's config.
     */
    public void reload() {
        File file = configFile();
        if (file == null) {
            present = false;
            enabled = false;
            worlds = Set.of();
            desiredDamage = Map.of();
            swordBlockingModule = false;
            attackCooldownDisabled = false;
            lastModified = 0;
            return;
        }

        present = true;
        lastModified = file.lastModified();
        final YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        enabled = yml.getBoolean("old-tool-damage.enabled", false);

        final Set<String> worldSet = new HashSet<>();
        for (String world : yml.getStringList("old-tool-damage.worlds")) {
            if (world != null && !world.isBlank()) worldSet.add(world.toLowerCase(Locale.ROOT));
        }
        worlds = Collections.unmodifiableSet(worldSet);

        final ConfigurationSection damageSection = yml.getConfigurationSection("old-tool-damage.damages");
        final Map<Material, Double> damages = new EnumMap<>(Material.class);
        if (damageSection != null) {
            for (String key : damageSection.getKeys(false)) {
                final Material material = toMaterial(key);
                if (material != null) damages.put(material, damageSection.getDouble(key));
            }
        }
        desiredDamage = Collections.unmodifiableMap(damages);

        // OCM has renamed this module over the years; accept both spellings.
        swordBlockingModule = yml.getBoolean("sword-blocking.enabled", false)
                || yml.getBoolean("old-sword-blocking.enabled", false);
        attackCooldownDisabled = yml.getBoolean("disable-attack-cooldown.enabled", false);
    }

    private @Nullable File configFile() {
        final Plugin ocm = Bukkit.getPluginManager().getPlugin(OCM_NAME);
        if (ocm == null) return null;
        final File file = new File(ocm.getDataFolder(), "config.yml");
        return file.isFile() ? file : null;
    }

    private static @Nullable Material toMaterial(@Nullable String key) {
        String normalised = normaliseMaterialName(key);
        return normalised == null ? null : Material.matchMaterial(normalised);
    }

    /**
     * Normalises a material name as it appears in OCM configs, including the
     * pre-flattening aliases still found in old files.
     */
    public static @Nullable String normaliseMaterialName(@Nullable String key) {
        if (key == null) return null;
        String name = key.trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty()) return null;

        for (String tool : List.of("SWORD", "AXE", "PICKAXE", "SHOVEL", "SPADE", "HOE")) {
            if (name.equals("WOOD_" + tool)) return "WOODEN_" + fixToolName(tool);
            if (name.equals("GOLD_" + tool)) return "GOLDEN_" + fixToolName(tool);
        }
        if (name.endsWith("_SPADE")) return name.substring(0, name.length() - "_SPADE".length()) + "_SHOVEL";
        return name;
    }

    private static @NotNull String fixToolName(@NotNull String tool) {
        return tool.equals("SPADE") ? "SHOVEL" : tool;
    }
}

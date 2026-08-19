package net.player005.vanillablocking;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

public final class OcmConfigReader {

    private static final String OCM_NAME = "OldCombatMechanics";

    private volatile boolean enabled;
    private volatile Set<String> worlds = Set.of(); // lowercase world names
    private volatile Map<Material, Double> desiredDamage = Map.of();

    public boolean isActiveIn(World world) {
        if (!enabled) return false;
        if (worlds.isEmpty()) return true;
        return worlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public Double desiredDamage(Material mat) {
        return desiredDamage.get(mat);
    }

    public void reload() {
        final Plugin ocm = Bukkit.getPluginManager().getPlugin(OCM_NAME);
        if (ocm == null) {
            enabled = false;
            worlds = Set.of();
            desiredDamage = Map.of();
            return;
        }

        final File cfg = new File(ocm.getDataFolder(), "config.yml");
        final YamlConfiguration yml = YamlConfiguration.loadConfiguration(cfg);

        enabled = yml.getBoolean("old-tool-damage.enabled", false);

        final List<String> w = yml.getStringList("old-tool-damage.worlds");
        final Set<String> wset = new HashSet<>();
        for (String s : w) if (s != null && !s.isBlank()) wset.add(s.toLowerCase(Locale.ROOT));
        worlds = Collections.unmodifiableSet(wset);

        final ConfigurationSection dmgSec = yml.getConfigurationSection("old-tool-damage.damages");
        final Map<Material, Double> map = new EnumMap<>(Material.class);
        if (dmgSec != null) {
            for (String key : dmgSec.getKeys(false)) {
                final double val = dmgSec.getDouble(key);
                final Material mat = toMaterial(key);
                if (mat != null) map.put(mat, val);
            }
        }
        desiredDamage = Collections.unmodifiableMap(map);
    }

    private static Material toMaterial(String key) {
        if (key == null) return null;

        String norm = key.trim().toUpperCase(Locale.ROOT);

        // legacy aliases seen in older configs
        if (norm.equals("WOOD_SWORD")) norm = "WOODEN_SWORD";
        if (norm.equals("WOOD_AXE")) norm = "WOODEN_AXE";
        if (norm.equals("WOOD_PICKAXE")) norm = "WOODEN_PICKAXE";
        if (norm.equals("WOOD_SHOVEL")) norm = "WOODEN_SHOVEL";
        if (norm.equals("WOOD_HOE")) norm = "WOODEN_HOE";
        if (norm.equals("GOLD_SWORD")) norm = "GOLDEN_SWORD";
        if (norm.equals("GOLD_AXE")) norm = "GOLDEN_AXE";
        if (norm.equals("GOLD_PICKAXE")) norm = "GOLDEN_PICKAXE";
        if (norm.equals("GOLD_SHOVEL")) norm = "GOLDEN_SHOVEL";
        if (norm.equals("GOLD_HOE")) norm = "GOLDEN_HOE";

        return Material.matchMaterial(norm);
    }
}
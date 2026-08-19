package net.player005.vanillablocking;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Carries a user's settings over to a newer bundled config. Kept free of
 * Bukkit's plugin classes so it can be unit tested.
 */
public final class ConfigMigrator {

    private ConfigMigrator() {
    }

    /**
     * Renames and restructures options from older config versions, in place
     * on the user's old config, so the generic value carry-over below finds
     * them under their new names.
     */
    public static void rename(@NotNull YamlConfiguration old, int oldVersion) {
        if (oldVersion <= 2) {
            // v3 derives blockable damage causes from "does armour apply to
            // this hit", which is what the v2 default list spelled out by
            // hand. Only keep using the list if the user actually changed it.
            List<String> causes = old.getStringList("blockable-damage-causes");
            if (!causes.isEmpty() && !isDefaultV2CauseList(causes)) {
                old.set("damage-reduction.cause-rule", "list");
            }
        }
    }

    /**
     * Copies every value the user set onto the bundled defaults, so new
     * options appear (with their comments) and removed options disappear.
     *
     * @return the defaults, with the user's values applied
     */
    public static @NotNull YamlConfiguration merge(@NotNull YamlConfiguration old,
                                                   @NotNull YamlConfiguration defaults,
                                                   int oldVersion,
                                                   int newVersion) {
        rename(old, oldVersion);

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            if (key.equals("config-version")) continue;
            if (old.contains(key)) defaults.set(key, old.get(key));
        }

        // Sections the user fills in themselves have no default keys to walk,
        // so copy them wholesale.
        for (String key : List.of("damage-reduction.per-item")) {
            ConfigurationSection section = old.getConfigurationSection(key);
            if (section == null) continue;
            // Copied as plain values: setting the section object itself does
            // not survive being written back out to YAML.
            defaults.createSection(key, section.getValues(false));
        }

        defaults.set("config-version", newVersion);
        return defaults;
    }

    /**
     * The v2 default {@code blockable-damage-causes} list, which exactly
     * reproduced "anything armour reduces".
     */
    private static boolean isDefaultV2CauseList(@NotNull List<String> causes) {
        List<String> expected = List.of(
                "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "PROJECTILE", "ENTITY_EXPLOSION",
                "BLOCK_EXPLOSION", "FIRE", "LAVA", "HOT_FLOOR", "CONTACT", "FALLING_BLOCK",
                "LIGHTNING", "THORNS");
        if (causes.size() != expected.size()) return false;
        return causes.stream()
                .map(cause -> Objects.toString(cause, "").toUpperCase(java.util.Locale.ROOT))
                .toList()
                .containsAll(expected);
    }
}

package net.player005.vanillablocking;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    private static final String DEFAULTS = """
            config-version: 3
            enabled: true
            strategy: auto
            damage-reduction:
              formula: legacy
              multiplier: 0.5
              cause-rule: armor-applicable
            block-hitting:
              enabled: true
              interrupt-ticks: 10
            blockable-items:
              - "#minecraft:swords"
            blockable-damage-causes:
              - ENTITY_ATTACK
              - ENTITY_SWEEP_ATTACK
              - PROJECTILE
              - ENTITY_EXPLOSION
              - BLOCK_EXPLOSION
              - FIRE
              - LAVA
              - HOT_FLOOR
              - CONTACT
              - FALLING_BLOCK
              - LIGHTNING
              - THORNS
            """;

    private static YamlConfiguration parse(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    @Test
    @DisplayName("user values survive, new options appear, removed options disappear")
    void carriesUserValuesOver() {
        YamlConfiguration old = parse("""
                config-version: 2
                enabled: false
                damage-reduction:
                  formula: multiplier
                  multiplier: 0.25
                block-hitting:
                  interrupt-ticks: 40
                removed-option: 12
                """);

        YamlConfiguration merged = ConfigMigrator.merge(old, parse(DEFAULTS), 2, 3);

        assertFalse(merged.getBoolean("enabled"));
        assertEquals("multiplier", merged.getString("damage-reduction.formula"));
        assertEquals(0.25, merged.getDouble("damage-reduction.multiplier"));
        assertEquals(40, merged.getInt("block-hitting.interrupt-ticks"));

        // untouched defaults are kept
        assertEquals("auto", merged.getString("strategy"));
        assertTrue(merged.getBoolean("block-hitting.enabled"));

        // options that no longer exist are dropped
        assertNull(merged.get("removed-option"));

        assertEquals(3, merged.getInt("config-version"));
    }

    @Test
    @DisplayName("a v2 config with the stock cause list moves to the derived rule")
    void defaultCauseListMigratesToArmorRule() {
        YamlConfiguration old = parse("""
                config-version: 2
                blockable-damage-causes:
                  - ENTITY_ATTACK
                  - ENTITY_SWEEP_ATTACK
                  - PROJECTILE
                  - ENTITY_EXPLOSION
                  - BLOCK_EXPLOSION
                  - FIRE
                  - LAVA
                  - HOT_FLOOR
                  - CONTACT
                  - FALLING_BLOCK
                  - LIGHTNING
                  - THORNS
                """);

        YamlConfiguration merged = ConfigMigrator.merge(old, parse(DEFAULTS), 2, 3);
        assertEquals("armor-applicable", merged.getString("damage-reduction.cause-rule"));
    }

    @Test
    @DisplayName("a v2 config with a customised cause list keeps using the list")
    void customCauseListKeepsListRule() {
        YamlConfiguration old = parse("""
                config-version: 2
                blockable-damage-causes:
                  - ENTITY_ATTACK
                  - PROJECTILE
                """);

        YamlConfiguration merged = ConfigMigrator.merge(old, parse(DEFAULTS), 2, 3);
        assertEquals("list", merged.getString("damage-reduction.cause-rule"));
        assertEquals(List.of("ENTITY_ATTACK", "PROJECTILE"), merged.getStringList("blockable-damage-causes"));
    }

    @Test
    @DisplayName("per-item overrides are copied even though the defaults have no keys under them")
    void copiesFreeFormSections() {
        YamlConfiguration old = parse("""
                config-version: 2
                damage-reduction:
                  per-item:
                    minecraft:wooden_sword: 0.75
                """);

        YamlConfiguration merged = ConfigMigrator.merge(old, parse(DEFAULTS), 2, 3);
        assertEquals(0.75, merged.getDouble("damage-reduction.per-item.minecraft:wooden_sword"));

        // The migrated config is written back out, so it has to survive a
        // save/load round trip - copying the section object itself does not.
        YamlConfiguration reloaded = parse(merged.saveToString());
        assertEquals(0.75, reloaded.getDouble("damage-reduction.per-item.minecraft:wooden_sword"));
    }
}

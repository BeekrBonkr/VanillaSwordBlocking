package net.player005.vanillablocking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Every player-facing string, loaded from {@code lang.yml} and formatted
 * with MiniMessage so server owners can translate and restyle them.
 */
public final class Messages {

    private static final String FILE_NAME = "lang.yml";

    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();

    public Messages(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * (Re)loads lang.yml, falling back to the bundled English strings for
     * any key the user's file is missing.
     */
    public void load() {
        messages.clear();

        YamlConfiguration bundled = loadBundled();
        if (bundled != null) copyInto(bundled);

        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        if (file.isFile()) {
            try {
                YamlConfiguration user = new YamlConfiguration();
                user.load(file);
                copyInto(user);
            } catch (Exception exception) {
                plugin.getSLF4JLogger().error("{} contains errors - using the built-in messages.", FILE_NAME, exception);
            }
        }
    }

    private void copyInto(@NotNull YamlConfiguration configuration) {
        for (String key : configuration.getKeys(true)) {
            if (configuration.isConfigurationSection(key)) continue;
            String value = configuration.getString(key);
            if (value != null) messages.put(key, value);
        }
    }

    private YamlConfiguration loadBundled() {
        InputStream resource = plugin.getResource(FILE_NAME);
        if (resource == null) return null;
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * The raw, unformatted string for a key.
     */
    public @NotNull String raw(@NotNull String key) {
        return messages.getOrDefault(key, key);
    }

    /**
     * A formatted message. Placeholders are MiniMessage tags, e.g.
     * {@code <count>}.
     */
    public @NotNull Component get(@NotNull String key, @NotNull TagResolver... placeholders) {
        return MiniMessage.miniMessage().deserialize(raw(key), placeholders);
    }

    /**
     * Shorthand for a single string placeholder.
     */
    public static @NotNull TagResolver placeholder(@NotNull String name, @NotNull Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /**
     * Whether a key resolves to an actually configured (non-empty) message.
     */
    public boolean has(@NotNull String key) {
        String value = messages.get(key);
        return value != null && !value.isBlank();
    }
}

package net.player005.vanillablocking.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Bedrock players connected through Geyser/Floodgate.
 * <p>
 * Bedrock clients handle held right-click differently from Java clients, so
 * sword blocking is unreliable for them and server owners may want to turn it
 * off with {@code restrictions.disable-for-bedrock}.
 */
public final class BedrockPlayers {

    private static Method isFloodgatePlayer;
    private static Object floodgateApi;
    private static boolean checked;

    private BedrockPlayers() {
    }

    /**
     * Whether this player is connected through Geyser.
     * <p>
     * Uses Floodgate's API when it is installed, and otherwise falls back to
     * the UUID shape Floodgate gives Bedrock players (a version-0 UUID, which
     * a Java account never has).
     */
    public static boolean isBedrock(@NotNull Player player) {
        Object api = api();
        if (api != null) {
            try {
                Object result = isFloodgatePlayer.invoke(api, player.getUniqueId());
                if (result instanceof Boolean bool) return bool;
            } catch (Exception ignored) {
                // Fall through to the UUID heuristic.
            }
        }
        return isFloodgateUuid(player.getUniqueId());
    }

    /**
     * Floodgate hands Bedrock players a UUID whose most significant bits are
     * zero, which no Java (version 4) UUID has.
     */
    static boolean isFloodgateUuid(@NotNull UUID uuid) {
        return uuid.getMostSignificantBits() == 0;
    }

    private static Object api() {
        if (checked) return floodgateApi;
        checked = true;

        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return null;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (Exception ignored) {
            floodgateApi = null;
        }
        return floodgateApi;
    }
}

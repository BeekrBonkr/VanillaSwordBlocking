package net.player005.vanillablocking.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Optional WorldGuard region control.
 * <p>
 * This class must not name a single WorldGuard type. The JVM resolves the
 * exception classes of a method's catch clauses when it verifies that method,
 * so merely catching a WorldGuard exception here would throw
 * NoClassDefFoundError on every server that does not have WorldGuard
 * installed. All the real work therefore lives in
 * {@code compat.worldguard.WorldGuardSupport}, which is loaded by name and
 * only once WorldGuard is known to be present.
 */
public final class WorldGuardHook {

    private static final String SUPPORT_CLASS = "net.player005.vanillablocking.compat.worldguard.WorldGuardSupport";
    private static final String FLAG_NAME = "sword-blocking";

    /**
     * Implemented by the WorldGuard-facing class.
     */
    public interface RegionCheck {

        /**
         * Whether WorldGuard allows this player to block where they stand.
         */
        boolean allowsBlocking(@NotNull Player player);
    }

    private static boolean flagRegistered;

    private volatile @Nullable RegionCheck check;

    /**
     * Registers the region flag. WorldGuard only accepts new flags before it
     * enables, so this has to run from the plugin's {@code onLoad}.
     */
    public static void registerFlag(@NotNull Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return;

        try {
            Class.forName(SUPPORT_CLASS).getMethod("registerFlag").invoke(null);
            flagRegistered = true;
            logger.info("Registered the WorldGuard region flag '{}'.", FLAG_NAME);
        } catch (Throwable throwable) {
            logger.warn("Could not register the WorldGuard region flag '{}' - region control is unavailable.",
                    FLAG_NAME, unwrap(throwable));
        }
    }

    /**
     * Called on enable, once WorldGuard is guaranteed to be up.
     */
    public void enable(@NotNull Logger logger) {
        if (!flagRegistered || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return;

        try {
            check = (RegionCheck) Class.forName(SUPPORT_CLASS).getDeclaredConstructor().newInstance();
            logger.info("WorldGuard found - the '{}' region flag is active.", FLAG_NAME);
        } catch (Throwable throwable) {
            logger.warn("Could not hook into WorldGuard - region control is unavailable.", unwrap(throwable));
        }
    }

    public void disable() {
        check = null;
    }

    /**
     * Whether WorldGuard allows this player to block where they are standing.
     * Always true when WorldGuard is absent or the flag was never registered.
     */
    public boolean allowsBlocking(@NotNull Player player) {
        RegionCheck current = check;
        return current == null || current.allowsBlocking(player);
    }

    private static @NotNull Throwable unwrap(@NotNull Throwable throwable) {
        Throwable cause = throwable.getCause();
        return cause != null ? cause : throwable;
    }
}

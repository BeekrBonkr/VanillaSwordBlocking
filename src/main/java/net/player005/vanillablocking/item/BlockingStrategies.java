package net.player005.vanillablocking.item;

import net.player005.vanillablocking.PluginConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Picks the blocking mechanism to use on this server.
 * <p>
 * The 1.21.5+ implementation is compiled against a newer API than the rest of
 * the plugin and lives in its own source set, so it is loaded by name and
 * only after the native component is known to exist. On older servers its
 * class is never touched.
 */
public final class BlockingStrategies {

    private static final String MODERN_CLASS = "net.player005.vanillablocking.item.BlocksAttacksBlockingStrategy";

    private BlockingStrategies() {
    }

    /**
     * @return the strategy matching the config, falling back to the
     * consumable trick whenever the native component is unavailable
     */
    public static @NotNull BlockingStrategy create(@NotNull PluginConfig config, @NotNull Logger logger) {
        boolean nativeAvailable = supportsBlocksAttacks();

        return switch (config.strategy()) {
            case CONSUMABLE -> new ConsumableBlockingStrategy();
            case AUTO -> nativeAvailable ? createNative(logger) : new ConsumableBlockingStrategy();
            case BLOCKS_ATTACKS -> {
                if (nativeAvailable) yield createNative(logger);
                logger.warn("strategy is set to 'blocks-attacks' but this server has no minecraft:blocks_attacks component (it needs 1.21.5 or newer) - falling back to 'consumable'.");
                yield new ConsumableBlockingStrategy();
            }
        };
    }

    private static @NotNull BlockingStrategy createNative(@NotNull Logger logger) {
        try {
            return (BlockingStrategy) Class.forName(MODERN_CLASS).getDeclaredConstructor().newInstance();
        } catch (Throwable throwable) {
            logger.warn("Could not use native blocking, falling back to the consumable component.", throwable);
            return new ConsumableBlockingStrategy();
        }
    }

    /**
     * Whether this server knows the native blocks_attacks item component.
     */
    public static boolean supportsBlocksAttacks() {
        try {
            io.papermc.paper.datacomponent.DataComponentTypes.class.getField("BLOCKS_ATTACKS");
            return true;
        } catch (NoSuchFieldException | RuntimeException | LinkageError exception) {
            return false;
        }
    }
}

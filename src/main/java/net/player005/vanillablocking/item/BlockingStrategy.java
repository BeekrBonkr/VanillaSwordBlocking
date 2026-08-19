package net.player005.vanillablocking.item;

import net.player005.vanillablocking.PluginConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * How an item is made able to block. Two implementations exist:
 * <ul>
 *     <li>{@link ConsumableBlockingStrategy} - works on every supported
 *     server, fakes the blocking animation with the consumable component and
 *     reduces damage in {@code EntityDamageEvent}.</li>
 *     <li>{@code BlocksAttacksBlockingStrategy} - 1.21.5+ only, uses the
 *     native {@code minecraft:blocks_attacks} component so the client
 *     predicts the block itself. Lives in its own source set and is loaded
 *     reflectively by {@link BlockingStrategies}.</li>
 * </ul>
 */
public interface BlockingStrategy {

    /**
     * Identifier used in the config and in log messages.
     */
    @NotNull String id();

    /**
     * Whether the server itself reduces the damage of a blocked hit. When
     * true the plugin must not reduce damage a second time and instead only
     * corrects the vanilla reduction (restrictions, caps, API events).
     */
    boolean nativeDamageReduction();

    /**
     * Called on enable and after every config reload.
     */
    void configure(@NotNull PluginConfig config);

    /**
     * Makes the stack able to block.
     *
     * @return whether the stack was modified
     */
    boolean apply(@NotNull ItemStack stack);

    /**
     * Removes everything this strategy added.
     *
     * @return whether the stack was modified
     */
    boolean remove(@NotNull ItemStack stack);

    /**
     * Whether this strategy's marker is currently on the stack.
     */
    boolean isApplied(@NotNull ItemStack stack);

    /**
     * Whether the item blocks on its own, without our help - a shield, or on
     * 1.21.5+ anything else carrying {@code minecraft:blocks_attacks}. Used to
     * decide whether an offhand item conflicts with sword blocking.
     */
    default boolean isNativeBlocker(@NotNull ItemStack stack) {
        return stack.getType() == Material.SHIELD;
    }
}

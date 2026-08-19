package net.player005.vanillablocking.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.player005.vanillablocking.PluginConfig;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fakes blocking with the {@code minecraft:consumable} component: the item
 * becomes "edible" with the BLOCK animation and a consume time long enough
 * that it is never actually eaten.
 * <p>
 * The consume time is deliberately {@link Float#MAX_VALUE}. Vanilla starts
 * emitting eating particles and sounds once 21.875% of the consume time has
 * elapsed, so a merely large value (an hour, say) would start making the
 * player chew on their sword after a few minutes of blocking.
 */
public final class ConsumableBlockingStrategy implements BlockingStrategy {

    private static final Consumable BLOCKING = Consumable.consumable()
            .consumeSeconds(Float.MAX_VALUE)
            .animation(ItemUseAnimation.BLOCK)
            .hasConsumeParticles(false)
            .build();

    @Override
    public @NotNull String id() {
        return "consumable";
    }

    @Override
    public boolean nativeDamageReduction() {
        return false;
    }

    @Override
    public void configure(@NotNull PluginConfig config) {
        // Nothing configurable: the component only drives the animation.
    }

    @Override
    public boolean apply(@NotNull ItemStack stack) {
        if (isApplied(stack)) return false;
        stack.setData(DataComponentTypes.CONSUMABLE, BLOCKING);
        return true;
    }

    @Override
    public boolean remove(@NotNull ItemStack stack) {
        if (!isApplied(stack)) return false;
        // resetData restores the item type's default (no component at all for
        // swords). unsetData would instead leave an explicit "component
        // removed" marker on the stack.
        stack.resetData(DataComponentTypes.CONSUMABLE);
        return true;
    }

    @Override
    public boolean isApplied(@NotNull ItemStack stack) {
        if (BlockingItems.isNativelyConsumable(stack.getType())) return false;
        Consumable consumable = stack.getData(DataComponentTypes.CONSUMABLE);
        return consumable != null && consumable.animation() == ItemUseAnimation.BLOCK;
    }
}

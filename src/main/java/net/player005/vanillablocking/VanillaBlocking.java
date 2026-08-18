package net.player005.vanillablocking;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import org.jetbrains.annotations.NotNull;

public final class VanillaBlocking {

    private static final Consumable BLOCKING_COMPONENT = Consumable.builder().consumeSeconds(Float.MAX_VALUE).animation(ItemUseAnimation.BLOCK).build();

    private VanillaBlocking() {
    }

    /**
     * Items that are consumable on their own (food, potions, ...) cannot be
     * used for blocking - overriding or removing their consumable component
     * would break them.
     */
    public static boolean canReceiveBlockingComponent(@NotNull ItemStack itemStack) {
        return !itemStack.getItem().components().has(DataComponents.CONSUMABLE);
    }

    public static boolean hasBlockingComponent(@NotNull ItemStack itemStack) {
        return canReceiveBlockingComponent(itemStack) && itemStack.getComponents().has(DataComponents.CONSUMABLE);
    }

    public static void addBlockingComponent(@NotNull ItemStack itemStack) {
        if (!canReceiveBlockingComponent(itemStack) || hasBlockingComponent(itemStack)) return;
        itemStack.applyComponents(
                DataComponentPatch.builder().set(DataComponents.CONSUMABLE, BLOCKING_COMPONENT).build()
        );
    }

    public static void removeBlockingComponent(@NotNull ItemStack itemStack) {
        if (!hasBlockingComponent(itemStack)) return;
        itemStack.applyComponents(DataComponentPatch.builder().remove(DataComponents.CONSUMABLE).build());
    }

}

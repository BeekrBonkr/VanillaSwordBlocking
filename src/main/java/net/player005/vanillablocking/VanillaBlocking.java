package net.player005.vanillablocking;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import org.jetbrains.annotations.NotNull;

public final class VanillaBlocking {

    private static final Consumable CONSUMABLE_COMPONENT = Consumable.builder().consumeSeconds(Float.MAX_VALUE).animation(ItemUseAnimation.BLOCK).build();

    private VanillaBlocking() {
    }

    public static boolean isBlockingSword(@NotNull Player player, boolean allowOffhand) {
        if (!player.getUseItem().is(ItemTags.SWORDS)) return false;
        return allowOffhand || player.getUsedItemHand() == InteractionHand.MAIN_HAND;
    }

    public static boolean hasSwordComponents(@NotNull ItemStack itemStack) {
        return itemStack.getComponents().has(DataComponents.CONSUMABLE);
    }

    public static void addSwordComponents(@NotNull ItemStack itemStack) {
        if (!itemStack.is(ItemTags.SWORDS)) return;
        if (hasSwordComponents(itemStack)) return;
        itemStack.applyComponents(
                DataComponentPatch.builder().set(DataComponents.CONSUMABLE, CONSUMABLE_COMPONENT).build()
        );
    }

    public static void removeSwordComponents(@NotNull ItemStack stack) {
        if (!stack.is(ItemTags.SWORDS)) return;
        stack.applyComponents(DataComponentPatch.builder().remove(DataComponents.CONSUMABLE).build());
    }

}

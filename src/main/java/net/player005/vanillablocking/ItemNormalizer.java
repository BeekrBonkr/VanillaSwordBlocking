package net.player005.vanillablocking;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import net.player005.vanillablocking.item.BlockingStrategy;
import net.player005.vanillablocking.ocm.OcmDamageDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the blocking component on exactly the items that should have it, and
 * makes sure it never travels anywhere it would be written to disk.
 * <p>
 * The component is a lie we tell the client. It must therefore only ever
 * exist on items inside a living player's inventory: anything that leaves -
 * into a chest, onto the ground, into a death drop - gets cleaned first, so
 * uninstalling the plugin cannot leave permanently right-clickable swords
 * lying around the world.
 */
public final class ItemNormalizer {

    /** Raw slot of the offhand in a {@link PlayerInventory}. */
    public static final int OFFHAND_SLOT = 40;

    private final BlockingService service;
    private final OcmDamageDisplay ocmDisplay;

    public ItemNormalizer(@NotNull BlockingService service, @NotNull OcmDamageDisplay ocmDisplay) {
        this.service = service;
        this.ocmDisplay = ocmDisplay;
    }

    /**
     * Ensures the item in the given slot has the blocking component exactly
     * when it should, and that its tooltip matches OldCombatMechanics.
     */
    public void normalizeSlot(@NotNull Player player, int slot) {
        normalizeSlot(player, slot, service.context(player));
    }

    private void normalizeSlot(@NotNull Player player, int slot, @NotNull BlockingService.Context context) {
        PlayerInventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getSize()) return;

        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType().isAir()) return;

        boolean changed = normalizeBlocking(context, stack, slot);
        changed |= ocmDisplay.updateItemForPlayer(player, stack);

        if (changed) inventory.setItem(slot, stack);
    }

    /**
     * Applies or removes the blocking component on a single stack.
     *
     * @return whether the stack was modified
     */
    private boolean normalizeBlocking(@NotNull BlockingService.Context context, @NotNull ItemStack stack, int slot) {
        boolean shouldBlock = service.shouldBlockingItemBeActive(context, stack, slot);
        boolean isApplied = service.strategy().isApplied(stack);

        if (shouldBlock == isApplied) return false;
        return shouldBlock
                ? service.strategy().apply(stack)
                : service.strategy().remove(stack);
    }

    /**
     * Normalizes the player's whole inventory.
     */
    public void normalizeInventory(@NotNull Player player) {
        BlockingService.Context context = service.context(player);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            normalizeSlot(player, slot, context);
        }
    }

    /**
     * Normalizes only the slots that can actually block, which is all that
     * changes when a block is interrupted.
     */
    public void normalizeHeldSlots(@NotNull Player player) {
        BlockingService.Context context = service.context(player);
        for (int slot = 0; slot <= 8; slot++) {
            normalizeSlot(player, slot, context);
        }
        normalizeSlot(player, OFFHAND_SLOT, context);
    }

    /**
     * Removes everything this plugin adds from a stack.
     *
     * @return whether the stack was modified, so the caller can write it back
     */
    public boolean strip(@Nullable ItemStack stack) {
        return strip(stack, service.strategy());
    }

    /**
     * Strips using a specific strategy - needed right after a config reload
     * swapped the strategy, when items still carry the old one's component.
     */
    public boolean strip(@Nullable ItemStack stack, @NotNull BlockingStrategy strategy) {
        return strip(stack, strategy, 0);
    }

    private boolean strip(@Nullable ItemStack stack, @NotNull BlockingStrategy strategy, int depth) {
        if (stack == null || stack.getType().isAir()) return false;

        boolean changed = strategy.remove(stack);
        changed |= ocmDisplay.forceRemove(stack);
        changed |= stripNested(stack, strategy, depth);
        return changed;
    }

    /**
     * Items can hold other items: a sword tucked into a bundle, or inside a
     * shulker box that was picked up. Those nested stacks are written to disk
     * with their container, so they have to be cleaned too.
     *
     * @param depth guards against a pathological chain of nested containers
     */
    private boolean stripNested(@NotNull ItemStack stack, @NotNull BlockingStrategy strategy, int depth) {
        // An item with no component patch cannot be holding anything, and
        // this runs for every item that drops anywhere on the server.
        if (depth >= 3 || !stack.hasItemMeta()) return false;

        boolean changed = false;

        BundleContents bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> contents = new ArrayList<>(bundle.contents());
            if (stripAll(contents, strategy, depth)) {
                stack.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(contents));
                changed = true;
            }
        }

        ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
        if (container != null) {
            List<ItemStack> contents = new ArrayList<>(container.contents());
            if (stripAll(contents, strategy, depth)) {
                stack.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(contents));
                changed = true;
            }
        }

        return changed;
    }

    private boolean stripAll(@NotNull List<ItemStack> contents, @NotNull BlockingStrategy strategy, int depth) {
        boolean changed = false;
        for (ItemStack nested : contents) {
            changed |= strip(nested, strategy, depth + 1);
        }
        return changed;
    }

    /**
     * Strips every item in an inventory - used for containers an item may
     * have been moved into, and on shutdown.
     *
     * @return how many stacks were cleaned
     */
    public int stripInventory(@Nullable Inventory inventory) {
        return stripInventory(inventory, service.strategy());
    }

    public int stripInventory(@Nullable Inventory inventory, @NotNull BlockingStrategy strategy) {
        if (inventory == null) return 0;
        int cleaned = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (strip(stack, strategy)) {
                inventory.setItem(slot, stack);
                cleaned++;
            }
        }
        return cleaned;
    }

    /**
     * Strips a player's whole inventory plus the two places
     * {@link PlayerInventory} does not cover: the stack on the cursor and
     * whatever is in the open window (the crafting grid, or a container).
     * The server drops both when the player disconnects.
     */
    public int stripPlayer(@NotNull Player player) {
        return stripPlayer(player, service.strategy());
    }

    public int stripPlayer(@NotNull Player player, @NotNull BlockingStrategy strategy) {
        int cleaned = stripInventory(player.getInventory(), strategy);

        ItemStack cursor = player.getItemOnCursor();
        if (strip(cursor, strategy)) {
            player.setItemOnCursor(cursor);
            cleaned++;
        }

        try {
            cleaned += stripInventory(player.getOpenInventory().getTopInventory(), strategy);
        } catch (Exception ignored) {
            // No open window, or a window that cannot be read right now.
        }
        return cleaned;
    }
}

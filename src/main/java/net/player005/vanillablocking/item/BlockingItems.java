package net.player005.vanillablocking.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides which items may be turned into blocking items, and why not.
 * <p>
 * Giving an item the blocking component overrides what right-clicking it
 * does, so anything with its own use behaviour would be broken by it: a bow
 * would raise instead of draw, a spyglass would never zoom. Those items are
 * refused outright unless the server owner opts into
 * {@code restrictions.allow-unsafe-items}.
 */
public final class BlockingItems {

    /**
     * Why an item cannot (or should not) be used for blocking.
     */
    public enum Problem {
        /** Fine to use. */
        NONE(false),
        /** Food, potions, milk - already consumable, we would break eating it. */
        CONSUMABLE(true),
        /** Bows, tridents, spyglasses, ... - right-click already does something. */
        USE_CONFLICT(true),
        /** Armour and elytra: right-click equips them. */
        EQUIPPABLE(true),
        /** Stackable items do not stack any more once they carry our component. */
        STACKABLE(false),
        /** Placing a block takes priority over blocking, so it would rarely work. */
        PLACEABLE(false),
        /** Not an obtainable item at all. */
        NOT_AN_ITEM(true);

        private final boolean fatal;

        Problem(boolean fatal) {
            this.fatal = fatal;
        }

        /**
         * Whether using the item anyway would break the item itself, rather
         * than merely being a bit odd.
         */
        public boolean isFatal() {
            return fatal;
        }
    }

    /**
     * Items whose right-click behaviour we would override. Component-based
     * checks catch food and armour; these are the ones whose use behaviour is
     * hard-coded in the item class instead.
     */
    private static final Set<Material> USE_CONFLICTS = EnumSet.of(
            Material.BOW,
            Material.CROSSBOW,
            Material.TRIDENT,
            Material.SHIELD,
            Material.SPYGLASS,
            Material.GOAT_HORN,
            Material.FISHING_ROD,
            Material.BUNDLE,
            Material.ENDER_PEARL,
            Material.ENDER_EYE,
            Material.EGG,
            Material.SNOWBALL,
            Material.FIREWORK_ROCKET,
            Material.WIND_CHARGE,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION,
            Material.EXPERIENCE_BOTTLE,
            Material.WRITABLE_BOOK,
            Material.WRITTEN_BOOK,
            Material.KNOWLEDGE_BOOK,
            Material.LEAD,
            Material.MAP,
            Material.BUCKET,
            Material.WATER_BUCKET,
            Material.LAVA_BUCKET,
            Material.MILK_BUCKET,
            Material.POWDER_SNOW_BUCKET,
            Material.FIREWORK_STAR,
            Material.ARMOR_STAND,
            Material.END_CRYSTAL,
            Material.FIRE_CHARGE,
            Material.FLINT_AND_STEEL,
            Material.SHEARS,
            Material.BRUSH,
            Material.MACE
    );

    /** Cache for the (registry) lookups below - they are hit per inventory slot. */
    private static final Map<Material, Boolean> NATIVELY_CONSUMABLE = new EnumMap<>(Material.class);
    private static final Map<Material, Boolean> NATIVELY_EQUIPPABLE = new EnumMap<>(Material.class);

    private BlockingItems() {
    }

    /**
     * Whether the item type is consumable on its own (food, potions, milk).
     * Such items can never receive the blocking component: overriding their
     * consumable component would stop them being edible.
     */
    public static boolean isNativelyConsumable(@NotNull Material material) {
        Boolean cached = NATIVELY_CONSUMABLE.get(material);
        if (cached != null) return cached;
        boolean result = hasDefaultData(material, DataComponentTypes.CONSUMABLE);
        NATIVELY_CONSUMABLE.put(material, result);
        return result;
    }

    private static boolean isNativelyEquippable(@NotNull Material material) {
        Boolean cached = NATIVELY_EQUIPPABLE.get(material);
        if (cached != null) return cached;
        boolean result = hasDefaultData(material, DataComponentTypes.EQUIPPABLE);
        NATIVELY_EQUIPPABLE.put(material, result);
        return result;
    }

    private static boolean hasDefaultData(@NotNull Material material, @NotNull io.papermc.paper.datacomponent.DataComponentType type) {
        if (!material.isItem()) return false;
        try {
            ItemType itemType = material.asItemType();
            return itemType != null && itemType.hasDefaultData(type);
        } catch (Exception exception) {
            // Unknown/legacy material - treat as "no default component".
            return false;
        }
    }

    /**
     * Checks whether an item configured in {@code blockable-items} can safely
     * be used for blocking.
     */
    public static @NotNull Problem inspect(@NotNull Material material) {
        if (!material.isItem()) return Problem.NOT_AN_ITEM;
        if (isNativelyConsumable(material)) return Problem.CONSUMABLE;
        if (USE_CONFLICTS.contains(material)) return Problem.USE_CONFLICT;
        if (isNativelyEquippable(material)) return Problem.EQUIPPABLE;
        if (material.isBlock()) return Problem.PLACEABLE;
        if (material.getMaxStackSize() > 1) return Problem.STACKABLE;
        return Problem.NONE;
    }

    /**
     * A human-readable reason for the problem, used in the startup warning.
     */
    public static @NotNull String describe(@NotNull Problem problem) {
        return switch (problem) {
            case NONE -> "no problem";
            case CONSUMABLE -> "it is food or a drink, so it is already consumable and giving it the blocking component would stop it being consumed";
            case USE_CONFLICT -> "right-clicking it already does something else, which blocking would override";
            case EQUIPPABLE -> "right-clicking it equips it";
            case STACKABLE -> "it stacks, and stacks with different components no longer merge";
            case PLACEABLE -> "it places a block on right-click, which takes priority over blocking";
            case NOT_AN_ITEM -> "it is not an obtainable item";
        };
    }
}

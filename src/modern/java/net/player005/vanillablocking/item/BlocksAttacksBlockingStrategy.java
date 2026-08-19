package net.player005.vanillablocking.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.datacomponent.item.blocksattacks.ItemDamageFunction;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.player005.vanillablocking.BlockingFormula;
import net.player005.vanillablocking.PluginConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Blocking through the native {@code minecraft:blocks_attacks} component,
 * added in 1.21.5. The client predicts the block itself, so there is no
 * server round trip and the reduction is applied by vanilla rather than by
 * an event handler.
 * <p>
 * The consumable component is applied alongside it: blocks_attacks decides
 * what a raised item does, the consumable component is what makes
 * right-clicking a sword raise it in the first place.
 * <p>
 * Compiled against the 1.21.5 API in a separate source set and only loaded
 * once {@link BlockingStrategies} has confirmed the component exists.
 */
public final class BlocksAttacksBlockingStrategy implements BlockingStrategy {

    private final ConsumableBlockingStrategy animation = new ConsumableBlockingStrategy();

    /** Cache of "does this item type block natively on its own" (shields). */
    private final Map<Material, Boolean> nativeByDefault = new EnumMap<>(Material.class);

    private volatile BlocksAttacks fallback = legacyDefault();
    private volatile Map<Material, BlocksAttacks> perItem = Map.of();

    @Override
    public @NotNull String id() {
        return "blocks-attacks";
    }

    @Override
    public boolean nativeDamageReduction() {
        return true;
    }

    @Override
    public void configure(@NotNull PluginConfig config) {
        fallback = build(-1, config);

        Map<Material, BlocksAttacks> overrides = new EnumMap<>(Material.class);
        for (Material material : config.blockableItems()) {
            double override = config.perItemMultiplier(material);
            if (override >= 0) overrides.put(material, build(override, config));
        }
        perItem = Map.copyOf(overrides);
    }

    /**
     * The plain 1.8.9 component, used until {@link #configure} runs.
     */
    private static @NotNull BlocksAttacks legacyDefault() {
        return BlocksAttacks.blocksAttacks()
                .disableCooldownScale(0f)
                .addDamageReduction(DamageReduction.damageReduction()
                        .horizontalBlockingAngle(180f)
                        .base(BlockingFormula.nativeBase(BlockingFormula.Formula.LEGACY, 0.5))
                        .factor(BlockingFormula.nativeFactor(BlockingFormula.Formula.LEGACY, 0.5))
                        .build())
                .bypassedBy(DamageTypeTagKeys.BYPASSES_ARMOR)
                .build();
    }

    /**
     * Builds the component for one damage-reduction setting.
     *
     * @param overrideMultiplier a per-item fraction of damage still taken, or
     *                           a negative value to use the global formula
     */
    private static @NotNull BlocksAttacks build(double overrideMultiplier, @NotNull PluginConfig config) {
        BlockingFormula.Formula formula = overrideMultiplier >= 0
                ? BlockingFormula.Formula.MULTIPLIER
                : config.formula();
        double multiplier = overrideMultiplier >= 0 ? overrideMultiplier : config.multiplier();

        DamageReduction reduction = DamageReduction.damageReduction()
                .horizontalBlockingAngle((float) config.blockingAngle())
                .base(BlockingFormula.nativeBase(formula, multiplier))
                .factor(BlockingFormula.nativeFactor(formula, multiplier))
                .build();

        BlocksAttacks.Builder builder = BlocksAttacks.blocksAttacks()
                .blockDelaySeconds(config.blockDelayTicks() / 20f)
                // 1.8.9 had no shield-disabling axe mechanic; 0 turns it off.
                .disableCooldownScale(0f)
                .addDamageReduction(reduction)
                .itemDamage(ItemDamageFunction.itemDamageFunction()
                        .threshold(0f)
                        .base(config.durabilityCost())
                        .factor(0f)
                        .build())
                // The 1.8.9 rule: anything armour does not reduce cannot be
                // blocked either.
                .bypassedBy(DamageTypeTagKeys.BYPASSES_ARMOR);

        String sound = config.feedbackSound();
        if (!sound.isBlank()) {
            try {
                builder.blockSound(Key.key(sound));
            } catch (Exception ignored) {
                // An invalid sound key just means no block sound.
            }
        }

        return builder.build();
    }

    @Override
    public boolean apply(@NotNull ItemStack stack) {
        boolean changed = animation.apply(stack);
        if (!isApplied(stack)) {
            stack.setData(DataComponentTypes.BLOCKS_ATTACKS, componentFor(stack.getType()));
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean remove(@NotNull ItemStack stack) {
        boolean changed = animation.remove(stack);
        if (isApplied(stack)) {
            stack.resetData(DataComponentTypes.BLOCKS_ATTACKS);
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean isApplied(@NotNull ItemStack stack) {
        if (blocksAttacksByDefault(stack.getType())) return false;
        return stack.hasData(DataComponentTypes.BLOCKS_ATTACKS);
    }

    @Override
    public boolean isNativeBlocker(@NotNull ItemStack stack) {
        if (stack.getType() == Material.SHIELD) return true;
        return stack.hasData(DataComponentTypes.BLOCKS_ATTACKS);
    }

    private @NotNull BlocksAttacks componentFor(@NotNull Material material) {
        BlocksAttacks override = perItem.get(material);
        return override != null ? override : fallback;
    }

    /**
     * Shields carry the component out of the box, so its presence there says
     * nothing about us.
     */
    private boolean blocksAttacksByDefault(@NotNull Material material) {
        Boolean cached = nativeByDefault.get(material);
        if (cached != null) return cached;

        boolean result;
        try {
            ItemType type = material.asItemType();
            result = type != null && type.hasDefaultData(DataComponentTypes.BLOCKS_ATTACKS);
        } catch (Exception exception) {
            result = false;
        }
        nativeByDefault.put(material, result);
        return result;
    }
}

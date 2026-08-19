package net.player005.vanillablocking.command;

import net.kyori.adventure.text.Component;
import net.player005.vanillablocking.BlockingService;
import net.player005.vanillablocking.ItemNormalizer;
import net.player005.vanillablocking.Messages;
import net.player005.vanillablocking.VanillaBlockingPaper;
import net.player005.vanillablocking.ocm.OcmConfigReader;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code /vanillablocking} (alias {@code /vsb}).
 */
public final class VanillaBlockingCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBCOMMANDS = List.of("reload", "cleanup", "refresh", "debug");

    private final VanillaBlockingPaper plugin;
    private final BlockingService service;
    private final ItemNormalizer normalizer;
    private final OcmConfigReader ocm;
    private final Messages messages;

    public VanillaBlockingCommand(@NotNull VanillaBlockingPaper plugin,
                                  @NotNull BlockingService service,
                                  @NotNull ItemNormalizer normalizer,
                                  @NotNull OcmConfigReader ocm,
                                  @NotNull Messages messages) {
        this.plugin = plugin;
        this.service = service;
        this.normalizer = normalizer;
        this.ocm = ocm;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "toggle" -> {
                toggle(sender);
                return true;
            }
            case "reload" -> {
                if (requireAdmin(sender)) reload(sender);
                return true;
            }
            case "refresh" -> {
                if (requireAdmin(sender)) refresh(sender);
                return true;
            }
            case "cleanup" -> {
                if (requireAdmin(sender)) cleanup(sender, args.length > 1 ? args[1] : null);
                return true;
            }
            case "debug" -> {
                if (requireAdmin(sender)) debug(sender, args.length > 1 ? args[1] : null);
                return true;
            }
            default -> {
                sender.sendMessage(messages.get("usage", Messages.placeholder("label", label)));
                return true;
            }
        }
    }

    private boolean requireAdmin(@NotNull CommandSender sender) {
        if (sender.hasPermission("vanillablocking.admin")) return true;
        sender.sendMessage(messages.get("no-permission"));
        return false;
    }

    private void reload(@NotNull CommandSender sender) {
        if (plugin.reload()) {
            sender.sendMessage(messages.get("reload.success"));
        } else {
            sender.sendMessage(messages.get("reload.failed"));
        }
    }

    private void refresh(@NotNull CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> normalizer.normalizeInventory(player), null);
        }
        sender.sendMessage(messages.get("refresh.done",
                Messages.placeholder("players", Bukkit.getOnlinePlayers().size())));
    }

    private void toggle(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("players-only"));
            return;
        }
        if (!player.hasPermission("vanillablocking.toggle")) {
            sender.sendMessage(messages.get("no-permission"));
            return;
        }

        boolean enabled = service.toggle(player);
        normalizer.normalizeInventory(player);
        sender.sendMessage(messages.get(enabled ? "toggle.enabled" : "toggle.disabled"));
    }

    /**
     * Strips leftover blocking components from everything loaded in a world:
     * containers, ground items, item frames and entity equipment. Meant for
     * worlds polluted by an older version of the plugin that could leak the
     * component into chests.
     */
    private void cleanup(@NotNull CommandSender sender, @Nullable String worldName) {
        List<World> worlds = new ArrayList<>();
        if (worldName == null || worldName.equalsIgnoreCase("all")) {
            worlds.addAll(Bukkit.getWorlds());
        } else {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sender.sendMessage(messages.get("cleanup.unknown-world", Messages.placeholder("world", worldName)));
                return;
            }
            worlds.add(world);
        }

        List<Chunk> chunks = new ArrayList<>();
        for (World world : worlds) {
            chunks.addAll(List.of(world.getLoadedChunks()));
        }

        sender.sendMessage(messages.get("cleanup.started", Messages.placeholder("chunks", chunks.size())));
        if (chunks.isEmpty()) {
            sender.sendMessage(messages.get("cleanup.finished", Messages.placeholder("cleaned", 0)));
            return;
        }

        AtomicInteger cleaned = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(chunks.size());

        for (Chunk chunk : chunks) {
            // Region-scheduled so this is safe on Folia, where each chunk
            // belongs to a thread that owns it.
            Bukkit.getRegionScheduler().execute(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> {
                try {
                    cleaned.addAndGet(cleanChunk(chunk));
                } catch (Exception exception) {
                    plugin.getSLF4JLogger().warn("Failed to clean chunk {},{} in {}.",
                            chunk.getX(), chunk.getZ(), chunk.getWorld().getName(), exception);
                }
                if (remaining.decrementAndGet() == 0) {
                    // Chunks are cleaned on their own region threads, so the
                    // summary goes back through the global one.
                    Bukkit.getGlobalRegionScheduler().execute(plugin, () ->
                            sender.sendMessage(messages.get("cleanup.finished", Messages.placeholder("cleaned", cleaned.get()))));
                }
            });
        }
    }

    private int cleanChunk(@NotNull Chunk chunk) {
        int cleaned = 0;

        for (BlockState state : chunk.getTileEntities(false)) {
            if (state instanceof Container container) {
                cleaned += normalizer.stripInventory(container.getInventory());
            }
        }

        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) continue; // handled while they are online

            if (entity instanceof Item item) {
                ItemStack stack = item.getItemStack();
                if (normalizer.strip(stack)) {
                    item.setItemStack(stack);
                    cleaned++;
                }
                continue;
            }

            if (entity instanceof ItemFrame frame) {
                ItemStack stack = frame.getItem();
                if (normalizer.strip(stack)) {
                    frame.setItem(stack);
                    cleaned++;
                }
                continue;
            }

            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                EntityEquipment equipment = living.getEquipment();
                if (equipment == null) continue;
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    try {
                        ItemStack stack = equipment.getItem(slot);
                        if (normalizer.strip(stack)) {
                            equipment.setItem(slot, stack);
                            cleaned++;
                        }
                    } catch (IllegalArgumentException | UnsupportedOperationException unsupportedSlot) {
                        // Not every entity has every slot.
                    }
                }
            }
        }

        return cleaned;
    }

    private void debug(@NotNull CommandSender sender, @Nullable String targetName) {
        Player target = targetName != null ? Bukkit.getPlayerExact(targetName)
                : (sender instanceof Player self ? self : null);

        sender.sendMessage(Component.text("VanillaSwordBlocking " + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Component.text("  strategy: " + service.strategy().id()
                + " (native reduction: " + service.strategy().nativeDamageReduction() + ")"));
        sender.sendMessage(Component.text("  server: " + Bukkit.getMinecraftVersion()
                + ", blocks_attacks available: " + net.player005.vanillablocking.item.BlockingStrategies.supportsBlocksAttacks()));
        sender.sendMessage(Component.text("  blockable items: " + service.config().blockableItems().size()
                + ", cause rule: " + service.config().causeRule().name().toLowerCase(Locale.ROOT).replace('_', '-')));
        sender.sendMessage(Component.text("  OldCombatMechanics: " + (ocm.isPresent()
                ? "installed (sword-blocking module: " + ocm.isSwordBlockingModuleEnabled()
                + ", attack cooldown disabled: " + ocm.isAttackCooldownDisabled() + ")"
                : "not installed")));

        if (target == null) {
            sender.sendMessage(Component.text("  no player to inspect"));
            return;
        }

        sender.sendMessage(Component.text("  " + target.getName() + ": may block: " + service.mayBlock(target)
                + ", blocking now: " + service.isBlocking(target)
                + ", toggled off: " + service.isToggledOff(target)
                + ", shield conflict: " + service.hasShieldConflict(target)
                + ", block-hit cooldown: " + service.isOnBlockHitCooldown(target.getUniqueId())));
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("vanillablocking.toggle")) options.add("toggle");
            if (sender.hasPermission("vanillablocking.admin")) options.addAll(ADMIN_SUBCOMMANDS);
            return filter(options, args[0]);
        }

        if (args.length == 2 && sender.hasPermission("vanillablocking.admin")) {
            if (args[0].equalsIgnoreCase("cleanup")) {
                List<String> worlds = new ArrayList<>();
                worlds.add("all");
                Bukkit.getWorlds().forEach(world -> worlds.add(world.getName()));
                return filter(worlds, args[1]);
            }
            if (args[0].equalsIgnoreCase("debug")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
        }

        return List.of();
    }

    private static @NotNull List<String> filter(@NotNull List<String> options, @NotNull String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

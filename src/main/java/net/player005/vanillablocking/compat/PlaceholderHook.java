package net.player005.vanillablocking.compat;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.player005.vanillablocking.BlockingService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes blocking state to PlaceholderAPI:
 * <ul>
 *     <li>{@code %vanillablocking_blocking%} - whether the player is blocking right now</li>
 *     <li>{@code %vanillablocking_allowed%} - whether they may block here at all</li>
 *     <li>{@code %vanillablocking_toggled%} - whether they turned blocking off themselves</li>
 * </ul>
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final Plugin plugin;
    private final BlockingService service;

    public PlaceholderHook(@NotNull Plugin plugin, @NotNull BlockingService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vanillablocking";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offlinePlayer, @NotNull String params) {
        if (!(offlinePlayer instanceof Player player)) return null;

        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "blocking" -> String.valueOf(service.isBlocking(player));
            case "allowed" -> String.valueOf(service.mayBlock(player));
            case "toggled" -> String.valueOf(service.isToggledOff(player));
            default -> null;
        };
    }
}

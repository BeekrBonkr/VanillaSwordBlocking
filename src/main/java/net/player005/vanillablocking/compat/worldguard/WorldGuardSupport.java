package net.player005.vanillablocking.compat.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.player005.vanillablocking.compat.WorldGuardHook;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The half of the WorldGuard hook that actually names WorldGuard types.
 * <p>
 * Only ever loaded by {@link WorldGuardHook} after it has confirmed
 * WorldGuard is installed, so servers without it never resolve these classes.
 */
public final class WorldGuardSupport implements WorldGuardHook.RegionCheck {

    private static final String FLAG_NAME = "sword-blocking";

    private static @Nullable StateFlag flag;

    /**
     * Adds the {@code sword-blocking} state flag, defaulting to allow.
     *
     * @throws IllegalStateException when a flag of that name exists with a
     *                               different type
     */
    public static void registerFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag created = new StateFlag(FLAG_NAME, true);
            registry.register(created);
            flag = created;
        } catch (FlagConflictException conflict) {
            // Another plugin, or a previous load of this one, got there first.
            if (registry.get(FLAG_NAME) instanceof StateFlag existing) {
                flag = existing;
                return;
            }
            throw new IllegalStateException("A WorldGuard flag named '" + FLAG_NAME
                    + "' already exists with a different type", conflict);
        }
    }

    @Override
    public boolean allowsBlocking(@NotNull Player player) {
        StateFlag current = flag;
        if (current == null) return true;

        try {
            LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
            if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, local.getWorld())) {
                return true;
            }

            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            return query.testState(BukkitAdapter.adapt(player.getLocation()), local, current);
        } catch (Throwable throwable) {
            // Never let a WorldGuard hiccup stop combat from working.
            return true;
        }
    }
}

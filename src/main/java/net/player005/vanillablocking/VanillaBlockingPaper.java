package net.player005.vanillablocking;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.player005.vanillablocking.api.VanillaBlockingApi;
import net.player005.vanillablocking.command.VanillaBlockingCommand;
import net.player005.vanillablocking.compat.WorldGuardHook;
import net.player005.vanillablocking.item.BlockingStrategies;
import net.player005.vanillablocking.item.BlockingStrategy;
import net.player005.vanillablocking.listener.CombatListener;
import net.player005.vanillablocking.listener.ItemLifecycleListener;
import net.player005.vanillablocking.listener.RegionListener;
import net.player005.vanillablocking.ocm.OcmConfigReader;
import net.player005.vanillablocking.ocm.OcmDamageDisplay;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Brings back 1.8-style sword blocking.
 */
public class VanillaBlockingPaper extends JavaPlugin {

    /**
     * bStats id for this plugin. Register the plugin at bstats.org and put
     * the id here to turn metrics on; 0 leaves them off entirely.
     */
    private static final int BSTATS_PLUGIN_ID = 0;

    private PluginConfig config;
    private Messages messages;
    private BlockingService service;
    private ItemNormalizer normalizer;
    private BlockingTracker tracker;

    private OcmConfigReader ocmReader;
    private OcmDamageDisplay ocmDisplay;
    private WorldGuardHook worldGuard;

    private @Nullable ScheduledTask ocmWatchTask;
    private @Nullable Metrics metrics;

    @Override
    public void onLoad() {
        // WorldGuard only accepts new flags before it enables.
        WorldGuardHook.registerFlag(getSLF4JLogger());
    }

    @Override
    public void onEnable() {
        config = new PluginConfig(this);
        config.load();

        messages = new Messages(this);
        messages.load();

        worldGuard = new WorldGuardHook();
        worldGuard.enable(getSLF4JLogger());

        ocmReader = new OcmConfigReader();
        ocmReader.reload();
        ocmDisplay = new OcmDamageDisplay(this, ocmReader);
        ocmDisplay.setEnabled(config.ocmTooltipCompat());

        BlockingStrategy strategy = BlockingStrategies.create(config, getSLF4JLogger());
        strategy.configure(config);
        getSLF4JLogger().info("Using the '{}' blocking strategy.", strategy.id());

        service = new BlockingService(this, config, strategy, worldGuard);
        normalizer = new ItemNormalizer(service, ocmDisplay);
        tracker = new BlockingTracker(this, service);

        registerListeners();
        registerCommand();
        registerPlaceholders();
        warnAboutOcmConflicts();
        startOcmWatcher();
        startUpdateChecker();
        startMetrics();
        VanillaBlockingApi.install(service, tracker);

        // Handles being enabled on a running server (e.g. via a plugin
        // manager or /reload)
        refreshAllPlayers();
    }

    @Override
    public void onDisable() {
        if (ocmWatchTask != null) ocmWatchTask.cancel();
        if (metrics != null) metrics.shutdown();

        // On a normal shutdown players quit before plugins disable, so this
        // only matters for runtime disables (e.g. plugin managers, /reload).
        if (normalizer != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (tracker != null) tracker.stop(player);
                try {
                    normalizer.stripPlayer(player);
                } catch (Exception ignored) {
                }
            }
        }
        VanillaBlockingApi.uninstall();
        if (service != null) service.clearAllCooldowns();
        if (worldGuard != null) worldGuard.disable();
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new CombatListener(this, service, normalizer, tracker, messages), this);
        getServer().getPluginManager().registerEvents(
                new ItemLifecycleListener(this, service, normalizer, tracker), this);

        // A PlayerMoveEvent handler is only worth its dispatch cost when
        // region control can actually change the answer.
        if (config.respectWorldGuard() && Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            getServer().getPluginManager().registerEvents(new RegionListener(service, normalizer), this);
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("vanillablocking");
        if (command == null) {
            getSLF4JLogger().error("The /vanillablocking command is missing from plugin.yml.");
            return;
        }
        VanillaBlockingCommand executor = new VanillaBlockingCommand(this, service, normalizer, ocmReader, messages);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * Applies config and lang changes at runtime.
     *
     * @return false when config.yml could not be parsed, in which case the
     * previous settings stay in effect
     */
    public boolean reload() {
        boolean ok = config.load();
        messages.load();
        ocmReader.reload();
        ocmDisplay.setEnabled(config.ocmTooltipCompat());

        BlockingStrategy previous = service.strategy();
        BlockingStrategy strategy = BlockingStrategies.create(config, getSLF4JLogger());
        strategy.configure(config);
        service.strategy(strategy);

        warnAboutOcmConflicts();
        startOcmWatcher();
        tracker.refreshSlowdowns();

        // Switching strategies leaves the old one's component on every item
        // in every inventory, and the new strategy would not recognise it.
        boolean switched = !previous.id().equals(strategy.id());

        for (Player player : getServer().getOnlinePlayers()) {
            player.getScheduler().run(this, task -> {
                if (switched) normalizer.stripPlayer(player, previous);
                normalizer.normalizeInventory(player);
            }, null);
        }
        return ok;
    }

    /**
     * OldCombatMechanics has a sword-blocking module of its own that fakes
     * blocking by swapping the sword for a shield. Two plugins fighting over
     * the same right-click is worth shouting about.
     */
    private void warnAboutOcmConflicts() {
        if (!ocmReader.isPresent()) return;

        if (ocmReader.isSwordBlockingModuleEnabled()) {
            getSLF4JLogger().warn("""
                    OldCombatMechanics' own sword-blocking module is enabled.
                    Both plugins will fight over the same right-click, and blocking will behave unpredictably.
                    Turn off 'sword-blocking' in OldCombatMechanics' config.yml, or turn this plugin off.""");
        }

        if (!ocmReader.isAttackCooldownDisabled()) {
            getSLF4JLogger().warn("OldCombatMechanics is installed but its attack cooldown module is off. "
                    + "Block-hitting is a 1.8 technique and feels wrong with the 1.9 attack cooldown - "
                    + "consider enabling 'disable-attack-cooldown'.");
        }
    }

    /**
     * OCM has no reload API and no reload event, so the only way to notice
     * an admin running its reload command is to watch the file.
     */
    private void startOcmWatcher() {
        if (ocmWatchTask != null) {
            ocmWatchTask.cancel();
            ocmWatchTask = null;
        }
        if (!config.ocmWatchConfig() || !ocmReader.isPresent()) return;

        long seconds = config.ocmWatchIntervalSeconds();
        ocmWatchTask = Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> {
            // File I/O off the server threads, then hand the player work back
            // to the server so Folia's region rules are respected.
            if (!ocmReader.reloadIfChanged()) return;
            getSLF4JLogger().info("OldCombatMechanics' config changed - reloaded its settings.");
            Bukkit.getGlobalRegionScheduler().execute(this, this::refreshAllPlayers);
        }, seconds, seconds, TimeUnit.SECONDS);
    }

    /**
     * Re-applies blocking items for everyone, each on their own scheduler.
     */
    private void refreshAllPlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            player.getScheduler().run(this, task -> normalizer.normalizeInventory(player), null);
        }
    }

    private void startUpdateChecker() {
        if (!config.updateCheckerEnabled()) return;

        UpdateChecker checker = new UpdateChecker(getSLF4JLogger(),
                config.updateCheckerProject(), getPluginMeta().getVersion());
        Bukkit.getAsyncScheduler().runNow(this, task -> checker.check());
    }

    private void startMetrics() {
        if (!config.metricsEnabled() || BSTATS_PLUGIN_ID == 0) return;

        metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("blocking_strategy", () -> service.strategy().id()));
        metrics.addCustomChart(new SimplePie("damage_formula", () -> config.formula().name().toLowerCase(java.util.Locale.ROOT)));
        metrics.addCustomChart(new SimplePie("block_hitting", () -> String.valueOf(config.blockHittingEnabled())));
        metrics.addCustomChart(new SimplePie("oldcombatmechanics", () -> String.valueOf(ocmReader.isPresent())));
    }

    /**
     * Registers the PlaceholderAPI expansion, if that plugin is installed.
     * <p>
     * Loaded and called by name for the same reason as the WorldGuard hook:
     * naming PlaceholderHook here would make this class depend on
     * PlaceholderAPI being on the class path.
     */
    private void registerPlaceholders() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;

        try {
            Class<?> hook = Class.forName("net.player005.vanillablocking.compat.PlaceholderHook");
            Object expansion = hook.getDeclaredConstructor(org.bukkit.plugin.Plugin.class, BlockingService.class)
                    .newInstance(this, service);
            expansion.getClass().getMethod("register").invoke(expansion);
            getSLF4JLogger().info("Registered PlaceholderAPI placeholders.");
        } catch (Throwable throwable) {
            getSLF4JLogger().warn("Could not register PlaceholderAPI placeholders.", throwable);
        }
    }
}

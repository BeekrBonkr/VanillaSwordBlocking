# Vanilla Sword Blocking

This is a very simple plugin that **brings back the old sword blocking mechanichs**, allowing You to block with your sword to reduce taken damage like in 1.8.9. <br>
The best part about it: It's **server-only**, so Players don't even need to install a mod on their client for this - just add the plugin to the server and it works for everyone!

The plugin **only** handles sword blocking - install it alongside a plugin like [OldCombatMechanics](https://github.com/kernitus/BukkitOldCombatMechanics) to restore the rest of 1.8.9 combat (attack cooldown removal, old damage values, no sweep attacks, ...).

## 1.8.9 accuracy

- Uses the exact 1.8.9 blocking formula: `damage = (damage + 1) / 2`, applied before armor (a simple percentage multiplier is available as an alternative).
- Only reduces damage types that were blockable in 1.8.9. The rule 1.8.9 used was "anything armor reduces", and the plugin works that out per hit rather than from a hand-written list, so datapack and plugin damage types are handled correctly too.
- Blocks attacks from **every** direction, as 1.8.9 did (modern shields only block a frontal arc). Configurable with `blocking-angle`.
- **Block-hitting** works like in 1.8.9: attacking never interrupts your block. Admins can nerf outgoing block-hit damage or disable block-hitting entirely (attacking then interrupts the block for a configurable number of ticks).
- Blocking only works with the main hand by default, since 1.8.9 had no offhand (configurable).
- Holding a shield in the offhand disables sword blocking by default, so right-click raises the shield as usual (configurable - set `allow-with-shield: true` to let the sword win instead).
- Configurable list of items that can block (`blockable-items`) - supports item tags like `#minecraft:swords` (the 1.8.9 default) and single items, including datapack-added ones. Items that blocking would break (bows, tridents, food, armor, ...) are refused with an explanation in the console.

Every option's comment in `config.yml` notes the authentic 1.8.9 value, so a stock config is a faithful 1.8.9 setup.

## How blocking is implemented

The plugin picks the best mechanism your server supports (`strategy: auto`):

| Server | Strategy | What happens |
| --- | --- | --- |
| 1.21.5+ | `blocks-attacks` | Minecraft's own `minecraft:blocks_attacks` item component. The **client predicts the block itself**, so there is no round trip on every hit, and the 1.8.9 formula is expressed natively as `base = -0.5, factor = 0.5`. |
| 1.21.4 | `consumable` | The `minecraft:consumable` component gives the sword the "block" use animation, and the plugin reduces the damage in an event handler. |

Either way the component only ever exists on an item a player is currently holding: it is stripped again before an item can reach a chest, the ground, a death drop or the player's disk data, so uninstalling the plugin never leaves modified items behind. `/vsb cleanup` scans loaded chunks for anything an older version may have leaked.

## Configuration

Everything is configurable in `plugins/vanilla-sword-blocking/config.yml`, and all player-facing text lives in `lang.yml` (MiniMessage formatting). The config is versioned and **updates itself automatically** when new options are added (your old file is backed up first).

Beyond the 1.8.9 options there are knobs for servers that want something other than pure 1.8.9: `max-reduction`, per-item reduction overrides, `block-delay-ticks` (defeats reaction-blocking macros), `durability-cost`, `movement-speed-multiplier`, knockback reduction, and optional sound/particle/action-bar feedback on a blocked hit.

### Commands and permissions

| Command | Permission | What it does |
| --- | --- | --- |
| `/vsb reload` | `vanillablocking.admin` | Reload `config.yml` and `lang.yml` |
| `/vsb refresh` | `vanillablocking.admin` | Re-apply blocking items to every online player |
| `/vsb cleanup [world\|all]` | `vanillablocking.admin` | Strip leftover blocking components from loaded chunks |
| `/vsb debug [player]` | `vanillablocking.admin` | Print the active strategy, OCM status and why a player can or cannot block |
| `/vsb toggle` | `vanillablocking.toggle` | Let a player turn sword blocking off for themselves |

`vanillablocking.block` (default: everyone) gates blocking itself, and only takes effect when `restrictions.require-permission` is on.

## Compatibility

- **OldCombatMechanics** - item tooltips are corrected to show the damage OCM actually deals, and its config is watched so its own reloads are picked up. The plugin warns on startup if OCM's *own* sword-blocking module is enabled (the two would fight over the same right-click) or if its attack-cooldown module is off (block-hitting is a 1.8 technique and feels wrong with the 1.9 cooldown).
- **WorldGuard** - adds a `sword-blocking` region flag. Deny it in a region to turn blocking off there.
- **PlaceholderAPI** - `%vanillablocking_blocking%`, `%vanillablocking_allowed%`, `%vanillablocking_toggled%`.
- **Geyser/Floodgate** - Bedrock clients handle held right-click differently from Java clients, so blocking can feel unreliable for them. `restrictions.disable-for-bedrock` turns it off just for them.
- **ViaVersion** - clients older than 1.21.4 do not know the blocking animation and will not render the raised sword, though the damage reduction still applies to them. This is a client limitation, not something the server can translate.
- **Anticheat** - block-hitting and blocking while moving look like `NoSlow` to Grim, Vulcan and Matrix. If your anticheat flags players for it, exempt the sword-blocking checks or ask its authors for a compatibility flag; the plugin cannot suppress those checks itself.
- **Folia** - supported. All per-player work runs on that player's scheduler and `/vsb cleanup` is region-scheduled.

## For plugin developers

`net.player005.vanillablocking.api` exposes:

```java
if (VanillaBlockingApi.isBlocking(player)) { ... }
ItemStack blocking = VanillaBlockingApi.blockingItem(player);
VanillaBlockingApi.stopBlocking(player);
```

and three events: `PlayerStartBlockingEvent` (cancellable), `PlayerStopBlockingEvent`, and `PlayerBlockedDamageEvent` (cancellable, with a settable amount of blocked damage).

![An image showing a Netherite sword being blocked, like in 1.8](https://github.com/user-attachments/assets/f3cc8477-04a8-4bb3-882c-451bbeee422b)

> [!IMPORTANT]
> The plugin runs on Paper 1.21.4 and newer. Blocking is only displayed correctly by clients on 1.21.4 (24w44a) and later.

## Building

```sh
./gradlew build     # requires JDK 21
```

The jar lands in `build/libs/`. The main source set is compiled against the Paper 1.21.4 API so it can never call anything a 1.21.4 server is missing; the 1.21.5+ native blocking lives in `src/modern` and is only class-loaded when the server provides the component.

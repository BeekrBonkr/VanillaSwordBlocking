# Vanilla Sword Blocking

This is a very simple plugin that **brings back the old sword blocking mechanichs**, allowing You to block with your sword to reduce taken damage like in 1.8.9. <br>
The best part about it: It's **server-only**, so Players don't even need to install a mod on their client for this - just add the plugin to the server and it works for everyone!

The plugin **only** handles sword blocking - install it alongside a plugin like [OldCombatMechanics](https://github.com/kernitus/BukkitOldCombatMechanics) to restore the rest of 1.8.9 combat (attack cooldown removal, old damage values, no sweep attacks, ...).

## 1.8.9 accuracy

- Uses the exact 1.8.9 blocking formula: `damage = (damage + 1) / 2`, applied before armor (a simple percentage multiplier is available as an alternative).
- Only reduces damage types that were blockable in 1.8.9 (the vanilla rule: anything armor reduces can be blocked - so burning, fall damage, potions, wither etc. are **not** blocked).
- **Block-hitting** works like in 1.8.9: attacking never interrupts your block. Admins can nerf outgoing block-hit damage or disable block-hitting entirely (attacking then interrupts the block for a configurable number of ticks).
- Blocking only works with the main hand by default, since 1.8.9 had no offhand (configurable).
- Holding a shield in the offhand disables sword blocking by default, so right-click raises the shield as usual (configurable - set `allow-with-shield: true` to let the sword win instead).
- Configurable list of items that can block (`blockable-items`) - supports item tags like `#minecraft:swords` (the 1.8.9 default) and single items, including datapack-added ones.

## Configuration

Everything is configurable in `plugins/vanilla-sword-blocking/config.yml` - each option's comment notes the authentic 1.8.9 value. The config is versioned and **updates itself automatically** when new options are added (your old file is backed up first).

Use `/vanillablocking reload` (alias `/vsb`, permission `vanillablocking.admin`) to apply config changes without a restart.

![An image showing a Netherite sword being blocked, like in 1.8](https://github.com/user-attachments/assets/f3cc8477-04a8-4bb3-882c-451bbeee422b)

> [!IMPORTANT]
> While this mod can be installed on any server version 1.21.2 and later, the blocking will only be displayed correctly by clients in version 24w44a (1.21.4) and later.

<details>
  
<summary>How it works</summary>

> Essentialy, this plugin uses the recently added component called "consumable", to make Minecraft think you can eat swords!
> 
> This is used to enable an animation called "block", so it looks like you're blocking.
> 
> Then, the plugin prevents you from actually eating your sword, and instead reduces damage when it detects you are blocking ("eating", from minecraft's perspective) your sword.
</details>

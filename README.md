*[日本語 README](README.ja.md)*

# Class Loadout (classloadout)

A standalone Minecraft 1.20.1 / Forge 47.x mod: no dependency on any squad/party mod.
Every player builds their own **personal loadout** by assigning an item to each of five slots (main weapon / sidearm / throwable / gadget / melee) from the death screen - but only from the items an OP has **whitelisted for that specific slot**. Admins can additionally define **presets** through a GUI editor, which a player can apply to their own loadout as a starting point and keep tweaking slot by slot. Whatever the player's personal loadout currently holds is auto-equipped into hotbar slots 0-4 on every respawn.

The gadget slot can also hold **resupply pack** gadgets, in two flavors: placeable blocks (stronger, right-click to place, stays until broken or its lifetime runs out) and throwable packs (weaker, snowball-style throw, settles where it lands). Both heal or resupply ammo for nearby players over time. Ammo resupply optionally hooks into TACZ/SuperbWarfare if installed (see [Design Notes](#design-notes)), including picking a **specific TACZ gun** (not just a generic gun item) for a loadout slot. The gadget slot can also hold [**Cover**](#cover) (`classloadout:cover`), a 1000-HP crouch barrier. To change your loadout without dying, place a [**Loadout Station**](#loadout-station) block instead.

## License

GNU General Public License v3.0 (GPL-3.0-only). Full text in [`LICENSE`](LICENSE).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/class assign <slot> <item>` | Set one slot (`main`/`sidearm`/`throwable`/`gadget`/`melee`) of your own loadout to a whitelisted item; `minecraft:air` clears it | - |
| `/class select <id>` | Apply a preset's five items into your own loadout as a starting point | - |
| `/class clear` | Clear your entire personal loadout | - |
| `/class editor` | Open the preset editor GUI | OP (level 2+) |
| `/class save <id> <icon> <main> <sidearm> <throwable> <gadget> <melee> <name...>` | Create or overwrite a preset (sent by the editor's Save button, not typed by hand) | OP (level 2+) |
| `/class delete <id>` | Delete a preset | OP (level 2+) |
| `/class whitelist` | Open the slot-whitelist editor GUI | OP (level 2+) |
| `/class whitelist add <slot> <item>` | Add an item to a slot's whitelist (sent by the whitelist editor, not typed by hand) | OP (level 2+) |
| `/class whitelist remove <slot> <item>` | Remove an item from a slot's whitelist | OP (level 2+) |
| `/class whitelist ammo <slot> <item> <ammoItem> <count>` | Attach an ammo grant to an already-whitelisted item: equipping it on respawn also gives `count` of `ammoItem` into the player's general inventory. `count 0` clears the grant (sent by the whitelist editor's ammo popup, not typed by hand) | OP (level 2+) |
| `/class protect` | Open the protected-items editor GUI | OP (level 2+) |
| `/class protect add <item>` | Add an item to the protected-items list (sent by the editor, not typed by hand) | OP (level 2+) |
| `/class protect remove <item>` | Remove an item from the protected-items list | OP (level 2+) |
| `/class spawnkit` | Open the spawn kit editor GUI | OP (level 2+) |
| `/class spawnkit add <item> <count>` | Set (or update) a spawn kit entry: every respawn now also grants `count` of `item`. `count 0` removes the entry (sent by the editor, not typed by hand) | OP (level 2+) |
| `/class spawnkit remove <item>` | Remove an item from the spawn kit | OP (level 2+) |

Item arguments accept any registered item id; `minecraft:air` is the "unset" sentinel.

**Every slot's whitelist starts empty**, meaning players can't self-assign anything to that slot until an OP adds at least one item via the whitelist editor. Presets aren't whitelist-restricted (an OP already has full control over what goes into one via the preset editor). Ammo grants are optional and per whitelist entry - most items have none. **The protected-items list also starts empty**, meaning [Death Behavior](#death-behavior)'s inventory clear affects everything until an OP exempts specific items.

## GUI

- **Loadout screen** (press **Loadout** in the top-right corner of the vanilla death screen): "My Loadout" shows your five slots as clickable icons - click one to open an item-grid picker (search box + scrollable grid, restricted to that slot's whitelist) and assign that slot directly. Below it, "Presets" lists whatever the admin has defined, each with an **Apply** button that copies its five items into your own loadout (you can keep editing individual slots afterward - applying a preset is just a starting point, not a lock).
- **Preset editor** (`/class editor`, OP only): left column lists existing presets with Edit/Delete; the right column edits one preset's name, icon and five slots. The preset editor's item picker is **unrestricted** (all `tacz`/`superbwarfare`/`minecraft` items) since an OP already needs permission to reach it. Save commits the whole preset in one atomic command; nothing reaches the server until you press it.
- **Whitelist editor** (`/class whitelist`, OP only, also reachable via a button in the preset editor): five slot tabs across the top; pick a slot, then click any item in the grid to toggle it in/out of that slot's whitelist (whitelisted items are outlined green). This is what actually powers the player-facing loadout screen's item picker. **Right-click** a whitelisted item to open the ammo grant popup (whitelisting it first if needed) - pick an ammo item and a count, Save to attach it (an orange corner marker shows which items already have one) or Clear to remove it.
- **Protected-items editor** (`/class protect`, OP only, also reachable via a button in the preset editor): a single grid (no slot tabs - this is one global list) where clicking an item toggles it in/out of the protected-items list (outlined orange). See [Death Behavior](#death-behavior) for what this list is for.
- **Spawn kit editor** (`/class spawnkit`, OP only, also reachable via a button in the preset editor): another single grid (outlined blue, with the count shown in the corner of each included cell). Click an item to add it at a count of 1 or remove it; **right-click** to open a small popup and set an exact count. See [Spawn Kit](#spawn-kit) for what this is for.
- **Equips immediately, and again on every respawn**: `assign`, `select` and `clear` all write your personal loadout's five items straight into **hotbar slots 0-4** (main/sidearm/throwable/gadget/melee order) the moment the command succeeds - no need to die first, whether you changed it from the death screen, from [the Loadout Station](#loadout-station) while alive, or by hand-typing the command. The same overwrite also happens unconditionally on every respawn once you've touched your loadout at least once, so it prevents duplicate gear if `keepInventory` is on. A player who has never touched their loadout is left alone. Missing items (e.g. a slot referencing a mod that isn't installed) are silently left empty rather than crashing. **Ammo grants only fire on respawn**, deliberately - if the immediate re-equip also granted ammo, repeatedly re-assigning the same item while alive would be a free, costless way to farm it. If the equipped item has an ammo grant attached, that ammo is added (not overwritten - it stacks with whatever the player already has) to the player's general inventory on respawn, dropping any overflow that doesn't fit.

## Death Behavior

If `death.clearInventoryOnDeath` is on (default), a player's **entire inventory** (main inventory, armor, offhand) is wiped on every respawn, except items on the OP-curated **protected-items list** (`/class protect`, matched by base item type - enchantments/NBT/count don't matter). This runs before the personal loadout is re-equipped into hotbar slots 0-4, so loadout gear always comes back regardless of this setting - it only affects everything else the player was carrying.

This is meant to pair with the vanilla **`keepInventory` gamerule turned on**: with it on, nothing is lost to death drops, but without this feature loot would otherwise just pile up across deaths forever. With `keepInventory` off, vanilla already drops (and permanently loses) the player's items - protected or not - before this mod's own respawn handling ever runs, so the protected list has no effect in that setup.

Same "never touched their loadout" exemption as the equip behavior above: a player who has never run `assign`/`select`/`clear` is left alone entirely, clear included.

## Spawn Kit

The **spawn kit** (`/class spawnkit`) is a flat list of item/count pairs that get given to **every player, on every respawn, unconditionally** - not tied to the loadout system at all. Unlike the personal loadout's five slots, it doesn't need whitelisting, self-assignment, or even the player having ever touched `/class` - it always applies, the same for everyone, like a fixed starting kit.

- Granted via the same additive `giveOrDrop` mechanism as ammo grants: it stacks with whatever the player already has and drops any overflow that doesn't fit, rather than overwriting anything.
- Granted after the [Death Behavior](#death-behavior) inventory clear and the loadout equip, so spawn kit items always survive that same respawn regardless of `death.clearInventoryOnDeath` - there's no need to also add them to the protected-items list for that.
- Resolved through the same `ItemResolver` as everything else, so an entry can point at an already-registered exact item variant id (see the whitelist editor's "Add Held Item"), not just a bare item type - though the spawn kit editor itself doesn't have its own button to mint a new variant, unlike the ammo grant popup.
- The spawn kit list starts empty, meaning it has no effect until an OP adds at least one entry.

## Loadout Station

`classloadout:loadout_station` is a placeable block that lets you **change your loadout any time, without dying**. Right-click it to open the exact same `LoadoutScreen` as the death screen's Loadout button - purely client-side (the loadout data is already synced to the client), so this needs no new packet either.

- Whatever you assign/apply/clear in the screen lands in your hotbar right then - same immediate-equip behavior as the [GUI](#gui) section describes, just usable while alive instead of only from the death screen.
- Available from the creative inventory (Building Blocks tab). No crafting recipe yet - meant to be handed out by an OP (`/give`, etc.).
- Anyone can right-click it, no permission check (same self-service action as the death-screen button). Slot assignment itself still goes through that slot's whitelist as usual.
- Placeholder look: a plain cube textured with the vanilla crafting table's top texture (`minecraft:block/crafting_table_top`) on all six faces.

## Resupply Packs

Two placeable items, `classloadout:health_pack` and `classloadout:ammo_pack` - like any other item, an OP has to whitelist them for the `gadget` slot before players can assign and place them. These are entities, not blocks (no hitbox to walk into, no blockstate/model/loot-table plumbing needed) - the entity renders its own item's registered model (a full 3D Blockbench box, not a flat icon) resting on the ground via `ItemRenderer.renderStatic`, the same technique squadtp uses for its respawn beacon.

- Right-click a block face to place it there. Every `resupplyIntervalSeconds`, any player within `resupplyRadius` blocks is healed (health pack) or resupplied (ammo pack); it self-destructs after `packLifetimeSeconds` with no drop either way.
- A player can have at most `maxActivePacksPerPlayer` packs active at once (health + ammo combined); placing beyond that is rejected.
- `friendlyOnlyDestroy` (default on): the pack has 1 HP, and only an attack from the player who placed it can actually damage it - everyone else's hits do nothing (there's no squad/team concept here, so "friendly" just means "the owner").
- **Ammo resupply**: for TACZ, guns using its "dummy ammo" reserve mode (`IGun.useDummyAmmo()`, i.e. carrying a `DummyAmmo` NBT tag) get that internal reserve topped up directly. Every other gun - including TACZ's default magazine-fed stock guns (AK47, M4, ...), which are neither dummy-ammo nor `useInventoryAmmo()` - instead gets real ammo, resolved from the gun's own ammo id: a matching TACZ ammo box already in the player's inventory is topped up in preference to giving loose ammo items (up to the box's own capacity - it never overflows into loose ammo once full), and loose ammo items are only added when no matching box exists at all. For SuperbWarfare, all five ammo pools (handgun/rifle/shotgun/sniper/heavy) are topped up regardless of what's currently held. Neither mod installed → the ammo pack just does nothing (no crash).

## Thrown Resupply Packs

Two throwable items, `classloadout:thrown_health_pack` and `classloadout:thrown_ammo_pack` - a deliberately weaker, more casual alternative to the placeable packs above (same whitelist requirement for the `gadget` slot).

- Throw like a snowball/ender pearl. On impact it settles in place, spawns a burst of particles, and starts affecting nearby players - smaller radius, longer interval, shorter lifetime than the placed version (see the `throwable` config section). Affected players see an action-bar message ("Healing..." / "Resupplying ammo...") each tick.
- **The item is never consumed** - only `throwCooldownSeconds` (vanilla's ender-pearl-style item cooldown) gates repeated throws, so it stays usable throw after throw once the cooldown clears.
- Counts against the same `maxActivePacksPerPlayer` limit as the placed packs (combined, not a separate pool) once it lands.

## Cover

`classloadout:cover` is a placeable item using the same "spawn an entity, not a block" technique as the two resupply packs above (a crouch-height barrier that stays until destroyed or its lifetime runs out), but it grants no effect at all - it's purely **a 1000-HP blocker standing in the way**. Right-click a block face to place it.

- Self-destructs after `coverLifetimeSeconds` (default 600 = 10 minutes) - deliberately much longer than a resupply pack's lifetime, since it's a defensive structure, not a consumable.
- A player can have at most `maxActiveCoversPerPlayer` covers active at once (a separate pool from the resupply pack limit).
- `friendlyOnlyDestroy` (shared with the resupply packs) still applies: only the placing player's attacks actually damage it.
- Currently a placeholder low-wall box model textured with a vanilla wood texture (`models/item/cover.json`) - like the other gadgets, swappable for a custom 3D model later.

## Configuration (`world/serverconfig/classloadout-server.toml`)

- `resupply.resupplyRadius` (default 4) - blocks around a pack that players are affected within
- `resupply.resupplyIntervalSeconds` (default 2) - seconds between each heal/ammo tick
- `resupply.resupplyHealthPerTick` (default 1) - health points (half-hearts) restored per tick by a health pack
- `resupply.resupplyAmmoPerTick` (default 10) - ammo units restored per tick by an ammo pack (see [Resupply Packs](#resupply-packs) for what "unit" means per weapon mod)
- `resupply.packLifetimeSeconds` (default 20) - seconds before a placed pack self-destructs
- `resupply.maxActivePacksPerPlayer` (default 1) - active packs (health + ammo, placed + thrown, all combined) a player may have at once
- `resupply.friendlyOnlyDestroy` (default true) - only the placing player can break their own pack
- `throwable.throwPackRadius` (default 2) - smaller than `resupplyRadius` by design
- `throwable.throwPackIntervalSeconds` (default 3)
- `throwable.throwPackHealthPerTick` (default 1)
- `throwable.throwPackAmmoPerTick` (default 5) - half of `resupplyAmmoPerTick` by default
- `throwable.throwPackLifetimeSeconds` (default 25) - shorter than `packLifetimeSeconds` by design
- `throwable.throwCooldownSeconds` (default 15) - per-player, per-item cooldown between throws
- `death.clearInventoryOnDeath` (default true) - wipe the whole inventory on respawn except protected items; see [Death Behavior](#death-behavior)
- `cover.coverLifetimeSeconds` (default 600) - seconds before a placed cover self-destructs
- `cover.maxActiveCoversPerPlayer` (default 2) - active covers a player may have at once

## Design Notes

- **Three independent things, two OP tools, one player screen**: a player's own personal loadout (self-service, always the source of truth for what gets equipped), OP-managed presets (a convenience "load this as a starting point" library, unrestricted), and OP-curated per-slot whitelists (what a player is allowed to self-assign). Presets are never themselves equipped - applying one just copies its items into the player's own loadout, and isn't checked against the whitelist (an OP already had to be OP to create it).
- **Server-side enforcement, not just a GUI filter**: `/class assign` re-checks the item against that slot's whitelist on the server before accepting it, so a hand-typed command can't bypass what the item picker shows.
- **Server-authoritative, no C2S packets**: presets, whitelists and every player's personal loadout are `SavedData`, persisted with the overworld. Every mutation (`assign`/`select`/`clear`/`save`/`delete`/`whitelist add`/`whitelist remove`) goes through a `/class` command, validated server-side - there is no client-to-server packet to spoof. The item picker doesn't need a server round trip either: the item registry is already fully populated on the client after login, so it just filters `ForgeRegistries.ITEMS` (or the synced whitelist) locally.
- **One exception**: opening the preset/whitelist/protected-items/spawn-kit editor screens. `/class editor`, `/class whitelist`, `/class protect` and `/class spawnkit` run on the server (permission-checked there), which then sends a tiny trigger packet back to that one client to open the screen - so the client never has to re-derive its own permission level.
- **Loadout/whitelist system: soft dependency, isolated the same way squadtp does it.** `compat/TaczCompat`/`compat/SuperbWarfareCompat` check `ModList.isLoaded(...)` and contain zero references to either mod's classes themselves; the actual API calls live in `compat/tacz/*`/`compat/superbwarfare/*`, only classloaded from inside the `isLoaded` branch. `build.gradle` declares both as `modCompileOnly` for this reason - never shipped, never required, but no longer purely string-based namespace matching.
- **TACZ guns aren't registered items - `ItemResolver` bridges the gap.** TACZ ships one generic item (e.g. `tacz:modern_kinetic_gun`) for every gun; the specific gun (AK47, M4, ...) is a `GunId` NBT tag resolved through TACZ's data-driven gun index (`TimelessAPI`), not a distinct registry entry. So a TACZ gun's "item id" as stored in a preset/loadout/whitelist is really a *gun id*, and every place that turns a stored `ResourceLocation` into a real `ItemStack` (icon rendering, respawn equip, the item picker's own grid) goes through `ItemResolver.resolve()`, which tries `TaczCompat.buildGunStack()` (via TACZ's `GunItemBuilder`) before falling back to a plain registered item. `ItemCatalog` adds every TACZ gun id (`TimelessAPI.getAllCommonGunIndex()`) to the pickable pool the same way. SuperbWarfare needs no such bridge - its weapons are ordinary registered items.
- **Ammo resupply**: the same `compat/tacz`/`compat/superbwarfare` modules also drive the resupply packs' ammo top-up (`TaczCompat.resupply`/`SuperbWarfareCompat.resupply`), sharing the guarded-classloading pattern above.

## Building & Running

Requires JDK 21 for Gradle itself (see `gradle.properties` → `org.gradle.java.home`); compiles against a Java 17 toolchain.

```
gradlew build         # -> build/libs/classloadout-<version>.jar
gradlew runServer      # dev server (run-server/)
gradlew runClient      # dev client, username "Dev1"
gradlew runClient2     # second dev client, username "Dev2" (separate game dir: run2/)
```

### Two-player test procedure

1. `gradlew runServer`, then `gradlew runClient` and `gradlew runClient2`. Op Dev1 on the server (`op Dev1`) so it can reach the editors.
2. On Dev1: `/class whitelist` → pick the `main` tab, click a couple of items to whitelist them; repeat for at least one other slot.
3. On Dev2: die, open **Loadout**, click the `main` slot icon and confirm only the items Dev1 whitelisted are shown (and that an un-whitelisted slot's picker is empty with the "nothing whitelisted yet" hint); assign one and confirm it lands in hotbar slot 0 immediately, even before respawning.
4. On Dev1: `/class editor` → create a preset (its item picker should show everything, not just whitelisted items), Save.
5. On Dev2: open **Loadout**, click **Apply** on the preset, respawn, confirm the items appear in hotbar slots 0-4 even if some weren't individually whitelisted (presets bypass the whitelist by design).
6. On Dev1: remove one of the earlier-whitelisted items via `/class whitelist`, confirm Dev2's loadout screen no longer offers it (existing assignment isn't retroactively cleared, only the picker's future choices change).
7. Whitelist `classloadout:health_pack` and `classloadout:ammo_pack` for the `gadget` slot, assign one to Dev2's loadout, respawn, place it, and confirm: nearby players heal/resupply on the configured interval, it disappears after `packLifetimeSeconds`, a second placement past `maxActivePacksPerPlayer` is rejected, and (with `friendlyOnlyDestroy` on) Dev1 attacking Dev2's pack does nothing while Dev2 attacking it destroys it in one hit.
8. Whitelist `classloadout:thrown_health_pack`/`classloadout:thrown_ammo_pack` for `gadget` too; assign, respawn, throw one at the ground and confirm it lands and starts affecting nearby players with a visibly smaller radius/weaker cadence than the placed version, plus the action-bar message. Throw again immediately and confirm it's blocked until `throwCooldownSeconds` passes, and that the item itself isn't consumed.
9. With TACZ installed: `/class whitelist` → `main` tab → confirm individual gun names (e.g. `tacz:ak47`), not just the generic gun item, appear in the grid and can be whitelisted; assign one to a loadout, respawn, and confirm the correct specific gun (not an empty/unconfigured one) lands in hotbar slot 0.
10. Whitelist `classloadout:cover` for `gadget`, assign it to Dev2's loadout, respawn, and place it. Confirm it shrugs off a few hits (1000 HP), disappears after `coverLifetimeSeconds`, a second placement past `maxActiveCoversPerPlayer` is rejected, and (with `friendlyOnlyDestroy` on) only Dev2's own attacks damage it.
11. Give Dev2 a `classloadout:loadout_station` (creative inventory or `/give`), have them place and right-click it while alive, and confirm: the same Loadout screen opens as the death screen's button, assigning a different item swaps it into their hotbar immediately while still alive (no respawn needed), and no ammo grant fires from this immediate swap (it should only show up after an actual respawn).
12. Turn on the `keepInventory` gamerule (`/gamerule keepInventory true`). On Dev1: `/class protect` → click a couple of items to protect them. On Dev2: pick up some unrelated junk items plus one of the protected items, then die and respawn; confirm the junk is gone, the protected item survived, and the loadout's five hotbar items came back as usual. Toggle `death.clearInventoryOnDeath` off and repeat - confirm nothing gets cleared this time.
13. On Dev1: `/class spawnkit` → left-click an item to add it at count 1, right-click it and set a different count, then Save. On Dev2 (even one who has never touched `/class` at all): respawn and confirm the spawn kit item(s) show up with the right count, stacking with whatever they already had. Remove the entry from the spawn kit and confirm the next respawn no longer grants it.

`build.gradle` pulls TACZ/SuperbWarfare (and their required Kotlin/GeckoLib/Curios chain) both as `modRuntimeOnly` (so dev clients/servers actually have the mods loaded to test against) and `modCompileOnly` (so the compat classes can compile against their real APIs); neither is required to build or ship the mod itself.

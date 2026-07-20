*[日本語 README](README.ja.md)*

# Class Loadout (classloadout)

A standalone Minecraft 1.20.1 / Forge 47.x mod: no dependency on any squad/party mod or on TACZ/SuperbWarfare.
Every player builds their own **personal loadout** by assigning an item to each of five slots (main weapon / sidearm / throwable / gadget / melee) from the death screen - but only from the items an OP has **whitelisted for that specific slot**. Admins can additionally define **presets** through a GUI editor, which a player can apply to their own loadout as a starting point and keep tweaking slot by slot. Whatever the player's personal loadout currently holds is auto-equipped into hotbar slots 0-4 on every respawn.

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

Item arguments accept any registered item id; `minecraft:air` is the "unset" sentinel.

**Every slot's whitelist starts empty**, meaning players can't self-assign anything to that slot until an OP adds at least one item via the whitelist editor. Presets aren't whitelist-restricted (an OP already has full control over what goes into one via the preset editor).

## GUI

- **Loadout screen** (press **Loadout** in the top-right corner of the vanilla death screen): "My Loadout" shows your five slots as clickable icons - click one to open an item-grid picker (search box + scrollable grid, restricted to that slot's whitelist) and assign that slot directly. Below it, "Presets" lists whatever the admin has defined, each with an **Apply** button that copies its five items into your own loadout (you can keep editing individual slots afterward - applying a preset is just a starting point, not a lock).
- **Preset editor** (`/class editor`, OP only): left column lists existing presets with Edit/Delete; the right column edits one preset's name, icon and five slots. The preset editor's item picker is **unrestricted** (all `tacz`/`superbwarfare`/`minecraft` items) since an OP already needs permission to reach it. Save commits the whole preset in one atomic command; nothing reaches the server until you press it.
- **Whitelist editor** (`/class whitelist`, OP only, also reachable via a button in the preset editor): five slot tabs across the top; pick a slot, then click any item in the grid to toggle it in/out of that slot's whitelist (whitelisted items are outlined green). This is what actually powers the player-facing loadout screen's item picker.
- **Auto-equip on respawn**: your personal loadout's five items are written straight into **hotbar slots 0-4** (main/sidearm/throwable/gadget/melee order), overwriting whatever was there - unconditional on every respawn once you've touched your loadout at least once (assign/select/clear), so it also prevents duplicate gear if `keepInventory` is on. A player who has never touched their loadout is left alone. Missing items (e.g. a slot referencing a mod that isn't installed) are silently left empty rather than crashing.

## Design Notes

- **Three independent things, two OP tools, one player screen**: a player's own personal loadout (self-service, always the source of truth for what gets equipped), OP-managed presets (a convenience "load this as a starting point" library, unrestricted), and OP-curated per-slot whitelists (what a player is allowed to self-assign). Presets are never themselves equipped - applying one just copies its items into the player's own loadout, and isn't checked against the whitelist (an OP already had to be OP to create it).
- **Server-side enforcement, not just a GUI filter**: `/class assign` re-checks the item against that slot's whitelist on the server before accepting it, so a hand-typed command can't bypass what the item picker shows.
- **Server-authoritative, no C2S packets**: presets, whitelists and every player's personal loadout are `SavedData`, persisted with the overworld. Every mutation (`assign`/`select`/`clear`/`save`/`delete`/`whitelist add`/`whitelist remove`) goes through a `/class` command, validated server-side - there is no client-to-server packet to spoof. The item picker doesn't need a server round trip either: the item registry is already fully populated on the client after login, so it just filters `ForgeRegistries.ITEMS` (or the synced whitelist) locally.
- **One exception**: opening the preset/whitelist editor screens. `/class editor` and `/class whitelist` run on the server (permission-checked there), which then sends a tiny trigger packet back to that one client to open the screen - so the client never has to re-derive its own permission level.
- **Zero coupling**: no compile-time or runtime dependency on any other mod. TACZ/SuperbWarfare are referenced only as string namespaces (`tacz:`/`superbwarfare:`) when building the item catalog, matched purely against whatever `ForgeRegistries.ITEMS` happens to contain.

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
3. On Dev2: die, open **Loadout**, click the `main` slot icon and confirm only the items Dev1 whitelisted are shown (and that an un-whitelisted slot's picker is empty with the "nothing whitelisted yet" hint); assign one, respawn, confirm it lands in hotbar slot 0.
4. On Dev1: `/class editor` → create a preset (its item picker should show everything, not just whitelisted items), Save.
5. On Dev2: open **Loadout**, click **Apply** on the preset, respawn, confirm the items appear in hotbar slots 0-4 even if some weren't individually whitelisted (presets bypass the whitelist by design).
6. On Dev1: remove one of the earlier-whitelisted items via `/class whitelist`, confirm Dev2's loadout screen no longer offers it (existing assignment isn't retroactively cleared, only the picker's future choices change).

`build.gradle` pulls TACZ/SuperbWarfare (and their required Kotlin/GeckoLib/Curios chain) as **dev-runtime-only** dependencies so the item picker has real `tacz:`/`superbwarfare:` items to show while testing; none of this is required to build or ship the mod.

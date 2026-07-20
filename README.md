*[日本語 README](README.ja.md)*

# Class Loadout (classloadout)

A standalone Minecraft 1.20.1 / Forge 47.x mod: no dependency on any squad/party mod or on TACZ/SuperbWarfare.
Every player builds their own **personal loadout** by freely assigning an item to each of five slots (main weapon / sidearm / throwable / gadget / melee) from the death screen. Admins can additionally define **presets** through a GUI editor, which a player can apply to their own loadout as a starting point and keep tweaking slot by slot. Whatever the player's personal loadout currently holds is auto-equipped into hotbar slots 0-4 on every respawn.

## License

GNU General Public License v3.0 (GPL-3.0-only). Full text in [`LICENSE`](LICENSE).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/class assign <slot> <item>` | Set one slot (`main`/`sidearm`/`throwable`/`gadget`/`melee`) of your own loadout; `minecraft:air` clears it | - |
| `/class select <id>` | Apply a preset's five items into your own loadout as a starting point | - |
| `/class clear` | Clear your entire personal loadout | - |
| `/class editor` | Open the preset editor GUI | OP (level 2+) |
| `/class save <id> <icon> <main> <sidearm> <throwable> <gadget> <melee> <name...>` | Create or overwrite a preset (sent by the editor's Save button, not typed by hand) | OP (level 2+) |
| `/class delete <id>` | Delete a preset | OP (level 2+) |

Item arguments accept any registered item id; `minecraft:air` is the "unset" sentinel.

## GUI

- **Loadout screen** (press **Loadout** in the top-right corner of the vanilla death screen): "My Loadout" shows your five slots as clickable icons - click one to open an item-grid picker (search box + scrollable grid, `tacz`/`superbwarfare`/`minecraft` namespaces) and assign that slot directly. Below it, "Presets" lists whatever the admin has defined, each with an **Apply** button that copies its five items into your own loadout (you can keep editing individual slots afterward - applying a preset is just a starting point, not a lock).
- **Preset editor** (`/class editor`, OP only): left column lists existing presets with Edit/Delete; the right column edits one preset's name, icon and five slots, using the same item-grid picker. Save commits the whole preset in one atomic command; nothing reaches the server until you press it.
- **Auto-equip on respawn**: your personal loadout's five items are written straight into **hotbar slots 0-4** (main/sidearm/throwable/gadget/melee order), overwriting whatever was there - unconditional on every respawn once you've touched your loadout at least once (assign/select/clear), so it also prevents duplicate gear if `keepInventory` is on. A player who has never touched their loadout is left alone. Missing items (e.g. a slot referencing a mod that isn't installed) are silently left empty rather than crashing.

## Design Notes

- **Two independent things, one screen**: a player's own personal loadout (self-service, always the source of truth for what gets equipped) and admin-managed presets (a convenience "load this as a starting point" library). Presets are never themselves equipped - applying one just copies its items into the player's own loadout.
- **Server-authoritative, no C2S packets**: presets and every player's personal loadout are `SavedData`, persisted with the overworld. Every mutation (`assign`/`select`/`clear`/`save`/`delete`) goes through a `/class` command, validated server-side - there is no client-to-server packet to spoof. The item picker doesn't need a server round trip either: the item registry is already fully populated on the client after login, so `ItemPickerScreen` just filters `ForgeRegistries.ITEMS` locally.
- **One exception**: opening the preset editor screen. `/class editor` runs on the server (permission-checked there), which then sends a tiny `OpenClassEditorPacket` back to that one client to open the screen - so the client never has to re-derive its own permission level.
- **Zero coupling**: no compile-time or runtime dependency on any other mod. TACZ/SuperbWarfare are referenced only as string namespaces (`tacz:`/`superbwarfare:`) when filtering the item picker, matched purely against whatever `ForgeRegistries.ITEMS` happens to contain.

## Building & Running

Requires JDK 21 for Gradle itself (see `gradle.properties` → `org.gradle.java.home`); compiles against a Java 17 toolchain.

```
gradlew build         # -> build/libs/classloadout-<version>.jar
gradlew runServer      # dev server (run-server/)
gradlew runClient      # dev client, username "Dev1"
gradlew runClient2     # second dev client, username "Dev2" (separate game dir: run2/)
```

### Two-player test procedure

1. `gradlew runServer`, then `gradlew runClient` and `gradlew runClient2`. Op Dev1 on the server (`op Dev1`) so it can reach the preset editor.
2. On Dev1: die, open **Loadout** on the death screen, click each of the five slot icons and assign items directly - confirm they persist and show up after respawning.
3. Still on Dev1: `/class editor` → create a preset, assign items to each slot, Save.
4. On Dev2: die, open **Loadout**, click **Apply** on the preset Dev1 created, respawn, and confirm the items appear in hotbar slots 0-4. Then tweak one slot directly and confirm only that slot changes.
5. Delete the preset from the editor while Dev2's loadout still has items that came from it; confirm Dev2's own loadout is unaffected (applying a preset copies items once, it doesn't keep a live link).

`build.gradle` pulls TACZ/SuperbWarfare (and their required Kotlin/GeckoLib/Curios chain) as **dev-runtime-only** dependencies so the item picker has real `tacz:`/`superbwarfare:` items to show while testing; none of this is required to build or ship the mod.

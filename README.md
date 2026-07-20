*[日本語 README](README.ja.md)*

# Class Loadout (classloadout)

A standalone Minecraft 1.20.1 / Forge 47.x mod: no dependency on any squad/party mod or on TACZ/SuperbWarfare.
Admins define equipment "classes" (a name, an icon, and five slots: main weapon / sidearm / throwable / gadget / melee) through an in-game GUI editor. Players pick one from the death screen, and it's auto-equipped into hotbar slots 0-4 on every respawn.

## License

GNU General Public License v3.0 (GPL-3.0-only). Full text in [`LICENSE`](LICENSE).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/class editor` | Open the class editor GUI | OP (level 2+) |
| `/class save <id> <icon> <main> <sidearm> <throwable> <gadget> <melee> <name...>` | Create or overwrite a class (sent by the editor's Save button, not typed by hand) | OP (level 2+) |
| `/class delete <id>` | Delete a class | OP (level 2+) |
| `/class select <id>` | Select your loadout class (sent by the class-select screen) | - |
| `/class clear` | Clear your loadout selection | - |

`<icon>`/slot arguments accept any registered item id; `minecraft:air` is the "unset" sentinel.

## GUI

- **Editor** (`/class editor`, OP only): left column lists existing classes with Edit/Delete; the right column edits one class's name, icon and five slots. Each slot opens an item-grid picker (search box + scrollable grid) listing every registered item in the `tacz`, `superbwarfare` and `minecraft` namespaces. Save commits the whole class in one atomic command; nothing reaches the server until you press it.
- **Selection**: press **Change Class** in the top-right corner of the vanilla death screen to open the class list (name + the five slot icons in a row) and pick one.
- **Auto-equip on respawn**: the selected class's five items are written straight into **hotbar slots 0-4** (main/sidearm/throwable/gadget/melee order), overwriting whatever was there - unconditional on every respawn, so it also prevents duplicate gear if `keepInventory` is on. Missing items (e.g. a slot referencing a mod that isn't installed) are silently left empty rather than crashing.

## Design Notes

- **Server-authoritative, no C2S packets**: class definitions and every player's selection are `SavedData`, persisted with the overworld. Every mutation (save/delete/select/clear) goes through a `/class` command, validated server-side - there is no client-to-server packet to spoof. The item picker doesn't need a server round trip either: the item registry is already fully populated on the client after login, so `ItemPickerScreen` just filters `ForgeRegistries.ITEMS` locally.
- **One exception**: opening the editor screen. `/class editor` runs on the server (permission-checked there), which then sends a tiny `OpenClassEditorPacket` back to that one client to open the screen - so the client never has to re-derive its own permission level.
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

1. `gradlew runServer`, then `gradlew runClient` and `gradlew runClient2`. Op both accounts on the server (`op Dev1`, `op Dev2` in the server console, or pre-seed `ops.json`).
2. On Dev1: `/class editor` → create a class, assign items to each slot, Save.
3. On Dev2: die, click **Change Class** on the death screen, select the class Dev1 created, respawn, and confirm the items appear in hotbar slots 0-4.
4. Delete the class from the editor while Dev2 still has it selected, then have Dev2 respawn again and confirm nothing crashes (the slots just stay empty).

`build.gradle` pulls TACZ/SuperbWarfare (and their required Kotlin/GeckoLib/Curios chain) as **dev-runtime-only** dependencies so the item picker has real `tacz:`/`superbwarfare:` items to show while testing; none of this is required to build or ship the mod.

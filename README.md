# Nether Portal Helper

A client-side Fabric portal planner with saved portal pairs, coordinate conversion, compass navigation and optional building guides.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.5 or later
- Fabric API 0.161.0 or later for Minecraft 26.2
- Java 25 or later

## Controls

- **O:** Open the portal planner.
- **P:** Capture and activate a new portal pair, or deactivate navigation when a target is already active.
- **F6:** Show or hide the HUD and building guides. All key bindings can be changed in Minecraft's Controls settings.

## Portal planner

Press **O** in a world to manage named portal pairs, or open the planner from the mod settings. A brief HUD hint shows the current planner key when you join or change navigation. Capture an aimed Nether portal to record its lower interior corner and opening axis. With no portal under the crosshair, capture uses your feet position. Saved pairs can be selected for navigation, renamed, deleted or edited with a separate construction Y and X/Z opening axis. Deactivating navigation keeps the pair in the list.

Manual entry and clipboard paste accept an explicit source dimension followed by integer X Y Z, for example `overworld 120 72 -48` or `nether -15 128 40`. Commas and semicolons also work between values. Copy exports the source coordinates in this format. Pasting fills the editor; it does not send a chat message or run a command.

Leave construction Y empty to use the original reference height. Setting it changes the building target without losing the source or calculated reference Y; both are shown in the planner, and the HUD shows the reference beside an overridden target height.

The planner also offers **Target marker** and **Frame preview** switches, both off by default. The marker appears within 256 blocks; the transparent minimal 4×5 frame appears within 48 blocks. It uses the destination's lower-left interior block as its anchor and follows the selected X/Z opening axis and construction height. Guides respect terrain depth, loaded chunks, the world border, build height and HUD visibility. They never place blocks or load chunks.

## Settings

With [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Nether Portal Helper's settings from the Mods screen. Adjust HUD placement, scale and background, choose a compact or detailed layout, and tune the compass. Changes are previewed before saving. Mod Menu is optional; settings can also be edited in `config/netherportalhelper.json`.

In compact mode, key hints appear briefly after joining a world, changing the HUD, or locking a portal target. Detailed mode keeps them visible.

The compact panel shrinks when its hints disappear. Coordinate rows always include X, Y and Z. Arrow smoothing ranges from 0 (instant response) to 1 (softest response). The settings preview uses the current GUI screen proportions and has switchable travelling, target-behind-you and horizontal-arrival examples. It has its own animation state and does not change live navigation.

## Targets and portal placement

Stand at the intended portal interior block (the player feet position), then press **P**. The calculated target refers to a portal interior block, not an obsidian frame block. X and Z are converted by 8; the original Y is kept as a height reference. The green check means **X/Z target reached**, even if your height differs. The coordinate rows keep the reference Y visible when optional height hints are disabled.

The helper calculates a building reference. It does not guarantee which portal Minecraft will select: existing nearby portals and their height affect linking. It does not inspect unloaded chunks or server portal rules. Check the connection in both directions after building.

Portal pairs and the active selection are saved locally in `config/netherportalhelper-targets.json` and restored when you reconnect to the same world or server. Version 1 last-target files are migrated to the version 2 library on the next successful save, including other worlds' entries. Deactivating navigation keeps saved entries; deleting removes the selected entry. Singleplayer worlds are identified by their save directory; multiplayer servers by their address and port. Different worlds behind the same proxy address share that server identity. Failed writes leave the previous library and navigation unchanged. Damaged or unsupported storage is preserved rather than silently replaced.

## Build

Run `./gradlew build` on Linux or macOS, or `gradlew.bat build` on Windows. The distributable JAR is written to `build/libs`.

`build` includes the assertion-enabled regression checks for coordinate input, portal capture, library migration and isolation, failed-write transactions, frame geometry, navigation, configuration, compass behavior and localization formats. UI/UX evaluation is performed manually by the maintainer.

See [CHANGELOG.md](CHANGELOG.md) for release notes.

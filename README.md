# Nether Portal Helper

A client-side Fabric portal planner with saved portal pairs, coordinate conversion, compass navigation and optional building guides.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.5 or later
- Fabric API 0.161.0 or later for Minecraft 26.2
- Java 25 or later

## Controls

- **O:** Open the portal planner.
- **P:** Capture a portal or position; if that endpoint is already saved, select its existing entry. This action never stops navigation.
- **F6:** Show or hide the HUD and building guides. All key bindings can be changed in Minecraft's Controls settings.
- **Stop navigation:** Available in the planner; an additional shortcut can be assigned in Controls (unbound by default).

## Portal planner

Press **O** in a world to manage named portal pairs, or open the planner from the mod settings. The saved-pair list sits beside the selected pair's build details on wide screens and directly above them on narrow screens. Capture an aimed Nether portal to record its lower interior corner and opening axis. With no portal under the crosshair, capture uses your feet position. Saved pairs can be selected for navigation, renamed, deleted or edited with a separate construction Y and X/Z opening axis. Stopping navigation keeps the pair in the list.

The normal journey is: capture a source, follow its planned counterpart coordinates in the other dimension, build a portal, then aim at that portal and choose **Record aimed counterpart** in the planner. Recording uses an actual aimed portal, never a feet-position fallback. **Navigate to counterpart** then leads to its recorded position; **Navigate to source** leads back to the saved source. Direction is retained when reconnecting. After travelling through the connection in both directions, you may explicitly mark it tested. This is your own confirmation; the client does not automatically verify Minecraft's portal selection.

The planner distinguishes a planned counterpart, a recorded counterpart and a connection you marked tested. Changing building height or opening axis clears the recorded counterpart and tested state; renaming preserves both. Repeated source capture preserves the saved name and building choices. Capturing a known recorded counterpart selects the return route instead of adding another entry.

Deleting an entry offers **Undo deletion** until another deletion or a connection/session change. Undo keeps intervening edits and other selections; if the portal was recaptured in the meantime, it keeps that current entry instead of creating a duplicate.

Manual entry and clipboard paste accept an explicit source dimension followed by integer X Y Z, for example `overworld 120 72 -48` or `nether -15 128 40`. Commas and semicolons also work between values. Separate **Copy source** and **Copy counterpart** actions export the respective endpoint in this format. Pasting fills the editor; it does not send a chat message or run a command.

Leave construction Y empty to use the original reference height. Setting it changes the building target without losing the source or calculated reference Y. Both are shown in the planner; the HUD displays the chosen building target.

The planner also offers **Target marker** and **Frame preview** switches, both off by default. Their section names the active navigation target, independently of the selected list entry. The marker appears within 256 blocks; the transparent minimal 4×5 frame appears within 48 blocks for a planned counterpart. Existing recorded portals and the return route only need a marker. The frame uses the planned destination's lower-left interior block as its anchor and follows the X/Z opening axis and construction height. Guides respect terrain depth, loaded chunks, the world border, build height and HUD visibility. They never place blocks or load chunks.

## Settings

With [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Nether Portal Helper's settings from the Mods screen. Adjust HUD placement, scale and background, choose a compact or detailed layout, and tune the compass. A detail preview shows the active HUD/compass settings at a readable size; a separate miniature overview shows screen placement. The **…** button beside each slider accepts an exact finite number, with visible range/rounding feedback. Changes remain a draft until **Save**. Mod Menu is optional; settings can also be edited in `config/netherportalhelper.json`.

The compact HUD distinguishes calculated coordinates, a planned building target, a recorded portal and a return destination. Enable **Show current position** to add a separately labeled position row in either layout. A short **P** hint explains capture/select; navigation hints point to **O** for the planner. While navigating, ordinary hints disappear after a few seconds in compact mode; dimension-change and building-step guidance stays visible when needed. Detailed mode keeps ordinary hints visible.

The compass reserves enough space for every arrow rotation, a stable width and a fixed turn-around row. Distance and optional height guidance share one compact line; long distances use kilometres. Coordinate rows include X, Y and Z. Arrow smoothing ranges from 0 (instant response) to 1 (softest response). Live HUD, compass and building guides are hidden behind the planner, editor and settings. The placement overview uses the current GUI screen proportions. The compass preview has switchable travelling, target-behind-you and horizontal-arrival examples. It has its own animation state and does not change live navigation.

## Targets and portal placement

Stand at the intended portal interior block (the player feet position), then press **P**. The calculated target refers to a portal interior block, not an obsidian frame block. X and Z are converted by 8; the original Y is kept as a height reference. The green check means **X/Z target reached**, even if your height differs. The target coordinates keep the chosen building Y visible when optional height hints are disabled.

The helper calculates a building reference. It does not guarantee which portal Minecraft will select: existing nearby portals and their height affect linking. It does not inspect unloaded chunks or server portal rules. Check the connection in both directions after building.

Portal pairs, observed counterparts, manual tested state and the active navigation direction are saved locally in `config/netherportalhelper-targets.json` and restored when you reconnect to the same world or server. Version 1 and 2 files are migrated to the version 3 library on the next successful save, including other worlds' entries. Stopping navigation keeps saved entries; deleting removes the selected entry. Singleplayer worlds are identified by their save directory; multiplayer servers by their address and port. Different worlds behind the same proxy address share that server identity. Failed writes leave the previous library, undo state and navigation unchanged. Damaged or unsupported storage is preserved rather than silently replaced.

## Build

Run `./gradlew build` on Linux or macOS, or `gradlew.bat build` on Windows. The distributable JAR is written to `build/libs`.

`build` includes the assertion-enabled regression checks for coordinate input, portal capture, library migration and isolation, failed-write transactions, frame geometry, navigation, configuration, compass behavior and localization formats. UI/UX evaluation is performed manually by the maintainer.

See [CHANGELOG.md](CHANGELOG.md) for release notes.

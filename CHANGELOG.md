# Changelog

## 1.4.0 — Portal Planner

- Added a portal planner opened with **O**, including named saved pairs, navigation selection, rename, delete and deactivation without deleting entries.
- Added aimed-portal capture with a consistent lower interior corner and opening axis, plus feet-position fallback.
- Added manual source-coordinate input and local clipboard copy/paste with an explicit dimension.
- Added independent construction height and X/Z frame orientation while retaining the original source and reference height.
- Added optional depth-tested target markers and translucent minimal-frame guides with range, dimension, chunk, border and build-height checks.
- Migrated last-target storage to atomic version 2 libraries, retaining other worlds and preserving damaged files.
- Translated all new planner controls into the seven included languages and extended non-UI regression coverage.

## 1.3.0 — QoL & HUD Update

- Fixed smoothing at zero; smoothing now increases consistently from instant to soft response.
- Made compact panel dimensions depend on visible content instead of leaving an empty hint area.
- Added Y coordinates, explicit X/Z arrival wording and portal-interior placement guidance without promising a guaranteed link.
- Added atomic storage of the last portal pair per world/server, including reconnect restoration and visible storage errors.
- Made the settings preview follow the actual GUI aspect ratio with separate animated compass state and three selectable scenarios.
- Completed German, English, Spanish, French, Polish, Russian and Simplified Chinese interface strings.
- Extended regressions for navigation lifecycle, smoothing endpoints, compact panel sizing, preview scenarios, localization formats and isolated persistent targets.
- Reworked the HUD and compass with clearer coordinate rows, target dimensions, consistent colors, and screen-edge fitting.
- Added an in-game preview and settings for HUD placement, size, background opacity, detail level, and compass behavior through optional Mod Menu integration.
- Added timed hints in compact mode, accurate arrival and height messages, and frame-rate-independent compass smoothing.
- Kept existing configuration values and added bounded defaults for the new options.
- Updated interface text in every included language.
- Added regression checks for settings normalization, HUD placement, and compass smoothing.

## 1.2.1 — Minecraft 26.2

- Updated Minecraft, Fabric Loader, Fabric API, Fabric Loom, and the Gradle wrapper for Minecraft 26.2.
- Added a Gradle build check for Nether and Overworld coordinate conversions, including negative coordinates.
- Added a GitHub Actions build for pull requests and pushes to `main`.

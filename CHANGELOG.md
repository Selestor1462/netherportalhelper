# Changelog

## 1.5.0 — Complete portal journey

- Made capture/select a consistent action on P; repeated capture selects the existing source or recorded counterpart without creating duplicates. Navigation can be stopped in the planner or through an optional key binding.
- Added explicit planned, counterpart-recorded, and manually tested states. Record an aimed built portal, navigate to either endpoint, and retain the original building reference.
- Saved actual counterpart coordinates, tested state and navigation direction in version 3 libraries, migrating version 1 and 2 data without claiming old connections are tested.
- Added transactional deletion undo, preserving newer edits and resolving endpoint collisions without duplicating routes.
- Separated selected-pair actions from named active-navigation guides, added explicit source/counterpart copy actions, and hid single-page pagination.
- Added readable HUD/compass detail previews with a separate placement overview, selected settings tabs, and exact numeric entry beside sliders.
- Stabilized compass width, shortened height guidance, clarified dimension changes and building steps, and strengthened frame/anchor outlines.
- Extended migration, direction, duplication, undo, failure and preview geometry regressions; translated all controls into the seven included languages.

## 1.4.1 — HUD and planner clarity

- Kept the compass arrow inside a rotation-safe area and reserved a stable row for the turn-around hint.
- Combined distance and height guidance, and made the target-only HUD compact by default with optional current position.
- Distinguished position and destination explicitly, reduced dimension icons, and moved linking guidance into contextual planner help.
- Connected saved-pair lists and build details in a responsive planner with prominent navigation and grouped build-preview controls.
- Hid live navigation and building guides behind the mod's menus while retaining the settings preview.
- Updated all seven languages and added compass-boundary regression checks.

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

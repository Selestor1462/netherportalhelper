# Portal planner design

The user approved all three proposed extensions and explicitly requests meaningful non-UI testing, followed by a commit on a separate branch and a push at the very end. UI/UX evaluation belongs to the user.

Build a small client-only portal planner for Minecraft26.2/Fabric without new runtime dependencies. Preserve current1.3.0 repairs and existing settings. Deliver as1.4.0 on feature/portal-planner.

PortalTarget is immutable and distinguishes source, calculated destination and optional construction Y. Names, IDs, X/Z opening axis and source dimension persist per world/server. A navigation selection can be deactivated without deleting the saved pair. Library commands persist before applying session state, so failed writes retain the prior library and navigation.

TargetStore version2 holds multiple pairs and an optional activeID. Version1 last-pair files migrate in memory; the next successful save writes all scopes in version2. Validate every scope before replacement and retain corrupted/unsupported files.

O opens the planner without requiring ModMenu. P captures an aimed, loaded portal (lower interior corner and axis), falling back to playerfeet, and activates a new named pair; while active it deactivates navigation. The list supports select, rename, delete, copy, manual input/paste and construction-height/orientation edits. Clipboard format explicitly includes source dimension: overworld|nether X Y Z. No chat/network actions.

Marker and frame preview are optional, disabled by default, respect HUD visibility and target dimension, use depth testing, and do not place blocks or load chunks. The minimal4x5 frame has14 blocks including corners; anchor is the lower-left interior block. Frame shown only nearby and within valid loaded build/border bounds.

Validate conversion/input/capture, immutable models, lifecycle/transaction failures, world separation, migration, locale placeholders and frame geometry; compile the actual26.2 renderer using installed Fabric APIs. Do not launch or automate UI/UX tests. Full build and independent code review precede commit and push. Preserve existing work in the requested checkout; no unrelated worktree migration or interim commits.

Integration refinement requested by the user: use native Minecraft screens/widgets and rebindable controls; link the planner from the existing mod settings as a third navigation entry. Keep explanations and editing in menus, with only navigation/coordinates persistently visible. Show the actual planner binding in brief contextual HUD hints. Retained settings drafts synchronize the planner-owned marker/frame preferences when returning from nested menus. UI/UX tests remain excluded.

Verification refinement: actual 26.2 ClientLevel.hasChunk always returns true. PortalWorldAccess therefore uses the real non-loading getChunk(FULL,false) lookup. Tests cover the misleading availability contract, negative chunk coordinates and complete-frame suppression across missing chunks. Windows replacement denial with NOSHARE_DELETE validates real post-serialization write failure and temporary-file cleanup in addition to corrupt-JSON preservation.

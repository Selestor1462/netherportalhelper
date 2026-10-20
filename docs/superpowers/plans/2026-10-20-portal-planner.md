# Portal planner implementation plan

Spec: ../specs/2026-10-20-portal-planner-design.md
Goal: complete the three approved planner features, test their logic, then commit/push feature/portal-planner.

- [x] Create the requested separate branch; preserve existing dirty1.3.0 work.
- [x] Write and observe failing domain/input/frame/session tests before their implementations.
- [x] Add PortalTarget, multi-entry TargetStore.Library and v1 migration; keep legacy compatibility APIs.
- [x] Add transactional PortalManager, explicit clipboard format and bounded loaded-portal capture.
- [x] Add native planner/editor screens and O/P integration; all seven locales.
- [x] Add pure frame geometry plus Fabric extraction/render callbacks with depth and visibility guards.
- [x] Run combined regression suite; repair integration failures and add tests for material findings.
- [x] Update README/changelog and verify JAR metadata/assets.
- [x] Obtain independent code review and resolve actionable defects.
- [ ] Run full build and diff checks, then commit once and push. Verify remote commit and clean working tree.

Global constraints: Minecraft26.2, Java25 bytecode, FabricAPI0.161.0+26.2, no new runtime dependencies; UI/UX tests excluded by user. Commit/push only after implementation and verification.
Review focus: corrupt sibling storage, inactive IDs/deletion/reconnect, negative coordinates and bounds, loaded portal capture limits, frame orientation/height and extraction snapshot, atomic rename+height edit, retained input on error, missing locale keys and formats.

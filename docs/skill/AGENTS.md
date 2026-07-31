# Nimbus Development Contract

This file defines the mandatory working rules for humans and AI agents modifying Nimbus.
Detailed architecture and usage information remains in `docs/`; do not duplicate it here.

## Read Before Editing

Read the documents relevant to the requested change before inspecting or modifying code:

- Base, packages, interfaces and refactoring: `docs/design-guidelines.md`
- Core, managers, modules, settings, config, commands and registration: `docs/core-manager-guide.md`
- HUD, Skija, rendering, native resources and lifecycle: `docs/skija-architecture.md`

When documentation and source disagree, verify the real implementation and third-party API,
then update the documentation in the same change. Never invent current behavior from a document.

## Mandatory Principles

- Inspect definitions, implementations and every caller before changing a public capability.
- Read Minecraft, Fabric, Skija or other dependency source before relying on an API contract.
- Ask the user when behavior depends on an unclear product or business choice.
- Reuse existing managers, interfaces and helpers before creating another abstraction.
- Preserve useful behavior during refactoring, including reflection and framework entry points.
- Keep `Core` limited to lifecycle orchestration and manager access.
- Keep managers explicit, cohesive, constructor-injected and lifecycle-safe.
- Keep Module settings automatically discovered; do not replace this with manual registration.
- Keep configuration centralized in `ConfigManager`; do not add migration behavior without a
  confirmed legacy format and an explicit user request.
- Enter Skija through `RenderManager.skija()` and respect `api`/`internal` ownership boundaries.
- Do not hide lifecycle, threading or ownership problems behind global state or empty wrappers.
- Do not perform unrelated cleanup, broad formatting or speculative future abstraction.
- Do not delete code only because text search finds no caller; check reflection, annotations,
  mixins, events and framework-managed entry points first.
- Preserve concurrent user changes and keep each diff scoped to the requested behavior.

## Working Procedure

1. Inspect `git status` and identify existing user changes.
2. Locate the real definitions, constructors, registrations and callers.
3. Record the behavior and capabilities that must remain unchanged.
4. Confirm ownership, lifecycle, thread boundary and failure behavior.
5. Present ambiguous or behavior-changing decisions to the user before implementation.
6. Make the smallest coherent change that follows the current package architecture.
7. Search again for stale callers, duplicate paths and bypasses after editing.
8. Compile and perform checks proportional to the affected runtime behavior.
9. Report modified locations, verification performed and anything not runtime-tested.

## Verification Floor

For Java changes, run at minimum:

```powershell
.\gradlew.bat compileJava --console=plain
git diff --check
```

Compilation alone is not enough for behavior involving Minecraft lifecycle, rendering, input,
configuration persistence or command completion. Provide a focused manual checklist for behavior
that cannot be exercised automatically in the current environment.

Never claim a change is correct, compatible or complete when the corresponding check was not run.

## Stop And Confirm

Stop and ask the user before proceeding when:

- the requested behavior has more than one materially different interpretation;
- a refactor would remove or weaken an existing capability;
- persistence formats, account data or migration rules would change;
- a new global entry point, manager, public interface or compatibility layer appears necessary;
- the real dependency API or Minecraft execution phase cannot be verified;
- completing the request requires modifying unrelated architecture.

Do not stop for naming and local implementation details that can be resolved from established
repository patterns without changing observable behavior.

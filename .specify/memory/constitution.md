<!--
Sync Impact Report
===================
Version change: 1.0.0 → 0.1.0
Modified principles:
  - "Resistance Constitution" → "Toyph Constitution" (title rename)
  - All principles updated to reflect general toolkit scope
Added sections: none
Removed sections: none
Templates requiring updates:
  - .specify/templates/plan-template.md        — ✅ no updates needed
  - .specify/templates/spec-template.md        — ✅ no updates needed
  - .specify/templates/tasks-template.md       — ✅ no updates needed
  - (no command files found in .specify/templates/commands/)
Follow-up TODOs: none
-->

# Toyph Constitution

## Core Principles

### I. Grid Fidelity

All rendering logic MUST target the 13x13 LED matrix (169 cells,
137 active in the circular layout of supported Nothing phones).
Brightness values MUST stay within the hardware range 0-4095.
Any frame produced by an animation MUST be exactly 169 integers;
out-of-bounds writes or reads are forbidden.

**Rationale**: The physical grid is fixed by hardware. Off-by-one
errors or oversized buffers cause silent visual corruption or SDK
crashes that are difficult to diagnose on-device.

### II. Battery Discipline

Because glyph toys run on the Always-on Display, every animation
MUST minimize CPU wake-ups and memory allocation. Frame intervals
MUST NOT be shorter than 30 ms. The toy configuration MUST define
an explicit rest interval (`animationIntervalMs`) between
animations so the LED controller is not driven continuously.
Allocations inside the animation loop (per-frame path) MUST be
limited to a single `IntArray.copyOf()` per frame; no object
creation beyond that.

**Rationale**: AOD runs on a resource-constrained co-processor.
Aggressive frame rates or GC pressure degrade standby battery
life — the single most visible quality metric for an AOD toy.

### III. SDK Compliance

The project MUST use the official GlyphMatrix Developer Kit SDK
and follow Nothing Developer Programme guidelines. The service
MUST register with the correct device constant for the target
phone. The API key MUST NOT be committed to version control (use
a placeholder value in the manifest and document the replacement
step). SDK version upgrades MUST be tested on a physical device
before merging.

**Rationale**: Nothing can revoke API keys or reject toys that
violate programme terms. Device constants change across phone
models; hardcoding the wrong one silently fails.

### IV. Animation as Interface

Every visual behavior MUST implement the `GlyphAnimation`
interface. The service MUST NOT contain frame-generation logic
directly; it only orchestrates the animation loop. Adding a new
animation MUST require only: (a) a new `GlyphAnimation`
implementation, (b) a new entry in the animation type mapping in
`GlyphToyService.createAnimation()`, and (c) a reference by name
in `toy-config.json`.

**Rationale**: A single extension point keeps the service thin
and makes animations independently testable without the SDK or
a physical device.

### V. Data-Driven Design

Glyph patterns (brightness maps) MUST be loaded from JSON asset
files, not hardcoded in Kotlin source. Animation classes MUST
accept pattern data as constructor parameters, never reference
asset paths directly. This separation allows pattern authoring
(via external tools or the Glyph Toy Builder) without touching
animation code.

**Rationale**: Pattern data changes frequently during design
iteration. Coupling it to source code forces recompilation and
increases merge conflicts when multiple patterns are developed
in parallel.

## Hardware Constraints

- **Supported devices**: Nothing phones with GlyphMatrix SDK
  support (currently Nothing Phone 4a Pro)
- **LED grid**: 13 columns x 13 rows = 169 cells (137 physically
  active in the circular mask)
- **Brightness**: 0 (off) to 4095 (maximum) per LED
- **Display mode**: AOD (Always-on Display) only — the toy
  service receives `EVENT_AOD` and `EVENT_CHANGE` callbacks
- **SDK**: GlyphMatrix Developer Kit 2.0 (`.aar` in `app/libs/`)
- Frame data is a flat `IntArray` of size 169, row-major order

Any code that generates or manipulates frames MUST respect these
constraints. Constants `GRID_SIZE` (13) and `MAX_BRIGHTNESS`
(4095) are defined in `GlyphAnimation.Companion` and MUST be
used instead of magic numbers.

## Development Workflow

1. **Build**: `./gradlew installDebug` — MUST compile without
   warnings. Lint errors block the build.
2. **Test on device**: Every animation change MUST be visually
   verified on a physical Nothing phone before merge. Emulators
   do not support the GlyphMatrix SDK.
3. **Pattern authoring**: New glyph JSON files go in
   `app/src/main/assets/glyphs/`. Each file MUST contain a
   top-level `brightness` array of exactly 169 integers.
4. **Animation development**: Implement `GlyphAnimation`, add
   a case in `GlyphToyService.createAnimation()`, and reference
   the type name in `toy-config.json`. No other files should
   need modification.
5. **Commits**: Each commit MUST be a single logical change
   (one animation, one pattern, one bug fix). Keep the history
   bisectable.

## Governance

This constitution is the authoritative reference for project
decisions. When a proposed change conflicts with a principle,
the principle takes precedence unless the constitution is
amended first.

**Amendment procedure**:

1. Propose the change with rationale in a PR description or
   issue.
2. Update this file with the new or revised text.
3. Increment the version following semantic versioning:
   - **MAJOR**: Principle removed or fundamentally redefined.
   - **MINOR**: New principle or section added, or material
     expansion of existing guidance.
   - **PATCH**: Clarifications, wording fixes, non-semantic
     refinements.
4. Update `LAST_AMENDED_DATE` to the date of the change.
5. Run the consistency propagation checklist (plan, spec, tasks,
   and command templates) to ensure alignment.

**Compliance review**: Any PR that adds or modifies animation
logic, service lifecycle code, or SDK integration MUST reference
the relevant principle(s) in its description.

**Version**: 0.2.0 | **Ratified**: 2026-04-07 | **Last Amended**: 2026-04-07

# Contributing to Toyph

Thanks for helping improve Toyph. Contributions are welcome for the rendering
pipeline, glyph content, docs, tooling, and bug fixes.

## Getting Started

1. Fork the repository.
2. Create a branch for your work.
3. Configure a local Nothing API key as described in [README.md](README.md).
4. Make focused changes.
5. Build with `./gradlew assembleDebug testDebugUnitTest`.
6. Verify visual behavior on a physical Nothing Phone before opening a PR.

## Development Setup

| Requirement | Notes |
|-------------|-------|
| Android Studio or JDK 17 | Android Studio's bundled runtime is fine |
| Nothing Phone (4a) Pro | Required for device validation |
| Nothing API key | Keep it in ignored local config only |

## What to Contribute

### Sequence items and content

- Add glyph JSON assets under `app/src/main/assets/glyphs/`
- Add or extend sequence item implementations under `app/src/main/java/com/toyph/app/sequence/`
- Update `ToyConfig.parse()` when introducing new config fields or item types

### Effects

- Implement `Effect` in `app/src/main/java/com/toyph/app/effects/`
- Use copy-on-write: call `frame.copyOf()`, mutate the copy, and return it
- Keep effect timing independent from sequence item changes

### Filters

- Implement `Filter` in `app/src/main/java/com/toyph/app/filters/`
- Return a new frame and clamp values to `0..4095`
- Keep filters stateless and composable

### Transitions

- Implement `Transition` in `app/src/main/java/com/toyph/app/transitions/`
- Blend source and target frames without mutating either input

### Docs and tooling

- Keep README examples aligned with the current `toy-config.json` schema
- Update `tools/README.md` when font generation or asset workflows change

## Project Constraints

- Every frame must be exactly `IntArray(169)`
- Pixel brightness must stay in the `0..4095` range
- Frame intervals must stay at or above `30ms`
- Per-frame code paths should allocate exactly one `IntArray.copyOf()`
- Do not introduce a second rendering coroutine
- Do not commit API keys or other local machine settings

## Pull Request Notes

- Reference related issues when applicable
- Keep changes scoped to one concern
- Include docs updates when public behavior or workflow changes

## Questions?

Open an issue with the behavior you are changing or the problem you hit.

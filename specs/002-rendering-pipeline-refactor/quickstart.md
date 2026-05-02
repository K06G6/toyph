# Quickstart: Layered Rendering Pipeline Refactor

**Branch**: `002-rendering-pipeline-refactor`

## Prerequisites

- Android Studio with AGP 9.1.0+
- Nothing Phone 4a Pro (physical device required — emulators don't support GlyphMatrix SDK)
- GlyphMatrix SDK 2.0 `.aar` in `app/libs/`

## Build & Run

```bash
./gradlew installDebug
```

## Config File

Edit `app/src/main/assets/toy-config.json`. The new format (v1.0.0) has separate sections:

```json
{
  "version": "1.0.0",
  "sequence": [
    { "type": "glyph", "path": "glyphs/mandalorian.json", "displayDurationMs": 10000 },
    { "type": "glyph", "path": "glyphs/rebel.json", "displayDurationMs": 8000 }
  ],
  "effects": [
    { "type": "SCANLINE", "frameIntervalMs": 50, "trailingIntervalMs": 10000 }
  ]
}
```

See [contracts/toy-config-v1.md](contracts/toy-config-v1.md) for the full schema.

## Adding New Components

### New Sequence Item Type

1. Create `app/src/main/java/com/toyph/app/sequence/MyItem.kt` implementing `SequenceItem`
2. Add a parsing case in `ToyConfig.parseSequenceItem()` for `"my_type"`
3. Add `"type": "my_type"` entries in `toy-config.json` sequence

### New Effect

1. Create `app/src/main/java/com/toyph/app/effects/MyEffect.kt` implementing `Effect`
2. Add a creation case in the effect factory for `"MY_EFFECT"`
3. Add `{"type": "MY_EFFECT", ...}` entries in the effects section

### New Filter

1. Create `app/src/main/java/com/toyph/app/filters/MyFilter.kt` implementing `Filter`
2. Add a parsing case in the filter factory for `"my_filter"`
3. Add `{"type": "my_filter", ...}` entries in the filters section

### New Transition

1. Create `app/src/main/java/com/toyph/app/transitions/MyTransition.kt` implementing `Transition`
2. Add a creation case in the transition factory for `"my_transition"`
3. Reference `"transition": {"type": "my_transition"}` in sequence items

## Testing

### Unit Tests (JVM)

```bash
./gradlew test
```

Pipeline logic, effects, filters, transitions, and config parsing are all testable on JVM without Android dependencies.

### On-Device Verification

Deploy to Nothing Phone 4a Pro. The toy runs on the Always-on Display — lock the phone to see it. Verify:
- Sequence items display in order with correct timing
- Effects overlay content without visual glitches
- Transitions blend smoothly between items
- Filters modify the final output consistently

## Key Architecture Concepts

**Rendering pipeline**: Single coroutine loop. Each tick:
1. `SequencePlayer` returns current content frame (handles item timing + transitions)
2. `EffectCycler` applies current effect to the content frame (handles effect cycling)
3. `FilterChain` applies all filters in order
4. Result sent to `GlyphMatrixManager.setMatrixFrame()`

**Independence**: Sequence and effects advance on their own clocks. The loop runs at the fastest needed frame interval.

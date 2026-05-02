package com.toyph.app

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class TransitionConfig(
    val type: String = "fade",
    val durationMs: Long = 1000L,
    val frameIntervalMs: Long = 50L
)

data class SequenceItemConfig(
    val type: String,
    val path: String? = null,
    val brightness: Double = 1.0,
    val displayDurationMs: Long = 5000L,
    val text: String? = null,
    val font: String? = null,
    val frameIntervalMs: Long = 80L,
    val transition: TransitionConfig? = null
)

data class EffectConfig(
    val type: String,
    val frameIntervalMs: Long,
    val trailingIntervalMs: Long = 0L
)

data class FilterConfig(
    val type: String,
    val value: Double = 1.0,
    val pixels: List<Pair<Int, Int>> = emptyList()
)

data class ToyConfig(
    val version: String,
    val defaultTransition: TransitionConfig,
    val sequence: List<SequenceItemConfig>,
    val effects: List<EffectConfig>,
    val filters: List<FilterConfig>
) {
    companion object {
        private const val TAG = "ToyConfig"
        private const val MIN_FRAME_INTERVAL = 30L
        private val REQUIRED_VERSION = intArrayOf(1, 0, 0)

        private fun parseVersion(v: String): IntArray =
            v.split(".").map { it.toIntOrNull() ?: 0 }.toIntArray()

        private fun isVersionSupported(v: String): Boolean {
            val parts = parseVersion(v)
            for (i in REQUIRED_VERSION.indices) {
                val actual = parts.getOrElse(i) { 0 }
                if (actual > REQUIRED_VERSION[i]) return true
                if (actual < REQUIRED_VERSION[i]) return false
            }
            return true
        }

        fun parse(json: String): ToyConfig {
            val obj = JSONObject(json)

            val version = obj.optString("version", "")
            if (version.isEmpty() || !isVersionSupported(version)) {
                val reqStr = REQUIRED_VERSION.joinToString(".")
                throw IllegalArgumentException(
                    "Config version ${version.ifEmpty { "(absent)" }} is no longer supported.\n" +
                    "Please update toy-config.json to version $reqStr.\n" +
                    "Key changes: \"images\" -> \"sequence\" with type field, " +
                    "\"animations\" split into \"effects\" section."
                )
            }

            val defaultTransition = parseOptionalTransitionConfig(obj, "defaultTransition")
            val sequence = parseOptionalArray(obj, "sequence", ::parseSequence)
            val effects = parseOptionalArray(obj, "effects", ::parseEffects)
            val filters = parseOptionalArray(obj, "filters", ::parseFilters)

            val knownKeys = setOf("version", "defaultTransition", "sequence", "effects", "filters")
            for (key in obj.keys()) {
                if (key !in knownKeys) {
                    Log.w(TAG, "Unknown config key: \"$key\" — ignoring")
                }
            }

            return ToyConfig(
                version = version,
                defaultTransition = defaultTransition,
                sequence = sequence,
                effects = effects,
                filters = filters
            )
        }

        private fun parseTransitionConfig(obj: JSONObject): TransitionConfig {
            return TransitionConfig(
                type = obj.optString("type", "fade"),
                durationMs = obj.optLong("durationMs", 1000L).coerceAtLeast(0),
                frameIntervalMs = obj.optLong("frameIntervalMs", 50L).coerceAtLeast(MIN_FRAME_INTERVAL)
            )
        }

        private fun parseOptionalTransitionConfig(obj: JSONObject, key: String): TransitionConfig {
            if (!obj.has(key) || obj.isNull(key)) {
                return TransitionConfig()
            }

            val transitionObj = obj.optJSONObject(key)
            if (transitionObj == null) {
                Log.w(TAG, "Config key \"$key\" is not an object — using defaults")
                return TransitionConfig()
            }

            return parseTransitionConfig(transitionObj)
        }

        private fun <T> parseOptionalArray(
            obj: JSONObject,
            key: String,
            parser: (JSONArray) -> List<T>
        ): List<T> {
            if (!obj.has(key) || obj.isNull(key)) {
                return emptyList()
            }

            val arr = obj.optJSONArray(key)
            if (arr == null) {
                Log.w(TAG, "Config key \"$key\" is not an array — treating as empty")
                return emptyList()
            }

            return parser(arr)
        }

        private fun parseOptionalItemTransition(obj: JSONObject, index: Int): TransitionConfig? {
            if (!obj.has("transition") || obj.isNull("transition")) {
                return null
            }

            val transitionObj = obj.optJSONObject("transition")
            if (transitionObj == null) {
                Log.w(TAG, "Sequence item at index $index has non-object transition — ignoring")
                return null
            }

            return parseTransitionConfig(transitionObj)
        }

        private fun parseSequence(arr: JSONArray): List<SequenceItemConfig> {
            val items = mutableListOf<SequenceItemConfig>()
            for (i in 0 until arr.length()) {
                try {
                    val item = arr.getJSONObject(i)
                    val type = item.getString("type")
                    val transition = parseOptionalItemTransition(item, i)
                    when (type) {
                        "glyph" -> items.add(SequenceItemConfig(
                            type = "glyph",
                            path = item.getString("path"),
                            brightness = item.optDouble("brightness", 1.0).coerceIn(0.0, 1.0),
                            displayDurationMs = item.optLong("displayDurationMs", 5000L).coerceAtLeast(100),
                            transition = transition
                        ))
                        "scrolling_text" -> items.add(SequenceItemConfig(
                            type = "scrolling_text",
                            text = item.getString("text"),
                            font = item.getString("font"),
                            brightness = item.optDouble("brightness", 1.0).coerceIn(0.0, 1.0),
                            frameIntervalMs = item.optLong("frameIntervalMs", 80L).coerceAtLeast(MIN_FRAME_INTERVAL),
                            transition = transition
                        ))
                        else -> Log.w(TAG, "Unknown sequence item type: \"$type\" at index $i — skipping")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed sequence item at index $i — skipping: ${e.message}")
                }
            }
            return items
        }

        private fun parseEffects(arr: JSONArray): List<EffectConfig> {
            val effects = mutableListOf<EffectConfig>()
            for (i in 0 until arr.length()) {
                try {
                    val e = arr.getJSONObject(i)
                    effects.add(EffectConfig(
                        type = e.getString("type"),
                        frameIntervalMs = e.getLong("frameIntervalMs").coerceAtLeast(MIN_FRAME_INTERVAL),
                        trailingIntervalMs = e.optLong("trailingIntervalMs", 0L).coerceAtLeast(0)
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed effect at index $i — skipping: ${e.message}")
                }
            }
            return effects
        }

        private fun parseFilters(arr: JSONArray): List<FilterConfig> {
            val filters = mutableListOf<FilterConfig>()
            for (i in 0 until arr.length()) {
                try {
                    val f = arr.getJSONObject(i)
                    val type = f.getString("type")
                    when (type) {
                        "brightness" -> filters.add(FilterConfig(
                            type = "brightness",
                            value = f.getDouble("value").coerceIn(0.0, 1.0)
                        ))
                        "dead_pixels" -> {
                            val pixArr = f.getJSONArray("pixels")
                            val pixels = mutableListOf<Pair<Int, Int>>()
                            for (j in 0 until pixArr.length()) {
                                val coord = pixArr.getJSONArray(j)
                                val row = coord.getInt(0).coerceIn(0, 12)
                                val col = coord.getInt(1).coerceIn(0, 12)
                                pixels.add(Pair(row, col))
                            }
                            filters.add(FilterConfig(type = "dead_pixels", pixels = pixels))
                        }
                        "blur" -> filters.add(FilterConfig(type = "blur"))
                        "pip_boy" -> filters.add(FilterConfig(
                            type = "pip_boy",
                            value = f.optDouble("value", 0.8).coerceIn(0.0, 1.0)
                        ))
                        else -> Log.w(TAG, "Unknown filter type: \"$type\" at index $i — skipping")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Malformed filter at index $i — skipping: ${e.message}")
                }
            }
            return filters
        }
    }
}

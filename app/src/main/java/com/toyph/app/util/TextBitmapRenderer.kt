package com.toyph.app.util

import android.content.res.AssetManager
import com.toyph.app.util.GridConstants.GRID_SIZE
import com.toyph.app.util.GridConstants.MAX_BRIGHTNESS
import org.json.JSONObject

class TextBitmapRenderer(assets: AssetManager, fontPath: String) {

    private val charHeight: Int
    private val topMargin: Int
    private val bottomMargin: Int
    private val spacing: Int
    private val glyphs: Map<String, CharGlyph>

    data class CharGlyph(val width: Int, val pixels: IntArray)

    init {
        val json = assets.open(fontPath).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        charHeight = obj.getInt("charHeight")
        topMargin = obj.getInt("topMargin")
        bottomMargin = obj.getInt("bottomMargin")
        spacing = obj.optInt("defaultSpacing", 1)

        require(charHeight + topMargin + bottomMargin == GRID_SIZE) {
            "Font '$fontPath': charHeight($charHeight) + topMargin($topMargin) + bottomMargin($bottomMargin) must equal GRID_SIZE($GRID_SIZE)"
        }

        val glyphMap = mutableMapOf<String, CharGlyph>()
        val glyphsObj = obj.getJSONObject("glyphs")
        for (key in glyphsObj.keys()) {
            val g = glyphsObj.getJSONObject(key)
            val w = g.getInt("width")
            val pixArr = g.getJSONArray("pixels")
            val pixels = IntArray(pixArr.length()) { pixArr.getInt(it) }
            require(pixels.size == charHeight * w) {
                "Font '$fontPath' glyph '$key': pixel array size ${pixels.size} != charHeight($charHeight) * width($w)"
            }
            glyphMap[key] = CharGlyph(w, pixels)
        }
        glyphs = glyphMap
    }

    fun renderText(text: String): Pair<IntArray, Int> {
        val charWidths = text.map { ch ->
            glyphs[ch.toString()]?.width ?: MISSING_CHAR_WIDTH
        }
        val textWidth = if (charWidths.isEmpty()) 0
        else charWidths.sum() + (charWidths.size - 1) * spacing

        val paddedWidth = GRID_SIZE + textWidth + GRID_SIZE
        val matrix = IntArray(GRID_SIZE * paddedWidth)

        var col = GRID_SIZE
        for (ch in text) {
            val glyph = glyphs[ch.toString()]
            if (glyph != null) {
                for (row in 0 until charHeight) {
                    for (px in 0 until glyph.width) {
                        val matrixRow = topMargin + row
                        val matrixCol = col + px
                        matrix[matrixRow * paddedWidth + matrixCol] = glyph.pixels[row * glyph.width + px]
                    }
                }
                col += glyph.width + spacing
            } else {
                col += MISSING_CHAR_WIDTH + spacing
            }
        }

        return Pair(matrix, paddedWidth)
    }

    companion object {
        private const val MISSING_CHAR_WIDTH = 5
    }
}

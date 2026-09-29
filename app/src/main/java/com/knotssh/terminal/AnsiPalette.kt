package com.knotssh.terminal

import kotlin.math.abs

/** Builds xterm 256-colour tables and maps 24-bit colours onto them. */
object AnsiPalette {

    private val CUBE_LEVELS = intArrayOf(0, 95, 135, 175, 215, 255)

    /** Indices 16-255 are fixed by the xterm specification and shared by every theme. */
    private val EXTENDED: IntArray = IntArray(240).also { table ->
        var index = 0
        for (r in CUBE_LEVELS) for (g in CUBE_LEVELS) for (b in CUBE_LEVELS) {
            table[index++] = argb(r, g, b)
        }
        for (i in 0 until 24) {
            val level = 8 + i * 10
            table[index++] = argb(level, level, level)
        }
    }

    fun build(background: Int, foreground: Int, base16: IntArray): TerminalPalette {
        require(base16.size == 16) { "A theme must define exactly 16 base colours" }
        val colors = IntArray(256)
        base16.copyInto(colors, 0)
        EXTENDED.copyInto(colors, 16)
        return TerminalPalette(background, foreground, colors)
    }

    /** Maps a 24-bit colour onto the closest palette slot so cells stay 4 bytes wide. */
    fun nearestIndex(r: Int, g: Int, b: Int): Int {
        val cubeIndex = 16 +
                36 * nearestCubeLevel(r) +
                6 * nearestCubeLevel(g) +
                nearestCubeLevel(b)
        val cubeDistance = distance(r, g, b, EXTENDED[cubeIndex - 16])

        val grayLevel = ((r + g + b) / 3 - 8).coerceIn(0, 238) / 10
        val grayIndex = 232 + grayLevel.coerceIn(0, 23)
        val grayDistance = distance(r, g, b, EXTENDED[grayIndex - 16])

        return if (grayDistance < cubeDistance) grayIndex else cubeIndex
    }

    private fun nearestCubeLevel(value: Int): Int {
        var best = 0
        var bestDelta = Int.MAX_VALUE
        for (i in CUBE_LEVELS.indices) {
            val delta = abs(CUBE_LEVELS[i] - value)
            if (delta < bestDelta) {
                bestDelta = delta
                best = i
            }
        }
        return best
    }

    private fun distance(r: Int, g: Int, b: Int, packed: Int): Int {
        val dr = r - ((packed shr 16) and 0xFF)
        val dg = g - ((packed shr 8) and 0xFF)
        val db = b - (packed and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

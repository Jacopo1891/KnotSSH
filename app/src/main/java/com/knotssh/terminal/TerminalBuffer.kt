package com.knotssh.terminal

/** Packed cell attributes. Colours are palette indices; [DEFAULT_COLOR] means "use theme colour". */
object CellStyle {
    const val DEFAULT_COLOR = 256

    private const val FG_SHIFT = 0
    private const val BG_SHIFT = 9
    private const val COLOR_MASK = 0x1FF

    const val BOLD = 1 shl 18
    const val ITALIC = 1 shl 19
    const val UNDERLINE = 1 shl 20
    const val INVERSE = 1 shl 21
    const val DIM = 1 shl 22
    const val STRIKE = 1 shl 23

    val DEFAULT: Int = pack(DEFAULT_COLOR, DEFAULT_COLOR, 0)

    fun pack(fg: Int, bg: Int, flags: Int): Int =
        ((fg and COLOR_MASK) shl FG_SHIFT) or ((bg and COLOR_MASK) shl BG_SHIFT) or flags

    fun foreground(style: Int): Int = (style ushr FG_SHIFT) and COLOR_MASK
    fun background(style: Int): Int = (style ushr BG_SHIFT) and COLOR_MASK
    fun flags(style: Int): Int = style and 0x00FC0000

    fun withForeground(style: Int, fg: Int): Int =
        (style and (COLOR_MASK shl FG_SHIFT).inv()) or ((fg and COLOR_MASK) shl FG_SHIFT)

    fun withBackground(style: Int, bg: Int): Int =
        (style and (COLOR_MASK shl BG_SHIFT).inv()) or ((bg and COLOR_MASK) shl BG_SHIFT)

    fun hasFlag(style: Int, flag: Int): Boolean = (style and flag) != 0
}

/**
 * One rendered terminal row. Instances pushed to scrollback are immutable and shared between
 * snapshots, so only the live screen rows are copied when the UI reads the buffer.
 */
class TerminalLine(
    val chars: CharArray,
    val styles: IntArray,
    val length: Int
) {
    /** True when every cell uses default attributes, allowing the UI to skip span building. */
    val isPlain: Boolean = run {
        var plain = true
        for (i in 0 until length) {
            if (styles[i] != CellStyle.DEFAULT) {
                plain = false
                break
            }
        }
        plain
    }

    fun text(): String = String(chars, 0, length)

    /**
     * Content equality, not identity: the screen rows are re-frozen on every frame, and without
     * this the UI would rebuild the annotated text of every visible row 60 times a second even
     * when nothing on that row changed.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalLine || other.length != length) return false
        for (i in 0 until length) {
            if (chars[i] != other.chars[i] || styles[i] != other.styles[i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = length
        for (i in 0 until length) {
            result = 31 * result + chars[i].code
            result = 31 * result + styles[i]
        }
        return result
    }

    companion object {
        val EMPTY = TerminalLine(CharArray(0), IntArray(0), 0)
    }
}

/** Immutable view of the emulator state handed to the UI layer. */
class TerminalSnapshot(
    val scrollback: List<TerminalLine>,
    val screen: List<TerminalLine>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val revision: Long,
    /** Full-screen app (vim, less, ...): there is no scrollback to pan through. */
    val altScreen: Boolean = false
) {
    val totalLines: Int get() = scrollback.size + screen.size

    fun lineAt(index: Int): TerminalLine =
        if (index < scrollback.size) scrollback[index] else screen[index - scrollback.size]

    companion object {
        val EMPTY = TerminalSnapshot(emptyList(), listOf(TerminalLine.EMPTY), 0, 0, true, 0)
    }
}

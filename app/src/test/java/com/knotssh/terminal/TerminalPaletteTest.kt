package com.knotssh.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPaletteTest {

    @Test
    fun `every theme defines a full 256 entry table`() {
        TerminalTheme.entries.forEach { theme ->
            val palette = theme.palette
            assertEquals(
                "${theme.name} index 255",
                0xFFEEEEEE.toInt(),
                palette.color(255)
            )
            assertTrue("${theme.name} background opaque", palette.background ushr 24 == 0xFF)
            assertTrue("${theme.name} foreground opaque", palette.foreground ushr 24 == 0xFF)
        }
    }

    @Test
    fun `themes differ in their base colours but share the xterm cube`() {
        val dark = TerminalTheme.DARK.palette
        val solarized = TerminalTheme.SOLARIZED_DARK.palette

        assertTrue(dark.color(1) != solarized.color(1))
        // Index 196 is pure red in the 6x6x6 cube for every terminal.
        assertEquals(dark.color(196), solarized.color(196))
        assertEquals(0xFFFF0000.toInt(), dark.color(196))
    }

    @Test
    fun `palette indices are clamped instead of throwing`() {
        val palette = TerminalTheme.DARK.palette
        assertEquals(palette.color(0), palette.color(-5))
        assertEquals(palette.color(255), palette.color(999))
    }

    @Test
    fun `truecolor maps onto the shared cube`() {
        assertEquals(196, AnsiPalette.nearestIndex(255, 0, 0))
        assertEquals(21, AnsiPalette.nearestIndex(0, 0, 255))
    }

    @Test
    fun `bracketed paste mode follows the DECSET toggle`() {
        val emulator = TerminalEmulator(40, 10, 100)
        fun feed(text: String) {
            val bytes = text.toByteArray()
            emulator.write(bytes, bytes.size)
        }

        assertFalse(emulator.bracketedPaste)
        feed("\u001B[?2004h")
        assertTrue(emulator.bracketedPaste)
        feed("\u001B[?2004l")
        assertFalse(emulator.bracketedPaste)
    }

    @Test
    fun `reset clears bracketed paste`() {
        val emulator = TerminalEmulator(40, 10, 100)
        val bytes = "\u001B[?2004h".toByteArray()
        emulator.write(bytes, bytes.size)
        assertTrue(emulator.bracketedPaste)

        emulator.reset()
        assertFalse(emulator.bracketedPaste)
    }
}

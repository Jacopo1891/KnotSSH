package com.knotssh.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {

    private fun emulator(columns: Int = 20, rows: Int = 5, scrollback: Int = 100) =
        TerminalEmulator(columns, rows, scrollback)

    private fun TerminalEmulator.feed(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        write(bytes, bytes.size)
    }

    private fun TerminalEmulator.screenText(): List<String> =
        snapshot().screen.map { it.text() }

    @Test
    fun `plain text lands on the first row`() {
        val emulator = emulator()
        emulator.feed("hello")
        assertEquals("hello", emulator.screenText()[0])
    }

    @Test
    fun `carriage return rewrites the current row instead of appending`() {
        val emulator = emulator()
        emulator.feed("aaaa\rbb")
        assertEquals("bbaa", emulator.screenText()[0])
    }

    @Test
    fun `backspace moves the cursor without deleting`() {
        val emulator = emulator()
        emulator.feed("abc\b\bX")
        assertEquals("aXc", emulator.screenText()[0])
    }

    @Test
    fun `line feed advances rows and keeps the column`() {
        val emulator = emulator()
        emulator.feed("ab\r\ncd")
        val screen = emulator.screenText()
        assertEquals("ab", screen[0])
        assertEquals("cd", screen[1])
    }

    @Test
    fun `sgr sets foreground colour on the styled run only`() {
        val emulator = emulator()
        emulator.feed("\u001B[31mRED\u001B[0mx")
        val line = emulator.snapshot().screen[0]
        assertEquals(1, CellStyle.foreground(line.styles[0]))
        assertEquals(CellStyle.DEFAULT_COLOR, CellStyle.foreground(line.styles[3]))
    }

    @Test
    fun `256 colour and truecolour map into the palette`() {
        val emulator = emulator()
        emulator.feed("\u001B[38;5;208mA\u001B[38;2;255;0;0mB")
        val line = emulator.snapshot().screen[0]
        assertEquals(208, CellStyle.foreground(line.styles[0]))
        assertTrue(CellStyle.foreground(line.styles[1]) in 16..255)
    }

    @Test
    fun `erase in line clears from the cursor to the end`() {
        val emulator = emulator()
        emulator.feed("abcdef\u001B[4G\u001B[K")
        assertEquals("abc", emulator.screenText()[0])
    }

    @Test
    fun `cursor position addressing is one based`() {
        val emulator = emulator()
        emulator.feed("\u001B[3;5HX")
        assertEquals("    X", emulator.screenText()[2])
    }

    @Test
    fun `clear screen empties every row without touching scrollback`() {
        val emulator = emulator()
        emulator.feed("one\r\ntwo\r\n\u001B[2J")
        assertTrue(emulator.screenText().all { it.isEmpty() })
    }

    @Test
    fun `rows scrolled off the top reach scrollback`() {
        val emulator = emulator(rows = TerminalEmulator.MIN_ROWS)
        emulator.feed("l1\r\nl2\r\nl3\r\nl4\r\nl5\r\nl6")
        val snapshot = emulator.snapshot()
        assertEquals(listOf("l1", "l2"), snapshot.scrollback.map { it.text() })
        assertEquals("l6", snapshot.screen.last().text())
    }

    @Test
    fun `scrollback never exceeds the configured limit`() {
        val emulator = emulator(rows = TerminalEmulator.MIN_ROWS, scrollback = 5)
        repeat(50) { emulator.feed("line$it\r\n") }
        assertEquals(5, emulator.snapshot().scrollback.size)
    }

    @Test
    fun `cached scrollback view is invalidated when it is cleared`() {
        val emulator = emulator(rows = TerminalEmulator.MIN_ROWS)
        repeat(10) { emulator.feed("line$it\r\n") }
        assertTrue(emulator.snapshot().scrollback.isNotEmpty())

        emulator.clearScrollback()
        assertTrue(emulator.snapshot().scrollback.isEmpty())

        emulator.feed("after\r\n".repeat(10))
        assertTrue(emulator.snapshot().scrollback.isNotEmpty())
    }

    @Test
    fun `revision only advances when output is written`() {
        val emulator = emulator()
        val idle = emulator.currentRevision
        assertEquals(idle, emulator.currentRevision)
        emulator.feed("x")
        assertTrue(emulator.currentRevision > idle)
    }

    @Test
    fun `alt screen restores the primary buffer on exit`() {
        val emulator = emulator()
        emulator.feed("main")
        emulator.feed("\u001B[?1049h")
        emulator.feed("\u001B[HOVERLAY")
        assertEquals("OVERLAY", emulator.screenText()[0])
        emulator.feed("\u001B[?1049l")
        assertEquals("main", emulator.screenText()[0])
    }

    @Test
    fun `snapshot reports the alternate screen`() {
        val emulator = emulator()
        assertFalse(emulator.snapshot().altScreen)
        emulator.feed("\u001B[?1049h")
        assertTrue(emulator.snapshot().altScreen)
        emulator.feed("\u001B[?1049l")
        assertFalse(emulator.snapshot().altScreen)
    }

    @Test
    fun `leaving an editor puts the cursor back where it was opened`() {
        val emulator = emulator(columns = 40, rows = 10)
        emulator.feed("line1\r\nline2\r\n$ nano file")
        val before = emulator.snapshot()

        emulator.feed("\u001B[?1049h")
        emulator.feed("\u001B[HGNU nano\r\ntesto del file")
        emulator.feed("\u001B[?1049l")

        val after = emulator.snapshot()
        assertEquals(before.cursorRow, after.cursorRow)
        assertEquals(before.cursorCol, after.cursorCol)
        assertEquals("line1", after.screen[0].text())
        assertEquals("$ nano file", after.screen[2].text())
    }

    @Test
    fun `utf8 sequences split across chunks decode correctly`() {
        val emulator = emulator()
        val bytes = "è".toByteArray(Charsets.UTF_8)
        emulator.write(byteArrayOf(bytes[0]), 1)
        emulator.write(byteArrayOf(bytes[1]), 1)
        assertEquals("è", emulator.screenText()[0])
    }

    @Test
    fun `auto wrap moves past the right margin onto the next row`() {
        val width = TerminalEmulator.MIN_COLUMNS
        val emulator = emulator(columns = width)
        emulator.feed("a".repeat(width) + "bc")
        val screen = emulator.screenText()
        assertEquals("a".repeat(width), screen[0])
        assertEquals("bc", screen[1])
    }

    @Test
    fun `application cursor key mode is reported to the input layer`() {
        val emulator = emulator()
        assertEquals(false, emulator.applicationCursorKeys)
        emulator.feed("\u001B[?1h")
        assertEquals(true, emulator.applicationCursorKeys)
        emulator.feed("\u001B[?1l")
        assertEquals(false, emulator.applicationCursorKeys)
    }

    @Test
    fun `cursor position report is answered on the write channel`() {
        val responses = mutableListOf<String>()
        val emulator = TerminalEmulator(20, 5, 10, onResponse = { responses += String(it) })
        val bytes = "\u001B[2;3H\u001B[6n".toByteArray()
        emulator.write(bytes, bytes.size)
        assertEquals(listOf("\u001B[2;3R"), responses)
    }

    @Test
    fun `bell is reported instead of being printed`() {
        var bells = 0
        val emulator = TerminalEmulator(20, 5, 10, onBell = { bells++ })
        val bytes = "a\u0007b".toByteArray()
        emulator.write(bytes, bytes.size)
        assertEquals(1, bells)
        assertEquals("ab", emulator.snapshot().screen[0].text())
    }

    @Test
    fun `osc window title is extracted and not rendered`() {
        var title: String? = null
        val emulator = TerminalEmulator(20, 5, 10, onTitle = { title = it })
        val bytes = "\u001B]0;my-host\u0007ok".toByteArray()
        emulator.write(bytes, bytes.size)
        assertEquals("my-host", title)
        assertEquals("ok", emulator.snapshot().screen[0].text())
    }

    @Test
    fun `resize keeps the rows around the cursor`() {
        val emulator = emulator(columns = 20, rows = 5)
        emulator.feed("r1\r\nr2\r\nr3")
        emulator.resize(10, 3)
        assertEquals("r3", emulator.snapshot().screen[2].text())
    }

    @Test
    fun `resizing inside the alternate screen still restores the primary buffer`() {
        val emulator = emulator(columns = 40, rows = 10)
        emulator.feed("primary")
        emulator.feed("\u001B[?1049h")
        emulator.feed("\u001B[Hoverlay")

        // Mirrors the keyboard opening while a full-screen program is running.
        emulator.resize(30, 6)
        assertEquals("overlay", emulator.screenText()[0])

        emulator.feed("\u001B[?1049l")
        assertEquals("primary", emulator.screenText()[0])
    }

    @Test
    fun `delete and insert characters shift the row`() {
        val emulator = emulator()
        emulator.feed("abcdef\u001B[1G\u001B[2P")
        assertEquals("cdef", emulator.screenText()[0])
        emulator.feed("\u001B[1G\u001B[2@")
        assertEquals("  cdef", emulator.screenText()[0])
    }
}

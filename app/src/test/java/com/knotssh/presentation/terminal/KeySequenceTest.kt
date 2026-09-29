package com.knotssh.presentation.terminal

import com.knotssh.domain.model.KeyModifier
import com.knotssh.presentation.settings.buildKeySequence
import com.knotssh.presentation.settings.defaultLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeySequenceTest {

    @Test
    fun `ctrl letters collapse to their control code`() {
        assertEquals('\u0003', 'c'.toControlChar())
        assertEquals('\u0003', 'C'.toControlChar())
        assertEquals('\u0004', 'd'.toControlChar())
        assertEquals('\u001A', 'z'.toControlChar())
        assertEquals('\u0001', 'a'.toControlChar())
    }

    @Test
    fun `ctrl handles the conventional aliases`() {
        assertEquals('\u0000', ' '.toControlChar())
        assertEquals('\u007F', '?'.toControlChar())
        assertEquals('\u001B', '['.toControlChar())
        assertEquals('\u001C', '\\'.toControlChar())
    }

    @Test
    fun `characters without a control mapping are left alone`() {
        assertEquals('5', '5'.toControlChar())
        assertEquals('è', 'è'.toControlChar())
    }

    @Test
    fun `alt prefixes the character with escape`() {
        assertEquals("\u001Bb", buildKeySequence(KeyModifier.ALT, "b"))
    }

    @Test
    fun `ctrl alt combines both transformations`() {
        assertEquals("\u001B\u0003", buildKeySequence(KeyModifier.CTRL_ALT, "c"))
    }

    @Test
    fun `no modifier sends the bare character`() {
        assertEquals("/", buildKeySequence(KeyModifier.NONE, "/"))
    }

    @Test
    fun `a key needs exactly one base character`() {
        assertNull(buildKeySequence(KeyModifier.CTRL, ""))
        assertNull(buildKeySequence(KeyModifier.CTRL, "ab"))
    }

    @Test
    fun `default labels follow the usual terminal notation`() {
        assertEquals("^C", defaultLabel(KeyModifier.CTRL, "c"))
        assertEquals("⎇B", defaultLabel(KeyModifier.ALT, "b"))
        assertEquals("^⎇C", defaultLabel(KeyModifier.CTRL_ALT, "c"))
        assertEquals("X", defaultLabel(KeyModifier.NONE, "x"))
    }
}

package com.knotssh.terminal

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Incremental xterm-compatible terminal emulator.
 *
 * Maintains a fixed `rows x columns` screen grid plus a bounded scrollback ring, so memory usage
 * is capped regardless of how much output the remote host produces. All mutation happens on a
 * single writer thread; [snapshot] produces an immutable view for rendering.
 */
class TerminalEmulator(
    columns: Int,
    rows: Int,
    scrollbackLimit: Int,
    private val onResponse: (ByteArray) -> Unit = {},
    private val onBell: () -> Unit = {},
    private val onTitle: (String) -> Unit = {}
) {
    var columns: Int = columns.coerceAtLeast(MIN_COLUMNS)
        private set
    var rows: Int = rows.coerceAtLeast(MIN_ROWS)
        private set
    var scrollbackLimit: Int = scrollbackLimit.coerceAtLeast(0)
        private set

    private var chars: Array<CharArray> = Array(this.rows) { CharArray(this.columns) { ' ' } }
    private var styles: Array<IntArray> = Array(this.rows) { IntArray(this.columns) { CellStyle.DEFAULT } }

    private var altChars: Array<CharArray>? = null
    private var altStyles: Array<IntArray>? = null

    private val scrollback = ArrayDeque<TerminalLine>()

    /** Rebuilt only when [scrollback] actually changes, so idle frames copy nothing. */
    private var scrollbackView: List<TerminalLine> = emptyList()
    private var scrollbackDirty = false

    private var cursorRow = 0
    private var cursorCol = 0
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var savedStyle = CellStyle.DEFAULT

    private var currentStyle = CellStyle.DEFAULT
    private var scrollTop = 0
    private var scrollBottom = this.rows - 1
    private var autoWrap = true
    private var wrapPending = false
    private var cursorVisible = true
    private var altScreenActive = false
    private var insertMode = false

    /** DECCKM: arrow keys must emit SS3 sequences while set. */
    @Volatile
    var applicationCursorKeys: Boolean = false
        private set

    /** DECSET 2004: pasted text must be wrapped in bracket markers. */
    @Volatile
    var bracketedPaste: Boolean = false
        private set

    private var revision = 0L

    /** Cheap change probe so the renderer can skip building a snapshot when nothing moved. */
    val currentRevision: Long get() = revision

    // --- Parser state ---------------------------------------------------------------------

    private enum class State { GROUND, ESCAPE, CSI, OSC, OSC_ESC, CHARSET, IGNORE_STRING, IGNORE_STRING_ESC }

    private var state = State.GROUND
    private val params = IntArray(MAX_PARAMS)
    private var paramCount = 0
    private var paramActive = false
    private var privateMarker = 0.toChar()
    private val oscBuffer = StringBuilder()

    // --- Incremental UTF-8 decoding ------------------------------------------------------

    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private val carry = ByteArray(8)
    private var carryLength = 0

    fun write(data: ByteArray, length: Int) {
        if (length <= 0) return

        val input: ByteBuffer = if (carryLength == 0) {
            ByteBuffer.wrap(data, 0, length)
        } else {
            ByteBuffer.allocate(carryLength + length).apply {
                put(carry, 0, carryLength)
                put(data, 0, length)
                flip()
            }
        }
        carryLength = 0

        val out = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, out, false)
        out.flip()

        while (out.hasRemaining()) process(out.get())

        // An incomplete multi-byte sequence at the tail is carried into the next chunk.
        val leftover = input.remaining()
        if (leftover in 1..carry.size) {
            input.get(carry, 0, leftover)
            carryLength = leftover
        }
        revision++
    }

    private fun process(c: Char) {
        when (state) {
            State.GROUND -> ground(c)
            State.ESCAPE -> escape(c)
            State.CSI -> csi(c)
            State.OSC -> osc(c)
            State.OSC_ESC -> {
                state = State.GROUND
                if (c == '\\') finishOsc() else ground(c)
            }
            State.CHARSET -> state = State.GROUND
            State.IGNORE_STRING -> if (c == ESC) state = State.IGNORE_STRING_ESC else if (c == BEL) state = State.GROUND
            State.IGNORE_STRING_ESC -> state = if (c == '\\') State.GROUND else State.IGNORE_STRING
        }
    }

    private fun ground(c: Char) {
        when (c) {
            BEL -> onBell()
            '\b' -> { wrapPending = false; if (cursorCol > 0) cursorCol-- }
            '\t' -> { wrapPending = false; cursorCol = nextTabStop(cursorCol) }
            '\n', '\u000B', '\u000C' -> { wrapPending = false; lineFeed() }
            '\r' -> { wrapPending = false; cursorCol = 0 }
            ESC -> { state = State.ESCAPE; resetParams() }
            '\u000E', '\u000F' -> Unit // SO / SI charset shifts: single-charset terminal
            DEL -> Unit
            else -> if (c >= ' ') putChar(c)
        }
    }

    private fun escape(c: Char) {
        when (c) {
            '[' -> { state = State.CSI; resetParams() }
            ']' -> { state = State.OSC; oscBuffer.setLength(0) }
            '(', ')', '*', '+' -> state = State.CHARSET
            'P', '^', '_' -> state = State.IGNORE_STRING // DCS / PM / APC
            '7' -> { saveCursor(); state = State.GROUND }
            '8' -> { restoreCursor(); state = State.GROUND }
            'D' -> { lineFeed(); state = State.GROUND }
            'E' -> { cursorCol = 0; lineFeed(); state = State.GROUND }
            'M' -> { reverseIndex(); state = State.GROUND }
            'c' -> { hardReset(); state = State.GROUND }
            '=' , '>' -> state = State.GROUND // keypad modes
            else -> state = State.GROUND
        }
    }

    private fun csi(c: Char) {
        when {
            c in '0'..'9' -> {
                if (paramCount < MAX_PARAMS) {
                    params[paramCount] = (params[paramCount] * 10 + (c - '0')).coerceAtMost(MAX_PARAM_VALUE)
                    paramActive = true
                }
            }
            c == ';' -> {
                if (paramCount < MAX_PARAMS - 1) paramCount++
                paramActive = true
            }
            c == '?' || c == '<' || c == '=' || c == '>' -> privateMarker = c
            c == ':' || c == ' ' || c == '!' || c == '"' || c == '$' || c == '\'' -> Unit
            else -> {
                if (paramActive) paramCount++
                dispatchCsi(c)
                state = State.GROUND
            }
        }
    }

    private fun osc(c: Char) {
        when (c) {
            BEL -> { finishOsc(); state = State.GROUND }
            ESC -> state = State.OSC_ESC
            else -> if (oscBuffer.length < MAX_OSC_LENGTH) oscBuffer.append(c)
        }
    }

    private fun finishOsc() {
        val content = oscBuffer.toString()
        oscBuffer.setLength(0)
        val separator = content.indexOf(';')
        if (separator <= 0) return
        // OSC 0/1/2 set the window/icon title.
        when (content.substring(0, separator)) {
            "0", "1", "2" -> onTitle(content.substring(separator + 1))
        }
    }

    private fun resetParams() {
        params.fill(0)
        paramCount = 0
        paramActive = false
        privateMarker = 0.toChar()
    }

    private fun param(index: Int, fallback: Int): Int =
        if (index < paramCount && params[index] != 0) params[index] else fallback

    private fun dispatchCsi(final: Char) {
        when (final) {
            'A' -> moveCursor(-param(0, 1), 0)
            'B' -> moveCursor(param(0, 1), 0)
            'C' -> moveCursor(0, param(0, 1))
            'D' -> moveCursor(0, -param(0, 1))
            'E' -> { cursorCol = 0; moveCursor(param(0, 1), 0) }
            'F' -> { cursorCol = 0; moveCursor(-param(0, 1), 0) }
            'G', '`' -> setCursor(cursorRow, param(0, 1) - 1)
            'H', 'f' -> setCursor(param(0, 1) - 1, param(1, 1) - 1)
            'd' -> setCursor(param(0, 1) - 1, cursorCol)
            'J' -> eraseInDisplay(if (paramCount > 0) params[0] else 0)
            'K' -> eraseInLine(if (paramCount > 0) params[0] else 0)
            'L' -> insertLines(param(0, 1))
            'M' -> deleteLines(param(0, 1))
            'P' -> deleteChars(param(0, 1))
            '@' -> insertChars(param(0, 1))
            'X' -> eraseChars(param(0, 1))
            'S' -> scrollUp(param(0, 1))
            'T' -> scrollDown(param(0, 1))
            'm' -> applySgr()
            'r' -> setScrollRegion()
            'h' -> setMode(true)
            'l' -> setMode(false)
            's' -> saveCursor()
            'u' -> restoreCursor()
            'n' -> deviceStatusReport()
            'c' -> onResponse(DEVICE_ATTRIBUTES)
            else -> Unit
        }
    }

    // --- Modes ----------------------------------------------------------------------------

    private fun setMode(enable: Boolean) {
        val count = maxOf(paramCount, 1)
        for (i in 0 until count) {
            val code = if (i < paramCount) params[i] else 0
            if (privateMarker == '?') {
                when (code) {
                    1 -> applicationCursorKeys = enable
                    7 -> autoWrap = enable
                    25 -> cursorVisible = enable
                    47, 1047 -> setAltScreen(enable)
                    // DECSET 1049 brackets the switch with DECSC/DECRC: the cursor must be
                    // captured before the buffers swap, or leaving the editor drops the caret
                    // at the top of the restored screen.
                    1049 -> when {
                        enable && !altScreenActive -> {
                            saveCursor()
                            setAltScreen(true)
                        }
                        !enable && altScreenActive -> {
                            setAltScreen(false)
                            restoreCursor()
                        }
                    }
                    2004 -> bracketedPaste = enable
                }
            } else if (code == 4) {
                insertMode = enable
            }
        }
    }

    private fun setAltScreen(enable: Boolean) {
        if (enable == altScreenActive) return
        if (enable) {
            altChars = chars
            altStyles = styles
            chars = blankChars(columns, rows)
            styles = blankStyles(columns, rows)
        } else {
            altChars?.let { chars = it }
            altStyles?.let { styles = it }
            altChars = null
            altStyles = null
        }
        altScreenActive = enable
        scrollTop = 0
        scrollBottom = rows - 1
        clampCursor()
    }

    private fun setScrollRegion() {
        val top = (param(0, 1) - 1).coerceIn(0, rows - 1)
        val bottom = (param(1, rows) - 1).coerceIn(0, rows - 1)
        if (top < bottom) {
            scrollTop = top
            scrollBottom = bottom
        } else {
            scrollTop = 0
            scrollBottom = rows - 1
        }
        setCursor(0, 0)
    }

    private fun deviceStatusReport() {
        when (if (paramCount > 0) params[0] else 0) {
            5 -> onResponse("\u001B[0n".toByteArray(Charsets.US_ASCII))
            6 -> onResponse("\u001B[${cursorRow + 1};${cursorCol + 1}R".toByteArray(Charsets.US_ASCII))
        }
    }

    // --- SGR ------------------------------------------------------------------------------

    private fun applySgr() {
        if (paramCount == 0) {
            currentStyle = CellStyle.DEFAULT
            return
        }
        var i = 0
        while (i < paramCount) {
            when (val code = params[i]) {
                0 -> currentStyle = CellStyle.DEFAULT
                1 -> currentStyle = currentStyle or CellStyle.BOLD
                2 -> currentStyle = currentStyle or CellStyle.DIM
                3 -> currentStyle = currentStyle or CellStyle.ITALIC
                4 -> currentStyle = currentStyle or CellStyle.UNDERLINE
                7 -> currentStyle = currentStyle or CellStyle.INVERSE
                9 -> currentStyle = currentStyle or CellStyle.STRIKE
                21, 22 -> currentStyle = currentStyle and (CellStyle.BOLD or CellStyle.DIM).inv()
                23 -> currentStyle = currentStyle and CellStyle.ITALIC.inv()
                24 -> currentStyle = currentStyle and CellStyle.UNDERLINE.inv()
                27 -> currentStyle = currentStyle and CellStyle.INVERSE.inv()
                29 -> currentStyle = currentStyle and CellStyle.STRIKE.inv()
                in 30..37 -> currentStyle = CellStyle.withForeground(currentStyle, code - 30)
                in 40..47 -> currentStyle = CellStyle.withBackground(currentStyle, code - 40)
                in 90..97 -> currentStyle = CellStyle.withForeground(currentStyle, code - 90 + 8)
                in 100..107 -> currentStyle = CellStyle.withBackground(currentStyle, code - 100 + 8)
                39 -> currentStyle = CellStyle.withForeground(currentStyle, CellStyle.DEFAULT_COLOR)
                49 -> currentStyle = CellStyle.withBackground(currentStyle, CellStyle.DEFAULT_COLOR)
                38, 48 -> i = applyExtendedColor(i, isForeground = code == 38)
            }
            i++
        }
    }

    /** Handles `38;5;n`, `38;2;r;g;b` and returns the index of the last consumed parameter. */
    private fun applyExtendedColor(start: Int, isForeground: Boolean): Int {
        if (start + 1 >= paramCount) return start
        return when (params[start + 1]) {
            5 -> {
                if (start + 2 >= paramCount) return start + 1
                val index = params[start + 2].coerceIn(0, 255)
                currentStyle = if (isForeground) CellStyle.withForeground(currentStyle, index)
                else CellStyle.withBackground(currentStyle, index)
                start + 2
            }
            2 -> {
                if (start + 4 >= paramCount) return paramCount - 1
                val index = AnsiPalette.nearestIndex(
                    params[start + 2].coerceIn(0, 255),
                    params[start + 3].coerceIn(0, 255),
                    params[start + 4].coerceIn(0, 255)
                )
                currentStyle = if (isForeground) CellStyle.withForeground(currentStyle, index)
                else CellStyle.withBackground(currentStyle, index)
                start + 4
            }
            else -> start + 1
        }
    }

    // --- Screen primitives ------------------------------------------------------------------

    private fun putChar(c: Char) {
        if (wrapPending) {
            cursorCol = 0
            lineFeed()
            wrapPending = false
        }
        if (cursorCol >= columns) {
            if (!autoWrap) cursorCol = columns - 1 else { cursorCol = 0; lineFeed() }
        }
        if (insertMode) insertChars(1)

        chars[cursorRow][cursorCol] = c
        styles[cursorRow][cursorCol] = currentStyle

        if (cursorCol == columns - 1 && autoWrap) wrapPending = true else cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) scrollUp(1) else if (cursorRow < rows - 1) cursorRow++
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) scrollDown(1) else if (cursorRow > 0) cursorRow--
    }

    private fun scrollUp(count: Int) {
        repeat(count.coerceIn(1, rows)) {
            // Only the primary screen's top region contributes to scrollback.
            if (!altScreenActive && scrollTop == 0) pushToScrollback(chars[scrollTop], styles[scrollTop])
            val rowChars = chars[scrollTop]
            val rowStyles = styles[scrollTop]
            for (r in scrollTop until scrollBottom) {
                chars[r] = chars[r + 1]
                styles[r] = styles[r + 1]
            }
            rowChars.fill(' ')
            rowStyles.fill(CellStyle.DEFAULT)
            chars[scrollBottom] = rowChars
            styles[scrollBottom] = rowStyles
        }
    }

    private fun scrollDown(count: Int) {
        repeat(count.coerceIn(1, rows)) {
            val rowChars = chars[scrollBottom]
            val rowStyles = styles[scrollBottom]
            for (r in scrollBottom downTo scrollTop + 1) {
                chars[r] = chars[r - 1]
                styles[r] = styles[r - 1]
            }
            rowChars.fill(' ')
            rowStyles.fill(CellStyle.DEFAULT)
            chars[scrollTop] = rowChars
            styles[scrollTop] = rowStyles
        }
    }

    private fun pushToScrollback(rowChars: CharArray, rowStyles: IntArray) {
        if (scrollbackLimit == 0) return
        scrollback.addLast(freeze(rowChars, rowStyles))
        while (scrollback.size > scrollbackLimit) scrollback.removeFirst()
        scrollbackDirty = true
    }

    /** Trims trailing blanks so scrollback memory tracks actual content, not terminal width. */
    private fun freeze(rowChars: CharArray, rowStyles: IntArray): TerminalLine {
        var end = minOf(rowChars.size, columns)
        while (end > 0 && rowChars[end - 1] == ' ' && rowStyles[end - 1] == CellStyle.DEFAULT) end--
        if (end == 0) return TerminalLine.EMPTY
        return TerminalLine(rowChars.copyOf(end), rowStyles.copyOf(end), end)
    }

    private fun moveCursor(deltaRow: Int, deltaCol: Int) {
        wrapPending = false
        cursorRow = (cursorRow + deltaRow).coerceIn(0, rows - 1)
        cursorCol = (cursorCol + deltaCol).coerceIn(0, columns - 1)
    }

    private fun setCursor(row: Int, col: Int) {
        wrapPending = false
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, columns - 1)
    }

    private fun clampCursor() {
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, columns - 1)
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorCol = cursorCol
        savedStyle = currentStyle
    }

    private fun restoreCursor() {
        cursorRow = savedCursorRow.coerceIn(0, rows - 1)
        cursorCol = savedCursorCol.coerceIn(0, columns - 1)
        currentStyle = savedStyle
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                clearRow(cursorRow, cursorCol, columns - 1)
                for (r in cursorRow + 1 until rows) clearRow(r, 0, columns - 1)
            }
            1 -> {
                for (r in 0 until cursorRow) clearRow(r, 0, columns - 1)
                clearRow(cursorRow, 0, cursorCol)
            }
            2 -> for (r in 0 until rows) clearRow(r, 0, columns - 1)
            3 -> clearScrollbackInternal()
        }
        wrapPending = false
    }

    private fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> clearRow(cursorRow, cursorCol, columns - 1)
            1 -> clearRow(cursorRow, 0, cursorCol)
            2 -> clearRow(cursorRow, 0, columns - 1)
        }
        wrapPending = false
    }

    private fun eraseChars(count: Int) {
        clearRow(cursorRow, cursorCol, (cursorCol + count - 1).coerceAtMost(columns - 1))
    }

    private fun clearRow(row: Int, from: Int, to: Int) {
        val rowChars = chars[row]
        val rowStyles = styles[row]
        for (c in from.coerceAtLeast(0)..to.coerceAtMost(columns - 1)) {
            rowChars[c] = ' '
            rowStyles[c] = CellStyle.DEFAULT
        }
    }

    private fun insertChars(count: Int) {
        val n = count.coerceIn(1, columns - cursorCol)
        val rowChars = chars[cursorRow]
        val rowStyles = styles[cursorRow]
        for (c in columns - 1 downTo cursorCol + n) {
            rowChars[c] = rowChars[c - n]
            rowStyles[c] = rowStyles[c - n]
        }
        clearRow(cursorRow, cursorCol, cursorCol + n - 1)
    }

    private fun deleteChars(count: Int) {
        val n = count.coerceIn(1, columns - cursorCol)
        val rowChars = chars[cursorRow]
        val rowStyles = styles[cursorRow]
        for (c in cursorCol until columns - n) {
            rowChars[c] = rowChars[c + n]
            rowStyles[c] = rowStyles[c + n]
        }
        clearRow(cursorRow, columns - n, columns - 1)
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val savedTop = scrollTop
        scrollTop = cursorRow
        scrollDown(count)
        scrollTop = savedTop
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val savedTop = scrollTop
        scrollTop = cursorRow
        scrollUp(count)
        scrollTop = savedTop
    }

    private fun nextTabStop(col: Int): Int =
        ((col / TAB_WIDTH) + 1).times(TAB_WIDTH).coerceAtMost(columns - 1)

    // --- Public control ----------------------------------------------------------------------

    fun resize(newColumns: Int, newRows: Int) {
        val targetColumns = newColumns.coerceAtLeast(MIN_COLUMNS)
        val targetRows = newRows.coerceAtLeast(MIN_ROWS)
        if (targetColumns == columns && targetRows == rows) return

        // Keep the rows nearest the cursor: that is what the user is actually looking at.
        val copyRows = minOf(rows, targetRows)
        val anchor = (cursorRow - (copyRows - 1)).coerceAtLeast(0).coerceAtMost(rows - copyRows)
        val copyColumns = minOf(columns, targetColumns)

        val resizedChars = blankChars(targetColumns, targetRows)
        val resizedStyles = blankStyles(targetColumns, targetRows)
        copyGrid(chars, styles, resizedChars, resizedStyles, anchor, copyRows, copyColumns)

        // Rows pushed off the top by a shrink are history, not garbage: dropping them is what
        // made content vanish when the keyboard opened.
        if (!altScreenActive) {
            for (r in 0 until anchor) pushToScrollback(chars[r], styles[r])
        }

        // The saved primary buffer must follow, otherwise leaving the alternate screen after a
        // resize (keyboard opening while in vim) would restore a mis-sized grid.
        altChars?.let { savedChars ->
            val savedStyles = altStyles ?: return@let
            val savedRows = minOf(savedChars.size, targetRows)
            val nextChars = blankChars(targetColumns, targetRows)
            val nextStyles = blankStyles(targetColumns, targetRows)
            copyGrid(savedChars, savedStyles, nextChars, nextStyles, 0, savedRows, copyColumns)
            altChars = nextChars
            altStyles = nextStyles
        }

        chars = resizedChars
        styles = resizedStyles
        columns = targetColumns
        rows = targetRows
        scrollTop = 0
        scrollBottom = targetRows - 1
        cursorRow = (cursorRow - anchor).coerceIn(0, targetRows - 1)
        cursorCol = cursorCol.coerceIn(0, targetColumns - 1)
        revision++
    }

    private fun blankChars(width: Int, height: Int) = Array(height) { CharArray(width) { ' ' } }

    private fun blankStyles(width: Int, height: Int) =
        Array(height) { IntArray(width) { CellStyle.DEFAULT } }

    private fun copyGrid(
        sourceChars: Array<CharArray>,
        sourceStyles: Array<IntArray>,
        destChars: Array<CharArray>,
        destStyles: Array<IntArray>,
        sourceOffset: Int,
        rowCount: Int,
        columnCount: Int
    ) {
        for (r in 0 until rowCount) {
            System.arraycopy(sourceChars[sourceOffset + r], 0, destChars[r], 0, columnCount)
            System.arraycopy(sourceStyles[sourceOffset + r], 0, destStyles[r], 0, columnCount)
        }
    }

    fun setScrollbackLimit(limit: Int) {
        scrollbackLimit = limit.coerceAtLeast(0)
        while (scrollback.size > scrollbackLimit) {
            scrollback.removeFirst()
            scrollbackDirty = true
        }
        revision++
    }

    fun clearScrollback() {
        clearScrollbackInternal()
        revision++
    }

    /**
     * Starts a new remote session on a clean grid while keeping the previous output as history.
     * Without this a reconnect would draw the new shell on top of whatever the old one left
     * behind, including a full-screen editor.
     */
    fun beginSession() {
        altChars = null
        altStyles = null
        altScreenActive = false

        var lastUsed = -1
        for (r in 0 until rows) if (!isRowBlank(r)) lastUsed = r
        for (r in 0..lastUsed) pushToScrollback(chars[r], styles[r])

        chars = blankChars(columns, rows)
        styles = blankStyles(columns, rows)
        cursorRow = 0
        cursorCol = 0
        currentStyle = CellStyle.DEFAULT
        scrollTop = 0
        scrollBottom = rows - 1
        autoWrap = true
        wrapPending = false
        cursorVisible = true
        insertMode = false
        applicationCursorKeys = false
        bracketedPaste = false
        state = State.GROUND
        carryLength = 0
        revision++
    }

    private fun isRowBlank(row: Int): Boolean {
        val rowChars = chars[row]
        val rowStyles = styles[row]
        for (c in 0 until columns) {
            if (rowChars[c] != ' ' || rowStyles[c] != CellStyle.DEFAULT) return false
        }
        return true
    }

    private fun clearScrollbackInternal() {
        if (scrollback.isEmpty()) return
        scrollback.clear()
        scrollbackDirty = true
    }

    fun reset() {
        hardReset()
        revision++
    }

    private fun hardReset() {
        clearScrollbackInternal()
        altChars = null
        altStyles = null
        altScreenActive = false
        chars = Array(rows) { CharArray(columns) { ' ' } }
        styles = Array(rows) { IntArray(columns) { CellStyle.DEFAULT } }
        cursorRow = 0
        cursorCol = 0
        currentStyle = CellStyle.DEFAULT
        scrollTop = 0
        scrollBottom = rows - 1
        autoWrap = true
        wrapPending = false
        cursorVisible = true
        insertMode = false
        applicationCursorKeys = false
        bracketedPaste = false
        state = State.GROUND
        carryLength = 0
    }

    /** Appends locally generated text (status messages) as if the host had sent it. */
    fun writeLocal(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        write(bytes, bytes.size)
    }

    fun snapshot(): TerminalSnapshot {
        if (scrollbackDirty) {
            scrollbackView = ArrayList(scrollback)
            scrollbackDirty = false
        }
        val screenLines = ArrayList<TerminalLine>(rows)
        for (r in 0 until rows) screenLines.add(freeze(chars[r], styles[r]))
        return TerminalSnapshot(
            scrollback = scrollbackView,
            screen = screenLines,
            cursorRow = scrollbackView.size + cursorRow,
            cursorCol = cursorCol,
            cursorVisible = cursorVisible,
            revision = revision,
            altScreen = altScreenActive
        )
    }

    /** Full visible text, used for "copy all". */
    fun plainText(): String = buildString {
        scrollback.forEach { appendLine(it.text().trimEnd()) }
        for (r in 0 until rows) appendLine(freeze(chars[r], styles[r]).text().trimEnd())
    }.trimEnd()

    companion object {
        private const val ESC = '\u001B'
        private const val BEL = '\u0007'
        private const val DEL = '\u007F'
        private const val TAB_WIDTH = 8
        private const val MAX_PARAMS = 16
        private const val MAX_PARAM_VALUE = 65535
        private const val MAX_OSC_LENGTH = 1024
        const val MIN_COLUMNS = 20
        const val MIN_ROWS = 4

        /** Reports as a VT100 with Advanced Video Option. */
        private val DEVICE_ATTRIBUTES = "\u001B[?1;2c".toByteArray(Charsets.US_ASCII)
    }
}

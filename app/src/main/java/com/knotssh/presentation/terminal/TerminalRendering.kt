package com.knotssh.presentation.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.knotssh.data.local.preferences.TerminalFont
import com.knotssh.terminal.CellStyle
import com.knotssh.terminal.TerminalLine
import com.knotssh.terminal.TerminalPalette

fun TerminalFont.toFontFamily(): FontFamily = when (this) {
    TerminalFont.MONOSPACE -> FontFamily.Monospace
    TerminalFont.SANS_SERIF -> FontFamily.SansSerif
    TerminalFont.SERIF -> FontFamily.Serif
}

/**
 * Converts one buffer row into styled text, collapsing equal adjacent cells into single spans so
 * the span count tracks colour changes rather than character count.
 *
 * URLs are annotated as links when [linkifyUrls] is set; detection is per row, so an address
 * wrapped across two terminal lines is not recognised.
 */
fun TerminalLine.toAnnotatedString(
    colorsEnabled: Boolean,
    palette: TerminalPalette,
    defaultForeground: Color,
    defaultBackground: Color,
    cursorColumn: Int?,
    linkifyUrls: Boolean,
    linkColor: Color
): AnnotatedString {
    val visibleLength = maxOf(length, cursorColumn?.plus(1) ?: 0)
    if (visibleLength == 0) return AnnotatedString("")

    val plainText = if (isPlain && cursorColumn == null) text() else null
    if (plainText != null && !(linkifyUrls && plainText.contains("://"))) {
        return AnnotatedString(plainText)
    }

    val builder = AnnotatedString.Builder(visibleLength)
    var runStyle = cellStyle(0, cursorColumn)
    var runStart = 0

    fun flush(endExclusive: Int) {
        if (endExclusive <= runStart) return
        for (i in runStart until endExclusive) {
            builder.append(if (i < length) chars[i] else ' ')
        }
        spanStyleFor(runStyle, colorsEnabled, palette, defaultForeground, defaultBackground)?.let {
            builder.addStyle(it, runStart, endExclusive)
        }
    }

    for (i in 0 until visibleLength) {
        val style = cellStyle(i, cursorColumn)
        if (style != runStyle) {
            flush(i)
            runStart = i
            runStyle = style
        }
    }
    flush(visibleLength)

    if (linkifyUrls) builder.addUrlLinks(text(), linkColor)
    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.addUrlLinks(row: String, linkColor: Color) {
    if (!row.contains("://")) return
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    URL_PATTERN.findAll(row).forEach { match ->
        val url = match.value.trimEnd(*TRAILING_PUNCTUATION)
        if (url.length < MIN_URL_LENGTH) return@forEach
        addLink(
            LinkAnnotation.Url(url, linkStyles),
            match.range.first,
            match.range.first + url.length
        )
    }
}

private fun TerminalLine.cellStyle(index: Int, cursorColumn: Int?): Int {
    val base = if (index < length) styles[index] else CellStyle.DEFAULT
    return if (index == cursorColumn) base xor CellStyle.INVERSE else base
}

private fun spanStyleFor(
    packed: Int,
    colorsEnabled: Boolean,
    palette: TerminalPalette,
    defaultForeground: Color,
    defaultBackground: Color
): SpanStyle? {
    if (packed == CellStyle.DEFAULT) return null

    val inverse = CellStyle.hasFlag(packed, CellStyle.INVERSE)
    var foreground = resolve(CellStyle.foreground(packed), defaultForeground, colorsEnabled, palette)
    var background = resolve(CellStyle.background(packed), defaultBackground, colorsEnabled, palette)
    if (inverse) {
        val swap = foreground
        foreground = background
        background = swap
    }
    if (CellStyle.hasFlag(packed, CellStyle.DIM)) foreground = foreground.copy(alpha = 0.6f)

    val decoration = when {
        CellStyle.hasFlag(packed, CellStyle.UNDERLINE) && CellStyle.hasFlag(packed, CellStyle.STRIKE) ->
            TextDecoration.Underline + TextDecoration.LineThrough
        CellStyle.hasFlag(packed, CellStyle.UNDERLINE) -> TextDecoration.Underline
        CellStyle.hasFlag(packed, CellStyle.STRIKE) -> TextDecoration.LineThrough
        else -> null
    }

    return SpanStyle(
        color = foreground,
        background = if (!inverse && background == defaultBackground) Color.Unspecified else background,
        fontWeight = if (CellStyle.hasFlag(packed, CellStyle.BOLD)) FontWeight.Bold else null,
        fontStyle = if (CellStyle.hasFlag(packed, CellStyle.ITALIC)) FontStyle.Italic else null,
        textDecoration = decoration
    )
}

private fun resolve(
    index: Int,
    default: Color,
    colorsEnabled: Boolean,
    palette: TerminalPalette
): Color =
    if (index == CellStyle.DEFAULT_COLOR || !colorsEnabled) default
    else Color(palette.color(index))

private val URL_PATTERN = Regex("""https?://[^\s"'<>`\\^{|}]+""", RegexOption.IGNORE_CASE)
private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', ')', ']', '}', '!', '?', '\'', '"')
private const val MIN_URL_LENGTH = 11

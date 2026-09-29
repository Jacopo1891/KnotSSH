package com.knotssh.domain.model

/** A user-defined key on the terminal accessory bar, sending [sequence] verbatim. */
data class CustomKey(
    val id: Long = 0,
    val label: String,
    val sequence: String,
    val sortOrder: Int = 0
) {
    companion object {
        /** Shipped on first run so the bar is useful before the user configures anything. */
        val DEFAULTS = listOf(
            CustomKey(label = "^C", sequence = "\u0003", sortOrder = 0),
            CustomKey(label = "^D", sequence = "\u0004", sortOrder = 1),
            CustomKey(label = "^Z", sequence = "\u001A", sortOrder = 2),
            CustomKey(label = "^L", sequence = "\u000C", sortOrder = 3),
            CustomKey(label = "^R", sequence = "\u0012", sortOrder = 4)
        )
    }
}

/** Modifier applied when composing a custom key from a single character. */
enum class KeyModifier { NONE, CTRL, ALT, CTRL_ALT }

package com.knotssh.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QuickCommand(
    val id: Long = 0,
    val label: String,
    val command: String,
    val sortOrder: Int = 0
)

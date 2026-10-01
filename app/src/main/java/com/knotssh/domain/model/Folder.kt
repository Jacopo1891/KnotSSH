package com.knotssh.domain.model

data class Folder(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isExpanded: Boolean = true
)

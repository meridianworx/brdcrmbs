package com.ventures814.brdcrmbs.domain

import java.util.UUID

/** Reusable details, independent of scheduled plans and historical outcomes. */
data class SavedActivity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ActivityType,
    val notes: String? = null
) {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

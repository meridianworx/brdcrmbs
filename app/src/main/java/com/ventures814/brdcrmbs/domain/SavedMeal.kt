package com.ventures814.brdcrmbs.domain

import java.util.UUID

/** Reusable details only: selecting one never creates a scheduled plan. */
data class SavedMeal(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val label: String? = null,
    val notes: String? = null
) {
    init { require(id.isNotBlank() && text.isNotBlank()) }
}

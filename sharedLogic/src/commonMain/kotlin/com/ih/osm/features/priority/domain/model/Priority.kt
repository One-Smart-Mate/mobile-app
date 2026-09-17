package com.ih.osm.features.priority.domain.model

data class Priority(
    val id: String,
    val code: String,
    val description: String,
    val days: Long,
    val status: String,
)

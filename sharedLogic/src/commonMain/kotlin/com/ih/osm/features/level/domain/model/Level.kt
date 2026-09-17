package com.ih.osm.features.level.domain.model

data class Level(
    val id: String,
    val ownerId: String?,
    val ownerName: String?,
    val superiorId: String,
    val name: String,
    val description: String,
    val status: String,
)

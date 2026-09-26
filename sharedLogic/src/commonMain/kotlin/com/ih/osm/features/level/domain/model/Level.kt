package com.ih.osm.features.level.domain.model

data class Level(
    val id: String,
    val ownerId: String?,
    val ownerName: String?,
    val superiorId: String,
    val name: String,
    val description: String,
    val status: String,
    val depth: Long = 0,
    val machineId: String? = null,
    val notifyResponsible: Boolean = false,
    val assignResponsibleOnCreate: Boolean = false,
)

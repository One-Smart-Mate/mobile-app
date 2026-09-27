package com.ih.osm.features.cards.domain.model

data class CardSyncWorkStatus(
    val isActive: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
)

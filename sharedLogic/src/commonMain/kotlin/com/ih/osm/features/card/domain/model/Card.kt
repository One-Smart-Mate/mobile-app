package com.ih.osm.features.card.domain.model

data class Card(
    val uuid: String,
    val serverId: String?,
    val siteCardId: Long,
    val siteId: Long,
    val siteCode: String?,
    val cardTypeColor: String?,
    val status: String,
    val creationDate: String,
    val dueDate: String?,
    val priorityId: String?,
    val priorityCode: String?,
    val priorityDescription: String?,
    val nodeId: String?,
    val nodeName: String?,
    val cardTypeId: String?,
    val cardTypeName: String?,
    val cardTypeValue: String?,
    val preclassifierId: String?,
    val preclassifierCode: String?,
    val preclassifierDescription: String?,
    val creatorId: String?,
    val creatorName: String?,
    val responsibleId: String?,
    val responsibleName: String?,
    val mechanicId: String?,
    val mechanicName: String?,
    val comments: String?,
    val evidenceAudioCreation: Long,
    val evidenceVideoCreation: Long,
    val evidenceImageCreation: Long,
    val location: String?,
    val definitiveSolutionDate: String?,
    val isLocal: Boolean,
    val hasLocalSolutions: Boolean,
    val updatedAt: String?,
    val appSo: String? = null,
    val appVersion: String? = null,
    val customDueDate: String? = null,
    val notifyResponsible: Boolean = false,
    val syncState: CardSyncState = CardSyncState.SYNCED,
    val syncError: String? = null,
    val syncAttempts: Long = 0,
) {
    val isOpen: Boolean
        get() = status in OPEN_STATUSES

    val isClosed: Boolean
        get() = status in CLOSED_STATUSES || !definitiveSolutionDate.isNullOrBlank()

    val problemDescription: String
        get() = comments?.takeIf(String::isNotBlank)
            ?: preclassifierDescription?.takeIf(String::isNotBlank)
            ?: cardTypeName.orEmpty()

    companion object {
        val OPEN_STATUSES = setOf("P", "A", "V")
        val CLOSED_STATUSES = setOf("R", "C")
    }
}

enum class CardSyncState {
    PENDING,
    SYNCING,
    FAILED,
    SYNCED,
}

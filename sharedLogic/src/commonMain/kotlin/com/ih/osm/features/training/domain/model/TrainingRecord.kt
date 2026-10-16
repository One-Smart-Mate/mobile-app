package com.ih.osm.features.training.domain.model

/** Training lifecycle for one (user, OPL) pair, mirrored from the backend. */
enum class TrainingStatus {
    PENDING,
    SELF_COMPLETED,
    VALIDATED,
    EXPIRED,
    UNKNOWN,
}

/** A single training record the authenticated user sees for one OPL (course). */
data class TrainingRecord(
    val id: Long,
    val oplId: Long,
    val siteId: Long? = null,
    val status: TrainingStatus,
    val selfCompletedAt: String? = null,
    val validatedAt: String? = null,
    val expiresAt: String? = null,
    val oplTitle: String,
    val oplObjective: String? = null,
)

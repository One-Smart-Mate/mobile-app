package com.ih.osm.features.training.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.training.domain.model.TrainingRecord

interface TrainingRepository {
    /** The authenticated user's own training records (scoped server-side by the JWT). */
    suspend fun getMyRecords(): NetworkResult<List<TrainingRecord>>

    /** The user declares they took the OPL -> moves to SELF_COMPLETED, pending validation. */
    suspend fun selfComplete(oplId: Long): NetworkResult<Unit>
}

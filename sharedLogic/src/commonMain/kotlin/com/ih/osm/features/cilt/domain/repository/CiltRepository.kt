package com.ih.osm.features.cilt.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.cilt.domain.model.CiltExecution
import com.ih.osm.features.cilt.domain.model.CiltRoutine
import com.ih.osm.features.cilt.domain.model.CiltSequence

interface CiltRepository {
    /** The authenticated user's CILT routine for the current day. */
    suspend fun getRoutineOfDay(): NetworkResult<CiltRoutine>

    /** A single sequence with its executions, used by the run screen. */
    suspend fun getSequence(sequenceId: Long): NetworkResult<CiltSequence>

    /** Marks a sequence execution as started (PUT /cilt-sequences-executions/start). */
    suspend fun startExecution(executionId: Long): NetworkResult<CiltExecution>

    /** Completes a sequence execution with captured parameters. */
    suspend fun stopExecution(
        executionId: Long,
        initialParameter: String,
        finalParameter: String,
        parameterOk: Boolean,
    ): NetworkResult<CiltExecution>

    /** Uploads one evidence photo for an execution (stored to R2 by the backend). */
    suspend fun uploadEvidence(
        siteId: Long,
        executionId: Long,
        type: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): NetworkResult<Unit>

    /** Fetches a single execution with its evidences (for consulting completed runs). */
    suspend fun getExecution(executionId: Long): NetworkResult<CiltExecution>
}

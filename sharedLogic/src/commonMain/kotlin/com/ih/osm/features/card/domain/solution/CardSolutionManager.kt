package com.ih.osm.features.card.domain.solution

import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.card.domain.create.CreateCardEvidenceDraft
import com.ih.osm.features.card.domain.create.CreateCardEvidenceError
import com.ih.osm.features.card.domain.create.CreateCardEvidenceErrorReason
import com.ih.osm.features.card.domain.evidence.CardEvidenceLimits
import com.ih.osm.features.card.domain.evidence.validateEvidenceDraft
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.model.CardEvidenceStage
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cardtype.domain.model.CardType
import com.ih.osm.features.cardtype.domain.repository.CardTypeRepository
import com.ih.osm.features.employee.domain.model.Employee
import com.ih.osm.features.employee.domain.repository.EmployeeRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CardSolutionType {
    PROVISIONAL,
    DEFINITIVE,
}

enum class CardSolutionValidationError {
    CARD_NOT_FOUND,
    CARD_ALREADY_CLOSED,
    SOLUTION_ALREADY_APPLIED,
    CARD_TYPE_UNAVAILABLE,
    EMPLOYEE_REQUIRED,
    COMMENTS_REQUIRED,
    COMMENTS_TOO_LONG,
    EVIDENCE_PROCESSING,
    SAVE_FAILED,
}

data class CardSolutionState(
    val initialized: Boolean = false,
    val card: Card? = null,
    val type: CardSolutionType = CardSolutionType.PROVISIONAL,
    val cardType: CardType? = null,
    val employees: List<Employee> = emptyList(),
    val filteredEmployees: List<Employee> = emptyList(),
    val selectedEmployee: Employee? = null,
    val employeeQuery: String = "",
    val showEmployeeSheet: Boolean = false,
    val comments: String = "",
    val evidences: List<CreateCardEvidenceDraft> = emptyList(),
    val evidenceError: CreateCardEvidenceError? = null,
    val isProcessingEvidence: Boolean = false,
    val validationError: CardSolutionValidationError? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val evidenceLimits: CardEvidenceLimits
        get() = cardType.limitsFor(type)
}

sealed interface CardSolutionSaveResult {
    data object Success : CardSolutionSaveResult
    data class Failure(val error: CardSolutionValidationError) : CardSolutionSaveResult
    data object Ignored : CardSolutionSaveResult
}

class CardSolutionManager(
    private val cards: CardRepository,
    private val cardTypes: CardTypeRepository,
    private val employees: EmployeeRepository,
) {
    private val mutableState = MutableStateFlow(CardSolutionState())
    val state: StateFlow<CardSolutionState> = mutableState.asStateFlow()

    fun initialize(user: AuthenticatedUser, cardUuid: String, type: CardSolutionType) {
        if (mutableState.value.initialized) return
        val card = cards.getCard(cardUuid)
        val cardType = card?.cardTypeId?.let { id ->
            cardTypes.getAll(card.siteId).firstOrNull { it.id == id }
        }
        val siteEmployees = card?.let { employees.getAll(it.siteId) }.orEmpty()
        val validation = when {
            card == null -> CardSolutionValidationError.CARD_NOT_FOUND
            card.isClosed -> CardSolutionValidationError.CARD_ALREADY_CLOSED
            type == CardSolutionType.PROVISIONAL && card.hasProvisionalSolution() ->
                CardSolutionValidationError.SOLUTION_ALREADY_APPLIED
            type == CardSolutionType.DEFINITIVE && card.hasDefinitiveSolution() ->
                CardSolutionValidationError.SOLUTION_ALREADY_APPLIED
            cardType == null -> CardSolutionValidationError.CARD_TYPE_UNAVAILABLE
            else -> null
        }
        mutableState.value = CardSolutionState(
            initialized = true,
            card = card,
            type = type,
            cardType = cardType,
            employees = siteEmployees,
            filteredEmployees = siteEmployees,
            validationError = validation,
        )
    }

    fun openEmployeeSheet() {
        mutableState.update {
            it.copy(showEmployeeSheet = true, employeeQuery = "", filteredEmployees = it.employees)
        }
    }

    fun dismissEmployeeSheet() {
        mutableState.update { it.copy(showEmployeeSheet = false, employeeQuery = "") }
    }

    fun searchEmployees(query: String) {
        mutableState.update { state ->
            val normalized = query.trim().lowercase()
            state.copy(
                employeeQuery = query,
                filteredEmployees = state.employees.filter { employee ->
                    normalized.isEmpty() || employee.name.lowercase().contains(normalized) ||
                        employee.email.lowercase().contains(normalized)
                },
            )
        }
    }

    fun selectEmployee(id: String) {
        mutableState.update { state ->
            val selected = state.employees.firstOrNull { it.id == id } ?: return@update state
            state.copy(
                selectedEmployee = selected,
                showEmployeeSheet = false,
                employeeQuery = "",
                validationError = null,
            )
        }
    }

    fun updateComments(value: String) {
        mutableState.update { it.copy(comments = value.take(MAX_COMMENTS_INPUT), validationError = null) }
    }

    fun setEvidenceProcessing(processing: Boolean) {
        mutableState.update { it.copy(isProcessingEvidence = processing) }
    }

    fun addEvidence(evidence: CreateCardEvidenceDraft): Boolean {
        val current = mutableState.value
        val error = validateEvidenceDraft(evidence, current.evidences, current.evidenceLimits)
        if (error != null) {
            mutableState.update { it.copy(evidenceError = error, isProcessingEvidence = false) }
            return false
        }
        mutableState.update {
            it.copy(evidences = it.evidences + evidence, evidenceError = null, isProcessingEvidence = false)
        }
        return true
    }

    fun removeEvidence(id: String): CreateCardEvidenceDraft? {
        val removed = mutableState.value.evidences.firstOrNull { it.id == id } ?: return null
        mutableState.update {
            it.copy(evidences = it.evidences.filterNot { item -> item.id == id }, evidenceError = null)
        }
        return removed
    }

    fun evidenceImportFailed() {
        mutableState.update {
            it.copy(
                isProcessingEvidence = false,
                evidenceError = CreateCardEvidenceError(CreateCardEvidenceErrorReason.IMPORT_FAILED),
            )
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(validationError = null, evidenceError = null) }
    }

    suspend fun save(): CardSolutionSaveResult {
        val before = mutableState.value
        if (before.isSaving) return CardSolutionSaveResult.Ignored
        val validation = before.validate()
        if (validation != null) {
            mutableState.update { it.copy(validationError = validation) }
            return CardSolutionSaveResult.Failure(validation)
        }
        mutableState.update { it.copy(isSaving = true, validationError = null) }
        return runCatching {
            val state = mutableState.value
            val card = requireNotNull(state.card)
            val employee = requireNotNull(state.selectedEmployee)
            val now = Clock.System.now().toString()
            val evidenceStage = if (state.type == CardSolutionType.PROVISIONAL) {
                CardEvidenceStage.PROVISIONAL_SOLUTION
            } else {
                CardEvidenceStage.DEFINITIVE_SOLUTION
            }
            val solutionEvidences = state.evidences.map { draft ->
                CardEvidence(
                    id = draft.id,
                    cardUuid = card.uuid,
                    siteId = card.siteId,
                    url = draft.localPath,
                    typeCode = draft.mediaType.typeCode(state.type),
                    stage = evidenceStage,
                    mediaType = draft.mediaType,
                    createdAt = now,
                    isLocal = true,
                )
            }
            val updated = when (state.type) {
                CardSolutionType.PROVISIONAL -> card.copy(
                    status = "P",
                    hasLocalSolutions = true,
                    updatedAt = now,
                    provisionalSolutionDate = now,
                    provisionalSolutionComments = state.comments.trim(),
                    provisionalSolutionUserId = employee.id,
                    provisionalSolutionUserName = employee.name,
                    provisionalSolutionPending = true,
                    syncError = null,
                    evidences = card.evidences + solutionEvidences,
                )
                CardSolutionType.DEFINITIVE -> card.copy(
                    status = "R",
                    hasLocalSolutions = true,
                    updatedAt = now,
                    definitiveSolutionDate = now,
                    definitiveSolutionComments = state.comments.trim(),
                    definitiveSolutionUserId = employee.id,
                    definitiveSolutionUserName = employee.name,
                    definitiveSolutionPending = true,
                    syncError = null,
                    evidences = card.evidences + solutionEvidences,
                )
            }
            cards.saveLocalSolution(updated)
            mutableState.update { it.copy(card = updated, isSaving = false, saved = true) }
            CardSolutionSaveResult.Success
        }.getOrElse {
            mutableState.update { it.copy(isSaving = false, validationError = CardSolutionValidationError.SAVE_FAILED) }
            CardSolutionSaveResult.Failure(CardSolutionValidationError.SAVE_FAILED)
        }
    }

    private fun CardSolutionState.validate(): CardSolutionValidationError? = when {
        card == null -> CardSolutionValidationError.CARD_NOT_FOUND
        type == CardSolutionType.PROVISIONAL && card.hasProvisionalSolution() ->
            CardSolutionValidationError.SOLUTION_ALREADY_APPLIED
        type == CardSolutionType.DEFINITIVE && card.hasDefinitiveSolution() ->
            CardSolutionValidationError.SOLUTION_ALREADY_APPLIED
        card.isClosed -> CardSolutionValidationError.CARD_ALREADY_CLOSED
        cardType == null -> CardSolutionValidationError.CARD_TYPE_UNAVAILABLE
        selectedEmployee == null -> CardSolutionValidationError.EMPLOYEE_REQUIRED
        comments.isBlank() -> CardSolutionValidationError.COMMENTS_REQUIRED
        comments.length > MAX_COMMENTS_LENGTH -> CardSolutionValidationError.COMMENTS_TOO_LONG
        isProcessingEvidence -> CardSolutionValidationError.EVIDENCE_PROCESSING
        else -> null
    }

    private companion object {
        const val MAX_COMMENTS_LENGTH = 500
        const val MAX_COMMENTS_INPUT = 501
    }
}

private fun Card.hasProvisionalSolution(): Boolean =
    provisionalSolutionPending || !provisionalSolutionDate.isNullOrBlank() ||
        !provisionalSolutionUserName.isNullOrBlank()

private fun Card.hasDefinitiveSolution(): Boolean =
    definitiveSolutionPending || isClosed || !definitiveSolutionDate.isNullOrBlank()

private fun CardType?.limitsFor(type: CardSolutionType): CardEvidenceLimits = when (type) {
    CardSolutionType.PROVISIONAL -> CardEvidenceLimits(
        images = this?.quantityImagesPs.orZero(),
        videos = this?.quantityVideosPs.orZero(),
        audios = this?.quantityAudiosPs.orZero(),
        videoDurationSeconds = this?.videosDurationPs.orZero(),
        audioDurationSeconds = this?.audiosDurationPs.orZero(),
    )
    CardSolutionType.DEFINITIVE -> CardEvidenceLimits(
        images = this?.quantityImagesClose.orZero(),
        videos = this?.quantityVideosClose.orZero(),
        audios = this?.quantityAudiosClose.orZero(),
        videoDurationSeconds = this?.videosDurationClose.orZero(),
        audioDurationSeconds = this?.audiosDurationClose.orZero(),
    )
}

private fun CardEvidenceMediaType.typeCode(type: CardSolutionType): String = when (type) {
    CardSolutionType.PROVISIONAL -> when (this) {
        CardEvidenceMediaType.IMAGE -> "IMPS"
        CardEvidenceMediaType.VIDEO -> "VIPS"
        CardEvidenceMediaType.AUDIO -> "AUPS"
    }
    CardSolutionType.DEFINITIVE -> when (this) {
        CardEvidenceMediaType.IMAGE -> "IMCL"
        CardEvidenceMediaType.VIDEO -> "VICL"
        CardEvidenceMediaType.AUDIO -> "AUCL"
    }
}

private fun Long?.orZero(): Long = this ?: 0L

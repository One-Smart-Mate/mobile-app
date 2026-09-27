package com.ih.osm.features.card.domain.create

import com.ih.osm.Platform
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.model.UserSite
import com.ih.osm.features.card.domain.model.Card
import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.card.domain.model.CardEvidenceStage
import com.ih.osm.features.card.domain.model.CardSyncState
import com.ih.osm.features.card.domain.repository.CardRepository
import com.ih.osm.features.cardtype.domain.model.CardType
import com.ih.osm.features.cardtype.domain.repository.CardTypeRepository
import com.ih.osm.features.level.domain.model.Level
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.preclassifier.domain.model.Preclassifier
import com.ih.osm.features.preclassifier.domain.repository.PreclassifierRepository
import com.ih.osm.features.priority.domain.model.Priority
import com.ih.osm.features.priority.domain.repository.PriorityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class CreateCardStep {
    CLASSIFICATION,
    LOCATION,
    EVIDENCE,
    DETAILS,
    REVIEW,
}

enum class CreateCardSheet {
    CARD_TYPE,
    CARD_TYPE_VALUE,
    PRECLASSIFIER,
    PRIORITY,
    LEVEL,
    CUSTOM_DUE_DATE,
}

enum class CreateCardValidationError {
    SITE_REQUIRED,
    CATALOGS_UNAVAILABLE,
    CARD_TYPE_REQUIRED,
    CARD_TYPE_VALUE_REQUIRED,
    PRECLASSIFIER_REQUIRED,
    PRIORITY_REQUIRED,
    LEVEL_REQUIRED,
    LEVEL_MUST_BE_FINAL,
    DESCRIPTION_REQUIRED,
    DESCRIPTION_TOO_LONG,
    CUSTOM_DUE_DATE_REQUIRED,
    CUSTOM_DUE_DATE_INVALID,
    INVALID_CATALOG_SELECTION,
    SAVE_FAILED,
}

enum class CreateCardEvidenceErrorReason {
    IMAGE_LIMIT_REACHED,
    VIDEO_LIMIT_REACHED,
    AUDIO_LIMIT_REACHED,
    VIDEO_DURATION_EXCEEDED,
    AUDIO_DURATION_EXCEEDED,
    FILE_TOO_LARGE,
    INVALID_MEDIA,
    TOTAL_LIMIT_REACHED,
    IMPORT_FAILED,
}

data class CreateCardEvidenceError(
    val reason: CreateCardEvidenceErrorReason,
    val limit: Long? = null,
)

data class CreateCardEvidenceDraft(
    val id: String,
    val localPath: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: CardEvidenceMediaType,
    val durationMillis: Long = 0,
    val sizeBytes: Long = 0,
)

data class CreateCardSelectionItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val hasChildren: Boolean = false,
)

data class CreateCardState(
    val initialized: Boolean = false,
    val step: CreateCardStep = CreateCardStep.CLASSIFICATION,
    val userId: Long = 0,
    val userName: String = "",
    val site: UserSite? = null,
    val cardTypes: List<CardType> = emptyList(),
    val preclassifiers: List<Preclassifier> = emptyList(),
    val priorities: List<Priority> = emptyList(),
    val levels: List<Level> = emptyList(),
    val selectedCardType: CardType? = null,
    val selectedCardTypeValue: String? = null,
    val selectedPreclassifier: Preclassifier? = null,
    val selectedPriority: Priority? = null,
    val customDueDate: String? = null,
    val selectedLevel: Level? = null,
    val selectedLevelPath: List<Level> = emptyList(),
    val description: String = "",
    val evidences: List<CreateCardEvidenceDraft> = emptyList(),
    val evidenceError: CreateCardEvidenceError? = null,
    val isProcessingEvidence: Boolean = false,
    val activeSheet: CreateCardSheet? = null,
    val sheetQuery: String = "",
    val sheetItems: List<CreateCardSelectionItem> = emptyList(),
    val levelNavigationPath: List<Level> = emptyList(),
    val validationError: CreateCardValidationError? = null,
    val isSaving: Boolean = false,
    val createdUuid: String? = null,
    val appVersion: String = "",
) {
    val requiresCardTypeValue: Boolean
        get() = selectedCardType?.cardTypeMethodology.equals("C", ignoreCase = true)

    val requiresCustomDueDate: Boolean
        get() = selectedPriority?.code?.trim().equals("XX", ignoreCase = true)

    val selectedLocation: String
        get() = selectedLevelPath.joinToString(" › ") { it.name }

    val responsibleName: String?
        get() = selectedLevel?.ownerName?.takeIf(String::isNotBlank)

    val imageEvidenceCount: Int
        get() = evidences.count { it.mediaType == CardEvidenceMediaType.IMAGE }

    val videoEvidenceCount: Int
        get() = evidences.count { it.mediaType == CardEvidenceMediaType.VIDEO }

    val audioEvidenceCount: Int
        get() = evidences.count { it.mediaType == CardEvidenceMediaType.AUDIO }
}

sealed interface CreateCardSaveResult {
    data class Success(val uuid: String) : CreateCardSaveResult
    data class Failure(val error: CreateCardValidationError) : CreateCardSaveResult
    data object Ignored : CreateCardSaveResult
}

/**
 * Shared state machine for Android and iOS. Presentation layers only forward user
 * intent and render the already-filtered state emitted here.
 */
class CreateCardManager(
    private val cardTypes: CardTypeRepository,
    private val preclassifiers: PreclassifierRepository,
    private val priorities: PriorityRepository,
    private val levels: LevelRepository,
    private val cards: CardRepository,
    private val platform: Platform,
) {
    private val mutableState = MutableStateFlow(CreateCardState())
    val state: StateFlow<CreateCardState> = mutableState.asStateFlow()

    fun initialize(user: AuthenticatedUser, siteId: Long?, appVersion: String) {
        if (mutableState.value.initialized) return
        val site = user.sites.firstOrNull { it.id == siteId } ?: user.sites.firstOrNull()
        val siteCardTypes = site?.let { cardTypes.getAll(it.id) }.orEmpty().activeOnly { it.status }
        val sitePreclassifiers = site?.let { preclassifiers.getAll(it.id) }.orEmpty()
        val sitePriorities = site?.let { priorities.getAll(it.id) }.orEmpty().activeOnly { it.status }
        val siteLevels = site?.let { levels.getAll(it.id) }.orEmpty().activeOnly { it.status }
        mutableState.value = CreateCardState(
            initialized = true,
            userId = user.id,
            userName = user.name,
            site = site,
            cardTypes = siteCardTypes,
            preclassifiers = sitePreclassifiers,
            priorities = sitePriorities,
            levels = siteLevels,
            appVersion = appVersion,
            validationError = when {
                site == null -> CreateCardValidationError.SITE_REQUIRED
                siteCardTypes.isEmpty() || sitePreclassifiers.isEmpty() ||
                    sitePriorities.isEmpty() || siteLevels.isEmpty() ->
                    CreateCardValidationError.CATALOGS_UNAVAILABLE
                else -> null
            },
        )
    }

    fun openSheet(sheet: CreateCardSheet) {
        val current = mutableState.value
        if (sheet == CreateCardSheet.PRECLASSIFIER && current.selectedCardType == null) {
            showError(CreateCardValidationError.CARD_TYPE_REQUIRED)
            return
        }
        val levelPath = if (sheet == CreateCardSheet.LEVEL) emptyList() else current.levelNavigationPath
        mutableState.update {
            it.copy(
                activeSheet = sheet,
                sheetQuery = "",
                levelNavigationPath = levelPath,
                validationError = null,
            ).withSheetItems()
        }
    }

    fun dismissSheet() {
        mutableState.update { it.copy(activeSheet = null, sheetQuery = "", levelNavigationPath = emptyList()) }
    }

    fun updateSheetQuery(value: String) {
        mutableState.update { it.copy(sheetQuery = value).withSheetItems() }
    }

    fun selectSheetItem(id: String) {
        val current = mutableState.value
        when (current.activeSheet) {
            CreateCardSheet.CARD_TYPE -> selectCardType(id)
            CreateCardSheet.CARD_TYPE_VALUE -> {
                mutableState.update {
                    it.copy(selectedCardTypeValue = id, activeSheet = null, sheetQuery = "", validationError = null)
                }
            }
            CreateCardSheet.PRECLASSIFIER -> {
                val selected = current.preclassifiers.firstOrNull {
                    it.id == id && it.cardTypeId == current.selectedCardType?.id
                } ?: return
                mutableState.update {
                    it.copy(selectedPreclassifier = selected, activeSheet = null, sheetQuery = "", validationError = null)
                }
            }
            CreateCardSheet.PRIORITY -> {
                val selected = current.priorities.firstOrNull { it.id == id } ?: return
                mutableState.update {
                    it.copy(
                        selectedPriority = selected,
                        customDueDate = null,
                        activeSheet = null,
                        sheetQuery = "",
                        validationError = null,
                    )
                }
            }
            CreateCardSheet.LEVEL -> selectLevel(id)
            CreateCardSheet.CUSTOM_DUE_DATE -> Unit
            null -> Unit
        }
    }

    fun updateCustomDueDate(value: String) {
        mutableState.update {
            it.copy(
                customDueDate = value.takeIf(String::isNotBlank),
                activeSheet = null,
                validationError = null,
            )
        }
    }

    fun navigateToLevel(levelId: String?) {
        mutableState.update { current ->
            val newPath = if (levelId == null) {
                emptyList()
            } else {
                current.pathTo(levelId)
            }
            current.copy(levelNavigationPath = newPath, sheetQuery = "").withSheetItems()
        }
    }

    fun updateDescription(value: String) {
        mutableState.update {
            it.copy(
                description = value.take(MAX_DESCRIPTION_INPUT),
                validationError = null,
            )
        }
    }

    fun setEvidenceProcessing(processing: Boolean) {
        mutableState.update { it.copy(isProcessingEvidence = processing) }
    }

    fun addEvidence(evidence: CreateCardEvidenceDraft): Boolean {
        val current = mutableState.value
        val error = current.validateEvidence(evidence)
        if (error != null) {
            mutableState.update { it.copy(evidenceError = error, isProcessingEvidence = false) }
            return false
        }
        mutableState.update {
            it.copy(
                evidences = it.evidences + evidence,
                evidenceError = null,
                isProcessingEvidence = false,
            )
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

    fun next(): Boolean {
        val current = mutableState.value
        val error = current.validationForCurrentStep()
        if (error != null) {
            showError(error)
            return false
        }
        if (current.step == CreateCardStep.REVIEW) return false
        mutableState.update {
            it.copy(step = CreateCardStep.entries[it.step.ordinal + 1], validationError = null)
        }
        return true
    }

    /** Returns false when Android/iOS should close the flow. */
    fun back(): Boolean {
        val current = mutableState.value
        if (current.isSaving) return true
        if (current.activeSheet != null) {
            dismissSheet()
            return true
        }
        if (current.step == CreateCardStep.CLASSIFICATION) return false
        mutableState.update {
            it.copy(step = CreateCardStep.entries[it.step.ordinal - 1], validationError = null)
        }
        return true
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun save(): CreateCardSaveResult {
        val before = mutableState.value
        if (before.isSaving) return CreateCardSaveResult.Ignored
        val error = before.validationForAllSteps()
        if (error != null) {
            showError(error)
            return CreateCardSaveResult.Failure(error)
        }

        mutableState.update { it.copy(isSaving = true, validationError = null) }
        return runCatching {
            val current = mutableState.value
            val cardType = requireNotNull(current.selectedCardType)
            val preclassifier = requireNotNull(current.selectedPreclassifier)
            val priority = requireNotNull(current.selectedPriority)
            val level = requireNotNull(current.selectedLevel)
            val site = requireNotNull(current.site)
            val uuid = Uuid.random().toString()
            val now = Clock.System.now()
            val card = Card(
                uuid = uuid,
                serverId = null,
                siteCardId = 0,
                siteId = site.id,
                siteCode = null,
                cardTypeColor = cardType.color,
                status = "A",
                creationDate = now.toString(),
                dueDate = current.customDueDate ?: (now + priority.days.days).toString().take(10),
                priorityId = priority.id,
                priorityCode = priority.code,
                priorityDescription = priority.description,
                nodeId = level.id,
                nodeName = level.name,
                cardTypeId = cardType.id,
                cardTypeName = cardType.name,
                cardTypeValue = current.selectedCardTypeValue,
                preclassifierId = preclassifier.id,
                preclassifierCode = preclassifier.code,
                preclassifierDescription = preclassifier.description,
                creatorId = current.userId.toString(),
                creatorName = current.userName,
                responsibleId = level.ownerId,
                responsibleName = level.ownerName,
                mechanicId = level.ownerId.takeIf { level.assignResponsibleOnCreate },
                mechanicName = level.ownerName.takeIf { level.assignResponsibleOnCreate },
                comments = current.description.trim(),
                evidenceAudioCreation = current.audioEvidenceCount.toLong(),
                evidenceVideoCreation = current.videoEvidenceCount.toLong(),
                evidenceImageCreation = current.imageEvidenceCount.toLong(),
                location = current.selectedLocation,
                definitiveSolutionDate = null,
                isLocal = true,
                hasLocalSolutions = false,
                updatedAt = now.toString(),
                appSo = platform.name,
                appVersion = current.appVersion,
                customDueDate = current.customDueDate,
                notifyResponsible = level.notifyResponsible,
                syncState = CardSyncState.PENDING,
                evidences = current.evidences.map { evidence ->
                    CardEvidence(
                        id = evidence.id,
                        cardUuid = uuid,
                        siteId = site.id,
                        url = evidence.localPath,
                        typeCode = evidence.mediaType.creationTypeCode(),
                        stage = CardEvidenceStage.CREATION,
                        mediaType = evidence.mediaType,
                        createdAt = now.toString(),
                        isLocal = true,
                    )
                },
            )
            cards.saveLocal(card)
            mutableState.update { it.copy(isSaving = false, createdUuid = uuid) }
            CreateCardSaveResult.Success(uuid)
        }.getOrElse {
            mutableState.update {
                it.copy(isSaving = false, validationError = CreateCardValidationError.SAVE_FAILED)
            }
            CreateCardSaveResult.Failure(CreateCardValidationError.SAVE_FAILED)
        }
    }

    private fun selectCardType(id: String) {
        val current = mutableState.value
        val selected = current.cardTypes.firstOrNull { it.id == id } ?: return
        mutableState.update {
            val cardTypeChanged = it.selectedCardType?.id != selected.id
            it.copy(
                selectedCardType = selected,
                selectedCardTypeValue = if (cardTypeChanged) null else it.selectedCardTypeValue,
                selectedPreclassifier = if (cardTypeChanged) null else it.selectedPreclassifier,
                selectedPriority = if (cardTypeChanged) null else it.selectedPriority,
                selectedLevel = if (cardTypeChanged) null else it.selectedLevel,
                selectedLevelPath = if (cardTypeChanged) emptyList() else it.selectedLevelPath,
                evidences = if (cardTypeChanged) emptyList() else it.evidences,
                evidenceError = if (cardTypeChanged) null else it.evidenceError,
                activeSheet = null,
                sheetQuery = "",
                validationError = null,
            )
        }
    }

    private fun selectLevel(id: String) {
        val current = mutableState.value
        val level = current.levels.firstOrNull { it.id == id } ?: return
        val children = current.levels.childrenOf(level.id)
        if (children.isNotEmpty()) {
            mutableState.update {
                it.copy(levelNavigationPath = it.pathTo(id), sheetQuery = "").withSheetItems()
            }
            return
        }
        mutableState.update {
            it.copy(
                selectedLevel = level,
                selectedLevelPath = it.pathTo(id),
                levelNavigationPath = emptyList(),
                activeSheet = null,
                sheetQuery = "",
                validationError = null,
            )
        }
    }

    private fun showError(error: CreateCardValidationError) {
        mutableState.update { it.copy(validationError = error) }
    }

    private fun CreateCardState.withSheetItems(): CreateCardState {
        val normalizedQuery = sheetQuery.trim().lowercase()
        val items = when (activeSheet) {
            CreateCardSheet.CARD_TYPE -> cardTypes.map {
                CreateCardSelectionItem(it.id, it.name, it.description)
            }
            CreateCardSheet.CARD_TYPE_VALUE -> listOf(
                CreateCardSelectionItem("safe", "safe"),
                CreateCardSelectionItem("unsafe", "unsafe"),
            )
            CreateCardSheet.PRECLASSIFIER -> preclassifiers
                .filter { it.cardTypeId == selectedCardType?.id }
                .map { CreateCardSelectionItem(it.id, it.description, it.code) }
            CreateCardSheet.PRIORITY -> priorities.map {
                CreateCardSelectionItem(it.id, it.description, it.code)
            }
            CreateCardSheet.LEVEL -> {
                val levelIds = levels.asSequence().map(Level::id).toSet()
                val childrenByParent = levels.groupBy(Level::superiorId)
                val source = if (normalizedQuery.isNotEmpty()) {
                    levels.filter { level ->
                        listOf(level.name, level.description, level.machineId, level.ownerName)
                            .filterNotNull()
                            .any { it.lowercase().contains(normalizedQuery) }
                    }
                } else {
                    val parentId = levelNavigationPath.lastOrNull()?.id
                    if (parentId == null) {
                        levels.filter {
                            it.superiorId.isBlank() || it.superiorId == "0" || it.superiorId !in levelIds
                        }
                    } else {
                        childrenByParent[parentId].orEmpty()
                    }.sortedWith(compareBy(Level::depth, Level::name))
                }
                source.map {
                    CreateCardSelectionItem(
                        id = it.id,
                        title = it.name,
                        subtitle = listOfNotNull(it.machineId, it.ownerName).joinToString(" · ").ifBlank { it.description },
                        hasChildren = childrenByParent[it.id].orEmpty().isNotEmpty(),
                    )
                }
            }
            CreateCardSheet.CUSTOM_DUE_DATE -> emptyList()
            null -> emptyList()
        }.filter { item ->
            normalizedQuery.isEmpty() || activeSheet == CreateCardSheet.LEVEL ||
                item.title.lowercase().contains(normalizedQuery) ||
                item.subtitle.orEmpty().lowercase().contains(normalizedQuery)
        }
        return copy(sheetItems = items)
    }

    private fun CreateCardState.validationForCurrentStep(): CreateCardValidationError? = when (step) {
        CreateCardStep.CLASSIFICATION -> classificationError()
        CreateCardStep.LOCATION -> locationError()
        CreateCardStep.EVIDENCE -> null
        CreateCardStep.DETAILS -> descriptionError()
        CreateCardStep.REVIEW -> validationForAllSteps()
    }

    private fun CreateCardState.validationForAllSteps(): CreateCardValidationError? =
        classificationError() ?: locationError() ?: descriptionError()

    private fun CreateCardState.classificationError(): CreateCardValidationError? = when {
        site == null -> CreateCardValidationError.SITE_REQUIRED
        cardTypes.isEmpty() || preclassifiers.isEmpty() || priorities.isEmpty() || levels.isEmpty() ->
            CreateCardValidationError.CATALOGS_UNAVAILABLE
        selectedCardType == null -> CreateCardValidationError.CARD_TYPE_REQUIRED
        requiresCardTypeValue && selectedCardTypeValue == null -> CreateCardValidationError.CARD_TYPE_VALUE_REQUIRED
        selectedPreclassifier == null -> CreateCardValidationError.PRECLASSIFIER_REQUIRED
        selectedPreclassifier.cardTypeId != selectedCardType.id -> CreateCardValidationError.INVALID_CATALOG_SELECTION
        selectedPriority == null -> CreateCardValidationError.PRIORITY_REQUIRED
        requiresCustomDueDate && customDueDate == null -> CreateCardValidationError.CUSTOM_DUE_DATE_REQUIRED
        requiresCustomDueDate && customDueDate?.matches(DATE_PATTERN) != true ->
            CreateCardValidationError.CUSTOM_DUE_DATE_INVALID
        requiresCustomDueDate && customDueDate.orEmpty() < Clock.System.now().toString().take(10) ->
            CreateCardValidationError.CUSTOM_DUE_DATE_INVALID
        else -> null
    }

    private fun CreateCardState.locationError(): CreateCardValidationError? = when {
        selectedLevel == null -> CreateCardValidationError.LEVEL_REQUIRED
        levels.childrenOf(selectedLevel.id).isNotEmpty() -> CreateCardValidationError.LEVEL_MUST_BE_FINAL
        else -> null
    }

    private fun CreateCardState.descriptionError(): CreateCardValidationError? = when {
        description.isBlank() -> CreateCardValidationError.DESCRIPTION_REQUIRED
        description.length > MAX_DESCRIPTION_LENGTH -> CreateCardValidationError.DESCRIPTION_TOO_LONG
        else -> null
    }

    private fun CreateCardState.validateEvidence(
        evidence: CreateCardEvidenceDraft,
    ): CreateCardEvidenceError? {
        val type = selectedCardType
            ?: return CreateCardEvidenceError(CreateCardEvidenceErrorReason.INVALID_MEDIA)
        if (evidence.sizeBytes <= 0L) {
            return CreateCardEvidenceError(CreateCardEvidenceErrorReason.INVALID_MEDIA)
        }
        if (evidence.sizeBytes > MAX_EVIDENCE_FILE_SIZE_BYTES) {
            return CreateCardEvidenceError(
                CreateCardEvidenceErrorReason.FILE_TOO_LARGE,
                MAX_EVIDENCE_FILE_SIZE_BYTES / BYTES_PER_MEGABYTE,
            )
        }
        if (evidences.size >= MAX_TOTAL_EVIDENCES) {
            return CreateCardEvidenceError(
                CreateCardEvidenceErrorReason.TOTAL_LIMIT_REACHED,
                MAX_TOTAL_EVIDENCES.toLong(),
            )
        }
        return when (evidence.mediaType) {
            CardEvidenceMediaType.IMAGE -> {
                val limit = type.quantityImagesCreate.orZero()
                if (imageEvidenceCount >= limit) {
                    CreateCardEvidenceError(CreateCardEvidenceErrorReason.IMAGE_LIMIT_REACHED, limit)
                } else {
                    null
                }
            }
            CardEvidenceMediaType.VIDEO -> {
                val countLimit = type.quantityVideosCreate.orZero()
                val durationLimit = type.videosDurationCreate.orZero()
                when {
                    evidence.durationMillis <= 0 -> CreateCardEvidenceError(CreateCardEvidenceErrorReason.INVALID_MEDIA)
                    videoEvidenceCount >= countLimit ->
                        CreateCardEvidenceError(CreateCardEvidenceErrorReason.VIDEO_LIMIT_REACHED, countLimit)
                    durationLimit <= 0 || evidence.durationMillis > durationLimit * MILLIS_PER_SECOND ->
                        CreateCardEvidenceError(CreateCardEvidenceErrorReason.VIDEO_DURATION_EXCEEDED, durationLimit)
                    else -> null
                }
            }
            CardEvidenceMediaType.AUDIO -> {
                val countLimit = type.quantityAudiosCreate.orZero()
                val durationLimit = type.audiosDurationCreate.orZero()
                when {
                    evidence.durationMillis <= 0 -> CreateCardEvidenceError(CreateCardEvidenceErrorReason.INVALID_MEDIA)
                    audioEvidenceCount >= countLimit ->
                        CreateCardEvidenceError(CreateCardEvidenceErrorReason.AUDIO_LIMIT_REACHED, countLimit)
                    durationLimit <= 0 || evidence.durationMillis > durationLimit * MILLIS_PER_SECOND ->
                        CreateCardEvidenceError(CreateCardEvidenceErrorReason.AUDIO_DURATION_EXCEEDED, durationLimit)
                    else -> null
                }
            }
        }
    }

    private fun CreateCardState.pathTo(levelId: String): List<Level> {
        val byId = levels.associateBy(Level::id)
        val reversed = mutableListOf<Level>()
        val visited = mutableSetOf<String>()
        var current = byId[levelId]
        while (current != null && visited.add(current.id)) {
            reversed += current
            current = byId[current.superiorId]
        }
        return reversed.asReversed()
    }

    private fun List<Level>.childrenOf(parentId: String?): List<Level> {
        val ids = asSequence().map(Level::id).toSet()
        return filter { level ->
            if (parentId == null) {
                level.superiorId.isBlank() || level.superiorId == "0" || level.superiorId !in ids
            } else {
                level.superiorId == parentId
            }
        }.sortedWith(compareBy(Level::depth, Level::name))
    }

    private inline fun <T> List<T>.activeOnly(status: (T) -> String): List<T> =
        filter { status(it).equals("A", ignoreCase = true) }

    private companion object {
        const val MAX_DESCRIPTION_LENGTH = 200
        const val MAX_DESCRIPTION_INPUT = 201
        const val MAX_TOTAL_EVIDENCES = 20
        const val MAX_EVIDENCE_FILE_SIZE_BYTES = 25L * 1024L * 1024L
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
        const val MILLIS_PER_SECOND = 1_000L
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun CardEvidenceMediaType.creationTypeCode(): String = when (this) {
    CardEvidenceMediaType.IMAGE -> "IMCR"
    CardEvidenceMediaType.VIDEO -> "VICR"
    CardEvidenceMediaType.AUDIO -> "AUCR"
}

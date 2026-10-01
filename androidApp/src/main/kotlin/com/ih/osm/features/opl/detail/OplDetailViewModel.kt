package com.ih.osm.features.opl.detail

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ih.osm.core.feature.viewmodel.GRViewModel
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import com.ih.osm.features.evidence.EvidenceMediaUiItem
import com.ih.osm.features.opl.detail.data.fileName
import com.ih.osm.features.opl.detail.domain.OplMediaRepository
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContent
import com.ih.osm.features.opl.domain.model.OplContentType
import com.ih.osm.features.opl.domain.model.OplDownloadProgress
import com.ih.osm.features.opl.domain.repository.OplRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OplDetailUiState(
    val isLoading: Boolean = true,
    val opl: Opl? = null,
    val error: OplDetailError? = null,
    val files: Map<Long, String> = emptyMap(),
    val loadingFiles: Set<Long> = emptySet(),
    val failedFiles: Set<Long> = emptySet(),
    val selectedPdfId: Long? = null,
    val isDownloading: Boolean = false,
    val isDeleting: Boolean = false,
    val progress: OplDownloadProgress? = null,
    val confirmation: OplConfirmation? = null,
    val operationError: OplOperationError? = null,
    val mediaEpoch: Int = 0,
    val preloadImages: Boolean = true,
) {
    val isBusy get() = isLoading || isDownloading || isDeleting
}

enum class OplDetailError { LOAD_FAILED, NO_CONNECTION, ACCESS_DENIED, NOT_FOUND }
enum class OplConfirmation { DOWNLOAD, REPLACE, DELETE }
enum class OplOperationError { DOWNLOAD_FAILED, DELETE_FAILED }

class OplDetailViewModel(
    private val repository: OplRepository,
    private val media: OplMediaRepository,
) : GRViewModel<OplDetailUiState, OplDetailViewModel.Action, OplDetailViewModel.Event>(OplDetailUiState()) {
    sealed interface Action {
        data class Load(val siteId: Long, val oplId: Long) : Action
        data object Retry : Action
        data class ResolveMedia(val contentId: Long) : Action
        data class OpenPdf(val contentId: Long) : Action
        data object ClosePdf : Action
        data object RequestDownload : Action
        data object RequestDelete : Action
        data object ConfirmOperation : Action
        data object DismissConfirmation : Action
        data object DismissOperationError : Action
    }
    sealed interface Event { data object DownloadFinished : Event; data object Deleted : Event }

    private var request: Action.Load? = null
    private val mediaJobs = mutableMapOf<Long, Job>()

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Load -> if (request != action) { request = action; load(action) }
            Action.Retry -> if (!getStateValue().isBusy) request?.let(::load)
            is Action.ResolveMedia -> resolve(action.contentId)
            is Action.OpenPdf -> {
                if (getStateValue().isBusy) return
                val content = getStateValue().opl?.content?.firstOrNull { it.id == action.contentId } ?: return
                if (content.type != OplContentType.PDF) return
                setState { copy(selectedPdfId = content.id) }
                resolve(content.id)
            }
            Action.ClosePdf -> setState { copy(selectedPdfId = null) }
            Action.RequestDownload -> {
                val state = getStateValue()
                if (state.isBusy || state.opl == null) return
                setState { copy(confirmation = if (state.opl.isDownloaded) OplConfirmation.REPLACE else OplConfirmation.DOWNLOAD) }
            }
            Action.RequestDelete -> {
                val state = getStateValue()
                if (state.isBusy || state.opl?.isDownloaded != true) return
                setState { copy(confirmation = OplConfirmation.DELETE) }
            }
            Action.ConfirmOperation -> {
                if (getStateValue().isBusy) return
                when (getStateValue().confirmation) {
                    OplConfirmation.DOWNLOAD, OplConfirmation.REPLACE -> download()
                    OplConfirmation.DELETE -> delete()
                    null -> Unit
                }
            }
            Action.DismissConfirmation -> setState { copy(confirmation = null) }
            Action.DismissOperationError -> setState { copy(operationError = null) }
        }
    }

    private fun load(action: Action.Load) {
        setState { OplDetailUiState() }
        viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { repository.getById(action.siteId, action.oplId) }) {
                    is NetworkResult.Success -> setState { copy(opl = result.data, isLoading = false) }
                    is NetworkResult.Failure -> setState {
                        copy(isLoading = false, error = when {
                            result.error.statusCode == 401 || result.error.statusCode == 403 -> OplDetailError.ACCESS_DENIED
                            result.error.statusCode == 404 -> OplDetailError.NOT_FOUND
                            result.error.kind == NetworkErrorKind.CONNECTIVITY -> OplDetailError.NO_CONNECTION
                            else -> OplDetailError.LOAD_FAILED
                        })
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e(TAG, "Failed to load OPL id=${action.oplId}", error)
                setState { copy(isLoading = false, error = OplDetailError.LOAD_FAILED) }
            }
        }
    }

    private fun resolve(id: Long) {
        val state = getStateValue()
        val opl = state.opl ?: return
        if (state.isBusy) return
        val content = opl.content.firstOrNull { it.id == id } ?: return
        if (id in state.files || id in state.loadingFiles) return
        setState { copy(loadingFiles = loadingFiles + id, failedFiles = failedFiles - id) }
        mediaJobs[id] = viewModelScope.launch {
            try {
                val file = media.resolve(opl, content)
                setState { copy(files = files + (id to file.absolutePath)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w(TAG, "Failed to resolve OPL media id=$id", error)
                setState { copy(failedFiles = failedFiles + id) }
            } finally {
                setState { copy(loadingFiles = loadingFiles - id) }
                mediaJobs.remove(id)
            }
        }
    }

    private suspend fun stopMedia() {
        mediaJobs.values.toList().forEach { it.cancelAndJoin() }
        mediaJobs.clear()
    }

    private fun download() {
        val opl = getStateValue().opl ?: return
        setState { copy(isDownloading = true, confirmation = null, selectedPdfId = null,
            operationError = null, mediaEpoch = mediaEpoch + 1) }
        viewModelScope.launch {
            try {
                stopMedia()
                val result = withContext(Dispatchers.IO) {
                    repository.download(opl.siteId, opl.id) { update -> setState { copy(progress = update) } }
                }
                when (result) {
                    is NetworkResult.Success -> {
                        media.clearResolvedFiles(opl.siteId, opl.id)
                        setState { copy(opl = result.data, files = emptyMap(), failedFiles = emptySet(), isDownloading = false) }
                        sendNewEvent(Event.DownloadFinished)
                    }
                    is NetworkResult.Failure -> {
                        Log.w(TAG, "OPL download failed: ${result.error.kind}")
                        setState { copy(operationError = OplOperationError.DOWNLOAD_FAILED) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w(TAG, "Failed to download OPL id=${opl.id}", error)
                setState { copy(operationError = OplOperationError.DOWNLOAD_FAILED) }
                refreshLocalMarker(opl)
            } finally {
                setState { copy(isDownloading = false, progress = null) }
            }
        }
    }

    private fun delete() {
        val opl = getStateValue().opl?.takeIf { it.isDownloaded } ?: return
        setState { copy(isDeleting = true, confirmation = null, selectedPdfId = null,
            operationError = null, mediaEpoch = mediaEpoch + 1) }
        viewModelScope.launch {
            try {
                stopMedia()
                when (val result = withContext(Dispatchers.IO) { repository.deleteDownloaded(opl.siteId, opl.id) }) {
                    is NetworkResult.Success -> {
                        media.clearResolvedFiles(opl.siteId, opl.id)
                        setState { copy(opl = opl.copy(isDownloaded = false, downloadRevision = null),
                            files = emptyMap(), failedFiles = emptySet(), isDeleting = false, preloadImages = false) }
                        sendNewEvent(Event.Deleted)
                    }
                    is NetworkResult.Failure -> setState { copy(operationError = OplOperationError.DELETE_FAILED) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w(TAG, "Failed to remove local OPL id=${opl.id}", error)
                setState { copy(operationError = OplOperationError.DELETE_FAILED) }
                refreshLocalMarker(opl)
            } finally {
                setState { copy(isDeleting = false) }
            }
        }
    }

    private suspend fun refreshLocalMarker(opl: Opl) {
        val result = withContext(Dispatchers.IO) { repository.getDownloaded(opl.siteId) }
        if (result is NetworkResult.Success) {
            val local = result.data.firstOrNull { it.id == opl.id }
            setState { copy(opl = local ?: opl.copy(isDownloaded = false, downloadRevision = null),
                files = emptyMap(), failedFiles = emptySet()) }
        }
    }

    private companion object { const val TAG = "OplDetailViewModel" }
}

internal fun OplContent.galleryType(): CardEvidenceMediaType? = when (type) {
    OplContentType.IMAGE -> CardEvidenceMediaType.IMAGE
    OplContentType.VIDEO -> CardEvidenceMediaType.VIDEO
    // Current backend only creates texto/imagen/video/pdf. Future/legacy audio remains displayable.
    OplContentType.UNKNOWN -> if (typeCode.lowercase() in setOf("audio", "sonido")) CardEvidenceMediaType.AUDIO else null
    else -> null
}

internal fun OplContent.galleryItem(state: OplDetailUiState) = EvidenceMediaUiItem(
    id = id.toString(),
    source = state.files[id],
    displayName = fileName(this),
    mediaType = requireNotNull(galleryType()),
    loading = id in state.loadingFiles,
    failed = id in state.failedFiles,
)

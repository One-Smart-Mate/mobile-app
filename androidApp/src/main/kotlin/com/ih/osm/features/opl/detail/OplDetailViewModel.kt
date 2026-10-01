package com.ih.osm.features.opl.detail

import android.net.Uri
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
import com.ih.osm.features.opl.domain.repository.OplRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    val isExporting: Boolean = false,
    val exportFailed: Boolean = false,
)

enum class OplDetailError { LOAD_FAILED, NO_CONNECTION, ACCESS_DENIED, NOT_FOUND }

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
        data class Export(val destination: Uri, val siteName: String) : Action
        data object DismissExportError : Action
    }
    sealed interface Event { data object ExportFinished : Event }

    private var request: Action.Load? = null

    override fun processImpl(action: Action) {
        when (action) {
            is Action.Load -> if (request != action) { request = action; load(action) }
            Action.Retry -> if (!getStateValue().isLoading) request?.let(::load)
            is Action.ResolveMedia -> resolve(action.contentId)
            is Action.OpenPdf -> {
                val content = getStateValue().opl?.content?.firstOrNull { it.id == action.contentId } ?: return
                if (content.type != OplContentType.PDF) return
                setState { copy(selectedPdfId = content.id) }
                resolve(content.id)
            }
            Action.ClosePdf -> setState { copy(selectedPdfId = null) }
            is Action.Export -> export(action)
            Action.DismissExportError -> setState { copy(exportFailed = false) }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Failed to load OPL id=${action.oplId}", error)
                setState { copy(isLoading = false, error = OplDetailError.LOAD_FAILED) }
            }
        }
    }

    private fun resolve(id: Long) {
        val state = getStateValue()
        val opl = state.opl ?: return
        val content = opl.content.firstOrNull { it.id == id } ?: return
        if (id in state.files || id in state.loadingFiles) return
        setState { copy(loadingFiles = loadingFiles + id, failedFiles = failedFiles - id) }
        viewModelScope.launch {
            try {
                val file = media.resolve(opl, content)
                setState { copy(files = files + (id to file.absolutePath)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Failed to resolve OPL media id=$id", error)
                setState { copy(failedFiles = failedFiles + id) }
            } finally {
                setState { copy(loadingFiles = loadingFiles - id) }
            }
        }
    }

    private fun export(action: Action.Export) {
        val state = getStateValue()
        val opl = state.opl ?: return
        if (state.isExporting) return
        setState { copy(isExporting = true, exportFailed = false) }
        viewModelScope.launch {
            try {
                media.export(opl, action.siteName, action.destination)
                sendNewEvent(Event.ExportFinished)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Failed to export OPL id=${opl.id}", error)
                setState { copy(exportFailed = true) }
            } finally {
                setState { copy(isExporting = false) }
            }
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

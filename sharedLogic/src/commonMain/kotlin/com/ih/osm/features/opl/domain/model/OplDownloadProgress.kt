package com.ih.osm.features.opl.domain.model

enum class OplDownloadStage { INFORMATION, MEDIA, SAVING, COMPLETE }

data class OplDownloadProgress(
    val stage: OplDownloadStage,
    val progress: Float,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val downloadedBytes: Long = 0,
)

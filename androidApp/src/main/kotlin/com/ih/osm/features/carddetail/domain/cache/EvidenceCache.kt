package com.ih.osm.features.carddetail.domain.cache

import com.ih.osm.features.card.domain.model.CardEvidence
import com.ih.osm.features.card.domain.model.CardEvidenceMediaType
import java.io.File

interface EvidenceCache {
    suspend fun resolve(evidence: CardEvidence): Result<File>
    suspend fun adopt(localFile: File, remoteReference: String, mediaType: CardEvidenceMediaType)
}

package com.ih.osm.features.opl.detail.domain

import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContent
import java.io.File

/** Android adapter for the shared offline evidence store. */
interface OplMediaRepository {
    suspend fun resolve(opl: Opl, content: OplContent): File
    suspend fun clearResolvedFiles(siteId: Long, oplId: Long)
}

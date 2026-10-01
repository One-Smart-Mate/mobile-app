package com.ih.osm.features.opl.detail.domain

import android.net.Uri
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.model.OplContent
import java.io.File

/** Android file access for the lesson viewer and its user-requested export. */
interface OplMediaRepository {
    suspend fun resolve(opl: Opl, content: OplContent): File
    suspend fun export(opl: Opl, siteName: String, destination: Uri)
}

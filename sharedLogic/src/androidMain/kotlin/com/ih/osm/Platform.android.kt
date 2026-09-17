package com.ih.osm

import android.os.Build
import java.util.TimeZone

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val authenticationName: String = "ANDROID"
    override val timeZone: String = TimeZone.getDefault().id
}

actual fun getPlatform(): Platform = AndroidPlatform()

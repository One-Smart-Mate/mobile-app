package com.ih.osm

import platform.UIKit.UIDevice
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val authenticationName: String = "IOS"
    override val timeZone: String = NSTimeZone.localTimeZone.name
}

actual fun getPlatform(): Platform = IOSPlatform()

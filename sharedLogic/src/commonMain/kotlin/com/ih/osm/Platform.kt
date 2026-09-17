package com.ih.osm

interface Platform {
    val name: String
    val authenticationName: String
    val timeZone: String
}

expect fun getPlatform(): Platform

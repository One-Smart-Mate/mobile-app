package com.ih.osm

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
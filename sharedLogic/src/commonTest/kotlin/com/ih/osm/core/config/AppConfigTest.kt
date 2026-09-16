package com.ih.osm.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AppConfigTest {
    @Test
    fun normalizesBaseUrl() {
        val config = AppConfig.create(
            baseUrl = "https://api.example.com",
            environment = AppEnvironment.DEV,
            enableNetworkLogging = true,
        )

        assertEquals("https://api.example.com/", config.baseUrl)
    }

    @Test
    fun disablesLoggingInProduction() {
        val config = AppConfig.create(
            baseUrl = "https://api.example.com/",
            environment = AppEnvironment.PROD,
            enableNetworkLogging = true,
        )

        assertFalse(config.enableNetworkLogging)
    }

    @Test
    fun rejectsInsecureProductionUrl() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.create(
                baseUrl = "http://api.example.com",
                environment = AppEnvironment.PROD,
                enableNetworkLogging = false,
            )
        }
    }
}

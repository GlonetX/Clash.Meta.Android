package com.github.kr328.clash.service.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class RestrictedProviderTest {
    @Test fun rejectsNetworkProvidersAndInvalidInventory() {
        listOf("{\"hasProviderUrl\":true}", "{\"error\":\"invalid\"}", "{}").forEach {
            assertTrue(runCatching { ExternalProfileDownload.validateProviderInventory(Json.parseToJsonElement(it).jsonObject) }.isFailure)
        }
        ExternalProfileDownload.validateProviderInventory(Json.parseToJsonElement("{\"hasProviderUrl\":false}").jsonObject)
    }
}

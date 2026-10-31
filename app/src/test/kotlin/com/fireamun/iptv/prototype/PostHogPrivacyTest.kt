package com.fireamun.iptv.prototype

import com.posthog.PersonProfiles
import com.posthog.PostHogEvent
import com.posthog.logs.PostHogLogRecord
import org.junit.Assert.*
import org.junit.Test

class PostHogPrivacyTest {
    private val privateValue = "https://provider.invalid/private?username=private-user&password=private-password"

    @Test fun missingOrInvalidConfigurationDisablesMonitoring() {
        assertNull(postHogConfiguration(null, null))
        assertNull(postHogConfiguration("", "https://us.i.posthog.com"))
        assertNull(postHogConfiguration("phc_fixture", "http://provider.invalid"))
    }

    @Test fun configuredClientDisablesSensitiveAutomaticCaptureAndInstallsFilters() {
        val config = postHogConfiguration("phc_fixture", "https://us.i.posthog.com")!!
        assertFalse(config.captureDeepLinks)
        assertFalse(config.captureScreenViews)
        assertFalse(config.sessionReplay)
        assertFalse(config.sessionReplayConfig.captureLogcat)
        assertFalse(config.captureElementInteractions)
        assertFalse(config.preloadFeatureFlags)
        assertEquals(PersonProfiles.NEVER, config.personProfiles)
        assertTrue(config.errorTrackingConfig.autoCapture)
        assertFalse(config.errorTrackingConfig.captureNativeCrashes)
        assertEquals(1, config.beforeSendList.size)
        assertEquals(1, config.logs.beforeSendList.size)
    }

    @Test fun unknownEventsAndFreeFormLogsAreDropped() {
        assertNull(safePostHogEvent(PostHogEvent(privateValue, "anonymous")))
        assertNull(safePostHogLog(PostHogLogRecord(privateValue)))
    }

    @Test fun productEventsKeepOnlyValidatedFieldsAndCountBuckets() {
        val event = safePostHogEvent(PostHogEvent("library_imported", "anonymous", mutableMapOf(
            "source_kind" to "xtream", "entry_count" to 12345,
            "url" to privateValue, "title" to "private-title", "\$set" to mapOf("name" to privateValue), "\$device_model" to privateValue,
        )))!!
        assertEquals("xtream", event.properties!!["source_kind"])
        assertEquals("10001_100000", event.properties!!["entry_count_bucket"])
        assertFalse(event.properties!!.containsKey("entry_count"))
        assertFalse(event.toString().contains("private-"))
        assertNull(safePostHogEvent(PostHogEvent("library_imported", "anonymous", mutableMapOf("source_kind" to privateValue))))
    }

    @Test fun exceptionPayloadDropsMessagesStacksAndUnapprovedProperties() {
        val event = safePostHogEvent(PostHogEvent("\$exception", "anonymous", mutableMapOf(
            "\$exception_list" to listOf(mapOf("type" to "java.lang.IllegalStateException", "value" to privateValue,
                "stacktrace" to mapOf("frames" to listOf(mapOf("filename" to privateValue))))),
            "\$exception_message" to privateValue, "\$exception_fingerprint" to privateValue,
            "\$exception_level" to "fatal", "\$app_version" to "0.8.0", "breadcrumbs" to privateValue,
        )))!!
        assertTrue(event.toString().contains("IllegalStateException"))
        assertEquals("0.8.0", event.properties!!["\$app_version"])
        assertEquals("fatal", event.properties!!["\$exception_level"])
        assertFalse(event.toString().contains("provider.invalid"))
        assertFalse(event.toString().contains("private-"))
        assertFalse(event.toString().contains("stacktrace"))
    }

    @Test fun structuredLogsDropExtraAttributesAndScreenContext() {
        val log = safePostHogLog(PostHogLogRecord("library import failed", attributes = mapOf(
            "operation" to "import", "source_kind" to "url", "error_code" to "read_failed", "url" to privateValue,
        ), screenName = privateValue, featureFlagKeys = listOf(privateValue)))!!
        assertEquals("read_failed", log.attributes["error_code"])
        assertFalse(log.toString().contains("provider.invalid"))
        assertNull(log.screenName)
        assertTrue(log.featureFlagKeys.isEmpty())
    }
}

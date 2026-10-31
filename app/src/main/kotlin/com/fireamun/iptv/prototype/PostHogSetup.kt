package com.fireamun.iptv.prototype

import com.posthog.PostHogEvent
import com.posthog.PersonProfiles
import com.posthog.android.PostHogAndroidConfig
import com.posthog.logs.PostHogLogRecord
import com.fireamun.iptv.core.model.ImportError

internal fun postHogConfiguration(token: String?, host: String?): PostHogAndroidConfig? {
    val key = token?.trim()?.takeIf { it.matches(Regex("phc_[A-Za-z0-9_-]+")) } ?: return null
    val endpoint = host?.trim()?.takeIf { it in setOf("https://us.i.posthog.com", "https://eu.i.posthog.com") } ?: return null
    return PostHogAndroidConfig(projectToken = key, host = endpoint).apply {
        captureDeepLinks = false
        captureScreenViews = false
        captureElementInteractions = false
        captureRageClicks = false
        captureDeadClicks = false
        sessionReplay = false
        sessionReplayConfig.captureLogcat = false
        surveys = false
        capturePushNotificationSubscriptions = false
        capturePushNotificationOpened = false
        personProfiles = PersonProfiles.NEVER
        preloadFeatureFlags = false
        errorTrackingConfig.autoCapture = true
        errorTrackingConfig.captureNativeCrashes = false
        maxQueueSize = 100
        addBeforeSend { safePostHogEvent(it) }
        logs.serviceName = "amar-iptv"
        logs.environment = if (BuildConfig.DEBUG) "debug" else "release"
        logs.addBeforeSend { safePostHogLog(it) }
    }
}

private val sourceKinds = setOf("file", "url", "xtream")
private val contentKinds = setOf("unknown", "live", "movie", "series", "episode")
private val sdkProperties = setOf("\$lib", "\$lib_version", "\$app_name", "\$app_version", "\$app_build", "\$app_namespace",
    "\$os_name", "\$os_version", "\$device_manufacturer", "\$device_model", "\$device_type", "\$is_emulator",
    "\$screen_density", "\$screen_height", "\$screen_width", "\$locale", "\$timezone", "\$session_id", "\$window_id", "\$process_person_profile")
private val errorTypes = setOf("NullPointerException", "IllegalStateException", "IllegalArgumentException", "RuntimeException",
    "IOException", "SecurityException", "OutOfMemoryError", "UnknownHostException", "SocketTimeoutException", "ConnectException",
    "SSLException", "IndexOutOfBoundsException", "CancellationException")

private fun countBucket(count: Number?): String? = when (val value = count?.toLong() ?: return null) {
    in 0..100 -> "0_100"
    in 101..1000 -> "101_1000"
    in 1001..10000 -> "1001_10000"
    in 10001..100000 -> "10001_100000"
    else -> if (value > 100000) "over_100000" else null
}

private fun importProperties(raw: Map<String, Any>): Map<String, Any>? {
    val kind = raw["source_kind"]?.takeIf { it in sourceKinds } ?: return null
    val count = countBucket(raw["entry_count"] as? Number) ?: return null
    return mapOf("source_kind" to kind, "entry_count_bucket" to count)
}

/** Runs before SDK queue persistence. Unknown events/properties never enter telemetry. */
@Suppress("DEPRECATION")
internal fun safePostHogEvent(event: PostHogEvent): PostHogEvent? {
    val raw = event.properties.orEmpty()
    val fields: Map<String, Any> = when (event.event) {
        "library_imported", "library_refreshed" -> importProperties(raw) ?: return null
        "content_playback_started" -> mapOf("content_kind" to (raw["content_kind"]?.takeIf { it in contentKinds } ?: return null),
            "playback_stage" to "requested") // Measures launching playback, not first decoded frame.
        "favourite_changed" -> mapOf("content_kind" to (raw["content_kind"]?.takeIf { it in contentKinds } ?: return null),
            "is_favourite" to (raw["is_favourite"] as? Boolean ?: return null))
        "Application Opened", "Application Backgrounded", "Application Installed", "Application Updated" -> emptyMap()
        "\$exception" -> {
            val types = (raw["\$exception_list"] as? List<*>)?.take(4)?.map {
                val name = ((it as? Map<*, *>)?.get("type") as? String)?.substringAfterLast('.')
                mapOf("type" to (name?.takeIf { it in errorTypes } ?: "UncaughtException"), "value" to "Details withheld")
            }.orEmpty().ifEmpty { listOf(mapOf("type" to "UncaughtException", "value" to "Details withheld")) }
            mapOf("\$exception_list" to types, "\$exception_level" to (raw["\$exception_level"]?.takeIf { it in setOf("fatal", "error") } ?: "error"))
        }
        else -> return null
    }
    val safe = raw.filter { (key, value) ->
        key in sdkProperties && when (value) {
            is String -> value.matches(Regex("[A-Za-z0-9 ._:+()-]{1,100}"))
            is Boolean -> true
            is Number -> value.toDouble().isFinite() && value.toDouble() in -1_000_000.0..1_000_000.0
            else -> false
        }
    }.toMutableMap()
    safe.putAll(fields)
    safe["app_environment"] = if (BuildConfig.DEBUG) "debug" else "release"
    safe["\$geoip_disable"] = true
    return event.copy(properties = safe, set = null, apiKey = null)
}

internal fun safePostHogLog(record: PostHogLogRecord): PostHogLogRecord? {
    val raw = record.attributes
    val operation = raw["operation"]?.takeIf { it in setOf("import", "refresh", "load_series") } ?: return null
    val fields = when (record.body) {
        "library import completed" -> importProperties(raw) ?: return null
        "library import failed" -> mapOf(
            "source_kind" to (raw["source_kind"]?.takeIf { it in sourceKinds || it == "unknown" } ?: return null),
            "error_code" to (raw["error_code"]?.takeIf { code -> ImportError.entries.any { it.name.lowercase() == code } } ?: return null))
        else -> return null
    }
    return record.copy(attributes = fields + ("operation" to operation), screenName = null, featureFlagKeys = emptyList(),
        traceId = null, spanId = null, traceFlags = null, appState = record.appState?.takeIf { it in setOf("foreground", "background") })
}

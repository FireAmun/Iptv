package com.fireamun.iptv.prototype

import android.content.Context
import com.fireamun.iptv.core.designsystem.AmarLegal
import com.posthog.PostHog

/**
 * Locally stored Terms acceptance (version and time) and the separate usage-statistics choice.
 * Nonsecret; sources never enter these preferences. Statistics stay off until the Terms are accepted.
 */
internal object LegalConsent {
    private const val PREFERENCES = "legal"
    private const val TERMS_VERSION = "terms_version"
    private const val TERMS_ACCEPTED_AT = "terms_accepted_at"
    private const val ANALYTICS = "analytics"

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun termsAccepted(context: Context): Boolean = preferences(context).getInt(TERMS_VERSION, 0) >= AmarLegal.TERMS_VERSION

    fun analyticsEnabled(context: Context): Boolean = preferences(context).getBoolean(ANALYTICS, true)

    /** Statistics may be sent only after the current Terms are accepted and while the user has not turned them off. */
    fun analyticsAllowed(context: Context): Boolean = termsAccepted(context) && analyticsEnabled(context)

    fun accept(context: Context, analytics: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        preferences(context).edit().putInt(TERMS_VERSION, AmarLegal.TERMS_VERSION).putLong(TERMS_ACCEPTED_AT, nowMillis)
            .putBoolean(ANALYTICS, analytics).commit()
        applyAnalytics(context)
    }

    fun setAnalytics(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ANALYTICS, enabled).commit()
        applyAnalytics(context)
    }

    /** Keeps the SDK's own persisted opt-out flag in line with this choice; a no-op when monitoring is not configured. */
    fun applyAnalytics(context: Context) {
        runCatching { if (analyticsAllowed(context)) PostHog.optIn() else PostHog.optOut() }
    }

    /** Test support: clears acceptance and the statistics choice. */
    fun reset(context: Context) { preferences(context).edit().clear().commit() }
}

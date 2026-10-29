# Monitoring

Future Gradle module: `:core:observability`.

Own a small allowlisted telemetry interface and managed monitoring adapters. Exclude credentials, provider addresses, raw stream/playlist URLs, titles, search text, and pairing tokens. Keep provider playback independent of monitoring availability.

The current PostHog adapter is in the app module; this separate Gradle module has not been created. See the [monitoring guide](../../docs/development/posthog.md) and [privacy boundary](../../app/src/main/kotlin/com/fireamun/iptv/prototype/PostHogSetup.kt).

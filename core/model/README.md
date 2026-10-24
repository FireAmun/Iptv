# Shared models

JVM module: `:core:model`. Implements safe UI channel/state types, stable error codes, and a protected address wrapper with explicit access and redacted diagnostics.

Own immutable IDs, catalogue/playback/entitlement state, safe error codes, and shared boundary types. Keep this module independent of UI, Room, networking, and billing SDKs. Add types only when a consumer needs them.

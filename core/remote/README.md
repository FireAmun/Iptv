# Phone-to-TV remote

Gradle module `:core:remote` (plain Kotlin, no Android dependencies).

Pinned-TLS transport, single-use QR pairing or compared-code pairing (commit-then-reveal) with explicit TV approval, revocable phone trust, versioned key commands, typing and sent playlists (`SentSource`) that wait for the TV's answer. The TV side (`RemoteServer`) performs keys, typing and sent playlists through an injected `RemoteActions`, so playback stays in the app's own player. Android storage, lifecycle and UI live in `:app` (`RemoteHost`, `RemoteController`, `RemoteUi.kt`).

See [docs/development/phone-remote.md](../../docs/development/phone-remote.md) for behaviour, the security model and the remaining parts.

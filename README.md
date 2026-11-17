# IPTV Android project

Arabic and English IPTV player for Android phones, tablets, and Android TV/Google TV. Users supply their own compatible sources; the app does not provide channels or content subscriptions.

## Start here

- [Progress and next task](progress.md)
- [Product brief](codex/docs/iptv-product-brief.md)
- [Architecture specification](docs/superpowers/specs/2026-10-24-iptv-architecture-design.md)
- [Project instructions](AGENTS.md)
- [GitHub delivery and CI](docs/development/github-workflow.md)
- [PostHog setup and captured data](docs/development/posthog.md)
- [Cinema design and app previews](docs/design/README.md)

## Directory layout

```text
iptv/
├── app/                    Android application, entry activities, dependency wiring
│   └── src/
│       ├── main/           Application code and Android resources
│       ├── test/           Local JVM tests
│       └── androidTest/    Device/emulator tests
├── core/
│   ├── model/              Shared IDs, state, errors, and boundary types
│   ├── data/               Source adapters, import, protected source/persistence contracts
│   ├── storage/            Android Room catalogue and Keystore-protected fields
│   ├── playback/           Media3 player ownership and playback coordination
│   ├── remote/             Local discovery, pairing, trust, and remote protocol
│   ├── billing/            Play purchases, verification adapter, and entitlements
│   └── observability/      Safe event interface and managed monitoring adapters
├── ui/
│   ├── mobile/             Phone/tablet Compose screens and ViewModels
│   └── tv/                 Compose for TV screens, focus, and remote navigation
├── gradle/                 Gradle wrapper and version catalogue
├── scripts/                Project development/verification scripts when needed
├── docs/
│   ├── development/        Setup and verification records
│   └── superpowers/
│       ├── specs/          Architecture and subsystem specifications
│       └── plans/          Implementation plans
├── codex/docs/             Existing product planning record
├── AGENTS.md               Cross-chat agent handoff instructions
└── progress.md             Current status, decisions, evidence, and next task
```

Core and UI modules have `src/main/kotlin` and `src/test/kotlin` directories. `.gitkeep` files preserve currently empty source directories when version control is initialized. Remove them when real files occupy those directories.

## Current state

Local AVD launching is suspended under the6 November2026 [host policy](AGENTS.md). Old devices were removed; the standalone launcher fails closed. Use local JVM/build checks and the existing cloud phone/TV matrix, not personal devices for fixtures.

Amar Iptv configures `:app`, `:ui:mobile`, `:ui:tv`, `:core:model`, `:core:data`, `:core:storage`, `:core:playback`, and `:core:designsystem`. It supports Arabic/English, multiple M3U file/URL or Xtream-compatible sources, cached source restoration, paged live/movie/series browsing, search, favourites, episode navigation and Media3 playback. Local Continue Watching adds classified movie/episode positions and resume choices. Cinema appearance provides saved dark/light modes, shared palette/fonts and phone/TV layouts; see [progress](progress.md) for current verification and delivery. The remaining core directories reserve future boundaries. The permanent Play application ID is `com.fireamun.amariptv` from version 0.11.0; see [release signing](docs/development/first-build.md#release-bundle-and-upload-key).

See [build/install instructions](docs/development/first-build.md), [M3U/playback behavior and manual testing](docs/development/m3u-playback.md), [server login](docs/development/xtream-login.md), [saved-library protection](docs/development/saved-library.md), [browsing/search/favourites](docs/development/library-browsing.md) and [Continue Watching](docs/development/continue-watching.md) and [300k capacity measurements](docs/development/catalogue-capacity.md). EPG, pairing, billing and physical-device compatibility checks remain roadmap work.

Keep provider credentials and private source URLs out of tracked files. Machine SDK paths belong in an ignored `local.properties` when build configuration is introduced.

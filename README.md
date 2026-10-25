# IPTV Android project

Arabic and English IPTV player for Android phones, tablets, and Android TV/Google TV. Users supply their own compatible sources; the app does not provide channels or content subscriptions.

## Start here

- [Progress and next task](progress.md)
- [Product brief](codex/docs/iptv-product-brief.md)
- [Architecture specification](docs/superpowers/specs/2026-10-24-iptv-architecture-design.md)
- [Project instructions](AGENTS.md)
- [GitHub delivery and CI](docs/development/github-workflow.md)
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
│   ├── data/               Source adapters, import, repositories, Room, search, EPG
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

The prototype configures `:app`, `:ui:mobile`, `:ui:tv`, `:core:model`, `:core:data`, `:core:playback`, and `:core:designsystem`. It supports Arabic/English, session-only M3U file/URL import and Xtream-compatible server login, live/movie/series entries, episode navigation and Media3 playback. Cinema appearance adds a saved dark/light choice, shared palette/fonts and separate phone/TV layouts; see the tracker for integration status. The remaining core directories reserve future boundaries. The temporary application ID is `com.fireamun.iptv.prototype`; branding remains open.

See [build/install instructions](docs/development/first-build.md), [M3U/playback behavior and manual testing](docs/development/m3u-playback.md), and [server login](docs/development/xtream-login.md). The library is not persisted across process death; database storage, EPG and protected source persistence are later slices.

Keep provider credentials and private source URLs out of tracked files. Machine SDK paths belong in an ignored `local.properties` when build configuration is introduced.

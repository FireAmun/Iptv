# IPTV Android project

Arabic and English IPTV player for Android phones, tablets, and Android TV/Google TV. Users supply their own compatible sources; the app does not provide channels or content subscriptions.

## Start here

- [Progress and next task](progress.md)
- [Product brief](codex/docs/iptv-product-brief.md)
- [Architecture specification](docs/superpowers/specs/2026-10-24-iptv-architecture-design.md)
- [Project instructions](AGENTS.md)
- [GitHub delivery and CI](docs/development/github-workflow.md)

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
├── gradle/                 Future wrapper and version catalogue
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

This is the directory skeleton with repository CI and downloaded local Android tools. Gradle settings, app build scripts, the wrapper, Android manifest, and application code have not been added. The directories describe intended boundaries; they are not configured Gradle modules yet. No production application ID or application dependency versions have been selected.

Next: prepare the first-build implementation plan, configure the minimal Android project, and verify a debug build. Add only the modules needed by that first build; the remaining directories reserve the agreed layout.

Keep provider credentials and private source URLs out of tracked files. Machine SDK paths belong in an ignored `local.properties` when build configuration is introduced.

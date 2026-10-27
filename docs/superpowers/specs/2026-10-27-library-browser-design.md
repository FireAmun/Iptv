# Library browser and fast cached startup

Date: 27 October 2026. Owner explicitly authorized implementation and phased delivery.

## Intent and reference

Amar Iptv remains a consumer product. The owner clarified that only the Flutter reference at `C:/Users/User/Desktop/side_p/Iptv` was personal. Read that project without modifying it: phone navigation, settings, category chips, poster catalogue, cross-kind search and local resume are useful interaction references. Keep Amar's approved Cinema black/saffron and cream light themes, Manrope/Cairo, Android/TV architecture and protected storage. No reference credentials/configuration/data are copied.

## Milestone delivered by this plan

- Five persistent bottom destinations: Live TV, Movies, Series, Settings and Favourites. Per the owner's later refinement, Search is an action in the top header with compact results and Back to its originating destination. TV has a remote-friendly bottom ribbon and focusable content. English/Arabic and dark/light remain supported.
- All source setup, source count/name, last-saved time, refresh, rename, switch, removal, file/URL/server entry and appearance/language controls live in Settings. Empty installations start there; browsing pages direct an empty library to Settings. Import controls never clutter content pages.
- Room-backed paging, categories and Arabic/English full-text search across the selected source's content. Live includes untyped M3U entries without guessing movie/series hierarchy. Switching destinations does not fetch catalogues. Source refresh is explicit in Settings and preserves the last committed cache on failure.
- Functional persisted favourites for root live/movie/series items. They survive matching refresh and restart, and are removed only with their source.
- Movie/series poster grids and a title-detail presentation using supplied artwork, category, year and rating. Missing artwork uses intentional Cinema placeholders; missing ratings stay absent. Existing caches have no previously discarded artwork/rating: a manual refresh fills those fields, without an automatic startup download.
- HTTP import, episode loading and playback proceed without the repeated confirmation dialog, as explicitly requested. Keep HTTP/HTTPS validation, normal TLS verification and HTTPS playback's prohibition on HTTP subresources. No blanket certificate bypass.
- Configure native seek increments to 30 seconds. Other advanced player features are tracked below rather than represented by non-functional controls.

## Startup diagnosis and data boundary

The v0.7.2 Activity waits for `LibraryViewModel` restoration. That calls `RoomLibraryStore.restore()`, loads every entry and authenticates/decrypts every playback URL before showing content. It does not automatically fetch the provider; this eager restoration is the expensive path for large catalogues.

Add a fast session/header API which unwraps only source configuration and reads source ID/generation/time/count. Browse queries project catalogue metadata in bounded pages without selecting/decrypting playback blobs. Resolve and authenticate one playback locator only when that item is selected. Artwork is resolved only when a displayed image is requested. Legacy full restore remains an explicit compatibility/test operation, not the app startup/switch path. Corrupt selected media fails safely when resolved; one damaged off-screen locator no longer blocks browsing the whole source. An unreadable source envelope still blocks selection without destroying another cache.

Use a JVM data contract (`LibrarySession`, `BrowseRequest`, `BrowsePage`, metadata categories and selected-item/artwork resolvers), an app-layer PagingSource and Paging 3 Compose. Keep Room/Android dependencies out of core:data. SQL queries carry explicit source/generation so stale pages cannot attach another source or refreshed generation. Keep selected filters/scroll state while returning from playback.

## Storage and provider metadata

Schema 3 adds nullable encrypted artwork, optional year/rating/category key, a standalone FTS4 table and source/item favourite rows. Migrate schema 2 without rewriting existing source/locator ciphertext, Keystore aliases or item IDs; retain the existing 1-to-2 migration. Backfill only metadata/search text. No destructive fallback. One-time migration/indexing is distinct from repeated startup latency.

Search preserves original labels and uses separate diacritic/tatweel removal and alef normalization. Sanitize search into literal FTS terms; user input is never SQL/FTS operators. Category and kind filtering happen in SQLite before paging. Page size 60, finite prefetch/cache; queries never materialize the entire source for UI.

Provider artwork is a protected MediaAddress in data only, encrypted at rest with source/generation/item associated data. Public Channel/UI models expose only an artwork-presence flag and opaque artwork reference. A Coil fetcher resolves the encrypted URL on demand, with opaque memory-cache keys, no logger, normal TLS and no HTTPS downgrade. Disk image caching is disabled in this slice so response headers cannot persist private provider URLs. Source connection/credentials never become UI cache keys or errors.

Pin Paging 3.5.3 (published POM uses Kotlin 2.1.20). Pin Coil 3.3.0 (Kotlin 2.2.0), compatible with the existing Kotlin 2.2.10 compiler; current Coil 3.6.3's Kotlin 2.4.10 would require a separate toolchain upgrade. Retain Room 2.8.5, minSdk 28, target 36 and existing application ID.

## Explicit follow-up work

| Work | State after this milestone | Required behavior |
| --- | --- | --- |
| Continue Watching and persistent movie/episode resume | Follow-up | Source-scoped stable identity, periodic/pause/stop persistence, completed-state handling and series last-episode/next-episode experience. Never invent provider-side watch history. |
| Full movie/series details | Follow-up | On-demand/cached info endpoints: plots, cast, genres, backdrops, duration and season/episode detail. Show only valid supplied fields. |
| Caption/audio/quality selection | Follow-up | Expose actual Media3 tracks and adaptive/fixed video choices; unavailable tracks do not produce pretend options. |
| Minimize / picture-in-picture | Follow-up | Explicit supported-phone PiP action with lifecycle/progress handling and no hidden background service. |
| EPG | Follow-up after browsing | XMLTV mapping and correct supplied offsets/source time zones. |
| Cross-source combined search | Follow-up | This milestone searches the selected source; source selection remains in Settings. |

These are requested backlog items, not delivered features or disabled product buttons. Billing/Play decisions remain open and do not block this milestone.

## Verification and delivery

TDD for normalization, metadata parsing, lazy session/state and no-prompt HTTP actions. Real Room/Keystore schema migration, page/filter/FTS/favourite persistence, artwork encryption, stale-generation isolation and lazy corruption handling on disposable devices. Update phone/TV journeys for Settings-only management and destination-scoped content; assert no HTTP modal and retain D-pad tests. Capture synthetic dark/light/RTL content/settings/search/favourites previews and inspect them. Profile owner cached startup before/after without resetting its My IPTV source, printing only elapsed times/version/ready flags, no private catalogue labels/screenshots. One local emulator; no host firewall/driver/reboot changes. Earlier unexpected shutdown remains unexplained.

Normal branch/PR/review/latest-head CI/exact-head merge/FF-sync/branch cleanup workflow applies. No instrumentation/reset/uninstall on the owner emulator. Record actual delivery and the follow-up matrix in progress.md and live PR.

Primary references: [Paging overview](https://developer.android.com/topic/libraries/architecture/paging/v3-overview), [Paging release notes](https://developer.android.com/jetpack/androidx/releases/paging), [Room FTS](https://developer.android.com/training/data-storage/room/defining-data#fts), [Coil](https://coil-kt.github.io/coil/getting_started/), [Media3 tracks](https://developer.android.com/media/media3/exoplayer/track-selection). Versions checked 27 October 2026; resolved POM compatibility checked separately.

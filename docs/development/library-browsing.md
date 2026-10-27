# Library browsing (v0.8.0)

The bottom bar contains Live TV, Movies, Series, Settings and Favourites on both phone and TV. Search is a separate action in the top header, with a Back action to return to the previous destination. Results use compact rows so titles and hearts fit below the search field. Setup, saved playlist selection/rename/removal, timestamps, entry counts, refresh, language and appearance live in Settings. An empty installation opens Settings; an existing cache opens Live TV.

Live TV includes untyped M3U entries. Movies and Series show provider artwork, year and rating when available, with deliberate Cinema placeholders otherwise. Selecting a movie/series opens basic details and a Play/View episodes action. Series episodes load on demand. No title-based hierarchy or invented ratings are supplied.

Use the heart on a root channel/movie/series to save it in Favourites. Favourites belong to their saved source and survive restarts and confident identity matches on refresh. Search matches all content kinds in the selected playlist, using all entered words, prefix matching for words and exact numeric tokens. Original Arabic labels remain unchanged; a separate index removes tashkeel/tatweel and unifies alef variants. Categories filter before paging. Combined search across saved sources is a follow-up.

On TV, gold borders show focus. Back from a content page moves focus to the bottom navigation; Back there can leave the activity. Back from playback returns to the current collection; Back from episodes returns to the parent. Details scroll on small screens or large text settings. The native player uses 30-second seek increments when the stream supports seeking.

## Cached startup and privacy

Opening or switching a saved source makes no provider catalogue request. Startup unwraps the source envelope, reads its count/generation, and loads metadata in pages of 60 (bounded in-memory paging cache). It does not load/decrypt every playback URL. Only selecting an item resolves its protected locator. An unreadable selected locator reports a fixed error while leaving the catalogue available; an unreadable source envelope blocks that source without resetting other caches.

Refresh is explicit in Settings. Existing v0.7 caches did not retain artwork/year/rating; refresh the playlist once to populate provider metadata if wanted. The schema-3 upgrade builds the search index once; this is distinct from repeated cached launch work. No destructive database fallback or encryption-key change is used.

HTTP sources/imports/playback proceed without a repeated confirmation, per the owner's 27 October decision. Normal URL validation and TLS trust remain; HTTPS playback still rejects plaintext subresources and HTTPS network clients do not follow HTTP downgrades.

Artwork URLs are encrypted in Room under their own source/generation/item context. Coil sees opaque references as request/cache data; its fetcher resolves the private URL on demand. Only memory image caching is enabled, with opaque keys and no logger. The app has no hosted backend, analytics or provider logging.

## Follow-up scope

Continue Watching/persistent resume, rich movie/series info, caption/audio/quality selectors, phone PiP/minimize and EPG remain requested follow-up slices. The UI does not expose placeholder controls for these. See the [feature matrix](../superpowers/specs/2026-10-27-library-browser-design.md) and [progress tracker](../../progress.md).

Device verification must include real Room migration/FTS/favourite/lazy-locator cases, paging/search/focus journeys, protected artwork fetches and distinct-process persistence. Run test APKs only on disposable CI devices, never on the owner's saved emulator. Owner hands-on checks use install-r/manual UI and preserve sources.

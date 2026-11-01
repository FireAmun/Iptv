# Continue Watching and saved playback positions

1 November 2026. Design for the next approved functional slice; implementation and device evidence are not delivered by this document. Follow the owner's order: deliver URL Show/Hide first, then this slice, then large-catalogue validation and physical-device testing.

## Outcome and boundaries

Remember unfinished movies and episodes locally, resume them after reopening, offer **Continue / Start over**, and show a **Continue Watching** row. Keep the approved Cinema appearance, Arabic/English, dark/light and remote navigation. Keep the current 100,000-entry cap. No cloud sync, new backend, monitoring events, purchases, automatic provider refresh or automatic playback on launch.

The [architecture](2026-10-24-iptv-architecture-design.md) and [approved UI audit](../../design/ui-audit-2026-10-27.md) already separate persistent user state from replaceable catalogues. Existing movie identities survive confident refresh matching. Current API episodes have transient list IDs and discard their provider ID after constructing a playback locator; retaining that ID is required before persisting episode progress.

## User experience

- Movies shows recent unfinished movies; Series shows recent unfinished episodes. Both rows belong to the active playlist, use supplied titles and existing artwork/fallbacks, and show the latest 20 matching entries. Saved positions beyond the visible row remain available through ordinary catalogue selection.
- Place the row before the normal poster grid on unfiltered root Movies/Series pages. Hide it during search, category filtering and episode-list browsing. Do not add another navigation destination.
- Selecting a row offers Continue from the saved timestamp, Start over, and cancellation. Ordinary catalogue Play offers the same choice when eligible progress exists. Cancel changes neither progress nor playback.
- Start over clears that position before starting at zero. Completed items leave the row when playback reaches its end; do not guess a completion percentage. Selecting a completed title through its catalogue starts normally from the beginning.
- Save meaningful progress from 1 second onward. Display a progress bar only when duration is known; otherwise display the saved timestamp. Media-time direction remains LTR in Arabic.
- Returning from playback restores the row/card focus when present; completing an item falls back to a remaining row item or its catalogue parent. Late Paging emissions must not take focus from the user.
- Use the existing Cinema poster treatment and semantic palette. Resume controls must fit phone/TV, large text and Arabic; primary Continue gets initial TV focus once per dialog. Do not introduce standard pale dialogs.

## Identity and protected storage

1. Retain `EPISODE:<provider ID>` in `PlaylistEntry.providerKey` while parsing API episodes. A transient `Channel.id`, title, season order or list offset is never an episode identity.
2. A root movie/episode uses its existing source-scoped stable item ID. An API episode uses its stable parent-series item ID plus provider episode key. Source UUIDs distinguish identical imports and separate accounts.
3. Add schema 4 `watch_progress`, keyed by source UUID and an HMAC of the stable media reference. Store root/parent item ID, kind, position, nullable duration, completion and last-watched time. Index recent rows by source and timestamp. Do not key progress by catalogue generation.
4. Store the bounded episode snapshot needed for a direct resume in an encrypted BLOB: provider episode key, supplied title, season/episode numbers and playback locator. Use the existing per-source data key with progress-specific associated data, independent of replaceable catalogue generation. Bound the decoded payload to 16 KiB and validate addresses/fields; redact holder `toString()` methods. Root titles/artwork continue to come from the current catalogue.
5. Reuse the parent-series artwork for an episode row. Keep raw addresses and provider keys out of UI identifiers, diagnostics, telemetry and shared display state.
6. Query only a bounded recent result set; never decrypt or materialize the entire root catalogue for this row. Fetch history when the relevant movie/series page is opened and after playback, without contacting the provider.
7. Join history to the current root item in the same source. Refresh keeps positions for stable matched identities; missing items become unavailable without attaching history to a different title. Preserve unmatched history for a confident later reappearance.
8. Rename keeps progress. Explicit playlist removal clears only that source's history in the existing transaction; clear-all removes all history. A delayed write validates that its source/root still exists and cannot recreate a deleted playlist.
9. Migration 3 to 4 adds tables/indexes only. Existing source UUIDs, generations, item IDs, favourites, ciphertext and Keystore aliases remain unchanged; retain the 1 to 2 to 3 to 4 migration chain and backup exclusions.

Cached episode locators follow the current manual-refresh model. Opening a series obtains its current episode data; playing a matching provider episode updates its snapshot while preserving position. A provider can invalidate a cached locator, which uses the existing safe playback failure/retry path. Do not fetch every series on startup to prevalidate history.

## Playback lifecycle and ordering

- Introduce a typed, protected playback request with a unique session token, address, content kind and initial offset. Keep the data/storage target private to the application coordinator; UI progress models contain no locator.
- Persist only explicitly classified MOVIE/EPISODE items whose Media3 timeline is seekable and not dynamic. Live/UNKNOWN entries retain current behavior and never receive invented VOD hierarchy or bookmarks. Kind classification for generic M3U content remains separate P3 work.
- Checkpoint every 5 seconds while active and immediately on pause, seek, ended, stop and disposal. Ignore unprepared/unseekable or invalid negative positions; normalize known durations and clamp resume offsets to a valid range. A failed preparation must not overwrite an existing position with zero.
- Keep the latest position synchronously in the session/coordinator for background return, retry and activity recreation. Key the player by session token/retry, not by each position update, so checkpointing cannot recreate the player.
- Use an application-owned serial recorder for asynchronous Room writes, holding no Activity, View or Player references. It survives ViewModel disposal long enough to finish a normal exit checkpoint while the process lives. Never block the UI thread on disk.
- Preserve FIFO ordering between saves, reset and flush barriers. Associate reports with a session epoch per media identity: old callbacks must not restore a pre-reset position after Start over or a newer playback session. Closing playback still accepts that session's final disposal checkpoint.
- Await pending writes before reading a resume prompt or refreshing the row. Reads and source/selection changes carry a context token; stale work cannot open media after the user changes playlist/tab or cancels.
- Force-killing a process cannot guarantee a final callback; periodic saves bound ordinary loss to roughly the checkpoint interval. Cold-process checks must wait for an acknowledged checkpoint before asserting persistence.
- Keep prior progress on a failed write; surface a fixed safe save failure without stopping playback or logging inputs. A failed progress read/reset gives a fixed actionable error rather than silently overwriting history. Monitoring remains optional and receives no new fields/events.

## Module responsibilities

| Area | Change |
| --- | --- |
| `core:model/PlaybackProgress.kt` | Position/checkpoint/request and safe row/prompt models; normalization and bookmark eligibility policy. |
| `core:data/XtreamJson.kt` | Retain the actual provider episode key. |
| `core:data/PlaybackProgressPersistence.kt` | Source-scoped progress/target contracts and bounded episode snapshot codec. |
| `core:storage` | Schema 4 DAO/migration, protected snapshot read/write, bounded history joins and targeted cleanup. Keep progress-specific code in its own file rather than enlarging the catalogue importer. |
| `core:playback` | Initial seek, checkpoint callbacks, periodic polling and reliable lifecycle cleanup. |
| `app/PlaybackProgressCoordinator.kt` | Pending resume choice, serial recorder/epoch ordering, safe failure state and bounded history loading. |
| `app/LibraryViewModel.kt`, `LibraryActivity.kt` | Wire the coordinator to existing source/episode selection, playback and lifecycle; retain existing import/paging behavior. |
| `app/ContinueWatching.kt`, browser/resources | Real row, prompt, time labels and focus restoration using existing Cinema components. |

## Verification required

- JVM policy/codec/parser tests: only stable provider episode identity survives reordering; positions normalize safely; live/unseekable media are excluded; finished/zero/reset behavior and protected holder formatting are correct.
- Serial-recorder/coordinator tests: final close checkpoint, reset versus late callback, source-switch/cancel races, failed writes preserving prior state, and no startup stream/provider request.
- Room device tests: fresh reopen, root movie and episode snapshots, source separation, rename, reordered refresh, unavailable identity, cancellation, targeted removal, corruption and all migration paths with old ciphertext/IDs preserved.
- Real Media3 phone/TV flows using the existing self-generated 30-second clip: seek/pause/Back, reopen, Continue at the saved offset, Start over at zero, ordinary episode selection after reordered API data, ended-row removal, background/rotation/retry and live playback unchanged.
- A separate-process seed/restore probe verifies the row/position with a new PID. It complements actual playback checks rather than substituting a database assertion for playback evidence.
- Inspect Arabic/light and English/dark phone/TV screenshots, large-text focus and complete prompts. Re-run appropriate local checks and independent review, then require complete latest-head CI XML and all process probes before merge.
- Physical S25 Ultra/selected TV codec, endurance and navigation results remain separate hardware evidence. Do not claim them from emulator passes.

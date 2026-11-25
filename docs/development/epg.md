# TV guide (EPG)

Version 1 requirement. Owner choices (25 November 2026): now/next plus a per-channel schedule (a full grid comes later); guide links found automatically plus an optional manual XMLTV link per playlist; automatic daily refresh on Wi-Fi plus a manual Refresh guide button.

The work ships in two pull requests. This document covers the first (guide data). The second adds the screens: now/next on live rows, the current programme in the player, and a 24–48 hour channel schedule.

## Where the guide comes from

For the active playlist, the first available link is used:

1. A **manual link** the user saved under Settings → TV guide.
2. The playlist header's `url-tvg` / `x-tvg-url` (first http(s) link of a comma-separated list), remembered at each import or refresh.
3. For Xtream logins and recognised Xtream playlist exports, the account guide `xmltv.php`.

Links can carry credentials. They are sealed with the same Keystore protector as source logins (`guide-manual:<source>` / `guide-playlist:<source>` contexts), never shown back in the settings field, and never logged or sent to analytics.

## Matching channels

Each live (or unknown-kind) catalogue entry stores a guide key: `id:` plus the lower-cased M3U `tvg-id` / Xtream `epg_channel_id`, or, when the provider gives none, `title:` plus a normalised title. Title normalisation reuses the search normaliser (Arabic marks and alif forms), drops a two- or three-letter country prefix such as `UK:` or `AR |`, and drops quality words (HD, FHD, UHD, SD, 4K, HEVC and similar). XMLTV channels are indexed under their id and each display name, so either kind of key finds them. Movies and series never take a guide key.

Playlists saved before this version get keys at their next refresh.

## Download and parsing

- `GuideLoader` (OkHttp, 5-minute call limit) feeds `XmltvParser`, a dependency-free streaming reader. Gzip is detected by its magic bytes; the XML declaration's encoding is honoured, with malformed bytes replaced instead of failing the guide.
- DOCTYPE and entity declarations are skipped and never expanded; only the five XML entities and numeric references are decoded.
- Only channels the playlist can show are kept, and only programmes ending after now − 2 h and starting before now + 48 h.
- Times follow XMLTV `YYYYMMDDhhmmss ±hhmm` (UTC when no offset). A missing or invalid stop ends at the next programme's start, or one hour later for the last one; programmes are capped at 24 hours.
- Limits: 512 MB decompressed, 50,000 channels, 300,000 programmes, 5 minutes. Titles keep 256 characters, descriptions 1,024.

## Refresh rules

- **Automatic:** when the app opens or the playlist changes, if the guide is more than 12 hours old (or the device clock moved backwards) and the network is unmetered (Wi-Fi or Ethernet). A failed attempt waits at least an hour before the next automatic try. This needs the normal `ACCESS_NETWORK_STATE` permission.
- **Manual:** Refresh guide works on any network.
- A new playlist guide link (when no manual link overrides it) or a saved/cleared manual link makes the guide due immediately.
- A failure keeps the previous schedule and shows that the last update failed.
- Playlists with no live channels skip the download.

## Storage

Room schema 5 (`MIGRATION_4_5`): `library_entries.guideKey` with an index, `guide_state` (sealed links, last success, last attempt, programme count, last error), `guide_channels` (key → XMLTV channel id) and `guide_programmes` (channel, start, stop, title, description). Programme text is provider schedule data and is stored unencrypted. Removing a playlist removes its guide; removing every playlist (including Forgot PIN) removes all guide data.

## Code

`core/data`: `XmltvParser`, `GuideKeys`, `GuideLoader`, `GuidePersistence`, `ParsedPlaylist.guideLink`, `PlaylistEntry.guideId`, `SourceSpec.guideAddress()`. `core/storage`: `RoomLibraryStore` guide methods and `MIGRATION_4_5`. `app`: `GuideRefresher` (process-wide, refresh rules), `GuideSettings` (settings card shared by phone and TV), wiring in `LibraryActivity` and `LibraryViewModel.onImported`.

## Tests

JVM: `GuideParsingTest` (times and offsets, entities and CDATA, window and channel filtering, missing stops, gzip, no entity expansion, limits, title keys, M3U `tvg-id`/`url-tvg`, Xtream `epg_channel_id` and account link) and `GuideRefresherTest` (unmetered and 12-hour rules, retry back-off, manual refresh on any network, manual link priority and validation). Device (gate 120 → 124): `GuideStorageTest` (id and title matching, sealed links and their priority, refresh keeps the guide, removal and clear) and `GuideSettingsFlowTest` on phone and TV (Refresh guide downloads from a local fixture server and the schedule matches). The migration test now also upgrades a version-4 database. Instrumentation turns automatic guide downloads off so fixture servers only see expected requests.

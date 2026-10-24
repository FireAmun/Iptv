# M3U import and playback slice

Owner authorized continuing P2, selected file plus URL import, and re-authorized local hands-on app testing on 24 October 2026.

## Behavior

Both device UIs offer a system file picker and an M3U URL field. A TV without a picker reports that limitation and can use the URL path. A successful import atomically replaces the in-memory library; errors/cancellation preserve the last library. Nothing supplies channels automatically. Library data and source URLs are not persisted in this slice; activity recreation retains the ViewModel, while process death requires re-import.

Users browse entries and select one for Media3 playback. Back returns to the library and TV focus returns to the selected entry. Provide loading, empty, safe error/retry and HTTP-consent states in English/Arabic. Media3's native controls handle touch and D-pad playback. Release the player when the playback screen leaves STARTED or is closed; do not implement background playback.

## Boundaries

- `core:model`: safe channel labels/IDs, import/playback state, a redacted media-address wrapper. UI modules never receive raw source or stream addresses.
- `core:data`: a streaming UTF-8 extended-M3U parser and OkHttp URL loader. Pure JVM for fast parser/network tests; database/catalogue persistence remains later work.
- `core:playback`: Media3 ExoPlayer HLS/DASH/direct playback plus lifecycle-bound native PlayerView hosted in Compose. No alternate engine, DRM design, billing, media service or cloud backend.
- `app`: document selection, ViewModel state/import coordination, user-approved HTTP operations, and screen routing. `ui:mobile`/`ui:tv` own their device controls and lists.

## Input and privacy rules

Require `#EXTM3U`; accept UTF-8 BOM, LF/CRLF, comments, metadata labels/groups and ordinary HTTP/HTTPS entry addresses. Quoted commas in metadata must not split the title early. Preserve separate duplicate entries. Reject HLS manifests as catalogue imports, malformed/unsupported addresses and incomplete metadata entries. Unlabelled entries use localized numbered labels, never raw URLs.

Prototype limits: 8 MiB decompressed bytes, 16,384 UTF-16 code units per line, 512 code units per label/group, 10,000 entries and a five-second parser budget checked during reads and before returning results. Character limits bound String memory without penalizing Arabic for its larger UTF-8 representation; the overall byte cap still applies. Network calls have a 20-second total timeout and bounded connection/read timeouts. These are provisional protective limits, not weak-device performance claims. Keep one import active; cancel superseded work and close streams/calls. A provider that blocks file opening remains an environment risk to document honestly.

URLs may contain credentials. Keep them in memory, mask URL entry, clear the form on submission, never include addresses or exception messages in error UI/logs/telemetry, and use redacted `toString()` wrappers. Disable Media3 logging that could serialize nested network exceptions. Do not attach network logging interceptors. No cookie jar, cache, provider headers, or credential persistence is introduced. HTTPS playback also rejects HTTP/local-file subresources; choosing an HTTPS master does not implicitly approve plaintext segments or keys.

Allow only HTTP and HTTPS. Require explicit per-operation confirmation before an HTTP import or playback. Android's cleartext setting permits the confirmed operation; application rules gate its use. Disable cross-protocol redirects for both importer and player, so HTTPS cannot silently downgrade to HTTP. Keep default TLS certificate validation. Do not interpret playlist directives as arbitrary headers, cookies or local-file paths.

## Verification

Parser tests cover mixed Arabic/Latin metadata, quoting, duplicate entries, malformed inputs, HLS rejection, every resource limit, cancellation and address redaction. Loader tests use a loopback MockWebServer for real HTTP responses, redirects, errors and truncation. ViewModel tests cover replacement/preservation, HTTP approval and safe failure states.

Instrumented phone API 28 / TV API 36 checks exercise import, playback, Back/focus restoration and existing language flows. Use a self-generated short H.264/AAC video served by a test-only loopback server; never depend on a private provider or external stream uptime. Exercise the real file result path with a test-only content provider. No production debug shortcut or test source is shipped.

Owner explicitly permits local hands-on testing again: use one emulator at a time, inspect screenshots and operate the app through touch/D-pad inputs. Do not change host firewall rules, virtualization drivers or reboot the laptop. Record exactly what was exercised, including unsupported cases. CI remains required for delivery.

## Sources

Checked 24 October 2026: [Media3 setup](https://developer.android.com/media/media3/exoplayer/hello-world), [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3), [system document selection](https://developer.android.com/training/data-storage/shared/documents-files), [OkHttp](https://github.com/square/okhttp). Media3 1.11.1 AAR metadata declares minCompileSdk 36, compatible with this project's current compile SDK.

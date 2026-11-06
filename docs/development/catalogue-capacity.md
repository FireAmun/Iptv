# Catalogue capacity and performance

Measured 5–6 November 2026. Candidate: Amar Iptv 0.10.0 / code 12, maximum 300,000 entries. Complete final regression/CI delivery remains recorded in [progress](../../progress.md). This report establishes synthetic emulator behavior, not universal phone/TV hardware compatibility.

## Workload and method

- Disposable Android TV API36 x86_64 AVD Iptv_UiReview_TV_API_36, 2 cores, 2 GB RAM, 192 MiB Java heap. One emulator/build owner; no owner AVD, S25, private source, firewall/driver or host reboot action.
- Test-APK-only CatalogueCapacityProbe and [guarded driver](../../scripts/profile-catalogue.py). The driver restricts AVDs, verifies local/installed APK SHA256, records working-source hashes, retains raw JSON events/caps and rejects partial/failed phases. Working-source hashes alone do not prove a later edited tree produced an earlier APK; build/source-freeze records and binary hashes accompany the measurements.
- M3U fixtures are generated incrementally to disk:100k=10,517,788bytes,200k=21,257,788bytes,300k=31,997,788bytes. Mixed Arabic/English labels,100 categories, no artwork. Two explicitly synthetic classified roots permit movie/API-episode history assertions; ordinary M3U remains UNKNOWN.
- Rich Xtream uses the real loader over loopback with 7 streamed responses,60,034,428total bytes,100k LIVE+100k MOVIE+100k SERIES, unique artwork for all 300k, category/year/rating/provider metadata. Each individual response remains within64MiB; no external image request is made.
- Durations are monotonic action timings. Java/native heaps are sampled every 100 ms and at phase boundaries. Used Java heap is not whole-process RAM or peak PSS, and early samples can include uncollected fixture-generation garbage. These are individual emulator observations, not p50/p95 or device-independent speed promises.

## Observed failure and fixes

The unchanged storage implementation, with only an experimental 300k count guard, parsed the fixture in 27.4s but failed with out-of-memory after 59.4s of saving. Sampled Java heap reached 201,325,440 of 201,326,592bytes. It simultaneously retained input, encrypted catalogue rows, full identity collections and remapped output. The accepted 100k baseline used 96.5MiB during save.

Storage now resolves identities/encrypts/inserts in batches of 250 within one atomic transaction, including FTS and activation. A save-wide occurrence counter preserves duplicate semantics; old identities are fetched only for the current source/batch. Crypto format, fresh nonces, AAD, schema 4 and migrations are unchanged. One immutable AES key specification is reused within the save.

The production ViewModel now uses saveSession, which returns committed source/generation/time/count metadata without constructing a discarded full remapped catalogue. Compatibility defaults preserve save/open/fallback behavior; legacy save continues returning complete remapped entries. Input and the occurrence map still scale with catalogue size: this is not a fully incremental parser-to-database pipeline.

A trial 1024-character line scanner produced no clear repeatable CPU benefit and was reverted. Its useful UTF-8/line/EOF/cancellation boundary tests were retained. No parser-speed improvement is claimed.

## Measurements on the session path

| Workload | Load/parse | Save | Reordered save | Save/refresh Java heap peaks |
| --- | --- | --- | --- | --- |
|100k M3U |12.17s initial /11.88s refresh |42.38s |38.87s |49.8 /50.4MiB |
|200k M3U |22.66s initial /23.52s refresh |89.81s |88.10s |90.6 /90.4MiB |
|300k M3U |36.44s initial /33.27s refresh |139.80s |130.16s |130.4 /127.6MiB |
|300k rich Xtream |47.65s API load |152.88s |153.68s |173.7 /177.7MiB |

Large initial imports and manual refreshes still take minutes on this emulator. The priority was eliminating OOM while preserving committed state; storage batching adds bounded query/transaction work, so no blanket import-speed improvement is claimed. The rich reordered-save measurement reuses the already parsed input; a real manual provider refresh also pays its fetch/parse cost.

300k distinct-process database reopening measured header174ms+first page67ms; deep page115ms, categories444ms, broad Arabic/Latin search249–283ms. A separate recovery process measured header181ms+first page55ms. These backend timings are not full UI startup. Actual TvActivity cold-launch observations were 4.275s and 8.479s, the latter immediately after emulator boot.

## State and usability verification

- All100k/200k/300k M3U matrices passed import, reversed refresh, distinct-PID cached open, first/deep pages,100 categories, selective/broad Arabic+Latin search, favourites and movie/API-episode positions.
- Rich300k passed actual provider metadata/artwork parsing, save and reordered refresh, protected lookup, stable IDs and exact300k entry/search/identity row counts. Bounded pages supply IDs; no full returned catalogue is retained by the probe.
- Five direct-storage regressions cover duplicates across249/250 and499/500, late SQL rejection, deterministic cancellation, lightweight header/paged protected lookup, and repeated-refresh row counts. The new VM legacy-output regression was observed failing before the saveSession switch, then passed; interface paged/fallback compatibility is covered too.
- Parser cancellation stopped after bounded input and closed it, with the remainder unread (final300k observation163ms). This is a parser callback/input check, not proof of a ViewModel storage-cancel button. The existing UI commit remains deliberately NonCancellable.
- A real process interruption stopped PID 3501 eight seconds after refresh_save started. The driver correctly failed; fresh PID 3600 then verified the old committed generation, catalogue/search, favourite and movie/episode state. No success is claimed for the interrupted operation.
- The actual TV app opened its cached300k synthetic source. Twelve remote Down presses focused channel12; searching item299999 found the last item; the category picker found Group99 and retained that result. Synthetic XML/screenshots were visually inspected. No private provider was used.
- Two further identical-content reversed/forward refreshes retained all 300k entry/search/identity rows, favourites and progress. Database size alternated206,405,632 and274,083,840bytes, never exceeding the first-refresh maximum across these observed runs. This is not an indefinite-growth guarantee for changing provider catalogues. Complete final91-case device/probe/CI results are recorded in the tracker when finished. Initial M3U database sizes were about65.6/131.2/196.8MiB for100k/200k/300k; the first reversed refresh grew them to87.1/174.2/261.4MiB. Rich300k used about329.3MiB. A row count alone is not proof that file size cannot grow.

## Current host restriction

The owner prohibited AEHD/GVM/HAXM and removed all old local AVDs on6 November. Earlier measurements above are historical evidence; they are not permission to recreate those devices. WHPX capability was verified after the owner's restart, but no fresh AVD is authorized. Local final suite completion was interrupted at65/91; cloud CI must supply complete latest-head phone/TV results and all probes. Keep APK/JVM/lint work local and use Ubuntu/KVM device CI.

## Reproducing the probe on an authorized disposable device

The historical driver currently accepts only Iptv_UiReview_TV_API_36 and Iptv_UiReview_API_34, which have now been removed. It remains available as evidence/tooling but must not be used to restore them. If a future owner-authorized disposable environment is established, review its guard/AVD naming first, build app/test APKs with Java21 and install only on that disposable device. Never run this on a personal device. The driver does not create emulators or install packages; it verifies installed APK hashes against the local build before each run.

```powershell
python scripts/profile-catalogue.py --mode clear --size 300000 --output .tools/capacity/reproduce/clear.log
python scripts/profile-catalogue.py --mode import --size 300000 --output .tools/capacity/reproduce/import.log
python scripts/profile-catalogue.py --mode refresh --size 300000 --output .tools/capacity/reproduce/refresh.log
python scripts/profile-catalogue.py --mode open --size 300000 --output .tools/capacity/reproduce/open.log
```

Use the same size for an isolated import/refresh/open sequence. Run clear before switching sizes or starting a rich xtream sequence. parse and cancel use only the fixture stream. ui_seed explicitly replaces the disposable review app's saved sources with the synthetic UI fixture; other modes use a separate fixture database/key. Use fresh output names to preserve earlier evidence and retain failures rather than treating incomplete JSON as a pass.

## Evidence and limits

Raw ignored evidence: .tools/capacity/baseline, experimental-baseline, optimized, r2-rich, r3-rich, final, final-interruption and previews. APK/source manifests identify each run. R1 used the experimental scanner; later R1 runs share its installed APK hashes even after the working source was restored. R2/R3 rebuilt the original scanner. Final 0.10 M3U/UI measurements use the same production session algorithm as R3 rich, with only version metadata changed. Final limit-copy resource changes do not affect importer logic; latest-head CI verifies the complete resulting tree.

The first rich fixture timed out because its custom MockWebServer body did not flush a small auth tail. Explicit streaming-body flush and numeric transport diagnostics fixed the fixture; all7 request/body completions passed. No production timeout was relaxed.

The 300k count guard retains 64MiB per response,120s parser/call budgets and the existing 300s API import budget. Long labels/locators/artwork and device heap/codec differences affect practical capacity. There is no new loading-screen capacity advertisement or physical-device claim. Physical S25/selected-TV, endurance, richer ongoing playback and release-readiness checks remain separate work.

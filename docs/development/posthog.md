# PostHog monitoring

The owner selected PostHog on 29 October 2026. Wizard 2.88.0 generated the initial integration; the reviewed app pins Android SDK 3.73.1 and applies the privacy policy in [PostHogSetup.kt](../../app/src/main/kotlin/com/fireamun/iptv/prototype/PostHogSetup.kt). Monitoring is optional and never controls source access or playback.

## Local configuration

Copy [.env.example](../../.env.example) to ignored `.env`, then set the project's **ingestion token** (`phc_...`) and official ingestion host. Gradle properties take precedence over process environment, then `.env`.

```dotenv
POSTHOG_API_KEY=phc_your_project_token
POSTHOG_HOST=https://us.i.posthog.com
```

EU projects use `https://eu.i.posthog.com`. Personal/management API keys (`phx_...`) and other hosts are rejected before BuildConfig generation. The ingestion token is a client configuration value included in a configured APK; it is not a management credential. Keep actual local configuration out of Git and logs. Missing, blank or invalid settings disable initialization in both debug and release builds. An SDK setup failure does not block the app.

Normal CI has no live project credentials. For local instrumentation, override the key to blank and use a disposable test AVD:

```powershell
./gradlew.bat --no-daemon -PPOSTHOG_API_KEY= :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

The regular [device workflow](github-workflow.md) uses synthetic sources. Keep the owner's phone/AVD and saved sources outside instrumentation runs.

## Captured data

| Event | Meaning | Product fields |
| --- | --- | --- |
| `library_imported` | A source was imported and committed locally. | Source kind and entry-count bucket. |
| `library_refreshed` | A source refresh committed successfully. | Source kind and entry-count bucket. |
| `content_playback_started` | A playable item was selected and playback was requested. | Content kind and `playback_stage=requested`. This is not first-frame/success evidence. |
| `favourite_changed` | A local favourite change succeeded. | Content kind and new favourite boolean. |
| Application Installed/Updated/Opened/Backgrounded | SDK lifecycle events. | Restricted app/device/SDK context. |
| `$exception` | SDK uncaught-exception capture; it can start before remote configuration arrives, which subsequently acts as a project kill switch. | A fixed known error-class label (otherwise `UncaughtException`) and a withheld-details message. Messages, frames, breadcrumbs and arbitrary exception attributes are removed. |

Events carry `app_environment=debug/release` and an SDK-generated anonymous installation identity. There is no provider-based identification. Counts use `0_100`, `101_1000`, `1001_10000`, `10001_100000` or `over_100000`; these are analytics buckets, not a capacity promise. The catalogue limit remains 100k.

Structured INFO/WARN import logs use service `amar-iptv`, environment `debug/release`, a fixed message, operation enum, source kind and count bucket or safe error code. Existing app/Logcat output is not forwarded. Failed refreshes retain `operation=refresh`.

The SDK filters run after enrichment and before queue persistence. Unknown events/log messages and unapproved fields are dropped. URLs, provider credentials/hosts/source IDs, titles, search text and response bodies have no allowed telemetry field. Automatic screens, deep links, interaction capture, replay, Logcat recording, surveys, push subscription capture, person profiles and native tombstone capture are disabled. The event filter requests GeoIP suppression. SDK remote configuration may still fetch asynchronously; no app feature waits for it.

## Verification and dashboard

Six JVM privacy tests cover optional configuration, collection flags, event/log allowlists, count buckets and exception redaction. The Android SDK transport test intercepts every request, exercises all four product events, the installed uncaught handler and structured logging, and checks serialized payloads contain no synthetic secret. It restores the original handler and closes the test client. These tests need no live key.

Live delivery was separately checked by opening an empty configured app on the disposable TV emulator. The owner confirmed **Application Installed** and **Application Opened** in PostHog. This is live SDK ingestion evidence; cloud ingestion of every custom event/error/log and physical-device compatibility are separate checks.

The wizard created [Analytics basics](https://us.posthog.com/project/696227/dashboard/2276341) and a [setup notebook](https://us.posthog.com/project/696227/notebooks/1686698e4d21). The notebook describes the initial generated code; this guide supersedes its dynamic SDK version, missing-SDK build blocker, mandatory debug configuration and raw exception-capture description. Product charts populate after their corresponding app actions. Filter debug/emulator traffic when assessing release usage.

P7 remains partial: release collection controls, privacy/Data safety disclosures, retention choices, physical-device checks and startup/network budgets remain open. No Play release, billing integration or hosted app backend is introduced here.

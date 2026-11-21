# Play Console setup

Prepared 21 November 2026 for Amar Iptv 0.12.0 (`com.fireamun.amariptv`, versionCode 14). Google requirements below were checked on that date against the linked official pages; recheck them before each submission. These answers describe the app as it is in `main`; update them whenever data collection, SDKs or features change.

## Order of work

1. **Create the app** in Play Console (app, not game; free). Do not upload a bundle yet.
2. **Switch Play app signing to the owner key before the first upload** (see [App signing](#app-signing)). New apps are otherwise enrolled with Google-generated keys, and the GitHub APK and the Play build could then never update each other.
3. **Fill App content**: privacy policy, app access, ads, content rating, target audience, Data safety, and any other declarations Play Console lists (sections below).
4. **Store listing**: phone and TV screenshots, TV banner, descriptions in English and Arabic.
5. **Internal testing**: upload the `play-bundle-<tag>` AAB from the Release workflow run, install from Play on a phone and a TV, and check the Play-signed build updates over the GitHub APK.
6. **Closed test**: where the account is subject to Google's testing requirement, at least 12 testers opted in continuously for at least 14 days, with real use and feedback ([testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)). Then apply for production access from the Dashboard; review usually takes up to seven days.

## App signing

Play's current default ([Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)): new apps are enrolled in quantum-ready hybrid signing with Google-generated keys. Developers can change the app signing key on **Protected with Play › Play Store protection › Manage Play app signing › Change key** before a release reaches open testing or production. Do it before the first upload of any kind, so no tester ever installs a build signed with a different key.

- Choose **Provide a copy of your app signing key**. Play shows its encryption key and a PEPK download; follow those on-screen steps on your own computer with the keystore in `C:\Users\User\AmarSigning`. The passwords are typed only into PEPK, never into chat, GitHub or a file in this repository.
- Upload the encrypted file Play asks for. Afterwards the App signing key certificate SHA-256 on that page must equal the repository variable `AMAR_SIGNING_CERT_SHA256` (`48:29:09:43:…:8D:98:AE`). If it differs, stop and do not upload a bundle.
- Upload key: Google recommends a separate upload key. The Release workflow signs both the GitHub APK and the Play AAB with the owner key today. A separate upload key would mean signing the AAB with a second key (new secrets and a workflow change); decide this before the first upload. Using the owner key for both is simpler; a separate upload key limits the damage if the CI secret leaks, because Google can reset an upload key but not the app signing key.

## Privacy policy and app access

- Privacy policy URL: `https://fireamun.github.io/Iptv/privacy/` (Arabic: `/Iptv/ar/privacy/`). The app links it under Settings › Legal & About.
- App access: the app has no Amar account, but it shows no content until the user adds a playlist, file or server login. Reviewers need working instructions and a lawful test source. **Needs work:** publish a small sample playlist of openly licensed test streams (for example on the website) and give its address plus step-by-step instructions in this section. Never give reviewers private provider logins.
- Ads: the app contains no ads.

## Data safety answers

Basis: the [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469). "Collect" means data sent off the device by the app or its SDKs. Transfers to a service provider processing data on the developer's behalf are not "sharing". Data the user sends to their own chosen service under that service's terms, with the app never collecting it, is not declared (the guidance's Google Drive/Dropbox example). Collection can be marked optional only if every user can opt out.

What leaves the device:

- **PostHog** (our service provider, US servers, HTTPS only), after the user accepts the Terms and while Settings › Legal & About › usage statistics is on. Events and fields are listed in [PostHog monitoring](posthog.md) and in the Privacy Policy.
- **The user's own providers**: playlist, server, logo and stream requests go directly to addresses the user enters. We never receive or store them; credentials stay encrypted on the device. Not declared as collection.
- No other SDK sends data: Media3, OkHttp, Coil, Room and Compose make no developer-bound transfers. The only permission is `INTERNET`. Android backup is off.

Form answers:

| Question | Answer |
| --- | --- |
| Collects or shares any required user data types? | Yes |
| All user data collected encrypted in transit? | Yes (PostHog ingestion is HTTPS; non-HTTPS hosts are rejected at build time) |
| Account creation | The app does not let users create an account. Provider logins are the user's own third-party accounts and stay on the device. |
| Way for users to request deletion? | Yes: by email to the support address in the Privacy Policy. Local data is deleted by removing a source, clearing storage or uninstalling. |
| Shared with third parties? | No (PostHog is a service provider) |

Data types (all: collected, not shared, not processed ephemerally, **optional**, purpose **Analytics** only):

| Category › type | What it covers |
| --- | --- |
| App activity › App interactions | App opened/backgrounded/installed/updated; source added or refreshed (type and size bucket); playback requested (content type); favourite changed (content type). |
| App info and performance › Crash logs | Uncaught-error events reduced to a fixed error-class label, without messages or stack frames. |
| App info and performance › Diagnostics | Structured import/refresh logs with operation, source type, size bucket or safe error code; app version, Android version, device model/type, screen size, language, time zone, emulator flag. |
| Device or other IDs | Random installation and session identifiers generated by the PostHog SDK. |

Not collected: location (GeoIP lookup is suppressed), personal info, financial info, health, messages, photos/videos, audio, files and docs, calendar, contacts, web browsing, in-app search history, installed apps, other user-generated content. Search text, titles, addresses, usernames and passwords have no telemetry field.

**Owner check before submitting:** in PostHog project settings, turn on discarding of client IP addresses if it is not already on, so stored events match "no location". Re-read [PostHog monitoring](posthog.md) if events are added.

## Other declarations

| Section | Answer and evidence | Status |
| --- | --- | --- |
| Target API | `targetSdk = 36`; new apps and updates must target API 36 (TV apps API 34) ([target API](https://developer.android.com/google/play/requirements/target-sdk)) | Verified |
| 16 KB pages, 64-bit | Release workflow runs `check_native_alignment.py` on every AAB | Verified (each release) |
| Target audience | Adults; the Privacy Policy says the app is not directed at children under 13. Select 18+ only if the Terms say so; otherwise choose the age groups the app is meant for, excluding under 13 | Owner decision |
| Content rating | Media player that plays content the user adds; it ships no content. Answer the questionnaire truthfully, including that users can load content from the internet | Owner fills in |
| Ads, news, health, financial, government, gambling | None | Not applicable |
| Account deletion | Not applicable while the app has no Amar accounts; becomes required if accounts or payments with accounts are added ([account deletion](https://support.google.com/googleplay/android-developer/answer/13327111)) | Not applicable |
| Intellectual property | IPTV players are reviewed for links to unlicensed content. The listing, screenshots and test source must show only lawful, openly licensed or own content and state that the app provides no channels | Needs care |
| Android TV | Add the Android TV form factor; manifest already declares `leanback` (not required), touchscreen not required, `LEANBACK_LAUNCHER` and a banner. Upload TV screenshots and the banner; TV builds get their own review | Needs work (listing assets) |
| Payments | None in 0.12.0; Play Billing rules apply when payments are added ([payments policy](https://support.google.com/googleplay/android-developer/answer/9858738)) | Not applicable yet |

## Keep out of this repository

Account type, creation date, verification status, tester emails, Play Console screenshots that show account details, keystore passwords and the PEPK output file.

# Arabic and English IPTV Player — Product Brief

Date: 22 October 2026  
Status: Historical product-planning record; later implementation authorization and delivery are tracked in [progress](../../progress.md).  
Working name: Undecided

Architecture: [Draft architecture specification, 24 October 2026](../../docs/superpowers/specs/2026-10-24-iptv-architecture-design.md). The owner subsequently authorized implementation, local testing and normal GitHub delivery; use the progress tracker for current scope and evidence.

## 1. Purpose and scope of this document

Capture the IPTV product idea discussed with the user, including agreed direction, proposed features, research, commercial options, and questions to resolve. This is a planning record, not an approved technical specification or a claim that every proposed feature is feasible on every device.

The original discussion was planning only. Later explicit user instructions authorized building the app. This document alone does not authorize publishing or purchasing services.

## 2. Product concept

Build an IPTV media player with an excellent Arabic and English experience for Android phones, tablets, and televisions. Users connect their own compatible IPTV service or playlist. The product supplies the player and its features, not television channels, movies, series, or content subscriptions.

The intended offer combines attractive Arabic design, easy setup, a substantial feature set, and a competitive one-time price. Social media would be an important channel for demonstrating and marketing the app, initially to people in Saudi Arabia and Egypt.

## 3. Decision status

| Topic | Status | Discussion outcome |
| --- | --- | --- |
| Product | Confirmed direction | IPTV player using users' own compatible content sources. |
| Content provision | Confirmed direction | Do not supply or sell actual channels or content subscriptions. |
| Initial platform | Confirmed direction | Start with Android. |
| Device coverage | Confirmed direction | Android phones, tablets, and Android TV/Google TV, with layouts suited to each. |
| Languages | Confirmed direction | Arabic and English, including proper right-to-left support. |
| Markets | Intended initial markets | Saudi Arabia and Egypt; broader expansion remains open. |
| Distribution and promotion | Intended direction | Google Play and social-media marketing; other distribution methods undecided. |
| Positioning | Confirmed direction | Better Arabic design, easy setup, broad features, and a good one-time price. |
| Phone remote | User-requested feature | Pair the phone with our TV app and control our player from the phone. |
| First release content types | Confirmed | Include live TV, movies, and series together, where supplied by the user's provider. |
| Initial remote networking | Confirmed | Phone remote control on the same Wi-Fi is sufficient initially. |
| Monetization | Alternatives under consideration | As of 24 October 2026, the user is considering one-time purchase or yearly renewal. The supplied stack proposal uses a one-time unlock; the final model, free access/trial, price, and device limits remain open. |
| Feature breadth | Confirmed ambition | Learn from established players and aim for a comprehensive product; exact launch scope is not approved. |
| Technology | Recommended direction | Native Android with Kotlin, Compose/Compose for TV, and Media3 is the working recommendation following the user's 24 October proposal. See section 18 for details and unresolved technical choices. |
| Implementation | Subsequently authorized | App development and PR delivery are underway; see the current progress tracker. Publication remains separate. |

“All devices” currently means the agreed Android device categories. It does not promise an initial release for iOS, Apple TV, Samsung Tizen, LG webOS, Windows, or every smart TV operating system. Android-based TV boxes also need compatibility testing.

## 4. How the player works

1. The user adds a compatible provider connection or playlist.
2. The app loads available categories, channels, and any on-demand catalogue supplied by that source.
3. The user browses or searches, then selects something to watch.
4. The device connects to the provider's stream and plays it.
5. A programme-guide feed supplies schedules when available.

Connection methods discussed:

- M3U playlist URL or local playlist file: channel information and stream addresses.
- Xtream-compatible connection: provider server address, username, and password.
- EPG/programme-guide feed: schedule data supplied separately or through the provider.

M3U channel catalogues and HLS manifests can both use M3U-related syntax or extensions; they serve different purposes and should not be confused in implementation or user instructions.

HLS, DASH, and supported direct media streams are playback considerations, separate from provider login methods. Specific supported formats, headers, authentication, codecs, and DRM require investigation and testing.

The player is not a universal login for commercial streaming services. A compatible, authorized integration is required. It must not promise to play every subscription, bypass DRM, or overcome provider restrictions.

## 5. Device and language experience

| Device | Proposed experience |
| --- | --- |
| Phone | Touch controls, portrait browsing, landscape playback, TV pairing, keyboard input, and remote control. |
| Tablet | Larger grids, landscape layouts, and list/detail or split-pane browsing where useful. |
| TV | Large readable text, clear focus, directional-pad navigation, remote-friendly controls, and a setup flow assisted by a phone. |

Use one consistent product identity across device layouts. Arabic should be designed properly rather than added as a superficial translation: right-to-left navigation, mixed Arabic/English text handling, readable fonts, appropriate alignment, and clear terminology. Language switching and device-language defaults are details to decide.

## 6. Feature inventory

The following is a candidate product inventory, not an approved launch commitment.

### Connections and library

- M3U and Xtream-compatible connections.
- Multiple playlists/providers and easy switching.
- Live TV, movies, series, seasons, and episodes where supplied.
- Categories, favourites, recently watched items, watchlists, and continue watching.
- EPG with current/next programmes and schedule browsing.
- Search across available libraries.
- Local catalogue caching and refresh controls.
- Backup/restore or transfer of settings and favourites.

### Playback

- Reliable playback and channel switching.
- Audio track and subtitle selection, including adjustable subtitle appearance.
- Aspect ratio and suitable playback controls.
- Resume for on-demand titles.
- Picture-in-picture on supported devices.
- Casting, subject to receiver, stream, authentication, and DRM compatibility.
- Multiview, subject to device capacity and provider connection allowances.
- Catch-up where the provider supplies archives.
- Timeshift, with provider-based or local-buffer behavior explicitly distinguished.
- Recording/downloads only where technically supported and authorized; storage and rights implications need review.

### Family and personalization

- Parental PIN and content/category restrictions.
- Separate local profiles and favourites as a proposed feature.
- Simple mode with large text and fewer controls.
- Custom favourite groups and category visibility.
- Themes/customization: discussed as a possible capability, with no design chosen.

### Connected services

- Phone-assisted TV setup and phone remote.
- Optional device management and purchase restoration.
- Optional synchronized favourites and viewing progress.
- Optional cloud backup and management dashboard.
- Reminder notifications where supported.

Cloud features introduce ongoing operational costs and privacy responsibilities. They are not automatically included forever in a one-time purchase.

## 7. Phone remote and pairing

The user specifically proposed a remote inside the phone app that connects to our TV app.

Proposed controls:

- Directional pad, OK/select, Back, and return to our app's home screen.
- Play/pause, channel switching, and seeking when the stream supports it.
- Phone keyboard for searches and provider details.
- Browse channels, movies, and episodes on the phone, then start playback in our TV app.
- Quick favourites without interrupting the current TV picture while browsing.

Proposed pairing flow, still to verify:

1. TV displays a QR code or short pairing code.
2. Phone scans or enters the code.
3. TV explicitly approves the phone.
4. The apps remember trusted devices and allow revocation.

The user confirmed that same-Wi-Fi remote control is sufficient initially. QR/code pairing remains a proposed interaction to refine. Off-network access has not been requested or approved. Pairing must authenticate the phone; sharing a network alone must not grant control. Do not expose provider credentials in QR codes, logs, analytics, or support exports.

The remote controls our player. TV power, system volume, and other apps are separate device-dependent capabilities and are not promised. Remote pairing and casting are distinct functions.

## 8. Proposed differentiation

These are hypotheses to validate, not verified market gaps:

| Idea | User value |
| --- | --- |
| Phone-to-TV setup | Avoid entering long URLs and credentials with a TV remote. |
| Arabic-aware search | Handle common spelling variations, mixed scripts, and inconsistent provider labels while preserving originals. |
| Cleaner catalogue | Group duplicate quality variants, hide unwanted categories, and organize favourites without changing the source playlist. |
| Family-friendly experience | Give household members their own favourites and a simpler interface. |
| Helpful troubleshooting | Explain detectable connection, authentication, format, and delivery problems with actionable next steps. Avoid claiming to diagnose causes the app cannot establish. |
| Easy device replacement | Restore purchases and transfer settings without rebuilding the setup. |
| Data-use controls | Show estimated usage and offer lower-quality variants when actually available. |
| Clear ownership | Explain purchase scope, device limits, restore behavior, and support expectations. |

The strongest proposed signature is phone-to-TV setup, Arabic-aware search, family mode, and clear troubleshooting. Arabic support alone is not unique; competing players already offer it.

## 9. Competitor research

Features below came from published product descriptions. They have not been independently tested. Availability and terms may change.

| Player | Advertised features or model studied |
| --- | --- |
| TiviMate | Multiple playlists, favourites, search, multiview, catch-up, recording, parental controls, personalization; subscription and one-time options. |
| iMPlayer | Cloud management, synchronization, EPG, catch-up, timeshift, multiview, recording/downloads; annual and lifetime options. |
| Televizo | Multiple playlists, EPG, catch-up, casting, subtitles/audio selection, parental controls, backup/restore; one-time premium model. |
| OMAA | Arabic/English and RTL, M3U/Xtream connections, live/on-demand libraries, continue watching, EPG, casting, and phone/TV interfaces. |

Individual Play reviews raised issues around synchronized favourites, search navigation, setup, and confusion about buying a player versus content. Those reviews suggest interview topics; they do not establish prevalence or prove competitors lack a feature today.

Study user needs and interaction patterns, then create original code, assets, branding, and layouts. Do not copy proprietary code or unlicensed artwork. Do not advertise this as the first Arabic IPTV player.

## 10. Pricing and commercial options

As of 24 October 2026, the user is considering a one-time purchase or yearly renewal. The subsequently supplied stack proposal lists a one-time non-consumable unlock, but the commercial choice has not been explicitly finalized. Earlier discussion also considered free access and monthly subscriptions.

Suggested starting model, not a final decision:

- Free download with enough basic playback or a trial to verify compatibility before paying.
- A one-time Pro unlock for a substantial local feature set.
- Consider optional recurring cloud services only after calculating costs and validating user demand.
- Do not remove features already purchased in order to force a subscription.

A yearly payment can be an automatically renewing subscription or a non-renewing annual licence. The purchase screen must make the distinction clear.

Illustrative price experiments previously suggested:

- Saudi Arabia: SAR 39 versus SAR 59.
- Egypt: EGP 199 versus EGP 299.

These amounts are unvalidated hypotheses, not approved prices, competitor benchmarks, or market research conclusions. Validate local willingness to pay, store fees, applicable taxes, refunds, support costs, and customer-acquisition costs. Do not assume a sustainable business from download numbers alone.

Unresolved commercial details include device allowance, purchase portability across stores/platforms, family use, free limitations, trial duration, refund handling, and the exact meaning of a one-time/lifetime entitlement.

## 11. Marketing direction

Initial audiences: people in Saudi Arabia and Egypt who already have a compatible authorized service and want a better player.

Social-media demonstrations could show:

- Pair a phone and TV, then complete setup from the phone.
- Browse and search in Arabic.
- Use the phone as a remote.
- Switch between family favourites or simple mode.
- Understand and resolve a detectable setup issue.

Draft messaging, not approved branding:

> مشغّل IPTV بواجهة عربية وإنجليزية — أضف اشتراكك المتوافق وتحكّم من جوالك. لا يشمل قنوات أو اشتراكات محتوى.

Meaning: An Arabic and English IPTV player—add your compatible subscription and control it from your phone. Channels and content subscriptions are not included.

Use original or authorized demo media. Avoid claims of universal compatibility, guaranteed buffer-free playback, included premium channels, or unverified market leadership. Keep the player-only offer clear before purchase.

## 12. Publishing, rights, privacy, and security

Apply the installed `google-play-publishing` skill when planning or reviewing release requirements. Verify current official policies and local business requirements before launch; this section records research topics, not legal clearance.

- Google Play prohibits intellectual-property infringement and encouragement of infringement. Providing no bundled content does not alone establish compliance.
- Use authorized test streams and original/authorized store screenshots and marketing materials.
- Saudi copyright enforcement has included unauthorized IPTV subscription sales. That does not decide whether this particular player needs local business or regulatory approvals. Verify relevant Saudi and Egyptian requirements for the actual business model.
- Digital feature unlocks in Play-distributed apps generally require Play Billing, subject to applicable exceptions/programs. Social-media advertising does not remove in-app billing requirements.
- Verify account type, identity/device verification, and applicable closed-testing requirements before publishing.
- Verify current target API and technical requirements separately for mobile and TV; do not freeze annual thresholds into the implementation plan.
- Provide an accurate privacy policy and Data safety disclosures covering all SDKs and optional cloud services.
- Protect provider credentials and pairing tokens, minimize logging, and make support diagnostics redact secrets.
- If our app offers account creation, check applicable account/data deletion requirements.
- Confirm licensing, attribution, caching, and commercial-use terms before integrating metadata or artwork services.

## 13. Suggested delivery sequence

This sequence is a proposal to organize the ambition, not permission to implement or a final launch scope:

1. Validate audience needs, competitor experience, provider compatibility, and the commercial offer.
2. Agree the first release's features and mobile/tablet/TV designs.
3. Prove real playback, playlist loading, guide behavior, and authenticated phone-to-TV pairing on representative devices.
4. Build the approved core experience, Arabic/English support, and local features.
5. Add advanced and connected features in an agreed order after feasibility and cost checks.
6. Complete release testing, declarations, pricing validation, and authorized marketing preparation.

## 14. Questions to resolve before an implementation plan

1. Resolved: the first public release should include live TV, movies, and series together, where the provider supplies them.
2. Resolved: phone remote control on the same Wi-Fi is sufficient initially. The exact pairing interaction remains to be designed.
3. Should our own account be optional for local use, with sign-in reserved for cloud features? Provider credentials and our app account are different concepts.
4. What should free access include, and what should the one-time unlock cover? How many devices should one purchase allow?
5. Which real phones, tablets, Android TV devices, and authorized provider sources are available for compatibility testing? No credentials should be added to this document.
6. What working name, visual direction, and definition of one-time ownership should be used?
7. Are cloud synchronization, recording, downloads, multiview, and casting required at launch or candidates for later releases?

## 15. Research links

Research was consulted during the discussion on 22 October 2026. Recheck current pages before relying on policies, feature availability, prices, or terms.

### Android and Google Play

- [Media3 supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats)
- [HLS playback](https://developer.android.com/media/media3/exoplayer/hls)
- [DRM playback](https://developer.android.com/media/media3/exoplayer/drm)
- [Adaptive Android apps](https://developer.android.com/develop/adaptive-apps)
- [Google Play intellectual property](https://support.google.com/googleplay/android-developer/answer/9888072)
- [Google Play payments](https://support.google.com/googleplay/android-developer/answer/9858738)
- [Personal developer account testing](https://support.google.com/googleplay/android-developer/answer/14151465)
- [User data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111)

### Competitors and reviews

- [TiviMate](https://tivimate.com/)
- [TiviMate purchase terms](https://tivimate.com/terms-of-use)
- [TiviMate Play listing and reviews](https://play.google.com/store/apps/details?id=ar.tvplayer.tv)
- [iMPlayer](https://implayer.tv/)
- [Televizo premium](https://televizo.net/premium-version.html)
- [Televizo Play listing and reviews](https://play.google.com/store/apps/details?id=com.ottplay.ottplay)
- [OMAA Play listing](https://play.google.com/store/apps/details?id=live.omaa)

### Saudi copyright context

- [SAIP: action against websites infringing rights, including unauthorized IPTV subscription sales](https://www.saip.gov.sa/en/media-center/media-library/media-center/1929)

## 16. Next step

Proceed with product planning using the existing brief and available research. The user explicitly declined discussing the idea with other people on 24 October 2026; interviews, surveys, and external idea validation are not prerequisites. Resolve the first-release scope and key unknowns through the user's preferences, desk research, and later direct technical testing. Create a detailed design or implementation plan only when requested. Do not treat the existence of this file as authorization to start development.

On 24 October 2026, the user authorized writing the architecture specification described above. That draft now covers module boundaries, data/import behavior, navigation, playback, authenticated pairing, purchase ownership, managed monitoring, and the physical-device verification matrix. Review its explicit commercial and technical decisions before preparing subsystem implementation plans.

## 17. First-release proposal — 24 October 2026

The user's confirmed decision is to work from the information already available without discussing the idea with others. The scope and defaults below are recommendations for review, not newly approved requirements. Willingness to pay and demand remain unverified assumptions.

### Product focus

Make adding a user's compatible source, finding content in Arabic or English, and watching on Android TV straightforward. Use the phone for assisted TV setup and authenticated same-network control. Retain the previously confirmed live TV, movies, and series scope across phone, tablet, and Android TV/Google TV.

### Recommended launch scope

- M3U URL/file and Xtream-compatible source entry; multiple sources and explicit switching.
- Live channels, movies, series, seasons, and episodes where supplied by the source.
- Arabic/English layouts, correct RTL behavior, touch controls, and complete TV D-pad navigation.
- Categories, search, favourites, EPG now/next and schedule browsing when supplied.
- Continue watching and on-demand resume; available audio/subtitle selection and basic playback controls.
- Phone-assisted TV setup and same-network remote with explicit TV approval, remembered trusted phones, and revocation.
- Parental PIN with defined category/content restrictions.
- Clear loading, empty, authentication, unavailable-stream, and connection error states; safe retry and source editing.
- Secure local credential storage and redacted diagnostics.
- Compatibility access before payment and a one-time Pro unlock; exact free limits, price, and device allowance remain open.

### Recommended later scope

Cloud synchronization/accounts, a web dashboard, recording, downloads, local-buffer timeshift, multiview, casting, advanced themes, recommendations, and separate household profiles. These are deferred proposals, not rejected features. Reconsider individual items when their value or feasibility justifies bringing them forward.

### Working defaults and unresolved choices

- Recommend no mandatory app account for local playback. Provider credentials remain separate from any future app account.
- Recommend keeping the initial paid offer focused on local features, without promising indefinite cloud services.
- Pairing should not require putting provider credentials in a QR code. Define whether the phone's source is securely transferred to the TV and which device retains it.
- A purchase unlock does not increase a provider's permitted simultaneous streams. Browsing on the phone should not start an extra stream unnecessarily.
- Favourites and viewing progress should survive source refreshes where items can be matched; define deletion and source-removal behavior explicitly.
- Device limits, purchase restoration, source transfer/backup behavior, free access, and the exact meaning of a one-time entitlement need decisions before billing design.
- Native Android is now the working technology recommendation; minimum supported Android versions, hardware compatibility, and implementation details remain to be settled. See section 18.

### Next planning outputs

1. Settle the first-release scope and commercial boundaries with the user.
2. Define phone/tablet and TV screen maps, including source setup, library, playback, pairing, remote, settings, and purchase restoration.
3. Specify source handling, local data, credential protection, pairing, and entitlement behavior.
4. Define a technical feasibility check using representative physical hardware and authorized test sources: playback, large catalogues, EPG time zones, RTL/D-pad navigation, pairing/reconnection, and provider connection limits.
5. After explicit development authorization, choose the stack and create a staged implementation plan. Technical tests and applicable release testing remain necessary even though external idea discussions are excluded.

## 18. Stack and managed monitoring direction — 24 October 2026

The user supplied a detailed native Android and managed-monitoring proposal. Adopt it as the working recommendation, with the qualifications below. This remains product/architecture planning and does not authorize scaffolding or implementation.

### Recommended application stack

| Layer | Working recommendation |
| --- | --- |
| Language and UI | Kotlin; Compose/Material 3 adaptive phone/tablet layouts; Compose for TV for remote navigation. |
| Playback | AndroidX Media3 ExoPlayer. Confirm containers, codecs, authentication, and stream behavior on physical devices. |
| Structure and state | Shared data/playback/pairing code with separate mobile and TV UI modules; ViewModel, Coroutines/Flow, Hilt. Prefer one Play application/package identity initially. Separate UI modules do not require separately licensed Play apps. |
| Local data | Room, Paging 3 for large collections, DataStore for settings. Keep credentials out of ordinary plaintext settings. |
| Search | Room FTS with a separate normalized search representation, preserving original labels. Test Arabic diacritic and letter handling. Treat potentially lossy equivalences such as ta marbuta/heh and ya/alef maqsura as search-expansion choices to evaluate, not mandatory canonical replacements. |
| Parsing | Incremental M3U parsing and XMLTV parsing with XmlPullParser; bounded import batches, cancellation, and resource limits. |
| Networking and images | OkHttp, Retrofit for Xtream-compatible endpoints, kotlinx.serialization, Coil. |
| Credential protection | Keystore-backed encryption; Tink is a candidate. Define and verify encrypted persistence and backup behavior rather than assuming DataStore encrypts data or integrates with Tink automatically. |
| Phone remote | NSD/mDNS discovery with a QR/manual connection fallback, explicit TV approval, encrypted authenticated transport, and revocable per-phone trust. An embedded Ktor CIO WebSocket server is a candidate pending Android TV compatibility, lifecycle, TLS, and reconnect testing. |
| Background work | WorkManager for deferrable catalogue/EPG refresh. Do not rely on it for an always-on remote server or exact refresh timing. |
| Payments | Play Billing behind a shared access/entitlement component. Keep the implementation capable of a one-time unlock or annual access until the offer is finalized. |

### Owner monitoring and hosted services

- Use Play Console and managed Firebase tooling rather than building an owner dashboard or custom telemetry API for launch.
- Start with Crashlytics and a minimal Analytics event set; use Performance Monitoring/custom traces where useful and verify collection on supported TV devices. Define playback startup, buffering, pairing success, and catalogue-import measurements explicitly; SDK installation alone does not provide every IPTV-specific metric.
- Use Remote Config for selected feature flags with safe in-app defaults. Changes require connectivity, fetching, and activation; a kill switch is not guaranteed to act immediately on every installation. It must not be the authority for paid entitlement.
- Exclude provider credentials, raw playlist/stream URLs, pairing tokens, search terms, and viewing titles from telemetry. Review exception messages and automatically collected network data as well as custom events.
- Add BigQuery/Looker Studio only when built-in dashboards are insufficient. BigQuery sandbox/no-cost quotas exist, but storage, queries, and streaming can incur charges outside those limits. Do not promise an unlimited free dashboard.
- Working launch preference: no custom hosted application backend, cloud sync, or required app account. Managed services still process data and require accurate privacy/Data safety declarations.

### Purchase verification and device ownership

Managed monitoring does not resolve purchase verification. Google recommends verifying and acknowledging purchases through a secure backend. A client-only launch can query Play purchases and process completed purchases locally, but is more exposed to client tampering and does not provide a secure custom cross-account/device licensing service. Select client-only verification with its explicit tradeoffs or a managed verification service before monetization is implemented; do not embed Google Play Developer API service-account credentials in the app.

One package identity simplifies Play distribution and purchase restoration. It does not automatically share purchases between different Google accounts. A custom household entitlement, arbitrary device cap, or cross-account activation would require a separate design. Annual access also needs an explicit expiry, renewal, refund, offline-access, and restoration policy.

### Hardware and decoder decisions

- Select minSdk from both the chosen dependency requirements and the devices we intend to support; do not assume every older or uncertified TV box will be compatible or have working Play services.
- Test physical devices early for weak CPU/memory, large catalogues, EPG time zones, RTL focus restoration, audio tracks, provider connection limits, and remote lifecycle/reconnection.
- Investigate the Media3 FFmpeg audio decoder only after demonstrating device codec gaps. It requires native builds and separate dependency licensing review; it is an audio decoder, not a universal video fallback. Keep libVLC as a separately evaluated alternative rather than shipping a second engine by default.
- Native decoder additions require ABI, release packaging, and current page-size compatibility checks. Do not derive minSdk from the build scripts of a single extension.

### Technical references checked on 24 October 2026

- [TV package identity and distribution](https://developer.android.com/training/tv/publishing/distribute)
- [Play purchase verification guidance](https://developer.android.com/google/play/billing/security)
- [Play purchase querying and processing](https://developer.android.com/google/play/billing/integrate)
- [Firebase export and BigQuery costs](https://support.google.com/firebase/answer/6318765?hl=en)
- [Remote Config loading and defaults](https://firebase.google.com/docs/remote-config/loading)
- [Ktor server engines](https://ktor.io/docs/server-engines.html)
- [Media3 FFmpeg audio decoder build and licensing notes](https://github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md)

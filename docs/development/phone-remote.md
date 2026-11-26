# Phone remote

Version 1 requirement D29, parts 1 and 2 of 4. Owner choices (26 November 2026): build the remote first, in four PRs; scan the TV's QR code with a bundled scanner so nothing is sent to Google; without a camera, pair by comparing a six-digit code shown on both screens (like Bluetooth pairing) rather than typing the TV's code into the phone.

| Part | Scope | Status |
| --- | --- | --- |
| 1 | QR pairing with TV approval, trusted-phone list and removal, D-pad/OK/Back/Play-pause/channel remote | Done (PR #43) |
| 2 | Code pairing without a camera, finding TVs on the Wi-Fi, typing from the phone into the selected TV text box | This PR |
| 3 | Sending a playlist or login from the phone to the TV (D26) | Planned |
| 4 | Favourites and Continue watching sync between paired devices | Planned |

## Behaviour

- **TV: Settings → Phone remote.** **Allow phone remote** (on by default), **Pair a phone**, the list of paired phones with **Remove** each, and **Remove all phones**. Without Wi-Fi or Ethernet the card says so and offers no pairing.
- **Pairing.** The TV shows a QR code that expires after two minutes (**New code** after that). The phone scans it from **Settings → TV remote → Scan TV code**. The TV then asks **Allow "phone name" to control this TV?** with **Don't allow** focused first; no answer within a minute counts as no. A code works once and stops after five wrong tries.
- **Pairing without a camera.** On the phone, **Pair with a code** lists TVs found on the same Wi-Fi whose pairing screen is open (or whose remote has paired phones). If the router hides them, the user types the address the TV's pairing screen shows under the QR code ("192.168.1.20", with ":port" only when the usual port was taken). Both screens then show the same six-digit code; the TV asks **Allow "phone name"?** above the code and **Allow only if the phone shows this code**. Each attempt uses up the code on screen, so a declined or failed attempt needs **New code**.
- **Phone: Settings → TV remote.** Once paired it shows **Open remote** and **Forget TV**. The remote screen has Up/Down/Left/Right around OK, then Back, Play/Pause and channel up/down, and says whether it is connected, with **Try again**.
- **Keys** act on whatever has focus on the TV, including dialogs such as the parental PIN, so the phone can do nothing that the TV's own remote cannot. Play/Pause and channel keys work during playback and report "Nothing is playing" otherwise; channel keys move through the loaded live list, as do the TV remote's own channel keys.
- **Typing.** **Keyboard** on the remote screen opens a typing box at the top, above the phone keyboard. While a text box on the TV is selected (search, playlist and server login fields, the guide link, playlist names, category search), what is typed on the phone appears there as it is typed, and deleting deletes; Arabic works too. **Done** starts a new line on the phone and leaves the TV text alone. If no TV text box is selected, the phone says "Select a text box on the TV first" and its box empties. Text only goes to the TV: the TV never sends a box's contents to the phone, which matters for passwords.
- **Address changes.** If a paired TV cannot be reached at its saved address, the phone looks for the same TV (same certificate) on the network for up to four seconds and, if found, connects there and saves the new address.
- **Removing a phone** on the TV ends its connection at once and tells the phone, which forgets the TV and asks for pairing again. **Forget TV** on the phone leaves the TV's list for the TV owner to clean up.
- **When it works:** same network, Amar Iptv open in front on the TV, phone remote allowed. The TV only listens while one of its screens is open, the switch is on, and either a phone is paired or a code is on screen.

## Protocol and security

- **Transport.** TLS 1.2/1.3 over TCP, port 47820 or any free port if that is taken. Messages are JSON in frames with a 4-byte length, at most 8 KiB, strict UTF-8.
- **TV identity.** Each TV makes a self-signed ECDSA P-256 certificate once (okhttp-tls) and keeps it sealed by the Android Keystore. The QR code carries the TV's IPv4 address, port, the certificate's SHA-256 fingerprint and a 128-bit single-use nonce: `amariptv://pair?v=1&h=…&p=…&f=…&n=…`. The phone accepts only that certificate, so another device on the network cannot pretend to be the TV.
- **Pairing.** The phone sends the nonce and its name; the TV checks it (constant time), consumes it, and waits for the person at the TV. On Allow, the TV gives the phone a random 32-byte secret and stores only its SHA-256 with a random phone id. The phone keeps the TV's address, fingerprint, its id and secret sealed by the Keystore.
- **Code pairing.** The phone connects without knowing the certificate and remembers the one the TV presents. It sends a SHA-256 commitment to a random 32-byte value; the TV replies with its own random value; the phone reveals its value and the TV checks it against the commitment. Both sides then compute six digits from SHA-256 over a fixed label, the certificate fingerprint each side used and both values. A device in the middle must present its own certificate to the phone, so the screens would show different codes, and the commit-then-reveal order leaves it one chance in a million to make them agree. The phone shows its code before the TV can be approved, the person at the TV is the check, and the phone pins the certificate it saw. Each attempt needs the TV's pairing screen to be open, uses it up and needs an approval, so a nuisance device on the network can at most make the TV owner press **Don't allow**.
- **Discovery.** While the TV listens, it announces `_amariptv._tcp` (DNS-SD/NSD) with its name, port, protocol version and certificate fingerprint. These are public values that grant nothing; a found TV is still checked by the code, or by the pinned certificate when reconnecting. Discovery adds no permission.
- **Typing messages** carry how many characters to erase from the end of the selected box and the text to add (at most 4,096 and 500 characters; longer pastes go in parts, erasing before adding). The TV drops control characters and text-direction overrides, and each box keeps its own length limits. Typing shares the key rate limit and the recheck that the phone is still trusted.
- **Later connections** authenticate with id + secret over the pinned TLS. Unknown or removed phones are refused (the TV cannot tell a forged secret from a removed one, and says so to neither). Each key is checked against the trusted list again right before it acts.
- **Limits.** Six connections, ten phones, 20 keys per second per connection, 10 s handshake, 45 s idle (the phone pings every 15 s), phone names cleaned of control characters and cut to 40 characters.
- **What stays private.** The QR code holds no lasting secret. Nothing passes through a server, nothing is logged or sent to analytics, and the app data that holds pairings is excluded from cloud backup and device transfer. If the TV's sealed identity cannot be opened (for example after a Keystore reset) it makes a new one and clears its phone list; phones then report "This isn't the TV from the code" and forget it.

## Scanner choice

The QR scanner is bundled ZXing (`zxing-android-embedded` 4.3.0), not the Google code scanner. The Google scanner brings ML Kit, which sends usage data to Google and would change the Data safety answers. ZXing adds the `CAMERA` permission, asked only when **Scan TV code** is tapped; the camera features are optional, so TVs without a camera can still install the app. The TV draws its QR code with ZXing's encoder.

## How keys reach the TV screen

- **Android 10 and newer:** the window with focus is found with `WindowInspector.getGlobalWindowViews()` and receives a D-pad key event, after leaving touch mode as a real remote press does. Back goes to that window's back dispatcher, because predictive back on Android 16 no longer turns a Back key into a back press.
- **Android 9:** there is no public window list, so `Instrumentation.sendKeyDownUpSync` sends the key; Android only lets an app inject into its own focused window. Only the CI phone-api28 profile exercises this path.
- Nothing is injected into other apps. If Amar Iptv is not in front, the phone hears "Amar Iptv isn't in front on the TV".

## Code

`core/remote` (plain Kotlin, JVM-tested): `RemoteProtocol` (frames, keys, refusals, `PairingLink`, `pairingCode`), `RemoteTls` (identity, pinned and recording clients), `RemoteServer` (TV side), `RemoteClient`/`RemoteSession` (phone side). `core/designsystem` `RemoteTyping` and `Modifier.remoteTyping` (the focused text box). `app`: `RemoteHost` (TV lifecycle, sealed identity, trusted-phone store, announcing), `RemoteDiscovery` (`RemoteAdvertiser`, phone search), `RemoteController` (phone state, sealed saved TV, typing), `RemoteUi.kt` (TV settings card, pairing dialog with QR, address and code, phone card, code pairing dialog, remote screen with typing), `LibraryActivity` (key delivery, play/pause, channel switching, typing).

## Tests

JVM: `RemotePairingTest` (9): approved phone's keys arrive; code single-use and declined phone untrusted; expiry and wrong-try limit; wrong certificate refused; removal closes the session, tells the phone and blocks reconnects; stopping keeps trust; strict pairing links; frame limits; identity round trip. Device (gate 126 → 128): `RemoteFlowTest` — on TV, pairing approved through the TV dialog, then phone keys close the dialog, switch channels during playback, play/pause and go back, then removal; on phone, a non-pairing QR value is refused, pairing waits for approval, the remote screen sends keys and shows "Nothing is playing", and Forget TV returns to Scan. Both run the phone side in the same process over loopback. Part 2 (gate 128 → 130): JVM `RemotePairingTest` grows to 14 (code pairing shows the same code on both sides and pins the TV; needs the open pairing screen and an approval; a reveal that does not match the commitment is refused; the code covers the certificate and both values; typing arrives cleaned and bounded). Device: on TV, the address is shown, the phone pairs by address with matching codes on both screens, typing without a selected box is refused, then typing, deleting and adding Arabic reach the TV search box; on phone, a non-address is refused, code pairing by address shows the TV's code and closes once approved, and the remote screen's typing box sends text and deletions, shows a refusal and stays above the keyboard. The fixture reset also clears typing. Network discovery itself is not covered by automated tests (emulators may hide multicast).

Still to check by hand: a real phone and TV on home Wi-Fi, including the camera scan, finding the TV in the list, network changes and typing with the phone keyboard.

## Next parts

- **Part 3:** source sending reuses the paired channel; the TV asks before saving and keeps the existing encrypted storage.
- **Part 4:** favourites and Continue watching sync between paired devices while both apps are open.

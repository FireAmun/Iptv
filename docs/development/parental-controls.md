# Parental controls

Version 1 requirement D17. Owner choices (25 November 2026): the parent picks which categories to lock; one correct PIN opens them until the app has been in the background for five minutes or is closed; "Forgot PIN" resets by removing every playlist.

## Behaviour

- **Settings → Parental controls** on phone and TV. Without a PIN it offers **Set PIN** (4–8 digits, entered twice). With a PIN it shows **Unlock**, and once unlocked **Change PIN**, **Lock now**, **Turn off PIN** and the **Locked categories** buttons for Live, Movies and Series.
- **Locked categories** are chosen per playlist and per tab from that playlist's own category labels. Unknown items count as Live; episodes count as Series. The same label in another tab stays open.
- **While locked**, items in locked categories disappear from browsing, favourites, search and Continue watching. Locked categories stay in the category chips and picker marked "Locked"; choosing one asks for the PIN and then opens it.
- **Every way an item opens** (list, search, favourites, history, and later the phone remote) goes through the same ViewModel check, which asks for the PIN if the item's category is locked.
- **Unlock window:** one correct PIN opens everything until the app spends five minutes in the background, the device clock jumps backwards, the process ends, or the parent taps **Lock now**.
- **Wrong PINs:** five free tries, then a wait of 30 seconds that doubles per further failure up to 15 minutes. The wait survives restarts.
- **Forgot PIN** (from any PIN prompt) explains the consequence, then removes every playlist, login, favourite and watch record together with the PIN and all locks. If removal fails, the PIN stays.
- **Turn off PIN** also removes every lock. Removing a playlist removes its locks.
- The settings text states that a device PIN is a household convenience: anyone who can clear the app's data can remove it.

## Storage and privacy

- Only a salted PBKDF2-HMAC-SHA256 verifier (60,000 iterations, 16-byte salt) is stored, never the PIN. Locks are the provider's category labels. Both live in the private `parental` preferences, which backup and device transfer already exclude.
- Nothing is sent off the device; no analytics event is added.

## Code

`core/model` `CategoryLocks` (tab-kind mapping); `core/data` `ParentalPin`, `PinVerifier`, `PinThrottle`; `core/storage` `RoomLibraryStore.conditions` adds the lock clause to every browse, favourites and search query, while `categories()` keeps locked labels listed; recent watches carry their root category. `app` `ParentalControls` (state, unlock window, preferences store), `ParentalUi.kt` (PIN keypad dialog and settings card), `LibraryViewModel` (PIN prompt, open guard, forgot-PIN reset), `CategoryPicker` (lock chooser and "Locked" marker).

## Tests

JVM: `ParentalPinTest`, `CategoryLocksTest`, `ParentalControlsTest`. Device (gate 117 → 120): `ParentalStorageTest` (store filtering across tabs, favourites and search; locked labels still listed) and `ParentalControlsFlowTest` on phone and TV (set PIN, lock a category, relock, item hidden, wrong PIN refused, right PIN opens the category). The test fixture reset also clears the PIN and locks.

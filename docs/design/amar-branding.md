# Amar Iptv branding and welcome

The owner selected **Amar Iptv** as the visible app name on 25 October 2026, with a gold-to-black welcome and a moving logo. This extends the approved Cinema palette and keeps the existing light/dark library and player themes.

The first native Android launch frame is black with a gold play mark. Android 12+ animates its vector with a short bounce and displays the Manrope wordmark where the platform supports the branding image. Earlier versions use the static mark. Android's native splash background accepts a single opaque color, so the full gradient belongs to the Compose welcome rather than that first system frame. See the [Android splash documentation](https://developer.android.com/develop/ui/views/launch/splash-screen).

The Compose welcome uses saffron/gold at the top fading into black, a bouncing/tilting play tile with a shadow, drifting gold specks and moving dots. It says **Welcome to** / **Amar Iptv**; Arabic localizes the welcome while preserving the brand spelling. The normal intro is one 900 ms cycle. It continues only if the local saved-library restore is still working, without a fake progress percentage or a network wait. Disabled system animations skip the intro delay. Ordinary activity recreation consumes the launch rather than replaying it, including rotation during the intro.

Shared `AmarWordmark` branding appears in phone/TV library and player headers. The application label and TV launcher banner use the same name. Native wordmark/banner paths were derived from the bundled Manrope font at weight 800; its existing OFL notice applies. From version 0.11.0 the permanent Play application ID is `com.fireamun.amariptv`; earlier `com.fireamun.iptv.prototype` installs remain separate apps with their own data.

Verification covers a clock-controlled moving-logo/completion check, disabled-animation behavior, existing phone/TV language and recreation journeys, build/lint and independent review. Device screenshots contain synthetic fixtures only. These checks do not establish physical TV-box compatibility or test the platform's native animated splash on every Android version.

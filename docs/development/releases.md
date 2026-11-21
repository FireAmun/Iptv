# Website and releases

The project website is <https://fireamun.github.io/Iptv/>. Its home page is the APK download page; it also publishes the Privacy Policy, Terms of Use (English and Arabic) and open-source notices. Google Play Console uses <https://fireamun.github.io/Iptv/privacy/> as the privacy policy address, and the app shows the same address in Settings > Legal & About.

## Website

- Source: `site/` (stylesheet, download script, icon, screenshots) and [`scripts/build_site.py`](../../scripts/build_site.py). The legal pages are generated from the Markdown the app bundles under `core/designsystem/src/main/assets/legal`, so edit those files, not HTML. Fonts come from `core/designsystem/src/main/res/font`.
- Build locally: `python scripts/build_site.py --out build/site`. To preview with the `/Iptv/` path, build into `build/preview/Iptv` and serve `build/preview` with `python -m http.server`.
- Tests: `tests/test_build_site.py` (run by CI's repository checks) builds every page and checks local links, language/direction, the Content Security Policy, that every legal heading appears, and that the app's privacy address matches a built page.
- Publishing: [`.github/workflows/pages.yml`](../../.github/workflows/pages.yml) rebuilds and deploys the site when a change to the site, its builder or the legal text reaches `main` (or when run manually). Repository Settings > Pages must use **GitHub Actions** as the source.
- The pages load nothing from other sites. The download script asks `api.github.com` for the latest release's version, size, date and SHA-256; without it the button still links to `releases/latest/download/amar-iptv.apk`. The website section of the Privacy Policy describes this.
- Set `PLAY_URL` in `scripts/build_site.py` once the Play listing is public to show the Google Play link.

Play policy: the Play build must never link to the download page, offer APK downloads or update itself. The app links only to the privacy page.

## Signing key (one-time, owner only)

One key signs every GitHub APK. Use the same key as the **app signing key** in Google Play App Signing (before the first Play upload, choose to export and upload your own key with Play's PEPK tool instead of a Google-generated key), so the Play and GitHub versions can update each other. If the key is lost, the GitHub APK can never be updated again: keep two offline backups of the keystore file and its passwords, outside this repository.

1. Create the keystore (run in a private folder, not in the repository; `keytool` asks for the passwords):

   ```bash
   keytool -genkeypair -v -keystore amar-release.jks -alias amar -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Amar Elshaer"
   ```

2. Record the certificate fingerprint:

   ```bash
   keytool -list -v -keystore amar-release.jks -alias amar
   ```

3. In GitHub, Settings > Secrets and variables > Actions, add these **repository secrets**:
   - `AMAR_UPLOAD_KEYSTORE_BASE64`: the keystore as one line of Base64. PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("amar-release.jks")) | Set-Clipboard`
   - `AMAR_UPLOAD_STORE_PASSWORD`, `AMAR_UPLOAD_KEY_ALIAS` (`amar`), `AMAR_UPLOAD_KEY_PASSWORD`
   - Optional `POSTHOG_API_KEY`: without it, GitHub APKs send no usage statistics.
4. Add the **repository variable** `AMAR_SIGNING_CERT_SHA256` with the SHA-256 fingerprint from step 2. The release fails if an APK is ever signed with a different certificate. Optional variable `POSTHOG_HOST` (`https://eu.i.posthog.com` for an EU project; it defaults to `https://us.i.posthog.com`, matching the Privacy Policy).
5. For local release builds, `keystore.properties` (ignored by Git) or `AMAR_UPLOAD_*` environment variables supply the same values; see `app/build.gradle.kts`.

## Making a release

1. In a normal PR, raise `versionCode` and `versionName` in `app/build.gradle.kts`, and merge it after CI passes.
2. Tag that merged `main` commit and push the tag:

   ```bash
   git tag v0.12.0 && git push origin v0.12.0
   ```

3. [`.github/workflows/release.yml`](../../.github/workflows/release.yml) then:
   - refuses tags that differ from `versionName` or are not on `main`;
   - stops if a signing secret is missing (it never publishes an unsigned APK);
   - builds the signed APK and AAB, checks 64-bit/16 KB native libraries and verifies the signing certificate;
   - publishes the GitHub Release with `amar-iptv.apk` and `amar-iptv.apk.sha256`, and keeps the AAB as the `play-bundle-<tag>` workflow artifact for 30 days for the Play Console upload.

The download page picks up the new release automatically; no website change is needed.

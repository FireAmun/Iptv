# Cinema app previews

For the newer six-destination browser, see the [v0.8.0 library previews](../library/README.md).

Actual prototype screenshots, 25 October 2026. These show the implemented M3U source-setup flow, not future film/guide mockups. Working wordmark only.

| Android TV API 36 | Preview |
| --- | --- |
| English dark | [Screenshot](tv-dark.png) |
| English light | [Screenshot](tv-light.png) |
| Arabic dark | [Screenshot](tv-arabic-dark.png) |
| Arabic light | [Screenshot](tv-arabic-light.png) |
| URL dialog | [Screenshot](tv-url-dialog.png) |

| Phone API 34 | Preview |
| --- | --- |
| English dark | [Screenshot](phone-dark.png) |
| English light | [Screenshot](phone-light.png) |
| Arabic dark | [Screenshot](phone-arabic-dark.png) |
| Arabic light | [Screenshot](phone-arabic-light.png) |
| Imported library, dark | [Screenshot](phone-library-dark.png) |
| Imported library, light | [Screenshot](phone-library-light.png) |
| Native pause/seek at 18 seconds | [Screenshot](phone-playback.png) |
| Safe missing-stream error and Retry | [Screenshot](phone-playback-error.png) |

TV checked with D-pad language/appearance navigation, real input-method opening, Back/cancel, visible focus, and cold-process light-mode restoration. Phone checked in both languages/modes, including a cold process restart with light mode retained, real Downloads picker import, library retention on appearance change, HTTP consent cancel/accept, actual synthetic video frames, native pause/seek, missing-stream error/Retry and Back to library.

Complete local suites ran ten tests on each emulator, covering synthetic import/playback, lifecycle, appearance/library retention, language and recreation. Fixtures contain only self-generated media and synthetic addresses. The gallery is prototype verification, not a physical-device/private-provider compatibility claim.

Palette and editable concepts: [design handoff](../README.md).

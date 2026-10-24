# Cinema appearance design

Date: 25 October 2026. Owner approved Cinema A and requested light mode.

Reference: [editable Figma concepts](https://www.figma.com/design/2Hg2v6XoCHTlmyDTZSI9te), [previews and palette](../../design/README.md), [architecture](2026-10-24-iptv-architecture-design.md).

## Scope

Apply the selected visual identity to existing M3U setup, library, consent, error and playback screens on phone and TV. Default to dark and offer a saved explicit dark/light toggle. Both modes use the same Cinema hierarchy; light mode uses warm cream surfaces and dark text. Preserve Arabic/English, RTL, source privacy, cancellation, lifecycle release and remote focus.

The working wordmark is `iptv.`. No final app name is chosen. Films, series, guide, search, favourites and continue-watching from concept previews require later data capabilities; do not add inert navigation or fictional production metadata. Use existing titles/groups with honest fallback labels.

## Visual system

- Night `#0B0B0B`, Charcoal `#2F2F2F`, Jet `#333533`, Eggshell `#F2ECDD`, Saffron `#F5CB5C`, Soft sand `#E3D5BB`, Gold `#A07F3A`.
- Shared semantic Compose tokens in `core:designsystem`; separate mobile/TV Material adapters. No platform dynamic colours replacing the approved palette.
- Manrope English and Cairo Arabic, bundled with OFL notices. Strong display weight, readable body text, restrained gold accents.
- Mobile: wordmark and language/appearance actions, large empty-state heading and source actions, compact header when loaded, rounded channel cards with text-derived fallback monograms and play affordance.
- TV: generous safe margins and two-pane first launch; dense readable channel rows when loaded; visible saffron focus border and scale. Preserve selected-entry focus after playback and language-button focus on empty launch.
- Native video canvas stays black in both modes; controls remain legible. Light mode must also change system-bar icon contrast and launch background.
- Primary text/action pairs meet 4.5:1 contrast; meaningful focus/borders meet 3:1. Gold accents do not become faint small text on cream.

## Behavior and verification

Only appearance preference persists in this slice; the current library remains session-only. Theme changes preserve the ViewModel library. One emulator at a time; no firewall, driver or reboot changes. Verify dark/light, Arabic/English, input/consent, real synthetic playback and D-pad focus on phone/TV, alongside build/lint/JVM and latest-head cloud device reports. Independently review changes before PR integration.

Figma's matching light frames exist but screenshot QA was blocked by its connected Starter tool cap. Validate implementation screenshots locally. Nine original concept previews were inspected before approval.

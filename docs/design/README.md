# IPTV design directions — 25 October 2026

Status: owner approved Cinema A with matching light mode on 25 October 2026. Production implementation is next; final branding remains open.

Figma: https://www.figma.com/design/2Hg2v6XoCHTlmyDTZSI9te

| Direction | Review frame | Intent |
| --- | --- | --- |
| A — Cinema (recommended) | https://www.figma.com/design/2Hg2v6XoCHTlmyDTZSI9te?node-id=5-674 | Deep black, cream type, saffron controls, media-first composition and clear gold TV focus. |
| B — Broadcast | https://www.figma.com/design/2Hg2v6XoCHTlmyDTZSI9te?node-id=5-830 | Denser charcoal live-channel list and selected programme preview. |
| C — Sand & Ink | https://www.figma.com/design/2Hg2v6XoCHTlmyDTZSI9te?node-id=5-945 | Cream surfaces, black type, wheat accents and editorial collection layout. |
| A — Setup and RTL | https://www.figma.com/design/2Hg2v6XoCHTlmyDTZSI9te?node-id=5-1092 | English/Arabic first launch plus an Arabic library, with reversed navigation order. |

Recommendation: A as the main visual language, with B's denser channel-list arrangement for Live TV. Gold highlights actions and focus; it does not replace all backgrounds or text. Use Manrope for English and Cairo for Arabic. The wordmark is a working placeholder, not a brand decision.

## Reference palette labels

| Colour | Hex | Proposed role |
| --- | --- | --- |
| Night | `#0B0B0B` | Main dark background |
| Jet | `#333533` | Raised neutral surfaces |
| Eggshell | `#F2ECDD` | Main text on dark / optional light background |
| Saffron | `#F5CB5C` | Primary action and TV focus |
| Satin gold | `#CD9C20` | Supporting gold detail |
| Gold | `#A07F3A` | Muted gold detail |
| Wheat | `#C8AA6F` | Secondary warm accent |
| Charcoal | `#2F2F2F` | Cards and panels |
| Soft sand | `#E3D5BB` | Secondary text on dark / warm light surface |
| Pure black | `#000000` | Deep backdrop |

These are the printed hex labels in the supplied reference, rather than a claim about every compressed-image pixel. The metallic reference is a texture, not a single flat colour; controls should use solid colours for consistent contrast.

## Scope and verification

- Nine screen previews: three TV directions, three matching phone directions, two first-launch examples, one Arabic library. Text, hierarchy, vectors and imported Material button instances remain editable.
- Shared primitive/semantic colour variables and text styles created in Figma. Screens visually inspected; corrected imported purple button surfaces, RTL ordering, selected-channel metadata and small-thumbnail text clipping.
- The original dune image was generated for these concepts in Figma and retained locally. Smaller covers are editable vector illustrations. No third-party channel logos, private sources or real subscriptions used.
- Fictional titles, programme data, content filters, continue-watching and poster rows illustrate a product direction. They are not claims about current app capabilities. M3U metadata gaps must get honest fallbacks; do not invent film/series/EPG data to match a mockup.
- The first implementation after selection should cover existing source setup, M3U library and playback with shared Compose colour/type/spacing tokens. Future catalogue and EPG work stays separate.
- Owner chose A and requested light mode. Preserve the Cinema layout in both modes, default to dark, and persist an explicit appearance choice. Preserve existing playback/import behavior and the standing PR/check/merge/delete workflow.
- Matching light-mode Figma frames were created (board `10:41`); their screenshot export was blocked by the connected Figma Starter tool limit, so their visual QA remains pending. The nine original previews below were visually checked.

## Local previews

- [Cinema A](cinema.png)
- [Broadcast B](broadcast.png)
- [Sand & Ink C](sand-ink.png)
- [Cinema setup and RTL](setup-rtl.png)

Research: [Android TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system), [TV navigation](https://developer.android.com/training/tv/get-started/navigation). These inform focus visibility and predictable remote navigation; the proposed visual identity is specific to the supplied palette.

# SonicLab — Public Landing Page Art Direction

## Creative direction

**Positioning:** a considered, offline-first music player and audio toolkit for people who want practical playback controls without handing their local library to a cloud service.

**Visual theme:** bold luxury black + violet. Use a near-black foundation, layered aubergine surfaces, luminous lavender for the main action, controlled violet atmosphere behind the product visual, fine borders, generous negative space, and confident typographic scale. Keep the treatment elegant: use glow as a single focal halo, not a decoration on every component. Real album art and real application captures should provide the texture.

**Primary message:** “Dengarkan musik. Dengan cara Anda.”

**Supporting copy:** “Pemutar musik lokal dengan kontrol suara dan alat audio dalam satu pengalaman yang fokus dan privat.”

**Primary CTA:** “Jelajahi proyek” → repository. Secondary CTA: “Lihat fitur” → feature section. Until a signed public APK is actually available, do not label a button “Download” or imply the app is production released.

## Mockup

[`landing-page-mockup.svg`](landing-page-mockup.svg) is a vector concept board for a 1440 × 1040 desktop landing page. It contains an illustrative player screen and conceptual track/artwork. It is an art-direction mockup, **not an actual application screenshot**. Replace the illustrative screen and track details with captures from the real app before presenting the page as a product screenshot.

## Layout and content hierarchy

1. **Header:** compact SonicLab wordmark; links to Features, Privacy, and Open Source; one GitHub CTA.
2. **Hero:** concise Indonesian headline, short explanation, two actions, and three factual trust points: offline by design, no account, open source.
3. **Product visual:** device frame with a real player capture, ideally showing album art, track info, timeline, and transport controls. Keep the visual large enough to read but subordinate to the headline.
4. **Three feature cards:** sound controls; local audio tools; privacy. Make each card about a user benefit, not a feature dump.
5. **Status and trust:** clearly distinguish debug evaluation builds from a signed release. State Android minimum version and link to source, licenses, and third-party notices.
6. **Footer:** concise repository links, Apache-2.0 attribution, and optional donation link. Donations must remain optional and must not imply paid features.

## Design tokens

| Token | Value | Use |
|---|---|---|
| Background | `#07060B` to `#120B1B` | Near-black base with an aubergine depth; avoid flat pure black across every surface |
| Surface | `#191322` | Raised content cards with subtle violet edge |
| Primary text | `#FBF9FF` | Headlines and key labels |
| Secondary text | `#B9B0C2` | Supporting copy; preserve readable contrast |
| Violet accent | `#C19AFF` | Primary action, selected state, and restrained glow |
| Bright accent | `#E0CEFF` | Small highlights and high-contrast accent details |
| Cyan accent | `#91DFF1` | Rare utility/data accent only |
| Positive status | `#A8E5CE` | Offline/privacy indicators; do not imply certification |
| Borders | `#49365E` | Subtle card and device edges |
| Corners | 16–24 px | Buttons, cards, and device frame |
| Spacing | 8 px base rhythm | Use 24–32 px between major content groups |

**Typography:** use a clean sans-serif (Inter where available; system UI fallback otherwise). Desktop hero 58–66 px, section headings 28–36 px, body 16–18 px, metadata no smaller than 12–13 px. Keep line lengths near 55–70 characters and avoid all-caps except tiny eyebrow labels.

## Asset and screenshot plan

Capture these screens from a real Android device in the same dark theme, with realistic local media and no personal notifications or private filenames:

- Library: track list and search state.
- Player: album art, audio information, and playback controls.
- Equalizer: visible 10-band curve and one spatial effect section.
- Studio: a completed analysis result or waveform.
- About: current version, licenses, developer link, and donation CTA.

Prefer consistent device, density, status bar, theme, and track metadata. Export lossless PNG at the device's native resolution; crop with consistent rounded corners only in the landing-page composition. Keep full, unaltered captures available in the repository when practical.

## Accuracy and release checks

- Say “offline” in the context supported by the manifest: the app does not request the Android Internet permission, while users may open external links from the interface.
- Describe enhancement as DSP fallback when a neural model is not bundled. Do not market the fallback as neural AI.
- Describe the vocal remover as a stereo spectral center-channel effect; it is not neural source separation and cannot guarantee clean stems for every mix.
- Do not claim universal Hi-Res output or device-independent sound quality.
- Do not publish download/release badges until a signed, tested public artifact exists.
- Replace donation placeholders with verified public links before soliciting contributions.
- Ensure third-party license attribution remains distinct from SonicLab's Apache-2.0 source license.

## Responsive behavior

At tablet/desktop widths, preserve the two-column hero and let the device visual overlap the right side subtly. Below roughly 900 px, stack the hero copy and device preview; at narrow mobile widths, convert feature cards to a single column, keep the primary CTA full-width, and simplify the top navigation to a compact menu. Do not shrink body copy to force a desktop layout onto mobile.

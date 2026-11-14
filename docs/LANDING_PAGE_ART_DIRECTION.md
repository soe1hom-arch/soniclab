# SonicLab — Public Landing Page Art Direction

## Creative direction

**Positioning:** a considered, offline-first music player and audio toolkit for people who want practical playback controls without handing their local library to a cloud service.

**Visual theme:** minimal premium / luxury tech. Use deep graphite backgrounds, restrained violet highlights, generous negative space, precise typography, thin borders, and a single confident accent CTA. Let actual album art and real application screens carry the visual interest; avoid stock headphones, fake audio claims, and exaggerated neon waveforms.

**Primary message:** “Dengarkan musik. Dengan cara Anda.”

**Supporting copy:** “Pemutar musik lokal dengan kontrol suara dan alat audio dalam satu pengalaman yang fokus dan privat.”

**Primary CTA:** “Jelajahi proyek” → repository. Secondary CTA: “Lihat fitur” → feature section. Until a signed public APK is actually available, do not label a button “Download” or imply the app is production released.

## Mockup

[`landing-page-mockup.svg`](landing-page-mockup.svg) is a vector concept board for a 1440 × 1040 desktop landing page. It contains an illustrative player screen and conceptual track/artwork. It is an art-direction mockup, **not an actual application screenshot**. Replace the illustrative screen and track details with captures from the real app before presenting the page as a product screenshot.

## Layout and content hierarchy

1. **Header:** compact SonicLab wordmark; links to Features, Privacy, and Open Source; one GitHub CTA.
2. **Hero:** concise Indonesian headline, one-line explanation, two actions, and three factual trust points: offline by design, no account, open source.
3. **Product visual:** device frame with a real player capture, ideally showing album art, track info, timeline, and transport controls. Keep the visual large enough to read but subordinate to the headline.
4. **Three feature cards:** sound controls; local audio tools; privacy. Make each card about a user benefit, not a feature dump.
5. **Status and trust:** clearly distinguish debug evaluation builds from a signed release. State Android minimum version and link to source, licenses, and third-party notices.
6. **Footer:** concise repository links, Apache-2.0 attribution, and optional donation link. Donations must remain optional and must not imply paid features.

## Design tokens

| Token | Value | Use |
|---|---|---|
| Background | `#0B0B10` to `#15121D` | Page background; subtle vertical gradient only |
| Surface | `#191720` | Cards and restrained raised surfaces |
| Primary text | `#F4F1F8` | Headline and key labels |
| Secondary text | `#AAA5B5` | Supporting copy; preserve contrast |
| Violet accent | `#C5A6FF` | Primary CTA and select highlights |
| Cyan accent | `#82DFF3` | Rare secondary data/utility highlight |
| Positive status | `#9DDFCA` | Offline / privacy indicators |
| Corners | 16–24 px | Buttons, cards, device visual |
| Spacing | 8 px base rhythm | Use 24–32 px between major content groups |

**Typography:** use a clean sans-serif (Inter where available; system UI fallback otherwise). Desktop hero 56–64 px, section headings 28–36 px, body 16–18 px, metadata no smaller than 12–13 px. Keep line lengths near 55–70 characters and avoid all-caps except tiny eyebrow labels.

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

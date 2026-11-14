# Everyday Keys

A privacy-first Android keyboard: **unlimited clipboard history**, a **full number pad**, and an **icon-only interface**. Kotlin, Jetpack Compose, Room, DataStore.

## Get the app

Every push to `main` builds a signed APK and publishes it under **Releases**. Download the latest `EverydayKeys-v*.apk`, install it, open the app and follow the two steps on the status card (enable, then switch).

## What works

- **Layouts:** QWERTY, Urdu, Arabic, Hindi (enable in Settings, cycle with the globe key); shift / caps lock (double tap); optional number row; long-press accents; number pad (phone or calculator order, operators row, `=` evaluates the expression before the cursor); two symbol pages; one-handed mode (left / right).
- **Clipboard:** every copy is saved; pin, search, delete, clear all, merge several items into one paste. Text over 20,000 characters is stored as a file with only a reference in the database. Pasting streams in 50–100 K chunks with a progress bar and cancel.
- **Privacy:** password and sensitive fields are never saved; optional lock for clipboard history (fingerprint or screen lock); typing stays on the device (the internet is used only for Translate and the optional live suggestions, see PRIVACY.md); API keys are encrypted with the Android Keystore.
- **Toolbar:** clipboard, snippets (`;addr` style shortcuts expand on space), voice typing, translate selection, undo/redo, cursor + select all/copy/cut/paste panel, unit converter (length, weight, volume, speed, data, temperature), spacebar cursor drag, emoji with recents and search. Reorder tools and languages with arrows in Settings.
- **Smart typing:** autocorrect (off / mild / strong), word completion, next-word suggestions learned on your phone, personal dictionary, auto-capitalization, double-space period, key preview popup. All local.
- **Settings:** 11 sections, instant apply, per-section reset, search, status card, backup/restore to one JSON file.
- **Access:** every button is a vector icon (only the emoji panel shows emoji); TalkBack labels on keys.

Tip: long-press the number-pad key to open symbols.

## Not built yet (roadmap)

Glide typing and floating keyboard mode. Both need testing on real devices to tune, so they are not shipped yet.

## Design system (Material 3)

The whole app follows Material 3.

- **Colour:** Material You dynamic colour on Android 12+ (follows your wallpaper; switch it off under Settings, Appearance). Otherwise the exact Material 3 tonal palette for the accent you pick (teal, blue, purple, orange), light and dark (`design/Schemes.kt`, generated with Google's material-color-utilities). Surface containers, primary / secondary / tertiary containers and outline roles are used the way the spec describes.
- **Settings app:** large collapsing top app bar, filled cards, list items with tonal circular leading icons, pill search bar, switches with a check icon, single-choice and confirm dialogs, filled / tonal / outlined buttons, and an extended FAB for adding snippets.
- **Keyboard:** keys are tonal surfaces on a surface-container background, function keys use the secondary container, the action key uses the primary colour, and key corners follow the Material 3 shape scale (12 dp by default), and the resize overlay uses the same colors. Clipboard, snippets, emoji, converter, cursor keys and Quick settings use the same pieces: filled cards, pill search, filter chips, tonal tiles and a tonal confirmation bar in the suggestion row (with a one-tap follow-up such as Paste or Redo) for copy, cut, paste, select all, undo and redo. While text is selected the toolbar switches to select all, copy, cut, paste, undo and redo, and the clipboard preview uses the keyboard's full height.
- **Icons:** Material Symbols (Rounded), including a themed (monochrome) launcher icon for Android 13+.
- **Code:** `design/Theme.kt` (theme, shapes, dynamic colour), `design/Design.kt` (shared cards, tiles, buttons, switches, notices used by both Settings and the keyboard), `ui/SettingsKit.kt` (Settings scaffold and components).

Colors are always Material 3. The optional layout styles under Appearance (Compact, Pill, Round) only change key shape and number pad layout. Key styles are Tonal, Elevated and Outlined.

## Build

Open in Android Studio (JDK 17) or run `./gradlew assembleRelease`. Release signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. In CI, add repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` for a stable signing key (without them a throwaway key is generated per build).

## Licenses

MIT (see LICENSE). Uses AndroidX, Jetpack Compose, Room, DataStore, kotlinx.coroutines (Apache-2.0). Bundled English word list: first20hours/google-10000-english (derived from Google's Trillion Word Corpus).

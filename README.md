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

## Design system

Settings and everything the keyboard shows above the keys are built from one set of pieces in `design/Design.kt`
(cards, rows, toggles, buttons, notes, notices). The keyboard and Settings only differ in the colours they feed in.
While typing you get the same look for the clipboard, snippets, emoji, converter, cursor keys, paste progress and the new
**Quick settings** panel (enable it under Settings, Toolbar). Copy, cut, paste, select all, undo and redo each show a short
confirmation in the suggestion row, with a one-tap follow-up (Paste, Redo...). While text is selected the toolbar switches to
select all, copy, cut, paste, undo and redo. The preview of a copied item uses the keyboard's full height.

## Build

Open in Android Studio (JDK 17) or run `./gradlew assembleRelease`. Release signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. In CI, add repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` for a stable signing key; without them a throwaway key is generated per build (APKs install, but you cannot update over a build signed with a different key).

## Licenses

MIT (see LICENSE). Uses AndroidX, Jetpack Compose, Room, DataStore, kotlinx.coroutines (Apache-2.0). Bundled English word list: first20hours/google-10000-english (derived from Google's Trillion Word Corpus).

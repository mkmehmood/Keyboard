# Everyday Keys

A privacy-first Android keyboard with an unlimited clipboard, a dedicated number pad and an icon-only interface.

## Features
- **Letters**: QWERTY, QWERTZ, AZERTY, Urdu, Arabic, Hindi. Shift, caps lock, long-press accents, optional number row, glide typing.
- **Number pad**: phone or calculator style, `+ − × ÷ = % /` row, type `1,250 + 340 =` to get the result, unit converter.
- **Symbols**: two pages of punctuation, currency, math and brackets.
- **One-handed and floating modes.**
- **Unlimited clipboard**: history, pin, search, delete, clear all, merge items, swap last items. Big items are stored as files and pasted in 50-100 KB chunks with a progress bar. Sensitive fields are never saved. Optional biometric lock.
- **Toolbar**: snippets with `;shortcuts`, voice typing, translate, quick search, undo/redo, cursor and edit keys, emoji with recents and search.
- **Smart typing**: autocorrect, next-word suggestions, personal dictionary, auto-capitalization, double-space period. All local.
- **Icon rule**: every button is a vector icon; only the emoji panel shows emoji.
- **Settings**: Compose app with search, status card, 11 sections, live preview field, backup and restore.

## Install
Every push to `main` builds the APK and publishes it under **Releases**. Download the latest `.apk`, install it, open Everyday Keys, then follow the status card (enable, then switch).

## Build
Android Studio (Hedgehog or newer) or `gradle :app:assembleRelease` with JDK 17.
The release build is signed with the debug key so it installs without secrets. Add your own keystore for Play Store releases.

Stack: Kotlin, InputMethodService, custom canvas keys, Jetpack Compose (settings), Room, DataStore.

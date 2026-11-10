# Everyday Keys

A privacy-first Android keyboard: unlimited clipboard history, a real number pad, and an icon-only interface. Everything is processed on the device; there is no network permission at all.

## Get the app
Every push to `main` builds a release APK and publishes it under **Releases** (tag `v1.0.<run number>`). Download the `.apk`, install it, open Everyday Keys and follow the two-step status card (enable, then switch).

The APK is signed with the debug key unless you add these repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Features in this version
- Letters: QWERTY, Urdu, Arabic, Hindi (enable in settings); shift and caps lock (double-tap), optional number row, long-press accents
- Number pad (phone or calculator order) with `+ - × ÷ = . , %`; `=` evaluates the expression before the cursor
- Two symbol pages, one-handed mode (left/right dock)
- Clipboard: history, pin, search, delete, clear all, merge several items into one paste, retention, biometric lock, skips password and sensitive fields
- No size limit: items over 20,000 characters are stored as files; pasting is chunked (50 to 100 KB) with a progress bar
- Toolbar: clipboard, snippets (`;shortcut` expansion), emoji with recents, voice typing, select all/cut/copy/paste, undo/redo, cursor keys, spacebar cursor drag, search, translate
- Smart typing: local autocorrect, suggestions, personal dictionary, auto-capitalization, double-space period
- Compose settings app with search, status card, live preview, backup/restore

## Not built yet
Glide typing, floating mode, unit converter, emoji search, custom colours, drag-to-reorder toolbar, key preview popup.

## Build locally
Open in Android Studio (JDK 17), or run `gradle :app:assembleRelease`.

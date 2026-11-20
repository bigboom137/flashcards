# Flashcards

[![Nightly tests](https://github.com/bigboom137/flashcards/actions/workflows/nightly.yml/badge.svg)](https://github.com/bigboom137/flashcards/actions/workflows/nightly.yml)

Offline flashcard app for Android and Windows: scan pages or paper flashcards,
then study with swipe gestures (left = hard, right = easy) on a spaced-repetition schedule.
The two apps sync their decks to each other over Wi-Fi.

## Download

Grab the latest from [**Releases**](https://github.com/bigboom137/flashcards/releases/latest):

| | |
|---|---|
| **Android** | `flashcards-android.apk` — Android 8.0+. Debug-signed, so allow installs from unknown sources. |
| **Windows** | `Flashcards-x.y.z.msi` — installs per-user, no admin needed. |

## Features

- **Decks** — create, rename, delete; shows how many cards are due.
- **Scan a page / list** — photograph a textbook page, vocabulary list or notes.
  Text is recognized on-device (Google ML Kit, no internet needed) and split into
  cards. Split modes: auto-detect, `Q:`/`A:` pairs, `term - definition` lines,
  alternating lines, or blank-line-separated blocks. Preview and untick any card before saving.
- **Scan a paper flashcard** — scan the front, scan the back, fix any OCR mistakes, save, repeat.
- **Manual cards** — type them in, edit or delete later.
- **Study** — tap the card to flip it. Swipe **right** (or tap *Easy*) to move the card up a
  box; swipe **left** (or tap *Hard*) to send it back to box 0 and see it again later in
  the same session.
- **Spaced repetition** — Leitner boxes with intervals of 10 min → 6 h → 1 d → 2 d → 3 d → 4 d (capped at 4 days).
  Only due cards are shown by default; you can also study the whole deck at any time,
  or reset a deck's progress.

## Build & install

Requires Android Studio (or just the SDK + JDK 17+). Min Android 8.0 (API 26).

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

## PC version (Windows)

The `desktop/` module is the same app for Windows, built with Compose Multiplatform.
It compiles the scheduler, card parser, cloze, import/export and quiz games straight from
`app/src/main/java`, so both versions behave identically, and it runs the same unit tests.

- **Data** lives in `%APPDATA%\Flashcards\flashcards.json`, which uses the backup format.
  Back up on the phone, then *Restore from backup…* on the PC (or the reverse) to move your decks.
- **Scanning** uses the OCR built into Windows (offline). Snip text with Win+Shift+S and
  click *Paste screenshot*, or open an image file. You can also just paste or type a list.
- **Keyboard**: in Study, Space flips, 1–4 grade (Repeat / Hard / Okay / Easy), Esc leaves.
  In multiple choice, 1–4 picks an answer. In typing mode, Enter checks.
- **Reminders**: while the app is open or minimized, a Windows notification appears when
  cards become due (decks with reminders muted are skipped).

```bash
./gradlew :desktop:run                 # run from source (needs JDK 17+)
./gradlew :desktop:test                # shared + desktop tests
./gradlew :desktop:createDistributable # standalone app: desktop/build/compose/binaries/main/app/Flashcards/Flashcards.exe
./gradlew :desktop:packageMsi          # installer (needs the WiX 3.x toolset on PATH)
```

Bump `packageVersion` in `desktop/build.gradle.kts` for any build you intend to install
over an older one: Windows Installer refuses an in-place upgrade when the version is unchanged.

## Wi-Fi sync

*Share over Wi-Fi…* on the PC (overflow menu) starts a small HTTP server on the local
network and shows a 6-digit code; *Sync with computer…* on the phone finds it by UDP
broadcast, or takes the address by hand. Both libraries are merged — cards are matched by
text rather than id, and the more-studied copy of a card wins — so neither side loses work.
Nothing leaves the local network, and the code has to match.

## Tests

```bash
./gradlew :desktop:test :app:testDebugUnitTest
```

The same suite runs nightly on Windows in GitHub Actions.

## Project layout

```
app/src/main/java/com/flashcards/
├── MainActivity.kt          Compose entry point
├── AppViewModel.kt          DB access + in-memory study session
├── data/
│   ├── Entities.kt          Deck / Card Room entities
│   ├── Daos.kt              Room DAOs
│   ├── AppDatabase.kt
│   ├── Scheduler.kt         Leitner spaced-repetition rules
│   └── CardParser.kt        Turns OCR / typed text into front-back cards
├── ocr/OcrEngine.kt         ML Kit text recognition + reading-order layout
└── ui/
    ├── FlashcardsApp.kt     Navigation graph
    ├── DeckListScreen.kt
    ├── DeckDetailScreen.kt
    ├── ScanScreen.kt        Camera / gallery → OCR → card preview
    ├── StudyScreen.kt       Swipeable, flippable card stack
    └── Dialogs.kt
```

Tech: Kotlin, Jetpack Compose (Material 3), Room, Navigation Compose, ML Kit Text Recognition.

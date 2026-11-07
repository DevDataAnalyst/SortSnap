# SortSnap — project brief

SortSnap (working name) is an Android photo gallery that automatically separates useful photos
from clutter.

## Categories

Every image gets exactly one primary category plus a confidence score in 0..1.

| Category | Meaning |
| --- | --- |
| `PHOTO` | Real camera photos (people, places, pets, food, events) |
| `SCREENSHOT` | Device screenshots |
| `MEME` | Memes, joke images, image macros, forwarded humour |
| `FORWARD` | Greetings, quotes, "good morning" images, chat-app forwards that are not memes |
| `DOCUMENT` | Receipts, bills, notes, whiteboards, ID-style scans, printed pages |
| `OTHER` | Anything low-confidence. **Never auto-hide these.** |

## Non-negotiables

- 100% on-device. No `INTERNET` permission (it is explicitly stripped with `tools:node="remove"`
  because ML Kit transitively requests it), no analytics, no photo ever leaves the phone.
- Never delete without explicit user confirmation through the system MediaStore trash/delete
  request (`createTrashRequest` / `createDeleteRequest` / `RecoverableSecurityException`).
  On Android 9 and below, where no system dialog exists, an in-app confirmation dialog is mandatory.
- The user's manual correction (`userOverride`) always overrides the classifier and is never
  overwritten by a rescan. Scanner code must skip rows where `userOverride != null`.
- Must handle libraries of 50,000+ images without freezing the UI (paging everywhere, all
  scanning in WorkManager, no full-table loads on the main thread).

## Tech stack

Kotlin, Jetpack Compose, Material 3 (dynamic colour), MVVM, Hilt, Kotlin Coroutines + Flow, Room,
WorkManager, Coil 3, ML Kit Image Labeling and Text Recognition (bundled, offline models),
Paging 3, DataStore. `minSdk 26`, `targetSdk`/`compileSdk` = latest stable platform installed (37.2).

## Architecture

Single activity (`MainActivity`) hosting Compose Navigation.

Gradle modules:

- `:core` — **pure Kotlin/JVM**, no Android dependency at all.
  - `com.sortsnap.classifier` — features, rules, `ClassifierConfig`, score fusion, explanations,
    perceptual hash, blur metric, evaluation harness types.
  - `com.sortsnap.domain` — pure domain logic: sync diff, folder priors, near-duplicate grouping,
    date buckets, daily review picking, undo stack.
- `:app` — Android application.
  - `com.sortsnap.data` — MediaStore access, Room database, DataStore settings, ML Kit wrappers,
    WorkManager scanner, deletion.
  - `com.sortsnap.domain` — use cases that combine repositories (correction, cleanup).
  - `com.sortsnap.ui` — Compose screens, ViewModels, theme, navigation.
- `:baselineprofile` — Baseline Profile generator (startup + gallery scroll).

Rules:

- Classifier logic must not depend on Android UI classes (enforced by `:core` being a JVM module).
- All weights and thresholds live in `ClassifierConfig`. Bump `ClassifierConfig.VERSION` whenever
  classification behaviour changes; the scanner reclassifies rows whose `classifierVersion` is older.
- ViewModels expose `StateFlow`; Room queries are `Flow` / `PagingSource` so counts update live.

## Conventions

- Use latest stable library versions via the Gradle version catalog (`gradle/libs.versions.toml`).
- After every change run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` (plus
  `./gradlew :core:test`) and fix failures before stopping.
- Write unit tests for every classifier rule.
- Compose UI tests run on the JVM via Robolectric (`app/src/test`), so they run with
  `testDebugUnitTest` and need no emulator.
- Coverage: `./gradlew :core:koverHtmlReport :core:koverVerify` (80% minimum on `:core`).
- Keep the "Progress" section at the bottom of this file updated at the end of each task with what
  was built and what is next.
- Environment: `ANDROID_HOME` must point to an SDK with `platforms;android-37.2` and
  `build-tools;37.0.0` (or set `sdk.dir` in `local.properties`, which is git-ignored).

## Progress

- **Prompt 0** — Brief written, `.gitignore` added, git initialised.

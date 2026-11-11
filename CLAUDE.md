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
- **Prompt 1** — Gradle KTS + version catalog (AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00,
  compileSdk/targetSdk 37.2), Hilt app class, single `MainActivity`, Compose Navigation with bottom bar
  (Gallery, Categories, Review, Settings), Material 3 with dynamic colour + dark mode, splash screen.
- **Prompt 2** — `PhotoAccess` (13+: READ_MEDIA_IMAGES, 14+: partial access banner, ≤12:
  READ_EXTERNAL_STORAGE), onboarding screen, `MediaStoreRepository` (Bundle LIMIT/OFFSET paging,
  stamps, by-id queries), `ContentObserver` flow, `MediaStoreCursorMapper` + tests.
- **Prompt 3** — Paged `LazyVerticalGrid` with date headers (Today / Yesterday / This week /
  Month Year), pinch for 3/4/5 columns, Coil thumbnails at bucketed sizes with stable keys, long-press
  multi-select, full-screen `HorizontalPager` viewer (placeholders so indices are absolute) with
  pinch/double-tap zoom and a details sheet.
- **Prompt 4** — Room `ImageRecord` (+ stored `effectiveCategory`), `IndexSync` using `SyncDiff`,
  `ScanWorker` (batches of 50, battery-not-low, progress), observer-triggered incremental scans, full
  rescan when `ClassifierConfig.VERSION` changes, slim progress bar in the Gallery top bar.
- **Prompt 5** — `:core` rule-based first pass: 11 rules, `ClassifierConfig`, `ScoreFusion`.
- **Prompt 6** — ML Kit bundled text recognition + labeling (max 1024 px, concurrency 2, only when
  first pass < 0.85), 9 ML rules, signals stored as JSON, classifier version 2.
- **Prompt 7** — Category tiles (cover, count, size, live from Room), album screens reusing the grid,
  filter chips (All / Photos only / per category; "Hide clutter" default ON), "Why?" list.
- **Prompt 8** — Move to category (viewer + multi-select) sets `userOverride`, corrections table,
  per-folder learned rules (5+ corrections, clear majority, deletable in Settings, deleted rules need
  5 more corrections to come back), daily "Is this right?" card (20 images, 0.55–0.75).
- **Prompt 9** — Review: old screenshots (>30 days), memes, forwards, near-duplicates (dHash,
  multi-index hashing, Hamming ≤ 4, largest copy kept), blurry photos (Laplacian variance < 60).
  Select-all grids with tap-to-keep, swipe mode with undo (last 10), trash/delete flow.
- **Prompt 10** — Instant first launch: "All" pages MediaStore directly; index builds in WorkManager.
  Resumable cursor in `scan_state`; initial full scan only when charging or battery > 30% (else waits
  for the charger), "Scan now anyway" in Settings. Room indices on category, effectiveCategory,
  dateTaken, bucket, contentHash, classifierVersion. Thumbnail-only decodes (256 px for stats,
  1024 px for ML, 2048 px in the viewer), bitmaps recycled, ML clients closed after each scan.
  `:baselineprofile` module (startup + gallery scroll). Debug-only scan statistics screen.
- **Prompt 11** — `:core` 129 tests, line coverage 98.9% (Kover, 80% gate). Evaluation harness with
  60 synthetic fixtures. `:app` 31 tests: cursor mapping, permissions, paging source, Room (override
  safety, priors), Robolectric Compose UI tests (onboarding, filter chips, multi-select, delete
  confirmation with a fake deleter, details "Why?").
- **Prompt 12** — Settings complete, empty/error/skeleton states, haptics on swipe/long-press,
  content descriptions, adaptive + monochrome icon, strings in `strings.xml`, R8 + resource shrinking,
  keep rules, `docs/SIGNING.md`, `docs/PLAY_STORE_CHECKLIST.md`. `bundleRelease` and `assembleRelease`
  build; merged manifest verified to have no INTERNET permission.
- **Smarter sorting (classifier v3, app 1.1.0)** — Personal learning: `PersonalModel` (`:core`) learns
  from every hand-sorted picture (userOverride rows keep their `signalsJson`): evidence fingerprints,
  Jaccard ≥ 0.75, ≥ 3 similar examples, ≥ 70 % agreement, weight ≤ 3.0, consulted only when the
  classifier's own confidence < 0.7 (so confident photos never flip). ScanWorker builds it once per run
  from the newest 5,000 examples. Settings "Learns from you" shows the example count and re-sorts
  unsure pictures (`requeueUnsure`). New rules: `LongScreenshotRule` (scrolling captures),
  `FlatGraphicRule`, `DocumentKeywordRule` (IDs, tickets, notes, medical, school), `ListLayoutRule`,
  `DocumentLabelRule`, `MemePhraseRule`, `SocialWatermarkRule`, `GrayscaleDocumentRule` (new
  Hasler-Süsstrunk `colorfulness` in `VisualFeatures`); more festival/devotional greetings. Single
  universal APK (`splits.abi` disabled) via `assembleRelease`. Synthetic eval: 0.917 → 0.983.
- **User guide + screenshots** — a 9-step "Getting started" section in `README.md` (screenshot beside each step). Screens are
  rendered by `GuideScreenshots` (Robolectric native graphics, `-PguideDir=docs/screenshots`) using sample
  illustrations from `tools/generate_sample_images.py`. To make that possible, screens were split into
  stateless `*Content` composables (`GalleryContent`, `CategoriesContent`, `ReviewContent`,
  `SettingsContent` + `SettingsActions`, `ViewerTopBar`, `SortSnapBottomBar`). Not device captures.

- **Select all + delete permanently (app 1.2.0)** — Gallery and album "More options" menu: "Select all"
  selects every image the current filter shows (not only loaded pages) via `GalleryRepository.allTargets`
  (MediaStore stamps for All, slim `TargetRow` Room queries for categories; selection is now
  `Map<Long, DeleteTarget>`). "Delete permanently" (selection mode only): `MediaDeleter.start(…, permanent = true)`
  always shows an in-app "cannot be recovered" dialog first, then Android 11+ uses `createDeleteRequest`
  (skips the trash), Android 10 the `RecoverableSecurityException` loop, Android 9 and below a direct delete.

### Evaluation (60 synthetic fixtures, classifier v3)

```
actual \ predicted    PHOTO SCREENSHOT MEME FORWARD DOCUMENT OTHER
PHOTO                   11      0        0     0       0       1
SCREENSHOT               0     12        0     0       0       0
MEME                     0      0       12     0       0       0
FORWARD                  0      0        0    12       0       0
DOCUMENT                 0      0        0     0      12       0
accuracy 0.983 · precision 1.00 for every category (v2 was 0.917; the one miss falls to OTHER)
First pass only (no ML): accuracy 0.317
```

### Evaluation (60 synthetic fixtures, classifier v2)

```
actual \ predicted    PHOTO SCREENSHOT MEME FORWARD DOCUMENT OTHER
PHOTO                   11      0        0     0       0       1
SCREENSHOT               0     12        0     0       0       0
MEME                     0      0       11     0       0       1
FORWARD                  0      0        0    12       0       0
DOCUMENT                 0      0        0     0       9       3
accuracy 0.917 · precision 1.00 for every category (misses all fall to OTHER, never hidden)
First pass only (no ML): accuracy 0.317
```

### Deletion edge cases handled (Prompt 9)

- Android 11+: `createTrashRequest` (recoverable 30 days); falls back to `createDeleteRequest` if
  trash throws. Selections split into chunks of 1000 to keep the IntentSender under the Binder limit;
  one dialog per chunk; declining a chunk stops the rest but keeps what was already approved.
- Android 10: delete one by one; `RecoverableSecurityException` launches its consent IntentSender,
  then the same item is retried and the loop continues.
- Android 9 and below: no system dialog exists, so an in-app confirmation dialog is mandatory;
  delete via `WRITE_EXTERNAL_STORAGE` (maxSdk 28).
- User cancels: nothing removed, "Nothing was deleted" snackbar.
- Duplicate ids in a selection are de-duplicated; an empty selection is a no-op.
- After success the rows are removed from Room immediately and "Freed X" is shown; the observer
  sync also removes trashed items (MediaStore hides them).
- A null MediaStore cursor is treated as an error, never as "library is empty", so a transient
  failure cannot wipe the index or user corrections.

### Known limitations / next

- Not yet run on a real device or emulator (no KVM in the build container): install
  `app-debug.apk`, seed a test phone, and paste any crash logcat back.
- Android 14 partial access: images the user deselects are dropped from the index on the next sync,
  including their manual overrides (the correction log keeps the history).
- Release APKs are signed with the build machine's debug key unless `keystore.properties` exists, so each
  cloud build has a different certificate and cannot install over the previous one (uninstall first).
- Swipe-mode undo of "move category" restores the previous category as an override rather than
  clearing the override.
- The Baseline Profile must be generated on a device (`./gradlew :app:generateReleaseBaselineProfile`).
- Personal learning only reaches already-indexed unsure pictures when the user taps "Learns from you"
  in Settings (new pictures get it automatically on scan).
- Next: run on device, real fixtures in the eval CSV (add the new optional `colorfulness` column), then optional extensions (custom TFLite model,
  FTS search over OCR text, cleanup reminders, Glance widget, Devanagari OCR).

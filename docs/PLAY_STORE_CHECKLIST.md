# Play Store checklist — SortSnap

## Build
- [ ] `./gradlew bundleRelease` with the real upload key (see SIGNING.md)
- [ ] Bump `versionCode` / `versionName` in `app/build.gradle.kts`
- [ ] Generate the Baseline Profile on a device: `./gradlew :app:generateReleaseBaselineProfile`
- [ ] Smoke-test the release APK on Android 9, 11, 13 and 14+ (partial photo access)
- [ ] Confirm the merged manifest has **no** `INTERNET` permission:
      `aapt2 dump permissions app-release.apk`

## Data safety form (Play Console → App content → Data safety)

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable: no data is transmitted (the app has no network permission) |
| Do you provide a way for users to request that their data is deleted? | Not applicable: no data is collected. Uninstalling removes the on-device index. |

Photos are *accessed* on-device to display and sort them but never leave the device, which Google
does not count as "collection" ("data processed only on the device is not collected").

## Photo and video permissions declaration

SortSnap requests `READ_MEDIA_IMAGES` because it is a gallery app whose core purpose is browsing and
organising the user's whole photo library. Suggested declaration text:

> SortSnap is a photo gallery. Its core feature is showing the user's entire photo library and
> automatically sorting it into Photos, Screenshots, Memes, Forwards and Documents, plus cleaning up
> clutter. This requires persistent, broad access to all images on the device; the one-time photo
> picker cannot provide a browsable, continuously updated library. All processing happens on the
> device; the app has no internet permission.

Also declared: `ACCESS_MEDIA_LOCATION` (read only to detect whether a photo has a GPS tag, a signal
that it is a real camera photo; coordinates are never stored or shown), and
`WRITE_EXTERNAL_STORAGE` with `maxSdkVersion=28` (deleting on Android 9 and below).

## Content rating
- [ ] Complete the questionnaire: no user-generated content sharing, no ads, no purchases.

## Store listing

**App name:** SortSnap: Smart Gallery

**Short description (80 chars):**
> Your gallery minus the clutter. Sorts memes, screenshots and forwards offline.

**Full description:**
> SortSnap is a photo gallery that automatically separates the photos you care about from
> everything else.
>
> • Smart albums: Photos, Screenshots, Memes, Forwards, Documents
> • "Photos only" view hides screenshots, memes and good-morning forwards from your main gallery
> • Tap "Why?" on any picture to see how it was sorted
> • Teach it: move a picture to the right album and SortSnap learns your folders
> • Clean up fast: old screenshots, near-duplicates and blurry photos, with a swipe mode
> • Safe: deletions go to the Android trash first and always ask before removing anything
> • Private by design: no internet permission, no ads, no account. Everything runs on your phone.

**Category:** Photography · **Tags:** gallery, photo organizer, cleaner

## Screenshots to capture (phone, 1080×2400, at least 4)
1. Gallery with "Photos only" chip selected and date headers
2. Categories screen with counts and sizes
3. Details sheet showing category, confidence and the "Why?" list
4. Review screen with "Free up space" sections and reclaimable sizes
5. Swipe mode mid-swipe
6. Onboarding "Everything stays on this phone"
7. (Optional) Dark theme gallery

Feature graphic 1024×500 and a 512×512 icon (export from `ic_launcher_foreground.xml`).

## Policy
- [ ] Privacy policy URL (required for photo permission): state that no data is collected,
      transmitted or shared; reuse the About & privacy text.
- [ ] Target API level meets the current Play requirement (targetSdk 37).

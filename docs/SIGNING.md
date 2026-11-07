# Release signing

Keystores are never committed (`*.jks`, `*.keystore` and `keystore.properties` are git-ignored).

## 1. Create an upload key (once)

```bash
keytool -genkeypair -v \
  -keystore ~/keys/sortsnap-upload.jks \
  -alias sortsnap-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep the `.jks` file and both passwords in a password manager. Losing the upload key is
recoverable through Play Console (key reset), but it takes days.

## 2. Point the build at it

Create `keystore.properties` in the repo root (git-ignored):

```properties
storeFile=/absolute/path/to/sortsnap-upload.jks
storePassword=…
keyAlias=sortsnap-upload
keyPassword=…
```

`app/build.gradle.kts` reads this file. Without it, `assembleRelease` falls back to the **debug**
key so you can install and smoke-test a minified release build locally. Never upload that build.

## 3. Build

```bash
./gradlew bundleRelease          # app/build/outputs/bundle/release/app-release.aab  (upload this)
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk     (sideload test)
```

## 4. Play App Signing

Enrol in Play App Signing when you create the app in Play Console. Google holds the app signing key;
you only ever upload with the upload key above.

## CI

Store the keystore as a base64 secret, decode it to a temp file in the job, and write
`keystore.properties` from secrets. Delete both at the end of the job.

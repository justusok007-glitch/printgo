# Releasing PrintGo

## 1. Debug builds (no setup)
Every push to `main` builds a debug APK in **Actions → Build PrintGo APK → Artifacts → PrintGo-debug-apk**.
Install it on a phone to test. Debug builds are signed with Android's debug key and are not for the Play Store.

## 2. Create the upload keystore (once)
Run on any computer with Java installed and **keep the file and passwords safe** – if you lose them you cannot update the app on Google Play.

```bash
keytool -genkeypair -v -keystore printgo-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 printgo-upload.jks > printgo-upload.jks.base64   # macOS: base64 -i printgo-upload.jks
```

## 3. Add GitHub secrets
Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | contents of `printgo-upload.jks.base64` |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |
| `KEY_ALIAS` | `upload` (or the alias you chose) |

## 4. Build the signed release
Actions → **Build signed release (APK + AAB)** → Run workflow (or push a tag such as `v1.0.0`).
Artifacts: `PrintGo-release-apk` (install directly), `PrintGo-release-aab` (upload to Google Play), `PrintGo-mapping` (R8 mapping for crash de-obfuscation).

## 5. Before each release
- Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
- Test on a real phone with a real printer (see the checklist in `docs/TEST_CHECKLIST.md`).
- Update `docs/PLAY_STORE_LISTING.md` if features changed.

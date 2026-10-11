# PrintGo

Android app (Kotlin, Jetpack Compose, Material 3) for printing photos, documents and web pages
from a phone. WiFi printers are discovered with mDNS and driven over IPP.

## Build

- GitHub: every push to `main` builds a debug APK (Actions tab, artifact `PrintGo-debug-apk`).
- Locally: open the folder in Android Studio and run the `app` configuration (JDK 17).

## Release signing (later)

Set the secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` and `KEY_ALIAS` in the repo.
The release build only signs when these are present.

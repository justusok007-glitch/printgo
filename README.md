# PrintGo

Android app (Kotlin, Jetpack Compose, Material 3) for printing photos, documents and web pages from a phone.
Wi-Fi printers are discovered with mDNS and driven directly over IPP. No account, no ads, no tracking.

## Features
- Photos, PDF, Word/Excel/PowerPoint, text and web pages; share-into-app and "open with"
- Full print settings: copies, collate, page ranges, odd/even, pages-per-sheet, duplex, paper, orientation, scale, margins, alignment, colour, quality, rotate/exclude pages, borders, page numbers, date stamp, file name header, watermark, presets
- Direct IPP printing (PDF / PWG Raster / JPEG chosen from the printer's capabilities), IPPS with trust-on-first-use certificates
- Save as PDF, system print dialog fallback, ESC/POS thermal printers over Bluetooth or USB, raw port 9100
- Job queue with live status, cancel, retry, notifications and a foreground service
- History: search, filters, reprint, CSV export, configurable retention
- Settings, Help/FAQ, About, Privacy policy, first-run tour

## Build
- **GitHub:** every push to `main` builds a debug APK (Actions → *Build PrintGo APK* → artifact `PrintGo-debug-apk`).
- **Signed release (APK + AAB):** see [docs/RELEASE.md](docs/RELEASE.md).
- **Locally:** open in Android Studio (JDK 17) and run the `app` configuration.

## Docs
[Release guide](docs/RELEASE.md) · [Test checklist](docs/TEST_CHECKLIST.md) · [Privacy policy](docs/PRIVACY_POLICY.md) · [Play Store listing](docs/PLAY_STORE_LISTING.md)

## Known limitations
- Bluetooth/USB output is for thermal (ESC/POS) printers, not general inkjet/laser models.
- Printers that accept none of PDF, PWG Raster or JPEG over IPP need the "Print with system dialog" fallback.
- Office conversion is best-effort; export to PDF from the source app for exact layout.
- Authentication-protected printers are not supported.
- Pause/Resume of a running job is not implemented (cancel and retry are).

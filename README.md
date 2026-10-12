# PrintGo

Android app (Kotlin, Jetpack Compose, Material 3) for printing photos, documents and web pages from a phone.
Wi-Fi printers are discovered with mDNS and driven directly over IPP. No account, no ads, no tracking.

## Features
- Photos, PDF, Word/Excel/PowerPoint, text and web pages; share-into-app and "open with"
- Full print settings: copies, collate, page ranges, odd/even, pages-per-sheet, duplex, paper, orientation, scale, margins, alignment, colour, quality, rotate/exclude pages, borders, page numbers, date stamp, file name header, watermark, presets
- Direct IPP printing (PDF / PWG Raster / JPEG chosen from the printer's capabilities), IPPS with trust-on-first-use certificates
- Save as PDF, system print dialog fallback, raw port 9100
- USB laser/inkjet printers: IPP-over-USB when the printer offers it, otherwise PDF, PostScript or PCL chosen from the printer's own USB device ID; ESC/POS for thermal printers
- Bluetooth printers: ESC/POS, PCL, PostScript or PDF (pick in Settings)
- Built-in printer drivers for over 5,000 models: Epson ESC/P-R, Gutenprint (Canon, Epson, HP, Lexmark…), HPLIP
  (hpcups) and SpliX (Samsung/Xerox/Dell lasers). Chosen automatically when a printer needs one, or by hand under
  Printers → ⓘ → Printer driver. Gutenprint, HPLIP and SpliX printer descriptions download once as small packs.
- Job queue with live status, cancel, retry, notifications and a foreground service
- History: search, filters, reprint, CSV export, configurable retention
- Settings, Help/FAQ, About, Privacy policy, first-run tour

## Build
- **GitHub:** every push to `main` builds a debug APK (Actions → *Build PrintGo APK* → artifact `PrintGo-debug-apk`).
  The printer drivers are cross-compiled first by `native/build.sh` (Android NDK) and smoke-tested on Linux.
- **Signed release (APK + AAB):** see [docs/RELEASE.md](docs/RELEASE.md).
- **Locally:** open in Android Studio (JDK 17) and run the `app` configuration.

## Docs
[Master prompt for future updates](docs/MASTER_PROMPT.md) · [Project guide](CLAUDE.md) · [Release guide](docs/RELEASE.md) · [Test checklist](docs/TEST_CHECKLIST.md) · [Privacy policy](docs/PRIVACY_POLICY.md) · [Play Store listing](docs/PLAY_STORE_LISTING.md)

## Known limitations
- Host-based printers that need a closed driver (for example Canon LBP2900 / CAPT, HP LaserJet P1102 / ZjStream) cannot be driven; use the system print dialog with the maker's print app. Few office printers accept Bluetooth serial data at all.
- Printers that accept none of PDF, PWG Raster or JPEG over IPP need the "Print with system dialog" fallback.
- Office conversion is best-effort; export to PDF from the source app for exact layout.
- Authentication-protected printers are not supported.
- Pause/Resume of a running job is not implemented (cancel and retry are).

## License
PrintGo is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
later version. See [LICENSE](LICENSE). Printer drivers and libraries keep their own licences, listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

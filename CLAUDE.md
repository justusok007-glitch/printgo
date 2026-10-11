# PrintGo — project guide for Claude

Read this whole file before changing anything. It records how the app is built, the owner's fixed
requirements, and the printer bugs already found and fixed (so they are not reintroduced).

## Owner and app identity
- Owner: Saqib Khalil (GitHub `justusok007-glitch`, repo `justusok007-glitch/printgo`, branch `main`).
- App: **PrintGo**, Android printing app similar to NokoPrint. Package `com.printgo.app`, versionName in
  `app/build.gradle.kts` (bump `versionCode`/`versionName` for every release the owner installs).
- Fixed design rules — do not change unless the owner asks:
  - Bright **light theme only**, PrintGo blue/green colours (`theme/Color.kt`). **No dark mode.**
  - **No ads**, no bottom slogan, no "Tip" line on the home screen.
  - Home screen footer must stay exactly: **"Created by : Muhammad Saad Awan"** and an envelope icon with
    **saadawanok@gmail.com** (tappable mailto). See `CreatorCredit` in `screens/HomeScreen.kt`.
  - Plain, friendly wording in the UI; no technical jargon in user-facing messages.

## How to build (there is no local Android SDK and no Gradle wrapper)
- All builds run on **GitHub Actions**. Push to `main`, then check the run.
- `.github/workflows/build-apk.yml` ("Build PrintGo APK") has three jobs:
  - `build` (JDK 17, Gradle 9.3.1 via `gradle/actions/setup-gradle`): `assembleDebug`, uploads artifact
    **PrintGo-debug-apk**, and replaces the GitHub Release **latest-debug** with `PrintGo-debug.apk`
    (easiest download on the phone: Releases → "PrintGo debug build").
  - `unit-tests` (**JDK 21** — Robolectric tests pin SDK 36, which needs Java 21).
  - `release-check` (R8 `assembleRelease`, `continue-on-error`).
- On failure the logs are pushed to branches: `ci-logs-build` (build.log), `ci-logs-tests` (log + test XML),
  `ci-logs-release`. Read them with:
  `git fetch -q origin +refs/heads/ci-logs-build:refs/remotes/origin/ci-logs-build && git show origin/ci-logs-build:build.log | tail -200`
- Check run status without auth:
  `curl -s "https://api.github.com/repos/justusok007-glitch/printgo/actions/runs?head_sha=<sha>"`
  then `.../actions/runs/<id>/jobs`. A build takes about 4–6 minutes.
- `build-release.yml` builds a signed APK + AAB only when repo secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`,
  `KEY_PASSWORD`, `KEY_ALIAS` exist (see `docs/RELEASE.md`).
- Commit only to `main` unless asked otherwise; keep CI green before telling the owner the APK is ready.

## Tech stack
Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, compileSdk 36.1, targetSdk 36, minSdk 26. Jetpack Compose +
Material3, MVVM, Navigation Compose, Room (KSP, **DB version 6 with destructive fallback — bump the version
when an entity changes**), DataStore Preferences, Coil, Moshi, OkHttp, coroutines. Release builds use R8;
keep rules in `app/proguard-rules.pro`.

## Code map (`app/src/main/java/com/printgo/app`)
- `screens/` — Home, Photos, Documents, WebPages, Preview (print settings sheet lives here), Printers,
  PrinterDetails, Jobs, JobDetails (shows Connection line + "First page as sent to the printer"),
  Settings, Onboarding, Help/About/Privacy (`InfoScreens.kt`).
- `navigation/` — `Routes.kt`, `PrintGoNavHost.kt` (onboarding gate; clearing selection on Home).
- `viewmodel/` — `PrinterViewModel` (discovery, connect, USB events), `PrintSettingsViewModel`
  (per-printer settings, clamping to capabilities, `onDocumentsChanged`), `SelectionViewModel`, etc.
- `model/` — `PrintSettings` (copies, page selection All/Odd/Even/Range, reverse order, pages per sheet,
  duplex, paper, orientation, scale incl. custom %, margins, alignment = print position, colour, quality),
  `PrinterCapabilities` (`defaultFor()` = permissive fallback for printers that cannot be asked).
- `data/layout/SheetLayoutEngine.kt` — turns documents + settings into sheets (reads real page sizes,
  per-sheet Auto orientation, clips pages to their cell) and exports the **master PDF** for a job.
- `data/converter/` — `DocumentConverter` (images/Office/text → PDF), `PdfPageRenderer`
  (`renderRegion`, `renderForPortraitPaper`).
- `data/print/PrintJobManager.kt` — the print engine: queue, Room-persisted jobs, retry/reprint/cancel,
  and per-connection sending. Encoders: `PwgRasterEncoder`, `PclEncoder` (+ `PostScriptEncoder`),
  `EscPosEncoder`; transports in `Transports.kt` (raw 9100, LPD 515, Bluetooth SPP, USB);
  `UsbSession`/`UsbAutoSetup`/`UsbIds` for USB; `PrintJobService` foreground service; notifications.
- `data/ipp/` — IPP encoder/decoder, `IppClient` (Get-Printer-Attributes with path fallbacks),
  `IppPrintClient` (probe, Print-Job, job status, cancel).
- `data/discovery/` — mDNS Wi-Fi discovery (`_ipp`, `_ipps`, `_pdl-datastream`, `_printer`), USB, Bluetooth.
- `data/prefs/AppPreferences.kt` — app settings incl. `dataFormat` (AUTO/PDF/PWG/JPEG for IPP) and
  `bluetoothLanguage` (raw printer language AUTO/ESCPOS/PCL/POSTSCRIPT/PDF for raw 9100/Bluetooth).
- Tests: `app/src/test/java/com/printgo/app/` (`PrintEngineTest.kt`, `SheetLayoutEngineTest.kt`, …).

## How a job is printed (keep these rules)
1. The Preview screen enqueues a job; `SheetLayoutEngine` builds one master PDF per job (kept in
   `filesDir/jobs/<id>.pdf` for retry/reprint).
2. **Wi-Fi IPP**: probe the printer live, pick a format it lists. AUTO order: PWG raster → PCL →
   PostScript → PDF → JPEG (PCL/PostScript are rendered here for older office printers). Fall through to
   the next format on 0x040A/0x040B/0x0400. JPEG is sent with `print-scaling=fit`; raster sends none.
   PCL/PostScript over IPP send `copies=1` because copies are inside the data.
3. **Wi-Fi raw (port 9100)**: first try IPP on 631; otherwise send raw in the chosen language (PCL for
   known office brands, else the Settings choice). **Never guess raw PDF.**
4. **Port 515 is LPD, not raw.** Printers announce both `_printer._tcp` (515) and `_pdl-datastream._tcp`
   (9100). Use 9100 when open; only speak real LPD (`Transports.sendLpd`, queue "raw") when 9100 is closed.
   Sending raw data to 515 caused "blank page with a few lines of junk" on the owner's HP.
5. **USB**: IPP-over-USB when the printer has the interface, else raw language from the IEEE-1284 device ID
   (CMD: PCL/POSTSCRIPT/PDF). Unknown printers are not guessed (strict) → offer the Play Store print service.
   USB printers are identified by vendor:product id (`usb_vvvv_pppp`), because Android's device id changes
   every time the cable is plugged in.
6. **PJL** is added only when the device ID lists PJL; otherwise it prints as text.
7. **PCL details**: send `ESC&l0E` (top margin 0) after orientation, skip the logical-page offset
   (71 dots @300 dpi for A sizes, 75 for US sizes), set raster width `ESC*r#S`.
8. Raster outputs (PCL, PostScript, PWG, JPEG) use `renderForPortraitPaper`: landscape sheets are turned
   90° anticlockwise so they fill portrait paper. ESC/POS is not rotated.
9. Printers that cannot be asked get `PrinterCapabilities.defaultFor()` — it must allow Legal/A5/A3 and
   two-sided, and must **never invent toner levels** (markerLevels empty).
10. Raw 9100 sockets close gracefully (shutdownOutput + drain) so the end of a job is not cut off.

## Verified on real hardware
- 2026-10-11: **HP LaserJet P2055dn over Wi-Fi → port 9100, PCL** prints correctly (owner confirmed).
- Not yet verified by the owner: two-sided, Legal, landscape, custom scale/position, USB, Bluetooth,
  other brands. Ask for the job's Connection line + a photo when a print comes out wrong.

## Known limitations (say so honestly; do not claim otherwise)
- Host-based/GDI printers (e.g. Canon LBP2900/LBP6000, HP LaserJet P1102/1020) need a computer driver and
  cannot be driven directly; use "Print with system dialog" + the maker's print-service plugin.
- No pause/resume; no printers that require a login; Office conversion is best-effort.
- Phone and printer must be on the same Wi-Fi (guest networks often block discovery).

## Working with the owner
- He tests on his own Android phone and printer and reports with screenshots/photos. Ship a fix, keep CI
  green, then tell him in a few plain sentences what changed and where to download
  (Releases → "PrintGo debug build", or Actions → newest green run → Artifacts → PrintGo-debug-apk).
- Add a unit test for every engine fix when it can be tested on the JVM.
- After a meaningful change, update this file (rules, verified hardware, limitations) and
  `docs/MASTER_PROMPT.md` if the restart instructions change.

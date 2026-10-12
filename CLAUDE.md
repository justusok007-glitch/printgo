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
  and per-connection sending (`runIppJob` = shared IPP format loop for Wi-Fi and USB). Encoders:
  `PwgRasterEncoder`, `UrfEncoder` (Apple/AirPrint raster), `EscprEncoder` (Epson ESC/P-R), `PclEncoder`
  (+ `PostScriptEncoder`), `EscPosEncoder`; shared row format in `RowSource.kt` (`RasterRows`);
  `IppFormatPlanner` (which format, dpi, grey/colour for a printer); `LanguagePicker` (+ `RawLanguage`) for
  raw printers; transports in `Transports.kt` (raw 9100, LPD 515, Bluetooth SPP, USB);
  `UsbSession`/`UsbAutoSetup`/`UsbIds` for USB; `PrintJobService` foreground service; notifications.
- `data/ipp/` — IPP encoder/decoder, `IppClient` (Get-Printer-Attributes with path fallbacks),
  `IppProbe` (what the printer supports), `IppPrintClient` (probe, Print-Job, job status, cancel).
- `data/discovery/` — mDNS Wi-Fi discovery (`_ipp`, `_ipps`, `_pdl-datastream`, `_printer`; prefers IPv4),
  `SnmpDeviceId` (IEEE-1284 device id over SNMP), USB, Bluetooth.
- `res/xml/network_security_config.xml` — **allows plain HTTP**. Without it Android blocks every IPP request
  (this was the reason only the P2055dn on raw 9100 worked until v1.1.0). Never remove it.
- `data/prefs/AppPreferences.kt` — app settings incl. `dataFormat` (AUTO/PDF/PWG/URF/JPEG for IPP) and
  `bluetoothLanguage` (raw printer language AUTO/ESCPOS/PCL/ESCPR/POSTSCRIPT/PDF for raw 9100/Bluetooth).
- Tests: `app/src/test/java/com/printgo/app/` (`PrintEngineTest.kt`, `PrinterCompatibilityTest.kt`,
  `SheetLayoutEngineTest.kt`, …). Pure engine files (encoders, planner, picker, SNMP, IPP model) can be
  compiled locally with the Kotlin compiler jar inside `/opt/gradle-*/lib` plus a tiny `android.graphics.Bitmap`
  stub — handy for quick checks before waiting for CI.

## How a job is printed (keep these rules)
1. The Preview screen enqueues a job; `SheetLayoutEngine` builds one master PDF per job (kept in
   `filesDir/jobs/<id>.pdf` for retry/reprint).
2. **Wi-Fi IPP**: probe the printer live (`IppProbe`), `IppFormatPlanner` picks formats it lists. AUTO order:
   PWG raster → AirPrint raster (URF) → PCL → PostScript → PDF → JPEG → ESC/P-R. PWG/URF use the printer's
   own resolution (closest to 300 dpi; HP lasers often only 600) and its colour type (sgray_8/srgb_8, W8/SRGB24),
   rendered in bands (`PdfPageRenderer.openRows`) at exactly the paper size. Job options (sides, colour mode,
   quality, scaling, media) are sent **only if the printer lists them**. On 0x040B/0x0400/HTTP 400/cut-off
   upload: retry once with `minimal=true` (format + copies only), then the next format; 0x040A → next format.
   JPEG is sent with `print-scaling=fit`; raster sends none. PCL/PostScript/ESC-P-R send `copies=1`.
   If IPP does not answer at all but port 9100 is open and the language is known → raw (`sendRaw`).
3. **Wi-Fi raw (port 9100)**: language from Settings, else SNMP device id, else mDNS `pdl`, else name
   (`LanguagePicker`). PCL/PostScript printers are sent raw directly (verified route); Epson/unknown are
   tried over IPP first, then raw. Inkjet brands are never guessed as PCL. **Never guess raw PDF.**
4. **Port 515 is LPD, not raw.** Printers announce both `_printer._tcp` (515) and `_pdl-datastream._tcp`
   (9100). Use 9100 when open; only speak real LPD (`Transports.sendLpd`, queue "raw") when 9100 is closed.
   Sending raw data to 515 caused "blank page with a few lines of junk" on the owner's HP.
5. **USB**: IPP-over-USB when the printer has the interface (same format loop as Wi-Fi); if it cannot be
   asked or refuses everything, the classic printer interface with the raw language from the IEEE-1284
   device ID (CMD order: ESCPR → PCL (PCL/PCL5/PCL3 only, not PCLXL/PCL3GUI/PCLm) → PostScript → PDF).
   No device id → USB descriptor names (Epson → ESC/P-R, thermal). Otherwise not guessed (strict) → offer
   the Play Store print service. USB writes advance by the bytes actually sent and wait up to 2 min.
   USB printers are identified by vendor:product id (`usb_vvvv_pppp`), because Android's device id changes
   every time the cable is plugged in.
6. **PJL** is added only when the device ID lists PJL; otherwise it prints as text.
7. **PCL details**: send `ESC&l0E` (top margin 0) after orientation, skip the logical-page offset
   (71 dots @300 dpi for A sizes, 75 for US sizes), set raster width `ESC*r#S`.
8. Raster outputs turn landscape sheets 90° anticlockwise so they fill portrait paper: PCL, PostScript and
   JPEG via `renderForPortraitPaper`; PWG, URF and ESC/P-R via the banded `openRows` (same rotation).
   ESC/POS is not rotated.
9. Printers that cannot be asked get `PrinterCapabilities.defaultFor()` — it must allow Legal/A5/A3 and
   two-sided, and must **never invent toner levels** (markerLevels empty).
10. Raw 9100 sockets close gracefully (shutdownOutput + drain) so the end of a job is not cut off.
11. **ESC/P-R** (Epson inkjets): 360 dpi, 3 mm margins, RGB lines RLE-compressed (0..127 literal n+1,
    129..255 repeat 257-n — the opposite of PWG). Sequence: exit packet mode, ESC @, REMOTE1 (TI, JS, JH, HD,
    PP), exit remote, ESC(R ESCPR, setq, setj, per page sttp/setn/dsnd…/endp, endj, ESC @, REMOTE1 LD JE.
    Based on Epson's open-source library and the MIT python-epson encoder; **not yet tested on a real Epson**.

## Verified on real hardware
- 2026-10-11: **HP LaserJet P2055dn over Wi-Fi → port 9100, PCL** prints correctly (owner confirmed).
- 2026-10-12 (v1.1.0): owner reported Epson L455, HP LaserJet Pro M404/M405 and HP LaserJet P3010 did not
  print over Wi-Fi or USB. Fixed in code (plain-HTTP block, URF, ESC/P-R, option filtering, USB writes);
  **waiting for the owner to test** — ask for each printer's Connection line.
- 2026-10-12 (v1.1.1): owner confirmed v1.1.0 prints on Epson L455, M404/M405 and P3010; two-sided works over
  USB (M404). Over Wi-Fi the P3010 showed "Long edge" unselectable because its IPP answer named no two-sided
  mode. v1.1.1: when a printer lists **no** two-sided mode (`twoSidedReported()` false) the choice is offered
  with a hint and still sent (IPP `sides`, raster duplex flag); printers that do list modes are still respected.
- Not yet verified by the owner: two-sided, Legal, landscape, custom scale/position, USB, Bluetooth,
  other brands. Ask for the job's Connection line + a photo when a print comes out wrong.

## Known limitations (say so honestly; do not claim otherwise)
- Two-sided raster (PWG/URF) does not yet turn the back sides for printers that need "flipped"/"rotated" backs.
- PCL output is black-and-white (1-bit); colour lasers fed raw PCL print in grey.
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

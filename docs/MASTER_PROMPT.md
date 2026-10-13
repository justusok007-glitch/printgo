# PrintGo — master prompt (copy everything below the line into a new chat)

How to use: copy the text below the line, fill in the last section ("What I want this time"), and send it
together with the repo name (and the project ZIP if you have one). Attach screenshots or photos of any
problem.

---

I am Saqib Khalil. I want you to continue developing my Android printing app **PrintGo**.

**Where the code is**
- GitHub repo: **justusok007-glitch/printgo** (branch `main`). Please attach/clone it and work there.
  If you cannot reach the repo, use the ZIP file I attached instead and tell me how to upload the result.
- First read **`CLAUDE.md`** in the repo root. It has the full project guide: how the app is built, the
  code map, the printing rules, the bugs already fixed, and what has been tested. Follow it.

**What the app is**
- A NokoPrint-style printing app: Kotlin + Jetpack Compose (Material3), MVVM, Room, package
  `com.printgo.app`, minSdk 26, targetSdk 36.
- It prints photos, documents (PDF, Word, Excel, text) and web pages to Wi-Fi printers (IPP with PWG raster,
  AirPrint raster, PCL, PostScript, PDF or JPEG; raw port 9100; LPD), USB printers (IPP-over-USB, PCL,
  PostScript, PDF, Epson ESC/P-R), Bluetooth printers, and can save as PDF.
- Built-in printer drivers (since v1.2.0): Epson ESC/P-R, Gutenprint, HPLIP (hpcups) and SpliX, compiled
  from source on GitHub Actions (`native/`), with a "Printer driver" picker (Printers → ⓘ) and downloadable
  data packs (release "driver-packs"). PrintGo is free software under GPL-3.0-or-later.
- Scanner (since v1.3.0): "Scan before Print" for gallery photos, "Scan document" with the camera, crop
  (free, paper shape, 4-corner straightening with automatic page detection, turn), filters (Original, Vibrant,
  Carbon Black, Soft Tone, Clean Colour) and multi-page scan to one PDF (print or save/share). Pure Kotlin,
  no ML Kit or OpenCV.
- Features already built: printer discovery and saving, USB auto-detect, print preview, copies,
  all/odd/even/range pages, reverse order, pages per sheet, 2-sided, paper sizes (A4, A5, Letter, Legal,
  4x6), orientation, fit/fill/actual/custom scale, print position, margins, colour/greyscale, print jobs
  and history (cancel, retry, print again), settings, help, onboarding.

**Rules you must keep**
- Bright light theme only, PrintGo blue/green design. No dark mode. No ads. No bottom slogan. No "Tip" line.
- The home screen footer must stay: **"Created by : Muhammad Saad Awan"** with an envelope icon and
  **saadawanok@gmail.com**.
- Builds run on **GitHub Actions** ("Build PrintGo APK" workflow: native drivers → APK, unit tests).
  After your changes, push to `main`, make sure the build, the driver smoke test and the unit tests pass
  (fix any errors from the `ci-logs-*` branches, e.g. `ci-logs-native`), then tell me.
- Keep the licence: PrintGo is GPL-3.0-or-later; keep `LICENSE`, `THIRD_PARTY_NOTICES.md` and the source link
  in About. The repository must be public for the GPL source offer and for the driver-pack downloads.
- Do not reintroduce fixed printer bugs. Never send raw PDF to an unknown printer, and never send raw
  data to port 515. The full list is in `CLAUDE.md`.
- Be honest about what you could not test. I test on my own phone with an HP LaserJet P2055dn (Wi-Fi),
  and also Epson L455, HP LaserJet Pro M404/M405 and HP LaserJet P3010 (Wi-Fi and USB).

**How I get the new APK**
- On my phone: GitHub repo → Releases → "PrintGo debug build" → PrintGo-debug.apk.
- Or: Actions → newest green "Build PrintGo APK" run → Artifacts → PrintGo-debug-apk (unzip, install).

**When you finish**
- Tell me in short, simple English what changed and what I should test.
- Update `CLAUDE.md` (and this file if needed) so the next update starts from the right place.

**What I want this time**
1. (write your first change or problem here)
2. (second change)
3. (attach screenshots/photos; for a wrong print, include the job's "Connection" line from Job details)

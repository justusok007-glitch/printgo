# Third-party software in PrintGo

PrintGo itself is free software under the GNU General Public License, version 3 or (at your option) any
later version. See [LICENSE](LICENSE). The complete source code, including the build scripts for the
printer drivers below, is at https://github.com/justusok007-glitch/printgo.

## Android libraries (Apache License 2.0)
AndroidX (Activity, Core, Compose UI/Material 3/Icons, Lifecycle, Navigation, Room, DataStore),
Kotlin standard library and kotlinx.coroutines, OkHttp, Moshi, Coil.
Apache 2.0 is compatible with GPL version 3.

## Printer drivers (built from source by `native/` on GitHub Actions)
The exact download address, version and SHA-256 of every source package are listed in
[`native/sources.json`](native/sources.json). Unmodified copies of these packages are kept in the GitHub release
**driver-sources** of this repository; together with the build scripts in `native/` and the patches in
`native/patches/` they are the complete corresponding source of the driver programs in the app.
Each driver keeps its own copyright notices and licence.

| Component | Covers | Licence |
| --- | --- | --- |
| Epson Inkjet Printer Driver (ESC/P-R) for Linux 1.8.8, Seiko Epson Corporation | Epson inkjet and ink-tank printers | GPL-2.0-or-later (see its COPYING and README) |
| Gutenprint 5.3.5 | Many Epson, Canon, HP, Lexmark and other printers | GPL-2.0-or-later |
| HPLIP 3.25.8 (hpcups), HP Development Company | HP inkjet and LaserJet printers | BSD-3-Clause (hpcups files); HPLIP as a whole: GPL-2.0, MIT, BSD |
| SpliX 2.0.1, Aurélien Croc | Samsung, Xerox and Dell lasers (SPL/QPDL) | GPL-2.0-only |
| jbigkit 2.1, Markus Kuhn | JBIG compression for SpliX colour models | GPL-2.0-or-later |
| libcups from CUPS 2.4.16, Apple Inc. / OpenPrinting | Raster and PPD support for HPLIP, SpliX and pg_cupsraster | Apache-2.0 with exceptions for GPL-2.0/LGPL-2.0 software |
| libjpeg-turbo 3.1.0 | JPEG compression for some HP printers | IJG and BSD-3-Clause |

PrintGo's own driver front-ends (`native/src`: pg_gutenprint, pg_cupsraster) are GPL-3.0-or-later.

Each driver runs as its own program (`lib/<abi>/libpg_*.so` inside the APK), started by PrintGo for a print
job. HPLIP's closed-source plug-in is **not** included, so HP printers that need it (for example the LaserJet
P1102 and other "ZjStream" models) are left out.

## Driver data packs
The packs PrintGo downloads (release **driver-packs**) contain only printer descriptions taken unchanged from
the packages above: Gutenprint's XML data, HPLIP's hpcups PPD files and SpliX's PPD files. They are made by
`native/tools/make_packs.py` and carry the licences of their packages.

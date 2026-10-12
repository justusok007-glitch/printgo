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
[`native/sources.lock`](native/sources.lock). Each driver keeps its own copyright notices and licence;
its unmodified source package is the "Corresponding Source" together with the patches in `native/patches/`.

| Driver | Covers | Licence |
| --- | --- | --- |
| Epson Inkjet Printer Driver (ESC/P-R) for Linux, Seiko Epson | Epson inkjet and ink-tank printers | see `native/sources.lock` notes |
| Gutenprint | Many Epson, Canon, HP, Lexmark and other inkjet/laser printers | GPL-2.0-or-later |
| HPLIP (hpcups), HP Development Company | HP inkjet and LaserJet printers | see `native/sources.lock` notes |
| SpliX | Samsung, Xerox and Dell lasers (SPL/QPDL) | GPL-2.0 |
| libcups (CUPS), Apple Inc. / OpenPrinting | Raster and PPD support for HPLIP and SpliX | Apache-2.0 (with GPL-2.0 linking exception) |

Drivers that run as separate programs (hpcups, rastertoqpdl) are separate works shipped alongside PrintGo.

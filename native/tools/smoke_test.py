#!/usr/bin/env python3
"""Runs every driver program once on a small test page and checks that the output looks like the
printer's own language. Uses the host build (native/build.sh host OUT) and the unpacked sources.

Usage: smoke_test.py OUT        exit code 0 = all drivers produced plausible printer data

Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
"""
import glob, gzip, os, struct, subprocess, sys, tempfile

OUT = os.path.abspath(sys.argv[1])
WORK = os.environ.get("PG_WORK", os.path.join(OUT, "work"))
SRC = os.path.join(WORK, "src")
BIN = os.path.join(OUT, "host", "bin")
GP_XML = os.path.join(OUT, "host", "gutenprint-xml")
TMP = tempfile.mkdtemp(prefix="pg-smoke-")
failures = []


def pgr(width, height, dpi, channels, pages):
    """PrintGo raster stream (native/src/pgr.h): black lines every half inch, a red block on colour pages."""
    white, black = b"\xff" * (width * channels), b"\x00" * (width * channels)
    red = bytearray(white)
    if channels == 3:
        for x in range(width // 4, width // 2):
            red[3 * x:3 * x + 3] = b"\xff\x00\x00"
    out = [b"PGR1"]
    for p in range(pages):
        out.append(b"PAGE" + struct.pack("<6I", width, height, dpi, dpi, channels, p % 2))
        rows = []
        for y in range(height):
            if y % (dpi // 2) == 0:
                rows.append(black)
            elif channels == 3 and dpi // 3 < y < dpi:
                rows.append(bytes(red))
            else:
                rows.append(white)
        out.append(b"".join(rows))
    out.append(b"DONE")
    return b"".join(out)


def run(name, args, data, env=None, expect=None):
    e = dict(os.environ)
    e.update(env or {})
    r = subprocess.run(args, input=data, capture_output=True, env=e, timeout=600)
    out = r.stdout
    ok = r.returncode == 0 and len(out) > 200 and all(m in out for m in (expect or []))
    print(f"{'ok  ' if ok else 'FAIL'} {name}: exit {r.returncode}, {len(out)} bytes")
    if not ok:
        print("     stderr:", r.stderr.decode("utf-8", "replace")[-1500:])
        print("     head:", out[:80])
        failures.append(name)
    return out


# A4 at 150 dpi keeps the test quick; the drivers scale to their own resolution.
A4_150 = (1240, 1754)

# ---- Epson ESC/P-R: the filter's own input (one byte = pages in first batch, per page one byte + rows) ----
w, h = 2976, 4209  # A4 at 360 dpi
mono_page = b"".join((b"\x00" * w) if y % 180 == 0 else (b"\xff" * w) for y in range(h))
escpr_in = bytes([2]) + bytes([1]) + mono_page + bytes([0]) + mono_page
run("escpr A4 mono 2 pages", [os.path.join(BIN, "pg_escpr"), "PrintGo", str(w), str(h), "360", "MONO", "A4",
                              "PLAIN_NORMAL", "None", "Auto", "0", "0", "0", "Off"], escpr_in,
    expect=[b"ESCPR", b"dsnd", b"endj"])

# ---- Gutenprint ------------------------------------------------------------------------------------
gp_env = {"STP_DATA_PATH": GP_XML}
grey2 = pgr(*A4_150, 150, 1, 2)
colour1 = pgr(*A4_150, 150, 3, 1)
run("gutenprint Epson XP-240 (escp2)", [os.path.join(BIN, "pg_gutenprint"), "escp2-xp240", "PaperPt=595x842",
                                        "PrintingMode=Color", "Quality=Standard"], colour1, gp_env, expect=[b"\x1b@"])
run("gutenprint Canon PIXMA iP2700", [os.path.join(BIN, "pg_gutenprint"), "bjc-PIXMA-iP2700", "PaperPt=595x842",
                                      "PrintingMode=Color"], colour1, gp_env, expect=[b"\x1b[K"])
run("gutenprint HP LaserJet 4 (PCL) two-sided", [os.path.join(BIN, "pg_gutenprint"), "pcl-4", "PaperPt=595x842",
                                                 "PrintingMode=BW", "Duplex=DuplexNoTumble"], grey2, gp_env,
    expect=[b"\x1bE", b"\x1b*r"])
listing = subprocess.run([os.path.join(BIN, "pg_gutenprint"), "--list"], capture_output=True, env=dict(os.environ, **gp_env))
n = len(listing.stdout.splitlines())
print(f"{'ok  ' if n > 2000 else 'FAIL'} gutenprint --list: {n} models")
if n <= 2000:
    failures.append("gutenprint --list")

# ---- HPLIP hpcups through pg_cupsraster --------------------------------------------------------------
p3010 = os.path.join(SRC, "hplip", "ppd", "hpcups", "hp-laserjet_p3010_series-pcl3.ppd.gz")
run("hpcups HP LaserJet P3010 two-sided", [os.path.join(BIN, "pg_cupsraster"), os.path.join(BIN, "pg_hpcups"), p3010,
                                           "PaperPt=595x842 PGColor=gray PGQuality=normal Duplex=DuplexNoTumble", "Smoke"],
    grey2, expect=[b"\x1b%-12345X", b"\x1b*r"])
q = subprocess.run([os.path.join(BIN, "pg_cupsraster"), "--query", p3010, "PaperPt=595x842 Duplex=DuplexNoTumble"],
                   capture_output=True, text=True)
ok = q.returncode == 0 and "hwres=600x600" in q.stdout and "duplex=1" in q.stdout and "backside=" in q.stdout
print(f"{'ok  ' if ok else 'FAIL'} hpcups P3010 query: {q.stdout.split()}")
if not ok:
    failures.append("hpcups query")
rotated = None
for f in sorted(glob.glob(os.path.join(SRC, "hplip", "ppd", "hpcups", "*.ppd.gz"))):
    if b'*cupsBackSide: "Rotated"' in gzip.decompress(open(f, "rb").read()):
        rotated = f
        break
if rotated:
    q = subprocess.run([os.path.join(BIN, "pg_cupsraster"), "--query", rotated, "PaperPt=595x842 Duplex=DuplexNoTumble"],
                       capture_output=True, text=True)
    ok = "backside=rotate" in q.stdout
    print(f"{'ok  ' if ok else 'FAIL'} back side of {os.path.basename(rotated)}: {'rotate' if ok else q.stdout}")
    if not ok:
        failures.append("backside query")

# ---- SpliX through pg_cupsraster ----------------------------------------------------------------------
for ppd, colour in (("ml1640.ppd", "gray"), ("clp310.ppd", "color")):
    path = os.path.join(SRC, "splix", "ppd", ppd)
    if not os.path.exists(path):
        print(f"FAIL splix {ppd}: PPD missing")
        failures.append(ppd)
        continue
    data = colour1 if colour == "color" else grey2
    run(f"splix {ppd} ({colour})", [os.path.join(BIN, "pg_cupsraster"), os.path.join(BIN, "pg_splix"), path,
                                    f"PaperPt=595x842 PGColor={colour}", "Smoke"], data, expect=[b"@PJL"])

if failures:
    print("smoke test failed:", ", ".join(failures))
    sys.exit(1)
print("all drivers produced printer data")

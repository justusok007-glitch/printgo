#!/usr/bin/env python3
"""Makes PrintGo's driver catalog (shipped in the app) and the downloadable driver data packs.

Usage: make_packs.py OUT     (after native/build.sh host OUT)

Writes:
  OUT/assets/drivers/catalog.json   every printer model a built-in driver supports, for matching and the picker
  OUT/assets/drivers/packs.json     the data packs: file name, size, sha256, download address
  OUT/packs/<pack>-<version>-<sha12>.zip   the data packs themselves (published to the release "driver-packs")

The packs contain only data (Gutenprint's printer XML, PPD files). All program code ships inside the APK.
Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
"""
import glob, gzip, hashlib, io, json, os, re, subprocess, sys, zipfile

OUT = os.path.abspath(sys.argv[1])
WORK = os.environ.get("PG_WORK", os.path.join(OUT, "work"))
SRC = os.path.join(WORK, "src")
DL = os.environ.get("PG_DL", os.path.join(WORK, "dl"))
REPO = os.environ.get("GITHUB_REPOSITORY", "justusok007-glitch/printgo")
BASE_URL = f"https://github.com/{REPO}/releases/download/driver-packs/"

# HP printer languages hpcups cannot drive without HP's closed-source plug-in.
HP_PLUGIN_ONLY = {"ljzjstream", "ljzxstream"}

found = json.load(open(os.path.join(DL, "found.json")))


def version(name):
    f = found[name]["file"]
    m = re.search(r"[-_](\d+(?:\.\d+)+)", f)
    return m.group(1) if m else "0"


def det_zip(entries, compress=True):
    """Deterministic zip: sorted names, fixed dates, so an unchanged pack keeps the same sha256."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name, data in sorted(entries):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED if compress else zipfile.ZIP_STORED
            info.external_attr = 0o644 << 16
            z.writestr(info, data, compresslevel=9 if compress else None)
    return buf.getvalue()


def write_pack(name, entries, compress=True):
    data = det_zip(entries, compress)
    digest = hashlib.sha256(data).hexdigest()
    fname = f"{name}-{version_of[name]}-{digest[:12]}.zip"
    os.makedirs(os.path.join(OUT, "packs"), exist_ok=True)
    open(os.path.join(OUT, "packs", fname), "wb").write(data)
    return {"file": fname, "size": len(data), "sha256": digest, "version": version_of[name], "files": len(entries)}


def ppd_attrs(text, keys):
    out = {}
    for k in keys:
        m = re.search(r"^\*" + re.escape(k) + r":\s*\"([^\"]*)\"", text, re.M)
        if m:
            out[k] = m.group(1).strip()
    return out


def has_duplex(text):
    return bool(re.search(r"^\*Duplex DuplexNoTumble", text, re.M))


def color_capable(text):
    for m in re.finditer(r"^\*ColorModel [^:]*:\s*\"([^\"]*)\"", text, re.M):
        cs = re.search(r"cupsColorSpace\s+(\d+)", m.group(1))
        if cs and int(cs.group(1)) not in (0, 3, 18):
            return True
    return bool(re.search(r"^\*ColorDevice:\s*True", text, re.M))


def make_of(model, fallback):
    first = model.split()[0] if model else fallback
    return {"hp": "HP", "epson": "Epson", "samsung": "Samsung", "xerox": "Xerox", "dell": "Dell",
            "canon": "Canon", "lexmark": "Lexmark", "brother": "Brother"}.get(first.lower(), fallback)


version_of = {"gutenprint": version("gutenprint"), "hplip": version("hplip"), "splix": version("splix")}
drivers = []

# ---- Epson ESC/P-R: built in, no pack ---------------------------------------------------------------
for path in sorted(glob.glob(os.path.join(SRC, "escpr", "ppd", "*.ppd"))):
    t = open(path, encoding="latin-1").read()
    a = ppd_attrs(t, ["ModelName", "1284DeviceID"])
    if "ModelName" not in a:
        continue
    base = os.path.basename(path).replace("-epson-escpr-en.ppd", "")
    drivers.append({"i": "escpr:" + base, "k": "escpr", "m": "Epson", "n": a["ModelName"], "d": a.get("1284DeviceID", ""),
                    "c": 1 if re.search(r"^\*Ink COLOR", t, re.M) else 0, "x": 1 if has_duplex(t) else 0})

# ---- Gutenprint ---------------------------------------------------------------------------------------
gp_xml = os.path.join(OUT, "host", "gutenprint-xml")
env = dict(os.environ, STP_DATA_PATH=gp_xml)
listing = subprocess.run([os.path.join(OUT, "host", "bin", "pg_gutenprint"), "--list"], capture_output=True, text=True,
                         env=env, check=True).stdout
for line in listing.splitlines():
    f = line.split("\t")
    if len(f) < 7:
        continue
    driver, make, model, family, devid, color, duplex = f[:7]
    drivers.append({"i": "gp:" + driver, "k": "gutenprint", "m": make, "n": model, "d": devid, "c": int(color),
                    "x": int(duplex), "f": driver, "g": family})
gp_entries = []
for path in sorted(glob.glob(os.path.join(gp_xml, "**", "*"), recursive=True)):
    if os.path.isfile(path):
        gp_entries.append(("xml/" + os.path.relpath(path, gp_xml), open(path, "rb").read()))

# ---- HPLIP hpcups -------------------------------------------------------------------------------------
hp_entries = []
for path in sorted(glob.glob(os.path.join(SRC, "hplip", "ppd", "hpcups", "*.ppd.gz"))):
    raw = open(path, "rb").read()
    t = gzip.decompress(raw).decode("latin-1")
    a = ppd_attrs(t, ["ModelName", "1284DeviceID", "hpPrinterLanguage", "NickName"])
    lang = a.get("hpPrinterLanguage", "")
    if not a.get("ModelName") or lang in HP_PLUGIN_ONLY:
        continue
    name = os.path.basename(path)
    hp_entries.append(("ppd/" + name, raw))
    drivers.append({"i": "hp:" + name[:-7], "k": "hpcups", "m": "HP", "n": a["ModelName"], "d": a.get("1284DeviceID", ""),
                    "c": 1 if color_capable(t) else 0, "x": 1 if has_duplex(t) else 0, "f": "ppd/" + name, "l": lang})

# ---- SpliX --------------------------------------------------------------------------------------------
sx_entries = []
sx_paths = sorted(glob.glob(os.path.join(SRC, "splix", "ppd", "*.ppd")))
names = {os.path.basename(p) for p in sx_paths}
for path in sx_paths:
    name = os.path.basename(path)
    # Skip the French/Portuguese copies of the same model.
    if re.search(r"(fr|pt)\.ppd$", name) and re.sub(r"(fr|pt)\.ppd$", ".ppd", name) in names:
        continue
    t = open(path, encoding="latin-1").read()
    a = ppd_attrs(t, ["ModelName", "1284DeviceID"])
    if not a.get("ModelName"):
        continue
    raw = gzip.compress(t.encode("latin-1"), mtime=0)
    sx_entries.append(("ppd/" + name + ".gz", raw))
    drivers.append({"i": "sx:" + name[:-4], "k": "splix", "m": make_of(a["ModelName"], "Samsung"), "n": a["ModelName"],
                    "d": a.get("1284DeviceID", ""), "c": 1 if color_capable(t) else 0, "x": 1 if has_duplex(t) else 0,
                    "f": "ppd/" + name + ".gz"})

packs = {
    "gutenprint": write_pack("gutenprint", gp_entries),
    "hplip": write_pack("hplip", hp_entries, compress=False),
    "splix": write_pack("splix", sx_entries, compress=False),
}
for p in packs.values():
    p["url"] = BASE_URL + p["file"]

assets = os.path.join(OUT, "assets", "drivers")
os.makedirs(assets, exist_ok=True)
json.dump({"version": 1, "drivers": drivers}, open(os.path.join(assets, "catalog.json"), "w"), separators=(",", ":"))
json.dump({"version": 1, "baseUrl": BASE_URL, "packs": packs, "sources": {k: {"file": v["file"], "sha256": v["sha256"]}
                                                                          for k, v in found.items()}},
          open(os.path.join(assets, "packs.json"), "w"), indent=2)
counts = {}
for d in drivers:
    counts[d["k"]] = counts.get(d["k"], 0) + 1
print("catalog:", counts, "total", len(drivers))
for k, p in packs.items():
    print(f"pack {k}: {p['file']} {p['size']} bytes, {p['files']} files")

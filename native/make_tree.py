#!/usr/bin/env python3
"""Extracts the downloaded source packages into a readable tree (for development), trimmed of
documentation, images, translations and very large files. Usage: make_tree.py SRC_DIR OUT_DIR"""
import json, os, shutil, subprocess, sys

src, out = sys.argv[1], sys.argv[2]
found = json.load(open(os.path.join(src, "found.json")))
DROP_DIRS = {"doc", "docs", "po", "images", "ui", "ui4", "ui5", "installer", "scan", "fax", "pcard", "copier",
             "localization", "locale", "man", "examples", "web", "templates", "desktop", "debian"}
DROP_EXT = (".png", ".jpg", ".jpeg", ".gif", ".pdf", ".ico", ".ttf", ".bmp", ".tif", ".tiff", ".svg", ".pyc", ".mo")
KEEP_BIG = ("models.dat", "printers.xml", "configure.ac", "Makefile.am")
MAX = 400 * 1024

os.makedirs(out, exist_ok=True)
for name, info in found.items():
    dest = os.path.join(out, name)
    shutil.rmtree(dest, ignore_errors=True)
    os.makedirs(dest)
    subprocess.check_call(["tar", "-xf", os.path.join(src, info["file"]), "-C", dest, "--strip-components=1"])
    listing = []
    for root, dirs, files in os.walk(dest):
        for f in files:
            p = os.path.join(root, f)
            listing.append(f"{os.path.getsize(p):>10} {os.path.relpath(p, dest)}")
    with open(os.path.join(out, f"{name}.FILES.txt"), "w") as fh:
        fh.write("\n".join(sorted(listing, key=lambda l: l[11:])) + "\n")
    for root, dirs, files in os.walk(dest, topdown=True):
        for d in list(dirs):
            if d.lower() in DROP_DIRS:
                shutil.rmtree(os.path.join(root, d))
                dirs.remove(d)
        is_ppd_dir = "ppd" in os.path.basename(root).lower()
        kept_ppd = 0
        for f in sorted(files):
            p = os.path.join(root, f)
            if os.path.islink(p):
                os.remove(p); continue
            big = os.path.getsize(p) > MAX and not f.endswith(KEEP_BIG)
            ppd = is_ppd_dir and (f.endswith(".ppd") or f.endswith(".ppd.gz"))
            if ppd:
                kept_ppd += 1
            if big or f.lower().endswith(DROP_EXT) or (ppd and kept_ppd > 6):
                os.remove(p)
json.dump(found, open(os.path.join(out, "found.json"), "w"), indent=2)

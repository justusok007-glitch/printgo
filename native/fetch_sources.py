#!/usr/bin/env python3
"""Downloads the driver source packages listed in sources.json into DIR.

Usage: fetch_sources.py DIR [--only name,name]

For each package: if 'url' and 'sha256' are pinned, download that and verify. Otherwise try the
candidate URLs, then the newest *.orig.tar.* of the Debian source package. Writes DIR/found.json with
the URL, file name and sha256 actually used, so they can be pinned in sources.json.
"""
import hashlib, json, os, re, subprocess, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
UA = {"User-Agent": "PrintGo-driver-build (+https://github.com/justusok007-glitch/printgo)"}


def get(url, dest=None, tries=4):
    last = None
    for _ in range(tries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
            if dest:
                with open(dest, "wb") as f:
                    f.write(data)
            return data
        except Exception as e:  # noqa
            last = e
    raise last


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def debian_latest(src):
    base = f"https://deb.debian.org/debian/pool/main/{src[0] if not src.startswith('lib') else src[:4]}/{src}/"
    html = get(base).decode("utf-8", "replace")
    names = sorted(set(re.findall(r'href="([^"]+\.orig\.tar\.(?:gz|xz|bz2))"', html)))
    if not names:
        raise RuntimeError(f"no orig tarball in {base}")
    vers = {n: n.split("_", 1)[1].rsplit(".orig", 1)[0] for n in names}
    out = subprocess.run(["sort", "-V"], input="\n".join(vers.values()), capture_output=True, text=True).stdout.split()
    newest = out[-1]
    name = [n for n, v in vers.items() if v == newest][0]
    return base + name


def main():
    dest = sys.argv[1]
    only = None
    if "--only" in sys.argv:
        only = set(sys.argv[sys.argv.index("--only") + 1].split(","))
    os.makedirs(dest, exist_ok=True)
    cfg = json.load(open(os.path.join(HERE, "sources.json")))
    found = {}
    for p in cfg["packages"]:
        if only and p["name"] not in only:
            continue
        urls = ([p["url"]] if p.get("url") else []) + p.get("candidates", [])
        got = None
        for u in urls:
            fn = os.path.join(dest, u.rstrip("/").split("/")[-1].split("?")[0])
            try:
                get(u, fn)
                got = (u, fn)
                break
            except Exception as e:
                print(f"  {p['name']}: {u} failed: {e}", file=sys.stderr)
        if not got and p.get("debian"):
            u = debian_latest(p["debian"])
            fn = os.path.join(dest, u.split("/")[-1])
            get(u, fn)
            got = (u, fn)
        if not got:
            raise SystemExit(f"could not download {p['name']}")
        digest = sha256(got[1])
        if p.get("sha256") and p["sha256"] != digest:
            raise SystemExit(f"{p['name']}: sha256 mismatch {digest} != {p['sha256']}")
        found[p["name"]] = {"url": got[0], "file": os.path.basename(got[1]), "sha256": digest,
                            "size": os.path.getsize(got[1])}
        print(f"{p['name']}: {got[0]} sha256={digest}")
    json.dump(found, open(os.path.join(dest, "found.json"), "w"), indent=2)


if __name__ == "__main__":
    main()

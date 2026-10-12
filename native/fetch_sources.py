#!/usr/bin/env python3
"""Downloads the driver source packages listed in sources.json into DIR.

Usage: fetch_sources.py DIR [--only name,name]

For each package the exact copy kept in this repository's GitHub release "driver-sources" is used
first (needs GITHUB_REPOSITORY and GH_TOKEN, as on GitHub Actions); otherwise the pinned upstream
'url', then the newest *.orig.tar.* of the Debian source package. A file already in DIR with the
right sha256 is reused. Every pinned sha256 is checked. Writes DIR/found.json.
"""
import hashlib, json, os, re, subprocess, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
UA = {"User-Agent": "PrintGo-driver-build (+https://github.com/justusok007-glitch/printgo)"}


def get(url, dest=None, headers=None, tries=4):
    last = None
    for _ in range(tries):
        try:
            req = urllib.request.Request(url, headers={**UA, **(headers or {})})
            with urllib.request.urlopen(req, timeout=180) as r:
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


def release_assets():
    repo, token = os.environ.get("GITHUB_REPOSITORY"), os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not repo or not token:
        return {}, None
    try:
        data = get(f"https://api.github.com/repos/{repo}/releases/tags/driver-sources",
                   headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}, tries=2)
        assets = {a["name"]: a["url"] for a in json.loads(data)["assets"]}
        return assets, token
    except Exception as e:  # noqa
        print(f"  release driver-sources not readable: {e}", file=sys.stderr)
        return {}, None


def debian_latest(src):
    base = f"https://deb.debian.org/debian/pool/main/{src[:4] if src.startswith('lib') else src[0]}/{src}/"
    html = get(base).decode("utf-8", "replace")
    names = sorted(set(re.findall(r'href="([^"]+\.orig\.tar\.(?:gz|xz|bz2))"', html)))
    if not names:
        raise RuntimeError(f"no orig tarball in {base}")
    vers = {n: n.split("_", 1)[1].rsplit(".orig", 1)[0] for n in names}
    out = subprocess.run(["sort", "-V"], input="\n".join(vers.values()), capture_output=True, text=True).stdout.split()
    name = [n for n, v in vers.items() if v == out[-1]][0]
    return base + name


def main():
    dest = sys.argv[1]
    only = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None
    os.makedirs(dest, exist_ok=True)
    cfg = json.load(open(os.path.join(HERE, "sources.json")))
    assets, token = release_assets()
    found = {}
    for p in cfg["packages"]:
        if only and p["name"] not in only:
            continue
        pinned = p.get("sha256") or ""
        fname = p.get("file") or p["url"].rstrip("/").split("/")[-1]
        path = os.path.join(dest, fname)
        got = None
        if os.path.exists(path) and pinned and sha256(path) == pinned:
            got = ("cache", path)
        if not got and fname in assets and token:
            try:
                get(assets[fname], path, headers={"Authorization": f"Bearer {token}", "Accept": "application/octet-stream"})
                got = ("release driver-sources", path)
            except Exception as e:  # noqa
                print(f"  {p['name']}: release copy failed: {e}", file=sys.stderr)
        if not got and p.get("url"):
            try:
                get(p["url"], path)
                got = (p["url"], path)
            except Exception as e:  # noqa
                print(f"  {p['name']}: {p['url']} failed: {e}", file=sys.stderr)
        if not got and p.get("debian"):
            u = debian_latest(p["debian"])
            path = os.path.join(dest, u.split("/")[-1])
            get(u, path)
            got = (u, path)
        if not got:
            raise SystemExit(f"could not download {p['name']}")
        digest = sha256(got[1])
        if pinned and pinned != digest:
            raise SystemExit(f"{p['name']}: sha256 mismatch {digest} != {pinned}")
        found[p["name"]] = {"url": p.get("url") if got[0] in ("cache", "release driver-sources") else got[0],
                            "from": got[0], "file": os.path.basename(got[1]), "sha256": digest,
                            "size": os.path.getsize(got[1])}
        print(f"{p['name']}: {got[0]} sha256={digest}")
    json.dump(found, open(os.path.join(dest, "found.json"), "w"), indent=2)


if __name__ == "__main__":
    main()

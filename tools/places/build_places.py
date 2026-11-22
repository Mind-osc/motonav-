#!/usr/bin/env python3
"""
Offline αναζήτηση: πόλεις, χωριά, οικισμοί, νησιά, κορυφές και διάσελα της Ελλάδας από το OpenStreetMap.
Γράφει app/src/main/assets/places/gr.tsv:  όνομα \t λατινικά \t lat \t lon \t είδος \t κοντινή πόλη
Τρέχει στο GitHub Actions (.github/workflows/places.yml).
"""
import json, math, os, sys, time, urllib.parse, urllib.request

SERVERS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]
UA = "MotoNav-places/1.0 (github.com/Mind-osc)"
OUT = "app/src/main/assets/places/gr.tsv"

QUERY = """
[out:json][timeout:600];
area["ISO3166-1"="GR"][admin_level=2]->.gr;
(
  node["place"~"^(city|town|village|hamlet|suburb|neighbourhood|quarter|locality|island|islet|isolated_dwelling)$"]["name"](area.gr);
  node["natural"~"^(peak|saddle|volcano)$"]["name"](area.gr);
  node["mountain_pass"="yes"]["name"](area.gr);
);
out;
"""

def overpass(q):
    last = None
    for attempt in range(3):
        for url in SERVERS:
            try:
                req = urllib.request.Request(url, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=700) as r:
                    return json.loads(r.read().decode("utf-8"))
            except Exception as e:
                last = e
                print("overpass", url, e, file=sys.stderr)
                time.sleep(20)
    raise last

def kind(t):
    if "place" in t: return t["place"]
    if t.get("mountain_pass") == "yes" or t.get("natural") == "saddle": return "pass"
    return t.get("natural", "")

def clean(s):
    return " ".join(s.replace("\t", " ").replace("\n", " ").split())

def main():
    data = overpass(QUERY)
    rows = []
    for e in data.get("elements", []):
        t = e.get("tags", {})
        name = clean(t.get("name", ""))
        if not name: continue
        latin = clean(t.get("name:en") or t.get("int_name") or t.get("name:el-Latn") or "")
        if latin == name: latin = ""
        k = kind(t)
        if k in ("peak", "volcano", "pass") and t.get("ele"):
            try: ele = round(float(str(t["ele"]).split()[0].replace(",", ".")))
            except Exception: ele = None
        else:
            ele = None
        rows.append([name, latin, round(e["lat"], 5), round(e["lon"], 5), k, ele])

    # «Κοντινή πόλη» για χωριά/οικισμούς/κορυφές: η πιο κοντινή πόλη/κωμόπολη (σε πλέγμα 0,5°).
    towns = [r for r in rows if r[4] in ("city", "town")]
    grid = {}
    for r in towns:
        grid.setdefault((int(r[2] * 2), int(r[3] * 2)), []).append(r)
    def near_town(r):
        best, bd = None, 1e9
        gx, gy = int(r[2] * 2), int(r[3] * 2)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for tw in grid.get((gx + dx, gy + dy), []):
                    if tw is r: continue
                    d = (tw[2] - r[2]) ** 2 + ((tw[3] - r[3]) * math.cos(math.radians(r[2]))) ** 2
                    if d < bd: best, bd = tw, d
        return best[0] if best else ""

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    order = {"city": 0, "town": 1, "island": 2, "village": 3, "suburb": 4, "peak": 5, "pass": 5}
    rows.sort(key=lambda r: (order.get(r[4], 9), r[0]))
    with open(OUT, "w", encoding="utf-8") as f:
        for r in rows:
            detail = "" if r[4] in ("city", "town") else near_town(r)
            if r[5] is not None:
                detail = (f"{r[5]} m" + (f" · {detail}" if detail else ""))
            f.write("\t".join([r[0], r[1], f"{r[2]}", f"{r[3]}", r[4], detail]) + "\n")
    print(f"{len(rows)} μέρη → {OUT} ({os.path.getsize(OUT) // 1024} KB)")

if __name__ == "__main__":
    main()

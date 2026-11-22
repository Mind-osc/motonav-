#!/usr/bin/env python3
"""
Offline αναζήτηση οδών: κάθε δρόμος με όνομα στην Ελλάδα, μία φορά ανά περιοχή (~3 km), με την πόλη/χωριό του.
Γράφει app/src/main/assets/places/gr-streets.tsv:  όνομα \t lat \t lon \t περιοχή
Χρειάζεται πρώτα το gr.tsv (build_places.py) για τα ονόματα περιοχών.
"""
import csv, io, math, os, sys, time, unicodedata, urllib.parse, urllib.request

SERVERS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]
UA = "MotoNav-streets/1.0 (github.com/Mind-osc)"
PLACES = "app/src/main/assets/places/gr.tsv"
OUT = "app/src/main/assets/places/gr-streets.tsv"

QUERY = """
[out:csv(::lat,::lon,name;false)][timeout:900][maxsize:1073741824];
area["ISO3166-1"="GR"][admin_level=2]->.gr;
way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|pedestrian|service|track|road|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"]["name"](area.gr);
out center;
"""

def overpass(q):
    last = None
    for attempt in range(3):
        for url in SERVERS:
            try:
                req = urllib.request.Request(url, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=1000) as r:
                    return r.read().decode("utf-8")
            except Exception as e:
                last = e
                print("overpass", url, e, file=sys.stderr)
                time.sleep(30)
    raise last

def norm(s):
    d = unicodedata.normalize("NFD", s.lower())
    return "".join(ch for ch in d if unicodedata.category(ch) != "Mn").replace("ς", "σ").strip()

def main():
    # περιοχές (πόλη, κωμόπολη, χωριό, συνοικία) για το «πού είναι»
    areas = []
    with open(PLACES, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) >= 5 and p[4] in ("city", "town", "village", "suburb", "neighbourhood", "quarter", "hamlet"):
                areas.append((p[0], float(p[2]), float(p[3]), p[4]))
    grid = {}
    for a in areas:
        grid.setdefault((int(a[1] * 10), int(a[2] * 10)), []).append(a)
    rank = {"suburb": 0, "neighbourhood": 0, "quarter": 0, "village": 1, "town": 1, "city": 2, "hamlet": 3}
    def area_of(lat, lon):
        best, bd = None, 1e9
        gx, gy = int(lat * 10), int(lon * 10)
        c = math.cos(math.radians(lat))
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for a in grid.get((gx + dx, gy + dy), []):
                    d = (a[1] - lat) ** 2 + ((a[2] - lon) * c) ** 2
                    d *= 1 + 0.15 * rank.get(a[3], 3)  # λίγη προτίμηση σε συνοικίες/χωριά
                    if d < bd: best, bd = a, d
        return best[0] if best else ""

    text = overpass(QUERY)
    groups = {}
    n = 0
    for row in csv.reader(io.StringIO(text), delimiter="\t"):
        if len(row) < 3: continue
        try: lat, lon = float(row[0]), float(row[1])
        except ValueError: continue
        name = " ".join(row[2].split())
        if not name or len(name) > 80: continue
        n += 1
        key = (norm(name), int(lat / 0.03), int(lon / 0.03))   # ~3 km
        g = groups.get(key)
        if g is None:
            groups[key] = [name, lat, lon, 1]
        else:
            g[1] += lat; g[2] += lon; g[3] += 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    rows = []
    for (k, _, _), (name, slat, slon, cnt) in groups.items():
        lat, lon = slat / cnt, slon / cnt
        rows.append((name, round(lat, 5), round(lon, 5), area_of(lat, lon)))
    rows.sort(key=lambda r: (norm(r[0]), r[1]))
    with open(OUT, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(f"{r[0]}\t{r[1]}\t{r[2]}\t{r[3]}\n")
    print(f"{n} δρόμοι → {len(rows)} εγγραφές → {OUT} ({os.path.getsize(OUT) // 1024} KB)")

if __name__ == "__main__":
    main()

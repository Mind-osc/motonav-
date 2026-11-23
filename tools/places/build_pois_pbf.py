#!/usr/bin/env python3
"""
Offline σημεία (βενζινάδικα, καφέ, φαγητό, συνεργεία, ύπνος, θέα, νερό, πλυντήρια) για όλη την Ελλάδα,
από το έτοιμο αρχείο OpenStreetMap του Geofabrik (όχι από το Overpass, που συχνά είναι «γεμάτο»).
Είσοδος: φιλτραρισμένο .osm.pbf (osmium tags-filter). Έξοδος: app/src/main/assets/places/gr-pois.tsv
    όνομα \t lat \t lon \t είδος
"""
import os, sys
import osmium

OUT = "app/src/main/assets/places/gr-pois.tsv"
WANT = {
    "amenity": {"fuel", "cafe", "drinking_water", "car_wash", "restaurant", "fast_food"},
    "shop": {"motorcycle", "motorcycle_repair", "car_repair", "tyres"},
    "tourism": {"hotel", "guest_house", "hostel", "camp_site", "viewpoint", "alpine_hut"},
}

def kind_of(tags):
    for k in ("amenity", "shop", "tourism"):
        v = tags.get(k)
        if v in WANT[k]:
            return v
    return None

def name_of(tags):
    n = tags.get("name:el") or tags.get("name") or tags.get("brand") or tags.get("operator") or ""
    return " ".join(n.split())[:60]

class H(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.rows = []

    def node(self, n):
        k = kind_of(n.tags)
        if k and n.location.valid():
            self.rows.append((name_of(n.tags), n.location.lat, n.location.lon, k))

    def way(self, w):
        k = kind_of(w.tags)
        if not k:
            return
        pts = [(nd.location.lat, nd.location.lon) for nd in w.nodes if nd.location.valid()]
        if pts:
            self.rows.append((name_of(w.tags), sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts), k))

def main(src):
    h = H()
    h.apply_file(src, locations=True)
    rows, seen = [], set()
    for name, lat, lon, kind in h.rows:
        lat, lon = round(lat, 5), round(lon, 5)
        key = (kind, round(lat, 4), round(lon, 4))
        if key in seen:
            continue
        seen.add(key)
        rows.append((name.replace("\t", " "), lat, lon, kind))
    rows.sort(key=lambda x: (x[3], x[1], x[2]))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        for name, lat, lon, kind in rows:
            f.write(f"{name}\t{lat}\t{lon}\t{kind}\n")
    by = {}
    for r in rows:
        by[r[3]] = by.get(r[3], 0) + 1
    print(f"{len(rows)} σημεία → {OUT} ({os.path.getsize(OUT) // 1024} KB)", by)
    if len(rows) < 1000:
        sys.exit("πολύ λίγα σημεία, κάτι πήγε στραβά")

if __name__ == "__main__":
    main(sys.argv[1])

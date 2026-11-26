#!/usr/bin/env python3
"""
Διασταυρώσεις οδών για όλη την Ελλάδα (offline αναζήτηση «Λιοσίων & Πραΐδου»).
Είσοδος: .osm.pbf με μόνο τους δρόμους με όνομα (osmium tags-filter w/highway=... + name).
Έξοδος: app/src/main/assets/places/gr-cross.tsv
    οδός Α \t οδός Β \t lat \t lon
Ένα σημείο ανά ζευγάρι οδών και ανά ~300 m (μεγάλες οδοί κόβονται πολλές φορές από την ίδια).
"""
import os, sys
import osmium

OUT = "app/src/main/assets/places/gr-cross.tsv"
ROADS = {"motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "living_street", "pedestrian", "road"}

class H(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.names = {}       # όνομα → id
        self.node_names = {}  # node id → id ονόματος ή set
        self.loc = {}

    def way(self, w):
        if w.tags.get("highway") not in ROADS:
            return
        n = w.tags.get("name:el") or w.tags.get("name")
        if not n:
            return
        n = " ".join(n.split())
        nid = self.names.setdefault(n, len(self.names))
        for nd in w.nodes:
            cur = self.node_names.get(nd.ref)
            if cur is None:
                self.node_names[nd.ref] = nid
            elif isinstance(cur, int):
                if cur != nid:
                    self.node_names[nd.ref] = {cur, nid}
                    if nd.location.valid():
                        self.loc[nd.ref] = (nd.location.lat, nd.location.lon)
            else:
                cur.add(nid)
                if nd.location.valid():
                    self.loc[nd.ref] = (nd.location.lat, nd.location.lon)

def main(src):
    h = H()
    h.apply_file(src, locations=True)
    names = [None] * len(h.names)
    for n, i in h.names.items():
        names[i] = n
    seen, rows = set(), []
    for ref, v in h.node_names.items():
        if isinstance(v, int) or ref not in h.loc:
            continue
        lat, lon = h.loc[ref]
        ids = sorted(v, key=lambda i: names[i])
        for a in range(len(ids)):
            for b in range(a + 1, len(ids)):
                A, B = names[ids[a]], names[ids[b]]
                key = (A, B, round(lat / 0.003), round(lon / 0.003))
                if key in seen:
                    continue
                seen.add(key)
                rows.append((A, B, round(lat, 5), round(lon, 5)))
    rows.sort()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        for A, B, lat, lon in rows:
            f.write(f"{A}\t{B}\t{lat}\t{lon}\n")
    print(f"{len(rows)} διασταυρώσεις, {len(names)} οδοί → {OUT} ({os.path.getsize(OUT) // 1024} KB)")
    if len(rows) < 10000:
        sys.exit("πολύ λίγες διασταυρώσεις, κάτι πήγε στραβά")

if __name__ == "__main__":
    main(sys.argv[1])

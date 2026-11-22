#!/usr/bin/env python3
"""
Offline κατηγορίες: βενζινάδικα, συνεργεία/μαγαζιά μηχανών, καφέ, φαγητό, ύπνος, θέα, νερό, πλυντήρια στην Ελλάδα.
Γράφει app/src/main/assets/places/gr-pois.tsv:  όνομα \t lat \t lon \t είδος (χωρίς internet: κατηγορίες, βενζίνη στη διαδρομή)
"""
import csv, io, os, sys, time, urllib.parse, urllib.request

SERVERS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]
UA = "MotoNav-pois/1.0 (github.com/Mind-osc)"
OUT = "app/src/main/assets/places/gr-pois.tsv"

GROUPS = [
    'nwr["amenity"="fuel"]',
    'nwr["amenity"~"^(cafe|drinking_water|car_wash)$"]',
    'nwr["amenity"~"^(restaurant|fast_food)$"]',
    'nwr["shop"~"^(motorcycle|motorcycle_repair|car_repair|tyres)$"]',
    'nwr["tourism"~"^(hotel|guest_house|hostel|camp_site|viewpoint|alpine_hut)$"]',
]
# Ελλάδα σε 4 κομμάτια: μικρότερα ερωτήματα, λιγότερα 504 από το Overpass
TILES = [(34.7, 19.3, 38.3, 24.0), (34.7, 24.0, 38.3, 29.7), (38.3, 19.3, 41.8, 24.0), (38.3, 24.0, 41.8, 29.7)]

def query(group, t):
    return (f"[out:csv(::lat,::lon,name,brand,amenity,shop,tourism;false)][timeout:300];"
            f"({group}({t[0]},{t[1]},{t[2]},{t[3]}););out center;")

def overpass(q):
    last = None
    for attempt in range(4):
        for url in SERVERS:
            try:
                req = urllib.request.Request(url, data=urllib.parse.urlencode({"data": q}).encode(), headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=400) as r:
                    return r.read().decode("utf-8")
            except Exception as e:
                last = e
                print("overpass", url, e, file=sys.stderr)
                time.sleep(20 + 20 * attempt)
    raise last

def main():
    text = ""
    for g in GROUPS:
        for t in TILES:
            text += overpass(query(g, t))
            time.sleep(5)
    rows, seen = [], set()
    for r in csv.reader(io.StringIO(text), delimiter="\t"):
        if len(r) < 7: continue
        try: lat, lon = round(float(r[0]), 5), round(float(r[1]), 5)
        except ValueError: continue
        name = " ".join((r[2] or r[3]).split())[:60]
        kind = r[4] or r[5] or r[6]
        if not kind: continue
        key = (kind, round(lat, 4), round(lon, 4))
        if key in seen: continue
        seen.add(key)
        rows.append((name, lat, lon, kind))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    rows.sort(key=lambda x: (x[3], x[1], x[2]))
    with open(OUT, "w", encoding="utf-8") as f:
        for name, lat, lon, kind in rows:
            f.write(f"{name}\t{lat}\t{lon}\t{kind}\n")
    print(f"{len(rows)} σημεία → {OUT} ({os.path.getsize(OUT) // 1024} KB)")

if __name__ == "__main__":
    main()

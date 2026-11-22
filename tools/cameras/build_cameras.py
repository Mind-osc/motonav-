"""
Κάμερες Αττικής: διασταυρώσεις από δημοσιευμένες λίστες → συντεταγμένες.

1. Κέντρο κάθε δήμου/περιοχής από το Nominatim (OpenStreetMap), 1 αίτημα/δευτερόλεπτο.
2. Overpass: δρόμοι με όνομα που ταιριάζει στον δρόμο Α και στον Β, σε ακτίνα γύρω από το κέντρο,
   και οι κοινοί τους κόμβοι (η διασταύρωση).
3. Κρατάμε ένα σημείο μόνο αν όλοι οι κοινοί κόμβοι είναι κοντά (≤ 250 m μεταξύ τους).
   Αλλιώς (κανένας, ή πολλές διασταυρώσεις με ίδια ονόματα) το αφήνουμε έξω. Καλύτερα να λείπει
   μια κάμερα παρά να ειδοποιεί σε λάθος σημείο.

Τρέχει στο GitHub Actions (εκεί υπάρχει internet). Γράφει:
  app/src/main/assets/cameras_attica.json   (για την εφαρμογή)
  replica/cameras-attica-report.md          (τι βρέθηκε, τι όχι και γιατί)
"""
import csv, json, math, os, sys, time, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
UA = "MotoNav/0.1 (personal app; camera geocoding)"
RADIUS = 4000
MAX_SPREAD = 250.0


def http(url, data=None, tries=3):
    for k in range(tries):
        try:
            req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=90) as r:
                return r.read().decode("utf-8")
        except Exception as e:
            if k == tries - 1:
                raise
            time.sleep(5 * (k + 1))


def dist(a, b):
    R = 6371008.8
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    s = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(min(1, s)))


# Κέντρα δήμων (κατά προσέγγιση, αρκεί για ακτίνα 4 km). Το Nominatim από το GitHub Actions
# επέστρεφε κενό μετά το πρώτο αίτημα, οπότε το χρησιμοποιούμε μόνο για ό,τι λείπει από εδώ.
_centers = {
    "Αθήνα": (37.9838, 23.7275), "Ζωγράφου": (37.9776, 23.7700), "Παπάγου": (37.9890, 23.7960),
    "Μαρούσι": (38.0500, 23.8070), "Καλλιθέα": (37.9550, 23.7020), "Άλιμος": (37.9100, 23.7230),
    "Άγιος Δημήτριος": (37.9330, 23.7300), "Δάφνη": (37.9450, 23.7360), "Ταύρος": (37.9620, 23.6990),
    "Μοσχάτο": (37.9530, 23.6800), "Περιστέρι": (38.0150, 23.6910), "Ίλιον": (38.0300, 23.7000),
    "Χαϊδάρι": (38.0110, 23.6650), "Πειραιάς": (37.9420, 23.6460), "Αγία Παρασκευή": (38.0110, 23.8200),
    "Αργυρούπολη": (37.9050, 23.7470), "Ασπρόπυργος": (38.0610, 23.5880), "Βούλα": (37.8440, 23.7750),
    "Δραπετσώνα": (37.9470, 23.6230), "Κερατσίνι": (37.9620, 23.6200), "Κορυδαλλός": (37.9830, 23.6500),
    "Μέγαρα": (37.9960, 23.3440), "Πέραμα": (37.9650, 23.5700), "Ραφήνα": (38.0220, 24.0090),
    "Ψυχικό": (38.0130, 23.7710),
}


def center(area):
    if area in _centers:
        return _centers[area]
    q = urllib.parse.urlencode({"q": f"{area}, Αττική, Ελλάδα", "format": "json", "limit": 1})
    time.sleep(1.2)
    res = json.loads(http("https://nominatim.openstreetmap.org/search?" + q))
    c = (float(res[0]["lat"]), float(res[0]["lon"])) if res else None
    if c:
        _centers[area] = c
    return c


def regex(names):
    # «Α|Β» → εναλλακτικά ονόματα. Η τελεία στα συντομευμένα ονόματα γίνεται «.*».
    return "|".join(n.strip().replace(".", ".*") for n in names.split("|"))


def crossing(c, a, b):
    q = f"""[out:json][timeout:60];
way(around:{RADIUS},{c[0]},{c[1]})["highway"]["name"~"{regex(a)}",i]->.a;
way(around:{RADIUS},{c[0]},{c[1]})["highway"]["name"~"{regex(b)}",i]->.b;
node(w.a)->.na;
node(w.b)->.nb;
node.na.nb;
out;"""
    time.sleep(1.0)
    body = http("https://overpass-api.de/api/interpreter", data=urllib.parse.urlencode({"data": q}).encode())
    return [(e["lat"], e["lon"]) for e in json.loads(body).get("elements", []) if e.get("type") == "node"]


def main():
    out, report = [], ["# Κάμερες Αττικής: αποτέλεσμα γεωκωδικοποίησης", "",
                       "| id | είδος | διασταύρωση | αποτέλεσμα |", "| --- | --- | --- | --- |"]
    with open(os.path.join(HERE, "attica_points.csv"), encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    for r in rows:
        try:
            c = center(r["area"])
            if not c:
                report.append(f"| {r['id']} | {r['kind']} | {r['label']} | ✗ δεν βρέθηκε η περιοχή «{r['area']}» |")
                continue
            nodes = crossing(c, r["street_a"], r["street_b"])
            if not nodes:
                report.append(f"| {r['id']} | {r['kind']} | {r['label']} | ✗ δεν βρέθηκε κοινός κόμβος |")
                continue
            lat = sum(n[0] for n in nodes) / len(nodes)
            lon = sum(n[1] for n in nodes) / len(nodes)
            spread = max(dist((lat, lon), n) for n in nodes)
            if spread > MAX_SPREAD:
                report.append(f"| {r['id']} | {r['kind']} | {r['label']} | ✗ πολλές διασταυρώσεις ({len(nodes)} κόμβοι, {spread:.0f} m) |")
                continue
            out.append({"id": r["id"], "la": round(lat, 6), "lo": round(lon, 6), "src": "attica", "kind": r["kind"], "label": r["label"]})
            report.append(f"| {r['id']} | {r['kind']} | {r['label']} | ✓ {lat:.5f}, {lon:.5f} ({len(nodes)} κόμβοι) |")
        except Exception as e:
            report.append(f"| {r['id']} | {r['kind']} | {r['label']} | ✗ σφάλμα: {e} |")
        print(report[-1], flush=True)

    # Δύο διαφορετικές διασταυρώσεις στο ίδιο σημείο (< 60 m) = κάποια ονόματα ταίριαξαν λάθος.
    # Δεν ξέρουμε ποια είναι σωστή, οπότε βγαίνουν και οι δύο.
    clash = set()
    for i, x in enumerate(out):
        for y in out[i + 1:]:
            if dist((x["la"], x["lo"]), (y["la"], y["lo"])) < 60:
                clash.update({x["id"], y["id"]})
    if clash:
        out = [o for o in out if o["id"] not in clash]
        report += ["", "Βγήκαν λόγω διπλού σημείου (ασαφές): " + ", ".join(sorted(clash))]
    report += ["", f"Βρέθηκαν {len(out)} από {len(rows)}."]
    os.makedirs(os.path.join(ROOT, "app/src/main/assets"), exist_ok=True)
    with open(os.path.join(ROOT, "app/src/main/assets/cameras_attica.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    with open(os.path.join(ROOT, "replica/cameras-attica-report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(report) + "\n")
    print(f"OK {len(out)}/{len(rows)}")


if __name__ == "__main__":
    main()

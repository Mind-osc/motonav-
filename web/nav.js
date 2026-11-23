/*
 * Moto Nav web: η «λογική» της πλοήγησης, χωρίς UI (τρέχει και σε Node για τα τεστ).
 * Ίδιοι κανόνες με την Android εφαρμογή: BRouter, στροφές, εκτός διαδρομής, φράσεις.
 */
(function (root) {
  "use strict";
  const R = 6371000;
  const rad = (d) => (d * Math.PI) / 180;
  const deg = (r) => (r * 180) / Math.PI;

  const Geo = {
    distance(a, b) {
      const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon);
      const s = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2;
      return 2 * R * Math.asin(Math.min(1, Math.sqrt(s)));
    },
    bearing(a, b) {
      const y = Math.sin(rad(b.lon - a.lon)) * Math.cos(rad(b.lat));
      const x = Math.cos(rad(a.lat)) * Math.sin(rad(b.lat)) - Math.sin(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.cos(rad(b.lon - a.lon));
      return (deg(Math.atan2(y, x)) + 360) % 360;
    },
    /** Προβολή του p στο τμήμα a-b: [t (0..1), απόσταση σε m]. */
    project(p, a, b) {
      const k = Math.cos(rad(p.lat));
      const ax = a.lon * k, ay = a.lat, bx = b.lon * k, by = b.lat, px = p.lon * k, py = p.lat;
      const dx = bx - ax, dy = by - ay;
      const len2 = dx * dx + dy * dy;
      let t = len2 === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
      t = Math.max(0, Math.min(1, t));
      const q = { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t };
      return [t, Geo.distance(p, q)];
    },
    angleDiff(a, b) {
      const d = (((a - b) % 360) + 360) % 360;
      return d > 180 ? 360 - d : d;
    },
  };

  // Κωδικοί στροφών του BRouter (voicehints) → ίδια ονόματα με την Android εφαρμογή.
  const TURN = { 1: "STRAIGHT", 2: "LEFT", 3: "SLIGHT_LEFT", 4: "SHARP_LEFT", 5: "RIGHT", 6: "SLIGHT_RIGHT", 7: "SHARP_RIGHT",
    8: "KEEP_LEFT", 9: "KEEP_RIGHT", 10: "UTURN", 11: "UTURN", 15: "UTURN", 13: "ROUNDABOUT", 14: "ROUNDABOUT_LEFT",
    17: "EXIT_LEFT", 18: "EXIT_RIGHT" };

  function makeRoute(points, elevations, maneuversRaw, durationS, limits, source) {
    const cum = new Float64Array(points.length);
    for (let i = 1; i < points.length; i++) cum[i] = cum[i - 1] + Geo.distance(points[i - 1], points[i]);
    const maneuvers = maneuversRaw
      .filter((m) => m.index >= 0 && m.index < points.length)
      .map((m) => ({ index: m.index, turn: m.turn, exit: m.exit || 0, at: m.at != null ? m.at : cum[m.index], street: m.street || null }))
      .sort((a, b) => a.at - b.at);
    maneuvers.push({ index: points.length - 1, turn: "ARRIVE", exit: 0, at: cum[points.length - 1] });
    const length = cum[points.length - 1];
    return { points, elevations, cum, maneuvers, length, duration: durationS || length / (60 / 3.6), limits: limits || [], source: source || "brouter" };
  }

  /** Όριο ταχύτητας στο σημείο m (από τα «messages» του BRouter: maxspeed ανά κομμάτι). */
  function limitAt(route, m) {
    for (const l of route.limits) if (m <= l.end) return l.kmh;
    return null;
  }

  function parseLimits(messages) {
    if (!messages || messages.length < 2) return [];
    const h = messages[0];
    const iD = h.indexOf("Distance"), iT = h.indexOf("WayTags");
    if (iD < 0 || iT < 0) return [];
    const out = [];
    let end = 0;
    for (let i = 1; i < messages.length; i++) {
      end += parseFloat(messages[i][iD]) || 0;
      const m = /(?:^|\s)maxspeed=(\d+)/.exec(messages[i][iT] || "");
      out.push({ end, kmh: m ? parseInt(m[1], 10) : null });
    }
    return out;
  }

  /** GeoJSON του BRouter → διαδρομή. */
  function parseBRouter(json) {
    const f = json && json.features && json.features[0];
    if (!f) throw new Error("Το BRouter δεν έστειλε διαδρομή.");
    const coords = f.geometry.coordinates;
    const points = coords.map((c) => ({ lon: c[0], lat: c[1] }));
    const elevations = coords.map((c) => (c.length > 2 ? c[2] : null));
    if (points.length < 2) throw new Error("Η διαδρομή βγήκε άδεια.");
    const props = f.properties || {};
    const hints = (props.voicehints || [])
      .map((h) => ({ index: h[0], turn: TURN[h[1]], exit: h[2] }))
      .filter((h) => h.turn);
    const time = parseFloat(props["total-time"]);
    return makeRoute(points, elevations, hints, isFinite(time) ? time : null, parseLimits(props.messages), "brouter");
  }

  // TomTom (κίνηση): ίδια ονόματα στροφών με την Android εφαρμογή.
  function ttTurn(m) {
    const map = { TURN_LEFT: "LEFT", TURN_RIGHT: "RIGHT", BEAR_LEFT: "SLIGHT_LEFT", BEAR_RIGHT: "SLIGHT_RIGHT", SHARP_LEFT: "SHARP_LEFT",
      SHARP_RIGHT: "SHARP_RIGHT", KEEP_LEFT: "KEEP_LEFT", KEEP_RIGHT: "KEEP_RIGHT", MAKE_UTURN: "UTURN", TRY_MAKE_UTURN: "UTURN",
      ROUNDABOUT_LEFT: "ROUNDABOUT", ROUNDABOUT_RIGHT: "ROUNDABOUT", ROUNDABOUT_CROSS: "ROUNDABOUT", ROUNDABOUT_BACK: "ROUNDABOUT",
      MOTORWAY_EXIT_LEFT: "EXIT_LEFT", MOTORWAY_EXIT_RIGHT: "EXIT_RIGHT", TAKE_EXIT: "EXIT_RIGHT", STRAIGHT: "STRAIGHT" };
    if (map[m]) return map[m];
    if (/^(DEPART|ARRIVE|FOLLOW|WAYPOINT)/.test(m)) return null;
    if (/LEFT/.test(m)) return "KEEP_LEFT";
    if (/RIGHT/.test(m)) return "KEEP_RIGHT";
    return null;
  }
  function parseTomTom(json) {
    const out = [];
    (json.routes || []).forEach((r) => {
      const points = [];
      (r.legs || []).forEach((l) => (l.points || []).forEach((p) => {
        const q = { lat: p.latitude, lon: p.longitude };
        const last = points[points.length - 1];
        if (!last || last.lat !== q.lat || last.lon !== q.lon) points.push(q);
      }));
      if (points.length < 2) return;
      const ins = ((r.guidance && r.guidance.instructions) || []).map((o) => ({
        index: Math.min(points.length - 1, o.pointIndex || 0), turn: ttTurn(o.maneuver || ""), exit: o.roundaboutExitNumber || 0,
        at: o.routeOffsetInMeters, street: o.street || (o.roadNumbers && o.roadNumbers[0]) || null,
      })).filter((x) => x.turn);
      const s = r.summary || {};
      const route = makeRoute(points, points.map(() => null), ins, s.travelTimeInSeconds, [], "tomtom");
      route.noTraffic = s.noTrafficTravelTimeInSeconds || null;
      out.push(route);
    });
    if (!out.length) throw new Error("Το TomTom δεν βρήκε διαδρομή.");
    return out;
  }

  /** Στροφές ανά χλμ (ίδιο με το Twistiness της Android): δείγμα ανά 25 m, άθροισμα αλλαγών κατεύθυνσης. */
  function twistiness(points) {
    if (points.length < 3) return 0;
    const s = [points[0]];
    let acc = 0;
    for (let i = 1; i < points.length; i++) {
      acc += Geo.distance(points[i - 1], points[i]);
      if (acc >= 25) { s.push(points[i]); acc = 0; }
    }
    if (s[s.length - 1] !== points[points.length - 1]) s.push(points[points.length - 1]);
    if (s.length < 3) return 0;
    let turn = 0, len = Geo.distance(s[0], s[1]), prev = Geo.bearing(s[0], s[1]);
    for (let i = 1; i < s.length - 1; i++) {
      const b = Geo.bearing(s[i], s[i + 1]);
      const d = Geo.angleDiff(b, prev);
      if (d < 150) turn += d;
      prev = b;
      len += Geo.distance(s[i], s[i + 1]);
    }
    return len > 0 ? turn / (len / 1000) : 0;
  }

  /** Από τις υποψήφιες: η πιο στριφτή, όχι πάνω από 1,6× τον χρόνο της πιο γρήγορης. */
  function pickTwisty(cands, maxSlow = 1.6) {
    const fastest = Math.min(...cands.map((r) => r.duration));
    return cands.filter((r) => r.duration <= fastest * maxSlow).sort((a, b) => twistiness(b.points) - twistiness(a.points))[0];
  }

  function pointAt(route, m) {
    const { points, cum } = route;
    if (m <= 0) return points[0];
    if (m >= route.length) return points[points.length - 1];
    let lo = 0, hi = cum.length - 1;
    while (lo < hi) { const mid = (lo + hi) >> 1; if (cum[mid] < m) lo = mid + 1; else hi = mid; }
    const i = Math.max(1, lo);
    const a = points[i - 1], b = points[i];
    const seg = cum[i] - cum[i - 1];
    const t = seg > 0 ? (m - cum[i - 1]) / seg : 0;
    return { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t };
  }

  /** Τα σημεία της διαδρομής από το m και μετά (για να «σβήνει» η γραμμή πίσω σου). */
  function remaining(route, m) {
    const { points, cum } = route;
    let i = 0;
    while (i < cum.length && cum[i] <= m) i++;
    return [pointAt(route, m)].concat(points.slice(i));
  }

  /** Πού είσαι πάνω στη διαδρομή και πότε βγήκες εκτός (ίδιοι κανόνες με την Android). */
  class Engine {
    constructor(route) { this.route = route; this.seg = 0; this.progress = 0; this.off = 0; this.need = 2; }

    headingFits(i, bearing) {
      const { points, cum } = this.route;
      const here = cum[i];
      let j = i;
      while (j > 0 && here - cum[j - 1] <= 40) j--;
      while (j < points.length - 1 && cum[j] - here <= 40) {
        if (cum[j + 1] - cum[j] > 3 && Geo.angleDiff(bearing, Geo.bearing(points[j], points[j + 1])) <= 50) return true;
        j++;
      }
      return false;
    }

    update(p, accuracy, bearing, speed) {
      const { points, cum } = this.route;
      const last = points.length - 2;
      const from = Math.max(0, this.seg - 3);
      const to = Math.min(last, this.seg + (this.off > 0 ? 400 : 120));
      let bestD = Infinity, bestI = this.seg, bestT = 0;
      for (let i = from; i <= to; i++) {
        const [t, d] = Geo.project(p, points[i], points[i + 1]);
        if (d < bestD) { bestD = d; bestI = i; bestT = t; }
      }
      const tol = Math.max(25, (accuracy || 20) * 1.2);
      const moving = bearing != null && speed > 3;
      const turnedOff = moving && bestD > 15 && bestD <= tol && !this.headingFits(bestI, bearing);
      let snapped = null;
      if (bestD > tol || turnedOff) {
        this.off++;
        const wrong = moving && !this.headingFits(bestI, bearing);
        this.need = bestD > 60 || wrong ? 2 : 4;
      } else {
        this.off = 0;
        this.seg = bestI;
        this.progress = cum[bestI] + bestT * (cum[bestI + 1] - cum[bestI]);
        const a = points[bestI], b = points[bestI + 1];
        snapped = { lat: a.lat + (b.lat - a.lat) * bestT, lon: a.lon + (b.lon - a.lon) * bestT };
      }
      const m = this.route.maneuvers;
      let k = m.findIndex((x) => x.at > this.progress + 5);
      if (k < 0) k = m.length - 1;
      const next = m[k];
      const then = m[k + 1] && m[k + 1].at - next.at < 150 ? m[k + 1] : null;
      return {
        progress: this.progress,
        remaining: Math.max(0, this.route.length - this.progress),
        next, then, distToNext: Math.max(0, next.at - this.progress),
        snapped, onRoute: this.off === 0, offRoute: this.off >= this.need,
        arrived: this.route.length - this.progress < 30 && this.off === 0,
      };
    }
  }

  const ORD = ["", "πρώτη", "δεύτερη", "τρίτη", "τέταρτη", "πέμπτη", "έκτη"];
  const ORD_EN = ["", "first", "second", "third", "fourth", "fifth", "sixth"];
  const EN = { STRAIGHT: "continue straight", LEFT: "turn left", SLIGHT_LEFT: "bear left", SHARP_LEFT: "turn sharp left",
    RIGHT: "turn right", SLIGHT_RIGHT: "bear right", SHARP_RIGHT: "turn sharp right", KEEP_LEFT: "keep left", KEEP_RIGHT: "keep right",
    UTURN: "make a U-turn", EXIT_LEFT: "take the exit on the left", EXIT_RIGHT: "take the exit on the right", ARRIVE: "you are arriving at your destination" };
  const GR_PAIRS = [["ου", "ou"], ["Ου", "Ou"], ["ού", "ou"], ["αι", "ai"], ["ει", "ei"], ["οι", "oi"], ["μπ", "b"], ["Μπ", "B"], ["ντ", "d"], ["Ντ", "D"],
    ["γκ", "g"], ["Γκ", "G"], ["γγ", "ng"], ["αυ", "av"], ["ευ", "ev"]];
  const GR = { α: "a", ά: "a", β: "v", γ: "g", δ: "d", ε: "e", έ: "e", ζ: "z", η: "i", ή: "i", θ: "th", ι: "i", ί: "i", ϊ: "i", ΐ: "i", κ: "k",
    λ: "l", μ: "m", ν: "n", ξ: "x", ο: "o", ό: "o", π: "p", ρ: "r", σ: "s", ς: "s", τ: "t", υ: "y", ύ: "y", ϋ: "y", φ: "f", χ: "ch", ψ: "ps", ω: "o", ώ: "o" };
  function greeklish(s) {
    if (!/[\u0370-\u03FF\u1F00-\u1FFF]/.test(s)) return s;
    let t = s;
    GR_PAIRS.forEach(([g, l]) => { t = t.split(g).join(l); });
    return Array.from(t).map((ch) => {
      const lo = ch.toLowerCase(), m = GR[lo];
      if (!m) return ch;
      return ch !== lo ? m.charAt(0).toUpperCase() + m.slice(1) : m;
    }).join("");
  }
  const Phrases = {
    en: false,
    L(gr, en) { return Phrases.en ? en : gr; },
    name(s) { return Phrases.en ? greeklish(s) : s; },
    action(m) {
      if (Phrases.en) {
        if (m.turn === "ROUNDABOUT" || m.turn === "ROUNDABOUT_LEFT") {
          const o = ORD_EN[m.exit];
          return o ? `at the roundabout, take the ${o} exit` : m.exit > 0 ? `at the roundabout, take exit ${m.exit}` : "enter the roundabout";
        }
        return EN[m.turn] || "continue";
      }
      return Phrases.actionGr(m);
    },
    actionGr(m) {
      switch (m.turn) {
        case "STRAIGHT": return "συνέχισε ευθεία";
        case "LEFT": return "στρίψε αριστερά";
        case "SLIGHT_LEFT": return "στρίψε ελαφρά αριστερά";
        case "SHARP_LEFT": return "στρίψε απότομα αριστερά";
        case "RIGHT": return "στρίψε δεξιά";
        case "SLIGHT_RIGHT": return "στρίψε ελαφρά δεξιά";
        case "SHARP_RIGHT": return "στρίψε απότομα δεξιά";
        case "KEEP_LEFT": return "κράτα αριστερά";
        case "KEEP_RIGHT": return "κράτα δεξιά";
        case "UTURN": return "κάνε αναστροφή";
        case "EXIT_LEFT": return "βγες αριστερά";
        case "EXIT_RIGHT": return "βγες δεξιά";
        case "ROUNDABOUT": case "ROUNDABOUT_LEFT":
          return m.exit > 0 ? `στον κυκλικό κόμβο, πάρε την ${ORD[m.exit] || m.exit + "η"} έξοδο` : "μπες στον κυκλικό κόμβο";
        case "ARRIVE": return "φτάνεις στον προορισμό";
        default: return "συνέχισε";
      }
    },
    title(m) {
      const a = Phrases.action(m);
      return a.charAt(0).toUpperCase() + a.slice(1);
    },
    distance(m) {
      if (Phrases.en) {
        if (m < 1000) return `${Math.max(50, Math.round(m / 50) * 50)} metres`;
        const k = Math.round(m / 100) / 10;
        return k === 1 ? "1 kilometre" : `${k} kilometres`;
      }
      if (m < 1000) return `${Math.max(50, Math.round(m / 50) * 50)} μέτρα`;
      const km = Math.round(m / 100) / 10;
      if (km === 1) return "1 χιλιόμετρο";
      return `${String(km).replace(".", ",")} χιλιόμετρα`;
    },
    short(m) {
      if (m < 1000) return `${Math.max(10, Math.round(m / 10) * 10)} m`;
      return `${(m / 1000).toFixed(m < 10000 ? 1 : 0).replace(".", ",")} km`;
    },
  };

  /** GPX: ανάγνωση (trk/rte/wpt) και γραφή. */
  const Gpx = {
    parse(text) {
      const doc = new DOMParser().parseFromString(text, "application/xml");
      const pick = (sel) => Array.from(doc.getElementsByTagName(sel)).map((n) => ({
        lat: parseFloat(n.getAttribute("lat")), lon: parseFloat(n.getAttribute("lon")),
      })).filter((p) => isFinite(p.lat) && isFinite(p.lon));
      let pts = pick("trkpt");
      if (pts.length < 2) pts = pick("rtept");
      if (pts.length < 2) pts = pick("wpt");
      const nameNode = doc.getElementsByTagName("name")[0];
      return { name: nameNode ? nameNode.textContent.trim() : "Διαδρομή GPX", points: pts };
    },
    /** GPX, KML ή GeoJSON (ό,τι ανοίγει και η Android εφαρμογή). */
    parseAny(text, filename) {
      const t = text.trim();
      if (/\.(geo)?json$/i.test(filename || "") || t[0] === "{") {
        const j = JSON.parse(t);
        const feats = j.type === "FeatureCollection" ? j.features : [j.type === "Feature" ? j : { geometry: j, properties: {} }];
        let pts = [], name = null;
        feats.forEach((f) => {
          const g = f.geometry || {};
          name = name || (f.properties && f.properties.name);
          const lines = g.type === "LineString" ? [g.coordinates] : g.type === "MultiLineString" ? g.coordinates : [];
          lines.forEach((l) => l.forEach((c) => pts.push({ lat: c[1], lon: c[0] })));
        });
        return { name: name || "Διαδρομή", points: pts };
      }
      if (/<kml/i.test(t)) {
        const doc = new DOMParser().parseFromString(t, "application/xml");
        const pts = [];
        Array.from(doc.getElementsByTagName("coordinates")).forEach((n) => n.textContent.trim().split(/\s+/).forEach((c) => {
          const [lon, lat] = c.split(",").map(Number);
          if (isFinite(lat) && isFinite(lon)) pts.push({ lat, lon });
        }));
        const nm = doc.getElementsByTagName("name")[0];
        return { name: nm ? nm.textContent.trim() : "Διαδρομή KML", points: pts };
      }
      return Gpx.parse(t);
    },
    write(name, points) {
      const esc = (s) => String(s).replace(/[<&>"]/g, (c) => ({ "<": "&lt;", "&": "&amp;", ">": "&gt;", '"': "&quot;" }[c]));
      const pts = points.map((p) => `<trkpt lat="${p.lat.toFixed(6)}" lon="${p.lon.toFixed(6)}"/>`).join("");
      return `<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Moto Nav web" xmlns="http://www.topografix.com/GPX/1/1"><metadata><name>${esc(name)}</name></metadata><trk><name>${esc(name)}</name><trkseg>${pts}</trkseg></trk></gpx>\n`;
    },
    /** Έως n ενδιάμεσα σημεία πάνω σε μια γραμμή (για να την ακολουθήσει το BRouter). */
    sample(points, n) {
      if (points.length <= n) return points.slice();
      const cum = [0];
      for (let i = 1; i < points.length; i++) cum.push(cum[i - 1] + Geo.distance(points[i - 1], points[i]));
      const total = cum[cum.length - 1];
      const out = [];
      let j = 0;
      for (let k = 0; k < n; k++) {
        const target = (total * k) / (n - 1);
        while (j < cum.length - 1 && cum[j] < target) j++;
        out.push(points[j]);
      }
      return out;
    },
  };

  /** Κυκλική βόλτα (όπως στην Android): σημεία σε κύκλο προς μια κατεύθυνση. */
  function loopPoints(start, lengthM, dirDeg) {
    const r = lengthM / (2 * Math.PI) / 1.25;
    const c = destPoint(start, r, dirDeg);
    const back = (dirDeg + 180) % 360;
    return [60, 120, 180, 240, 300].map((a) => destPoint(c, r, (back + a) % 360));
  }
  function destPoint(p, d, brg) {
    const δ = d / R, θ = rad(brg), φ1 = rad(p.lat), λ1 = rad(p.lon);
    const φ2 = Math.asin(Math.sin(φ1) * Math.cos(δ) + Math.cos(φ1) * Math.sin(δ) * Math.cos(θ));
    const λ2 = λ1 + Math.atan2(Math.sin(θ) * Math.sin(δ) * Math.cos(φ1), Math.cos(δ) - Math.sin(φ1) * Math.sin(φ2));
    return { lat: deg(φ2), lon: deg(λ2) };
  }
  /** Απόσταση σημείου από τη διαδρομή και πού πέφτει πάνω της (μέτρα από την αρχή). */
  function nearestOnRoute(route, p) {
    let best = { d: Infinity, along: 0 };
    for (let i = 0; i < route.points.length - 1; i++) {
      const [t, d] = Geo.project(p, route.points[i], route.points[i + 1]);
      if (d < best.d) best = { d, along: route.cum[i] + t * (route.cum[i + 1] - route.cum[i]) };
    }
    return best;
  }

  root.MotoNav = { Geo, parseBRouter, parseTomTom, makeRoute, limitAt, twistiness, pickTwisty, pointAt, remaining, Engine, Phrases, Gpx,
    greeklish, loopPoints, destPoint, nearestOnRoute };
})(typeof window !== "undefined" ? window : globalThis);

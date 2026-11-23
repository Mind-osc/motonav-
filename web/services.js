/*
 * Moto Nav web: οι online υπηρεσίες (όλες δέχονται κλήσεις από browser).
 * BRouter (διαδρομές), TomTom (κίνηση, με δικό σου κλειδί), Photon (αναζήτηση), Overpass (βενζινάδικα, μέρη),
 * Open-Meteo (καιρός), ElevenLabs (φωνές, με δικό σου κλειδί).
 */
(function (root) {
  "use strict";
  const { parseBRouter, parseTomTom, pickTwisty, Geo } = root.MotoNav;
  const BROUTER = "https://brouter.de/brouter";
  const PHOTON = "https://photon.komoot.io";
  const OVERPASS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"];

  const lonlats = (wps) => wps.map((p) => `${p.lon.toFixed(6)},${p.lat.toFixed(6)}`).join("|");
  const nogoParam = (list) => list && list.length ? "&nogos=" + list.map((n) => `${n.lon.toFixed(6)},${n.lat.toFixed(6)},${n.r}`).join("|") : "";

  function explain(t) {
    if (/not mapped|operation killed|no route|target island|position not mapped/i.test(t)) return "Δεν βρέθηκε δρόμος ως εκεί (σημείο μακριά από δρόμο ή σε νησί).";
    if (/profile/i.test(t)) return "Πρόβλημα με το προφίλ διαδρομής. Ξαναδοκίμασε.";
    if (/busy|too many|503|limit/i.test(t)) return "Ο server διαδρομών είναι απασχολημένος. Ξαναδοκίμασε σε λίγο.";
    return "Δεν βγήκε διαδρομή: " + String(t).slice(0, 120);
  }

  async function brouter(wps, profile, params, alt, nogos) {
    const extra = Object.entries(params || {}).map(([k, v]) => `&profile:${k}=${v}`).join("");
    const url = `${BROUTER}?lonlats=${lonlats(wps)}&profile=${encodeURIComponent(profile)}&alternativeidx=${alt || 0}&format=geojson&timode=2${extra}${nogoParam(nogos)}`;
    let res;
    try { res = await fetch(url); } catch (e) { throw new Error("Χωρίς σύνδεση: οι διαδρομές στη web έκδοση θέλουν internet."); }
    const text = await res.text();
    if (!res.ok || text.trim()[0] !== "{") throw new Error(explain(text));
    return parseBRouter(JSON.parse(text));
  }

  // Το δικό μας προφίλ (moto-dirt.brf) ανεβαίνει μία φορά στο BRouter και παίρνει προσωρινό όνομα.
  let customId = null;
  async function customProfile(force) {
    if (customId && !force) return customId;
    const text = await (await fetch("moto-dirt.brf")).text();
    const res = await fetch(`${BROUTER}/profile`, { method: "POST", body: text });
    const j = await res.json();
    if (!j.profileid) throw new Error("Το προφίλ δεν ανέβηκε: " + (j.error || "άγνωστο λάθος"));
    customId = j.profileid;
    return customId;
  }
  async function withCustom(wps, params, alt, nogos) {
    let id = await customProfile(false);
    try { return await brouter(wps, id, params, alt, nogos); }
    catch (e) {
      if (!/προφίλ/.test(e.message)) throw e;
      id = await customProfile(true);
      return brouter(wps, id, params, alt, nogos);
    }
  }

  /**
   * Διαδρομή όπως στην Android: Γρήγορη (car-fast), Στροφές (4 εναλλακτικές, η πιο στριφτή ως 1,6× χρόνο),
   * Χώμα (moto-dirt), Κίνηση (TomTom). Με αποφυγές πλοίων/κύριων/στενών/χώματος → το δικό μας προφίλ σε «δρόμο».
   */
  async function route(wps, o) {
    const nogos = o.nogos || [];
    const common = {};
    if (o.avToll) common.avoid_toll = 1;
    const av = {};
    if (o.avFerry) av.avoid_ferries = 1;
    if (o.avMain) av.avoid_main = 1;
    if (o.avNarrow) av.avoid_narrow = 1;
    if (o.avUnpaved) av.avoid_unpaved = 1;
    const anyAv = Object.keys(av).length > 0;
    if (o.type === "traffic") return (await tomtom(wps, o))[0];
    if (o.type === "dirt") {
      const p = Object.assign({}, common, av);
      delete p.avoid_unpaved;
      return withCustom(wps, p, 0, nogos);
    }
    const road = (params, alt) => anyAv
      ? withCustom(wps, Object.assign({ use_offroad: 0, dirt_preference: 0 }, params, av), alt, nogos)
      : brouter(wps, "car-fast", params, alt, nogos);
    if (o.type === "twisty") {
      const p = Object.assign({ vmax: o.moreCurves ? 60 : 80, avoid_motorways: 1, consider_town: 1 }, common);
      const tries = await Promise.allSettled((o.quick ? [0] : [0, 1, 2, 3]).map((a) => road(p, a)));
      const ok = tries.filter((t) => t.status === "fulfilled").map((t) => t.value);
      if (!ok.length) throw tries[0].reason;
      const pick = pickTwisty(ok, o.moreCurves ? 2.2 : 1.6);
      pick.others = ok.filter((r) => r !== pick);
      return pick;
    }
    const p = Object.assign({ vmax: 110 }, common);
    if (o.avMoto) p.avoid_motorways = 1;
    try { return await road(p, 0); }
    catch (e) {
      if (anyAv || /Χωρίς σύνδεση|Δεν βρέθηκε/.test(e.message)) throw e;
      return brouter(wps, "car-vario", p, 0, nogos);
    }
  }

  /** Εναλλακτικές (BRouter alternativeidx 1..3), μόνο λογικές: ως 1,3× χρόνο (ή +2′) και 1,5× μήκος. */
  async function alternatives(wps, o, main) {
    if (o.type === "traffic") {
      const all = await tomtom(wps, Object.assign({}, o, { alternatives: 2 }));
      return all.slice(1);
    }
    const profile = o.type === "dirt" ? await customProfile(false) : "car-fast";
    const p = o.type === "twisty" ? { vmax: 80, avoid_motorways: 1 } : Object.assign({ vmax: 110 }, o.avMoto ? { avoid_motorways: 1 } : {});
    if (o.avToll) p.avoid_toll = 1;
    const tries = await Promise.allSettled([1, 2, 3].map((a) => brouter(wps, profile, p, a, o.nogos)));
    return tries.filter((t) => t.status === "fulfilled").map((t) => t.value)
      .filter((r) => r.duration <= Math.max(main.duration * 1.3, main.duration + 120) && r.length <= main.length * 1.5)
      .filter((r) => Math.abs(r.length - main.length) > 50 || Math.abs(r.duration - main.duration) > 20);
  }

  async function tomtom(wps, o) {
    if (!o.ttKey) throw new Error("Για «Κίνηση» βάλε δωρεάν κλειδί TomTom: Ρυθμίσεις → Κλειδιά.");
    const pts = wps.map((p) => `${p.lat.toFixed(6)},${p.lon.toFixed(6)}`).join(":");
    let url = `https://api.tomtom.com/routing/1/calculateRoute/${pts}/json?key=${encodeURIComponent(o.ttKey)}` +
      "&traffic=true&routeType=fastest&travelMode=motorcycle&instructionsType=coded&language=el-GR&computeTravelTimeFor=all";
    if (o.alternatives) url += `&maxAlternatives=${o.alternatives}`;
    if (o.avToll) url += "&avoid=tollRoads";
    if (o.avMoto) url += "&avoid=motorways";
    if (o.avFerry) url += "&avoid=ferries";
    if (o.avUnpaved) url += "&avoid=unpavedRoads";
    let res;
    try { res = await fetch(url); } catch (e) { throw new Error("Χωρίς σύνδεση."); }
    if (res.status === 403 || res.status === 401) throw new Error("Το κλειδί TomTom δεν δέχτηκε. Έλεγξέ το στις Ρυθμίσεις → Κλειδιά.");
    if (!res.ok) throw new Error("Το TomTom δεν βρήκε διαδρομή.");
    return parseTomTom(await res.json());
  }

  // ---------- Αναζήτηση ----------
  async function search(q, near) {
    const r = await fetch(`${PHOTON}/api/?q=${encodeURIComponent(q)}&limit=10&lat=${near.lat}&lon=${near.lon}`);
    const j = await r.json();
    return j.features.map((f) => {
      const p = f.properties;
      const name = p.name || [p.street, p.housenumber].filter(Boolean).join(" ") || p.city || "Σημείο";
      const sub = [p.street && p.name ? [p.street, p.housenumber].filter(Boolean).join(" ") : null, p.city || p.town || p.village, p.state, p.country].filter(Boolean).join(", ");
      return { lat: f.geometry.coordinates[1], lon: f.geometry.coordinates[0], name, sub };
    });
  }
  async function reverse(p) {
    try {
      const r = await fetch(`${PHOTON}/reverse?lat=${p.lat}&lon=${p.lon}&limit=1`);
      const j = await r.json();
      const f = j.features && j.features[0];
      if (!f) return null;
      const q = f.properties;
      return q.name || [q.street, q.housenumber].filter(Boolean).join(" ") || q.city || null;
    } catch (e) { return null; }
  }

  // ---------- Overpass ----------
  async function overpass(query) {
    let last;
    for (const url of OVERPASS) {
      try {
        const res = await fetch(url, { method: "POST", body: "data=" + encodeURIComponent(query), headers: { "Content-Type": "application/x-www-form-urlencoded" } });
        if (!res.ok) { last = new Error("Overpass " + res.status); continue; }
        return (await res.json()).elements || [];
      } catch (e) { last = e; }
    }
    throw last || new Error("Overpass");
  }
  const centerOf = (e) => (e.lat != null ? { lat: e.lat, lon: e.lon } : e.center ? { lat: e.center.lat, lon: e.center.lon } : null);

  const CATEGORIES = {
    fuel: { label: "Βενζινάδικα", emoji: "⛽", q: 'nwr["amenity"="fuel"]' },
    cafe: { label: "Καφέ", emoji: "☕", q: 'nwr["amenity"="cafe"]' },
    food: { label: "Φαγητό", emoji: "🍽", q: 'nwr["amenity"~"^(restaurant|fast_food)$"]' },
    sleep: { label: "Ύπνος", emoji: "🛏", q: 'nwr["tourism"~"^(hotel|guest_house|hostel|camp_site)$"]' },
    moto: { label: "Συνεργεία μηχανών", emoji: "🔧", q: 'nwr["shop"~"^(motorcycle|motorcycle_repair)$"]' },
    water: { label: "Νερό", emoji: "💧", q: 'node["amenity"="drinking_water"]' },
  };
  async function category(key, near, radiusM) {
    const c = CATEGORIES[key];
    const els = await overpass(`[out:json][timeout:25];${c.q}(around:${radiusM || 8000},${near.lat},${near.lon});out center tags 60;`);
    return els.map((e) => {
      const p = centerOf(e);
      if (!p) return null;
      const t = e.tags || {};
      return { lat: p.lat, lon: p.lon, name: t["name:el"] || t.name || t.brand || c.label, sub: [t.brand, t["addr:street"]].filter(Boolean).join(" · "),
        d: Geo.distance(near, p) };
    }).filter(Boolean).sort((a, b) => a.d - b.d).slice(0, 30);
  }

  /** Βενζινάδικα κοντά στη διαδρομή (≤ 300 m), με τη θέση τους πάνω της. */
  async function fuelOnRoute(route) {
    let s = 90, w = 180, n = -90, e = -180;
    route.points.forEach((p) => { s = Math.min(s, p.lat); n = Math.max(n, p.lat); w = Math.min(w, p.lon); e = Math.max(e, p.lon); });
    const pad = 0.004;
    const els = await overpass(`[out:json][timeout:25];nwr["amenity"="fuel"](${s - pad},${w - pad},${n + pad},${e + pad});out center tags 400;`);
    const out = [];
    els.forEach((el) => {
      const p = centerOf(el);
      if (!p) return;
      const near = root.MotoNav.nearestOnRoute(route, p);
      if (near.d > 300) return;
      const t = el.tags || {};
      out.push({ lat: p.lat, lon: p.lon, name: t.brand || t.name || "Βενζινάδικο", along: near.along });
    });
    return out.sort((a, b) => a.along - b.along);
  }

  // Μέρη για μηχανόβιους (ίδιο ερώτημα με την Android): ανά κελί 0,25°, κρατιούνται 30 μέρες.
  const SPOT_KINDS = {
    VIEW: { label: "Θέα", emoji: "🏞" }, PASS: { label: "Αυχένας", emoji: "⛰" }, WATER: { label: "Νερό", emoji: "💧" },
    BIKER_CAFE: { label: "Στέκι μηχανόβιων", emoji: "☕" }, MOTO_SHOP: { label: "Συνεργείο μηχανών", emoji: "🔧" },
    WASH: { label: "Πλυντήριο", emoji: "🚿" }, PICNIC: { label: "Στάση/πικνίκ", emoji: "🧺" },
  };
  function spotKind(t) {
    if (t.tourism === "viewpoint") return "VIEW";
    if (t.mountain_pass === "yes") return "PASS";
    if (t.amenity === "drinking_water" || t.natural === "spring") return "WATER";
    if (t.shop === "motorcycle" || t.shop === "motorcycle_repair") return "MOTO_SHOP";
    if (t.amenity === "car_wash") return "WASH";
    if (t.tourism === "picnic_site") return "PICNIC";
    if (t.motorcycle === "yes" || t["motorcycle:friendly"] === "yes") return "BIKER_CAFE";
    return null;
  }
  async function spotsCell(i, j) {
    const key = `motonav.spots.${i}.${j}`;
    try {
      const c = JSON.parse(localStorage.getItem(key) || "null");
      if (c && Date.now() - c.t < 30 * 864e5) return c.list;
    } catch (e) { /* */ }
    const s = i * 0.25, w = j * 0.25, n = s + 0.25, e = w + 0.25;
    const els = await overpass(`[out:json][timeout:25][bbox:${s},${w},${n},${e}];(nwr["tourism"="viewpoint"];node["mountain_pass"="yes"];node["amenity"="drinking_water"];node["natural"="spring"]["drinking_water"!="no"];nwr["shop"~"^(motorcycle|motorcycle_repair)$"];nwr["amenity"="car_wash"];nwr["tourism"="picnic_site"];nwr["amenity"~"^(cafe|bar|restaurant)$"]["motorcycle"="yes"];);out center tags 3000;`);
    const list = els.map((el) => {
      const p = centerOf(el);
      const t = el.tags || {};
      const k = p && spotKind(t);
      if (!k) return null;
      return { id: el.type + el.id, k, lat: +p.lat.toFixed(5), lon: +p.lon.toFixed(5), n: t["name:el"] || t.name || "", ele: t.ele || "" };
    }).filter(Boolean);
    try { localStorage.setItem(key, JSON.stringify({ t: Date.now(), list })); } catch (e) { /* γεμάτο */ }
    return list;
  }

  // ---------- Καιρός ----------
  const SKY = (code) => code >= 95 ? "⛈" : code >= 71 && code <= 86 ? "❄" : code >= 51 ? "🌧" : code >= 45 ? "🌫" : code >= 2 ? "⛅" : "☀";
  async function weatherNow(p) {
    const r = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${p.lat.toFixed(3)}&longitude=${p.lon.toFixed(3)}&current=temperature_2m,weather_code,wind_speed_10m`);
    const c = (await r.json()).current;
    return { temp: Math.round(c.temperature_2m), icon: SKY(c.weather_code), wind: c.wind_speed_10m };
  }
  /** Καιρός πάνω στη διαδρομή την ώρα που θα περάσεις (σημεία ανά ~15 km). */
  async function weatherOnRoute(route) {
    const n = Math.min(12, Math.max(2, Math.ceil(route.length / 15000)));
    const samples = [];
    for (let k = 0; k <= n; k++) {
      const at = (route.length * k) / n;
      samples.push({ at, p: root.MotoNav.pointAt(route, at), t: Date.now() + route.duration * (at / route.length) * 1000 });
    }
    const lats = samples.map((s) => s.p.lat.toFixed(3)).join(","), lons = samples.map((s) => s.p.lon.toFixed(3)).join(",");
    const r = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lats}&longitude=${lons}&hourly=precipitation_probability,weather_code,temperature_2m,wind_speed_10m&forecast_days=2&timezone=auto`);
    let j = await r.json();
    if (!Array.isArray(j)) j = [j];
    return samples.map((s, i) => {
      const h = j[i] && j[i].hourly;
      if (!h) return null;
      let best = 0, bd = Infinity;
      h.time.forEach((t, k) => { const d = Math.abs(new Date(t).getTime() - s.t); if (d < bd) { bd = d; best = k; } });
      return { at: s.at, rain: h.precipitation_probability[best], code: h.weather_code[best], temp: h.temperature_2m[best], wind: h.wind_speed_10m[best] };
    }).filter(Boolean);
  }
  function weatherBad(w) {
    if (w.code >= 95) return "καταιγίδα";
    if (w.code >= 71 && w.code <= 86) return "χιόνι";
    if (w.rain >= 50) return "βροχή";
    if (w.wind >= 45) return "δυνατός αέρας";
    if (w.temp <= 2) return "παγωνιά";
    return null;
  }

  // ---------- ElevenLabs ----------
  async function elevenVoices(key) {
    const r = await fetch("https://api.elevenlabs.io/v1/voices", { headers: { "xi-api-key": key } });
    if (!r.ok) throw new Error("Το κλειδί ElevenLabs δεν δέχτηκε.");
    return ((await r.json()).voices || []).filter((v) => v.category === "premade" || v.category === "generated")
      .map((v) => ({ id: v.voice_id, name: v.name, preview: v.preview_url, desc: [v.labels && v.labels.gender, v.labels && v.labels.accent].filter(Boolean).join(" · ") }));
  }
  async function elevenSpeak(key, voiceId, text, en) {
    const r = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?output_format=mp3_44100_64`, {
      method: "POST",
      headers: { "xi-api-key": key, "Content-Type": "application/json" },
      body: JSON.stringify({ text, model_id: "eleven_flash_v2_5", language_code: en ? "en" : "el" }),
    });
    if (!r.ok) throw new Error("ElevenLabs " + r.status);
    return URL.createObjectURL(await r.blob());
  }

  root.MotoServices = { route, alternatives, search, reverse, category, CATEGORIES, fuelOnRoute, SPOT_KINDS, spotsCell,
    weatherNow, weatherOnRoute, weatherBad, elevenVoices, elevenSpeak };
})(typeof window !== "undefined" ? window : globalThis);

/*
 * Moto Nav web (PWA) για iPhone και υπολογιστή: ίδια διάταξη και ίδια μενού με την Android εφαρμογή,
 * με ό,τι δουλεύει σε browser. Δεν επηρεάζει την Android εφαρμογή (ξεχωριστός φάκελος, ανεβαίνει μόνο στο GitHub Pages).
 */
(function () {
  "use strict";
  const N = window.MotoNav, SV = window.MotoServices;
  const { Geo, pointAt, remaining, Engine, Phrases, Gpx, limitAt } = N;
  const $ = (id) => document.getElementById(id);
  const esc = (s) => String(s == null ? "" : s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const store = {
    get(k, d) { try { const v = localStorage.getItem("motonav." + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem("motonav." + k, JSON.stringify(v)); } catch (e) { toast("Δεν χωράει άλλο στη μνήμη του browser."); } },
  };

  // ---------- Ρυθμίσεις (ίδια ονόματα με την Android) ----------
  const DEF = {
    theme: "dark", accent: "#A78BFA", type: "fast", avMoto: false, avToll: false, avFerry: false, avMain: false, avNarrow: false, avUnpaved: false,
    moreCurves: false, showSpots: true, hiddenSpots: [], alternatives: true, cameras: true, limitVoice: true, overGlow: true, fuelAlert: true,
    weatherAlert: true, voice: true, english: false, voiceEngine: "phone", elevenKey: "", elevenVoice: "", ttKey: "", lean: false, compass: true,
    autoRecord: true, startPhrase: "Πάμε.", showFavs: true,
    uiScale: 0.9, roadColors: true, customStatus: true,
    marker: { shape: "CLASSIC", size: 84, fill: null, outline: true, outlineColor: "#FFFFFF", lineColor: null, lineOutlineColor: null, lineOutline: 2, image: null },
  };
  const P = Object.assign({}, DEF, store.get("prefs", {}));
  P.marker = Object.assign({}, DEF.marker, P.marker || {});
  const I = window.MotoIcons;
  function setPref(k, v) { P[k] = v; store.set("prefs", P); applyPrefs(k); }

  // ---------- Κατάσταση ----------
  const S = {
    me: null, dest: null, stops: [], via: [], track: null, route: null, alts: [], planning: 0, nav: false, engine: null, state: null,
    stage: {}, lastReroute: 0, rerouting: false, muted: false, follow: true, wake: null, anim: null, nogos: store.get("nogos", []),
    tempNogos: [], fuel: [], fuelShown: false, weather: [], sim: false, simSpeed: 1, rec: null, heading: null, said: {},
  };

  // ---------- Χάρτης ----------
  const STYLE_DARK = "https://tiles.openfreemap.org/styles/dark", STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty";
  const map = new maplibregl.Map({
    container: "map", style: P.theme === "light" ? STYLE_LIGHT : STYLE_DARK,
    center: store.get("center", [23.7275, 37.9838]), zoom: store.get("zoom", 11),
    // Οι πηγές του χάρτη (© OpenStreetMap, OpenFreeMap) είναι στις Ρυθμίσεις → Πηγές και άδειες, όπως στην Android.
    attributionControl: false,
  });
  map.on("moveend", () => {
    if (S.nav) return;
    const c = map.getCenter();
    store.set("center", [c.lng, c.lat]);
    store.set("zoom", map.getZoom());
    loadSpots();
  });
  // Στην πλοήγηση: με το πρώτο άγγιγμα ο χάρτης σταματάει να ακολουθεί (αλλιώς «κολλάει» το σύρσιμο),
  // και ξαναγυρίζει μόνος του 10″ μετά το τελευταίο άγγιγμα (ή αμέσως με ένα πάτημα στην πυξίδα).
  let touchT = 0, resumeTimer = 0;
  function pauseFollow() {
    if (!S.nav) return;
    S.follow = false; touchT = Date.now();
    clearTimeout(resumeTimer);
    resumeTimer = setTimeout(function chk() {
      if (!S.nav || S.follow) return;
      if (Date.now() - touchT < 10000) { resumeTimer = setTimeout(chk, 1000); return; }
      resumeFollow();
    }, 10000);
  }
  function resumeFollow() {
    clearTimeout(resumeTimer);
    S.follow = true; zNow = map.getZoom(); S.followIn = performance.now();
  }
  ["touchstart", "mousedown", "wheel"].forEach((ev) => map.getCanvasContainer().addEventListener(ev, pauseFollow, { passive: true }));
  map.on("dragstart", pauseFollow);
  const empty = { type: "FeatureCollection", features: [] };
  // Ίδια αντίθεση με τον σκούρο χάρτη της Android εφαρμογής (MapContrast.kt): γεμάτοι γκρι δρόμοι, ανοιχτά ονόματα.
  const CONTRAST_LINES = { highway_path: "#454A53", highway_minor: "#5A606B", highway_major_subtle: "#555B66", highway_major_casing: "#1B1D22",
    highway_major_inner: "#878D98", highway_motorway_subtle: "#6A707B", highway_motorway_casing: "#1B1D22", highway_motorway_inner: "#A6ACB6",
    railway: "#3A3E46", railway_minor: "#3A3E46", railway_transit: "#3A3E46" };
  const LABELS = ["water_name", "highway_name_other", "highway_name_motorway", "place_other", "place_suburb", "place_village", "place_town",
    "place_city", "place_city_large", "place_state", "place_country_other", "place_country_minor", "place_country_major"];
  function applyContrast() {
    if (document.documentElement.dataset.theme === "light") return;
    const set = (id, k, v) => { try { if (map.getLayer(id)) map.setPaintProperty(id, k, v); } catch (e) { /* */ } };
    set("background", "background-color", "#07080B");
    Object.entries(CONTRAST_LINES).forEach(([id, c]) => {
      set(id, "line-color", c);
      if (/path|minor|casing/.test(id)) set(id, "line-opacity", 1);
    });
    LABELS.forEach((id) => {
      set(id, "text-color", id.startsWith("highway") ? "#E3E6EB" : "#C9CDD4");
      set(id, "text-halo-color", "#07080B");
      set(id, "text-halo-width", 1.6);
    });
  }
  /** Η διαδρομή μπαίνει πάνω από όλους τους δρόμους/γέφυρες και κάτω από τα ονόματα (όπως στην Android). */
  const firstLabel = () => {
    const ls = (map.getStyle() && map.getStyle().layers) || [];
    let lastRoad = -1;
    ls.forEach((l, i) => { if (l.type === "line" && /highway|road|bridge|tunnel|transportation|railway|aeroway|building/.test(l.id)) lastRoad = i; });
    for (let i = lastRoad + 1; i < ls.length; i++) {
      const l = ls[i];
      if (l.type === "symbol" && !/^(route|alts|pts|nogo)/.test(l.id)) return l.id;
    }
    return undefined;
  };
  function addLayers() {
    applyContrast();
    if (map.getSource("route")) return;
    ["alts", "route", "pts", "nogo"].forEach((id) => map.addSource(id, { type: "geojson", data: empty }));
    map.addLayer({ id: "nogo-fill", type: "fill", source: "nogo", paint: { "fill-color": "#FF4D4D", "fill-opacity": 0.18 } }, firstLabel());
    map.addLayer({ id: "nogo-line", type: "line", source: "nogo", paint: { "line-color": "#FF4D4D", "line-width": 2, "line-dasharray": [2, 2] } }, firstLabel());
    map.addLayer({ id: "alts", type: "line", source: "alts", layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": "#8A8F9C", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 5, 16, 11], "line-opacity": 0.85 } }, firstLabel());
    map.addLayer({ id: "alts-label", type: "symbol", source: "alts", layout: { "symbol-placement": "line-center", "text-field": ["get", "label"],
      "text-font": ["Noto Sans Bold"], "text-size": 14 }, paint: { "text-color": "#fff", "text-halo-color": "#111", "text-halo-width": 2 } });
    map.addLayer({ id: "route-casing", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": accentNow(), "line-width": ["interpolate", ["linear"], ["zoom"], 10, 7, 16, 17] } }, firstLabel());
    map.addLayer({ id: "route-line", type: "line", source: "route", layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": "#FFFFFF", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 4, 16, 12] } }, firstLabel());
    map.addLayer({ id: "pts", type: "circle", source: "pts", paint: { "circle-radius": 11, "circle-color": accentNow(), "circle-stroke-color": "#0E0F13", "circle-stroke-width": 3 } });
    map.addLayer({ id: "pts-n", type: "symbol", source: "pts", filter: ["has", "n"],
      layout: { "text-field": ["get", "n"], "text-font": ["Noto Sans Bold"], "text-size": 12, "text-allow-overlap": true }, paint: { "text-color": "#0E0F13" } });
    setTimeout(drawPuck, 0);
    map.on("click", "alts", (e) => { const i = e.features[0].properties.i; if (S.alts[i]) pickAlt(i); });
    map.on("click", "nogo-fill", (e) => {
      const i = e.features[0].properties.i;
      if (i == null || !confirm("Διαγραφή της περιοχής αποφυγής;")) return;
      S.nogos.splice(i, 1); store.set("nogos", S.nogos); drawNogos(); if (S.dest) plan();
    });
    redraw();
  }
  map.on("load", () => { addLayers(); fromHash(); loadSpots(); drawCameras(); drawFavs(); });
  map.on("styledata", () => { if (!map.getSource("route")) addLayers(); });
  function redraw() {
    if (S.route) drawRoute(S.nav && S.state ? remaining(S.route, S.state.progress).map((p) => p) : S.route.points);
    drawPoints(); drawNogos(); drawAlts();
  }

  const puckEl = document.createElement("div");
  puckEl.className = "puck";
  function darker(hex, f) {
    const n = parseInt(hex.slice(1), 16);
    const c = (v) => Math.round(v * (1 - f)).toString(16).padStart(2, "0");
    return "#" + c(n >> 16) + c((n >> 8) & 255) + c(n & 255);
  }
  /** Το βελάκι θέσης και η γραμμή, όπως τα διάλεξες (Ρυθμίσεις → Εμφάνιση → Βελάκι και γραμμή). */
  function drawPuck() {
    const m = P.marker;
    const px = Math.round(Math.max(30, Math.min(140, m.size)) * 0.55);
    puckEl.style.width = puckEl.style.height = px + "px";
    puckEl.innerHTML = I.puck(Object.assign({}, m, { fill: m.fill || darker(accentNow(), 0.4) }));
    if (map.getLayer("route-line")) {
      map.setPaintProperty("route-line", "line-color", m.lineColor || "#FFFFFF");
      map.setPaintProperty("route-casing", "line-color", m.lineOutlineColor || accentNow());
      const o = m.lineOutline || 0;
      map.setPaintProperty("route-casing", "line-width", ["interpolate", ["linear"], ["zoom"], 10, 4 + o * 1.5, 16, 12 + o * 2.5]);
    }
  }
  const puck = new maplibregl.Marker({ element: puckEl, rotationAlignment: "map", pitchAlignment: "map" });

  // ---------- Μηνύματα ----------
  let toastTimer = 0;
  function toast(msg, ms) {
    const t = $("toast");
    t.textContent = msg;
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), ms || 2800);
  }

  // ---------- Θέμα ----------
  function applyPrefs(k) {
    document.documentElement.dataset.theme = P.theme === "auto" ? (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark") : P.theme;
    const light = document.documentElement.dataset.theme === "light";
    document.documentElement.style.setProperty("--accent", accentNow());
    document.documentElement.style.setProperty("--on-accent", isLight(accentNow()) ? "#0E0F13" : "#FFFFFF");
    document.querySelector('meta[name="theme-color"]').content = light ? "#FFFFFF" : "#0E0F13";
    if (k === "theme" || k === "_init") {
      const want = light ? STYLE_LIGHT : STYLE_DARK;
      if (k === "theme") map.setStyle(want);
    }
    if (map.getLayer("route-casing")) {
      map.setPaintProperty("route-casing", "line-color", accentNow());
      map.setPaintProperty("pts", "circle-color", accentNow());
    }
    Phrases.en = !!P.english;
    drawPuck();
    document.documentElement.style.setProperty("--ui", P.uiScale);
    document.querySelector(".pills").classList.toggle("hidden", !P.customStatus);
    showLean();
    if (k === "showSpots" || k === "hiddenSpots") drawSpots();
    if (k === "cameras") drawCameras();
    if (k === "showFavs") drawFavs();
    if (k && k.startsWith("av") || k === "moreCurves") if (S.dest && !S.nav) plan();
  }
  // Στο ανοιχτό θέμα: η πιο σκούρα εκδοχή κάθε χρώματος (όπως τα presets της Android).
  const LIGHT_OF = { "#A78BFA": "#6D28D9", "#FF7A7A": "#C62828", "#FB923C": "#B45309", "#FACC15": "#8A6A00", "#4ADE80": "#15803D",
    "#22D3EE": "#0E7490", "#60A5FA": "#1D4ED8", "#F472B6": "#BE185D", "#E5E7EB": "#374151" };
  function accentNow() { return document.documentElement.dataset.theme === "light" ? (LIGHT_OF[P.accent] || P.accent) : P.accent; }
  function isLight(hex) {
    const n = parseInt(hex.slice(1), 16);
    return (0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) > 150;
  }

  // ---------- Ώρα, καιρός, μπαταρία ----------
  function tick() {
    $("clock").textContent = new Date().toLocaleTimeString("el-GR", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
  }
  tick();
  setInterval(tick, 10000);
  let wxAt = 0;
  async function updateWeather() {
    if (!S.me || Date.now() - wxAt < 15 * 60000) return;
    wxAt = Date.now();
    try { const w = await SV.weatherNow(S.me); $("wx").textContent = `${w.icon} ${w.temp}°`; } catch (e) { wxAt = 0; }
  }
  if (navigator.getBattery) navigator.getBattery().then((b) => {
    const upd = () => {
      $("batt").classList.remove("hidden");
      $("batt").firstElementChild.style.width = Math.round(b.level * 100) + "%";
      $("batt").style.borderColor = b.level < 0.2 ? "var(--danger)" : "var(--green)";
      $("batt").firstElementChild.style.background = b.level < 0.2 ? "var(--danger)" : "var(--green)";
    };
    upd(); b.onlevelchange = upd;
  }).catch(() => { /* iPhone: όχι */ });

  // ---------- Πυξίδα ----------
  (function buildCompass() {
    const svg = $("compass");
    let s = '<g id="rose">';
    for (let a = 0; a < 360; a += 15) {
      const big = a % 45 === 0;
      const r1 = big ? 46 : 50, r2 = 56;
      const x1 = 60 + r1 * Math.sin(a * Math.PI / 180), y1 = 60 - r1 * Math.cos(a * Math.PI / 180);
      const x2 = 60 + r2 * Math.sin(a * Math.PI / 180), y2 = 60 - r2 * Math.cos(a * Math.PI / 180);
      s += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${a === 0 ? "#E11D48" : "currentColor"}" stroke-width="${a === 0 ? 4 : big ? 2.4 : 1.4}" stroke-linecap="round" opacity="${a === 0 ? 1 : 0.7}"/>`;
    }
    [["Α", 90], ["Ν", 180], ["Δ", 270]].forEach(([t, a]) => {
      const x = 60 + 37 * Math.sin(a * Math.PI / 180), y = 60 - 37 * Math.cos(a * Math.PI / 180) + 5;
      s += `<text x="${x}" y="${y}" text-anchor="middle" font-size="13" font-weight="700" fill="currentColor" opacity=".8" transform="rotate(${a} ${x} ${y - 5})">${t}</text>`;
    });
    svg.innerHTML = s + "</g>";
    svg.style.color = "var(--text)";
  })();
  function setHeading(h) {
    if (h == null || isNaN(h)) return;
    S.heading = h;
    const rose = document.getElementById("rose");
    if (rose) rose.setAttribute("transform", `rotate(${-h} 60 60)`);
  }
  $("gauge").onclick = () => {
    if (S.nav) { if (S.follow) showOverview(); else resumeFollow(); return; }
    if (S.me) map.easeTo({ center: [S.me.lon, S.me.lat], zoom: Math.max(map.getZoom(), 15), bearing: 0, pitch: 0 });
    else { wantCenter = true; startGps(); }
  };

  // ---------- GPS ----------
  let watchId = null, wantCenter = false;
  function startGps() {
    if (watchId != null || !navigator.geolocation) return;
    watchId = navigator.geolocation.watchPosition((pos) => { if (!S.sim) onFix(pos.coords); }, (e) => {
      if (e.code === 1) toast("Δεν επιτρέπεται η θέση. iPhone: Ρυθμίσεις → Απόρρητο → Υπηρεσίες τοποθεσίας → Safari.", 6000);
    }, { enableHighAccuracy: true, maximumAge: 1000, timeout: 20000 });
  }
  function onFix(c) {
    const prev = S.me;
    const here = { lat: c.latitude, lon: c.longitude };
    let heading = c.heading != null && !isNaN(c.heading) && (c.speed || 0) > 1 ? c.heading : null;
    if (heading == null && prev && (c.speed || 0) > 2 && Geo.distance(prev, here) > 4) heading = Geo.bearing(prev, here);
    S.me = Object.assign(here, { acc: c.accuracy || 20, heading: heading != null ? heading : prev && prev.heading,
      speed: c.speed != null && c.speed >= 0 ? c.speed : 0, alt: c.altitude, t: Date.now() });
    if (heading != null) setHeading(heading);
    $("gps").textContent = `◎ ±${Math.round(S.me.acc)}m`;
    $("alt").textContent = S.me.alt != null ? `${Math.round(S.me.alt)}m` : "–";
    showSpeed();
    updateWeather();
    record(S.me);
    if (S.nav) navTick();
    else {
      const moving = S.me.speed > 2;
      puck.setLngLat([S.me.lon, S.me.lat]).setRotation((moving ? S.me.heading : S.heading) || 0).addTo(map);
      if (wantCenter) { wantCenter = false; map.easeTo({ center: [S.me.lon, S.me.lat], zoom: Math.max(map.getZoom(), 14) }); }
    }
  }
  function showSpeed() {
    const kmh = S.me ? Math.round((S.me.speed || 0) * 3.6) : 0;
    $("speed").textContent = kmh;
    const lim = S.nav && S.route && S.state ? limitAt(S.route, S.state.progress) : null;
    $("limit").textContent = lim || "";
    $("limit").classList.toggle("hidden", !lim);
    const over = lim && kmh > lim + 3;
    $("gauge").classList.toggle("over", !!over);
    $("glow").classList.toggle("hidden", !(over && P.overGlow));
    if (lim && P.limitVoice && S.lastLimit !== lim) { if (S.lastLimit != null) say(Phrases.L(`Όριο ${lim}.`, `Speed limit ${lim}.`)); S.lastLimit = lim; }
  }

  // Το κλισιόμετρο φαίνεται μόνο στην πλοήγηση (αν είναι ανοιχτό στις Ρυθμίσεις → Όργανα).
  function showLean() {
    const on = !!P.lean && S.nav;
    $("lean").classList.toggle("hidden", !on);
    document.body.classList.toggle("lean-on", on);
  }
  const IOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  // ---------- Κλισιόμετρο και πυξίδα συσκευής (iPhone: θέλει άδεια) ----------
  let leanZero = store.get("leanZero", 0), leanMax = { l: 0, r: 0 }, leanNow = 0;
  async function enableSensors() {
    try {
      if (window.DeviceMotionEvent && DeviceMotionEvent.requestPermission) await DeviceMotionEvent.requestPermission();
      if (window.DeviceOrientationEvent && DeviceOrientationEvent.requestPermission) await DeviceOrientationEvent.requestPermission();
      store.set("sensorsGranted", true);
    } catch (e) { toast("Χωρίς άδεια για κίνηση και προσανατολισμό."); return false; }
    if (sensorsOn) return true;
    sensorsOn = true;
    window.addEventListener("devicemotion", onMotion);
    // Android (Chrome): η πυξίδα (σε σχέση με τον βορρά) έρχεται από το deviceorientationabsolute.
    if ("ondeviceorientationabsolute" in window) window.addEventListener("deviceorientationabsolute", onOrient);
    else window.addEventListener("deviceorientation", onOrient);
    return true;
  }
  function onMotion(e) {
    const g = e.accelerationIncludingGravity;
    if (!g || g.x == null) return;
    const ang = (screen.orientation && screen.orientation.angle) || window.orientation || 0;
    // Το iPhone δίνει τη βαρύτητα με αντίθετο πρόσημο από το Android.
    let x = IOS ? -g.x : g.x, y = IOS ? -g.y : g.y;
    if (ang === 90) [x, y] = [-y, x]; else if (ang === -90 || ang === 270) [x, y] = [y, -x]; else if (ang === 180) [x, y] = [-x, -y];
    // 0° όρθιο, θετικό = κλίση δεξιά.
    // Κινητό σχεδόν ξαπλωμένο: η κλίση δεν βγαίνει σωστά (πηδάει) → δεν την αλλάζουμε.
    if (Math.hypot(x, y) < 5) return;
    const roll = Math.atan2(-x, y) * 180 / Math.PI;
    // Πιο σταθερό: εξομάλυνση ~0,5″ και «νεκρή ζώνη» 2° γύρω από το όρθιο.
    const target = roll - leanZero;
    leanNow = leanNow * 0.93 + target * 0.07;
    if (S.me && S.me.speed > 5) { if (leanNow < 0) leanMax.l = Math.max(leanMax.l, -leanNow); else leanMax.r = Math.max(leanMax.r, leanNow); }
    if (!P.lean) return;
    let v = Math.max(-70, Math.min(70, Math.round(leanNow)));
    if (Math.abs(v) <= 2) v = 0;
    $("leanV").textContent = `${Math.abs(v)}°`;
    $("leanL").style.setProperty("--w", `${Math.min(100, Math.max(0, -v) / 50 * 100)}%`);
    $("leanR").style.setProperty("--w", `${Math.min(100, Math.max(0, v) / 50 * 100)}%`);
  }
  let sensorsOn = false;
  /**
   * Πού κοιτάει το κινητό (μοίρες από τον βορρά), με διόρθωση της κλίσης:
   * όρθιο στη βάση της μηχανής → η κατεύθυνση της πλάτης του· ξαπλωμένο → η κατεύθυνση της πάνω άκρης της οθόνης.
   */
  function deviceHeading(e) {
    const r = Math.PI / 180, a = e.alpha * r, b = (e.beta || 0) * r, g = (e.gamma || 0) * r;
    const cA = Math.cos(a), sA = Math.sin(a), cB = Math.cos(b), sB = Math.sin(b), cG = Math.cos(g), sG = Math.sin(g);
    const zUp = cB * cG; // πόσο «κοιτάει πάνω» η οθόνη
    let x, y;
    if (Math.abs(zUp) > 0.7) {
      // Ξαπλωμένο: η πάνω άκρη της οθόνης (με τη γωνία της οθόνης για το οριζόντιο).
      const ang = ((screen.orientation && screen.orientation.angle) || window.orientation || 0) * r;
      const tx = -sA * cB, ty = cA * cB;
      const rx = cA * cG - sA * sB * sG, ry = sA * cG + cA * sB * sG;
      x = tx * Math.cos(ang) + rx * Math.sin(ang);
      y = ty * Math.cos(ang) + ry * Math.sin(ang);
    } else {
      // Όρθιο: η πλάτη του κινητού (προς τα εκεί που πας).
      x = -(cA * sG + sA * sB * cG);
      y = -(sA * sG - cA * sB * cG);
    }
    return (Math.atan2(x, y) * 180 / Math.PI + 360) % 360;
  }
  function onOrient(e) {
    const moving = S.me && S.me.speed > 2;
    if (moving || S.sim || S.nav) return; // στην πλοήγηση το βελάκι ακολουθεί μόνο τη διαδρομή
    let h = e.webkitCompassHeading != null ? e.webkitCompassHeading : e.alpha != null && e.absolute !== false ? deviceHeading(e) : null;
    if (h == null || isNaN(h)) return;
    // Λίγο εξομάλυνση (η πυξίδα του κινητού «τρέμει»).
    if (S.heading != null) {
      const d = ((h - S.heading + 540) % 360) - 180;
      if (Math.abs(d) < 3) return; // μικρό «τρέμουλο» της πυξίδας: αγνοείται
      h = (S.heading + d * (Math.abs(d) > 40 ? 0.25 : 0.08) + 360) % 360;
    }
    setHeading(h);
    // Σταματημένος: το βελάκι γυρίζει με την πυξίδα (όπως στην Android εφαρμογή).
    if (!S.nav && S.me) puck.setRotation(h);
  }

  // ---------- Αναζήτηση ----------
  let qTimer = 0, qSeq = 0;
  $("searchBtn").onclick = () => { openSearch(); };
  $("searchBack").onclick = () => $("search").classList.add("hidden");
  function openSearch() {
    $("search").classList.remove("hidden");
    $("q").value = "";
    $("results").innerHTML = "";
    renderCats();
    renderSearchHome();
    setTimeout(() => $("q").focus(), 50);
  }
  function renderCats() {
    const box = $("cats");
    box.innerHTML = "";
    Object.entries(SV.CATEGORIES).forEach(([k, c]) => {
      const b = document.createElement("button");
      b.textContent = `${c.emoji} ${c.label}`;
      b.onclick = () => searchCategory(k);
      box.appendChild(b);
    });
    const loop = document.createElement("button");
    loop.textContent = "🔁 Κυκλική βόλτα";
    loop.onclick = loopDialog;
    box.appendChild(loop);
  }
  function renderSearchHome() {
    const box = $("searchHome");
    box.innerHTML = "";
    const lists = App.lists();
    const hist = store.get("history", []);
    const favs = lists.flatMap((l) => l.items.map((it) => Object.assign({ color: l.color, list: l.name }, it)));
    if (favs.length) {
      box.insertAdjacentHTML("beforeend", '<div class="h3">Αγαπημένα</div>');
      const wrap = document.createElement("div");
      wrap.className = "list";
      favs.slice(0, 30).forEach((it) => wrap.appendChild(resultItem(it, `<span style="color:${it.color}">●</span> ${esc(it.list)}`)));
      box.appendChild(wrap);
    }
    if (hist.length) {
      box.insertAdjacentHTML("beforeend", '<div class="h3">Πρόσφατα</div>');
      const wrap = document.createElement("div");
      wrap.className = "list";
      hist.slice(0, 15).forEach((it) => wrap.appendChild(resultItem(it, "🕘")));
      box.appendChild(wrap);
    }
  }
  function resultItem(it, sub) {
    const b = document.createElement("button");
    b.className = "item";
    const d = S.me ? Phrases.short(Geo.distance(S.me, it)) : "";
    b.innerHTML = `<span class="t">${esc(it.name)}<small>${sub || esc(it.sub || "")}</small></span><span class="muted">${d}</span>`;
    b.onclick = () => { $("search").classList.add("hidden"); $("q").blur(); setDest(it); };
    return b;
  }
  $("q").addEventListener("input", () => {
    clearTimeout(qTimer);
    const q = $("q").value.trim();
    $("searchHome").classList.toggle("hidden", q.length > 0);
    if (q.length < 2) { $("results").innerHTML = ""; return; }
    qTimer = setTimeout(() => doSearch(q), 350);
  });
  $("q").addEventListener("keydown", (e) => { if (e.key === "Enter") { clearTimeout(qTimer); doSearch($("q").value.trim()); } });
  async function doSearch(q) {
    const seq = ++qSeq;
    const box = $("results");
    const m = q.match(/^\s*(-?\d+(?:\.\d+)?)\s*[, ]\s*(-?\d+(?:\.\d+)?)\s*$/);
    if (m) { box.innerHTML = ""; box.appendChild(resultItem({ lat: +m[1], lon: +m[2], name: q, sub: "Συντεταγμένες" })); return; }
    const near = S.me || { lat: map.getCenter().lat, lon: map.getCenter().lng };
    try {
      const list = await SV.search(q, near);
      if (seq !== qSeq) return;
      box.innerHTML = list.length ? "" : '<p class="foot">Δεν βρέθηκε τίποτα.</p>';
      list.forEach((it) => box.appendChild(resultItem(it)));
    } catch (e) { box.innerHTML = '<p class="foot">Χωρίς σύνδεση: η αναζήτηση θέλει internet.</p>'; }
  }
  async function searchCategory(k) {
    const box = $("results");
    $("searchHome").classList.add("hidden");
    box.innerHTML = `<p class="foot">Ψάχνω ${SV.CATEGORIES[k].label.toLowerCase()} κοντά σου…</p>`;
    const near = S.me || { lat: map.getCenter().lat, lon: map.getCenter().lng };
    try {
      const list = await SV.category(k, near);
      box.innerHTML = list.length ? "" : '<p class="foot">Δεν βρέθηκε τίποτα κοντά.</p>';
      list.forEach((it) => box.appendChild(resultItem(it, `${SV.CATEGORIES[k].emoji} ${esc(it.sub || SV.CATEGORIES[k].label)}`)));
    } catch (e) { box.innerHTML = '<p class="foot">Η υπηρεσία δεν απάντησε. Ξαναδοκίμασε σε λίγο.</p>'; }
  }
  function loopDialog() {
    const box = $("results");
    $("searchHome").classList.add("hidden");
    box.innerHTML = '<p class="foot">Κυκλική βόλτα με στροφές από εκεί που είσαι, για πόση ώρα;</p>';
    const row = document.createElement("div");
    row.className = "chips wrap";
    [[30, "30′"], [60, "1 ώρα"], [90, "1,5 ώρα"], [120, "2 ώρες"], [180, "3 ώρες"]].forEach(([min, label]) => {
      const b = document.createElement("button");
      b.textContent = label;
      b.onclick = () => { $("search").classList.add("hidden"); planLoop(min * 60); };
      row.appendChild(b);
    });
    box.appendChild(row);
  }

  // ---------- Παρατεταμένο πάτημα ----------
  let pressTimer = 0, pressAt = null, pressStart = null;
  map.on("touchstart", (e) => {
    if (e.originalEvent.touches.length !== 1) return clearTimeout(pressTimer);
    const t = e.originalEvent.touches[0];
    pressStart = { x: t.clientX, y: t.clientY };
    const ll = e.lngLat;
    clearTimeout(pressTimer);
    pressTimer = setTimeout(() => openPop({ lat: ll.lat, lon: ll.lng }), 550);
  });
  map.on("touchmove", (e) => {
    const t = e.originalEvent.touches[0];
    if (!pressStart || !t || Math.hypot(t.clientX - pressStart.x, t.clientY - pressStart.y) > 10) clearTimeout(pressTimer);
  });
  map.on("touchend", () => clearTimeout(pressTimer));
  map.on("touchcancel", () => clearTimeout(pressTimer));
  map.on("contextmenu", (e) => openPop({ lat: e.lngLat.lat, lon: e.lngLat.lng }));
  map.on("click", () => closePop());
  // Καρφίτσα στο σημείο που πάτησες (μένει όσο είναι ανοιχτό το μενού του σημείου)
  let pressPin = null;
  function closePop() { $("pop").classList.add("hidden"); if (pressPin) { pressPin.remove(); pressPin = null; } }
  async function openPop(p, name) {
    if (S.nav) return;
    pressAt = p;
    if (name) p.name = name;
    $("popTitle").textContent = name || `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}`;
    $("popStop").classList.toggle("hidden", !S.dest);
    $("pop").classList.remove("hidden");
    if (pressPin) pressPin.remove();
    pressPin = new maplibregl.Marker({ color: accentNow(), anchor: "bottom" }).setLngLat([p.lon, p.lat]).addTo(map);
    if (navigator.vibrate) navigator.vibrate(20);
    if (!name) {
      const n = await SV.reverse(p);
      if (n && pressAt === p) { p.name = n; $("popTitle").textContent = n; }
    }
  }
  $("popGo").onclick = () => { closePop(); if (pressAt) setDest(pressAt); };
  $("popStop").onclick = () => {
    closePop();
    if (!pressAt) return;
    S.stops.push({ lat: pressAt.lat, lon: pressAt.lon, name: pressAt.name || `Στάση ${S.stops.length + 1}` });
    plan();
  };
  $("popFav").onclick = () => { closePop(); if (pressAt) App.addFavorite(pressAt); };

  // ---------- Προορισμός και διαδρομή ----------
  function setDest(p) {
    S.dest = { lat: p.lat, lon: p.lon, name: p.name || `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}` };
    S.stops = []; S.via = []; S.track = null;
    const hist = store.get("history", []).filter((h) => Geo.distance(h, S.dest) > 30);
    hist.unshift(S.dest);
    store.set("history", hist.slice(0, 30));
    plan();
  }
  function opts(extra) {
    return Object.assign({ type: P.type, avMoto: P.avMoto, avToll: P.avToll, avFerry: P.avFerry, avMain: P.avMain, avNarrow: P.avNarrow,
      avUnpaved: P.avUnpaved, moreCurves: P.moreCurves, ttKey: P.ttKey, nogos: S.nogos.concat(S.tempNogos) }, extra || {});
  }
  const waypoints = (from) => [from].concat(S.via, S.stops, [S.dest]);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  async function plan() {
    if (!S.dest) return;
    const seq = ++S.planning;
    showCard();
    setMsg("Υπολογίζω διαδρομή…");
    $("startBtn").disabled = true;
    S.alts = []; drawAlts();
    startGps();
    for (let i = 0; i < 16 && !S.me; i++) await sleep(250);
    const from = S.me || { lat: map.getCenter().lat, lon: map.getCenter().lng };
    if (!S.me) toast("Χωρίς θέση GPS: η διαδρομή ξεκινάει από το κέντρο του χάρτη.");
    try {
      const r = await SV.route(waypoints(from), opts());
      if (seq !== S.planning) return;
      setRoute(r);
      fitRoute(r);
      setMsg(null);
      $("startBtn").disabled = false;
      afterRoute(seq);
      if (P.alternatives && r.length > 5000) loadAlternatives(seq, from);
    } catch (e) {
      if (seq !== S.planning) return;
      S.route = null;
      drawRoute([]); drawPoints(); renderCard();
      setMsg(e.message || "Δεν βγήκε διαδρομή.", true);
    }
  }
  function setRoute(r) {
    S.route = r;
    S.fuel = []; S.weather = [];
    drawRoute(r.points); drawPoints(); renderCard();
  }
  async function afterRoute(seq) {
    const r = S.route;
    SV.fuelOnRoute(r).then((f) => { if (seq === S.planning && S.route === r) { S.fuel = f; renderCard(); } }).catch(() => { S.fuel = null; renderCard(); });
    SV.weatherOnRoute(r).then((w) => { if (seq === S.planning && S.route === r) { S.weather = w; renderCard(); } }).catch(() => {});
  }
  async function loadAlternatives(seq, from, asked) {
    try {
      const alts = await SV.alternatives(waypoints(from), opts(), S.route);
      if (seq !== S.planning) return;
      S.alts = alts;
      drawAlts();
      if (!alts.length && asked) toast("Καμία λογική εναλλακτική.");
      else if (alts.length && asked) toast("Πάτα μια γκρι γραμμή για να τη διαλέξεις.");
    } catch (e) { /* χωρίς εναλλακτικές */ }
  }
  function pickAlt(i) {
    const chosen = S.alts[i];
    S.alts[i] = S.route;
    setRoute(chosen);
    drawAlts();
    afterRoute(S.planning);
  }
  function drawAlts() {
    const src = map.getSource("alts");
    if (!src) return;
    const main = S.route;
    src.setData({ type: "FeatureCollection", features: S.nav ? [] : S.alts.map((r, i) => ({
      type: "Feature", properties: { i, label: main ? `${r.duration >= main.duration ? "+" : "−"}${Math.round(Math.abs(r.duration - main.duration) / 60)}′` : "" },
      geometry: { type: "LineString", coordinates: r.points.map((p) => [p.lon, p.lat]) },
    })) });
  }
  async function planLoop(seconds) {
    startGps();
    for (let i = 0; i < 16 && !S.me; i++) await sleep(250);
    if (!S.me) { toast("Χρειάζομαι τη θέση σου για κυκλική βόλτα."); return; }
    const start = { lat: S.me.lat, lon: S.me.lon };
    showCard();
    setMsg("Ψάχνω κυκλική βόλτα με στροφές…");
    const lengthM = seconds * 50 / 3.6;
    const turn = Math.floor(Math.random() * 90);
    const tries = await Promise.allSettled([0, 90, 180, 270].map((d) => {
      const via = N.loopPoints(start, lengthM * (0.9 + Math.random() * 0.2), (d + turn) % 360);
      return SV.route([start].concat(via, [start]), opts({ type: "twisty", quick: true })).then((r) => ({ r, via }));
    }));
    const ok = tries.filter((t) => t.status === "fulfilled").map((t) => t.value);
    if (!ok.length) { setMsg("Δεν βγήκε κυκλική βόλτα εδώ.", true); return; }
    ok.sort((a, b) => Math.abs(a.r.duration - seconds) / seconds - N.twistiness(a.r.points) / 3000 - (Math.abs(b.r.duration - seconds) / seconds - N.twistiness(b.r.points) / 3000));
    const best = ok[0];
    S.dest = Object.assign({}, start, { name: "Κυκλική βόλτα" });
    S.via = best.via; S.stops = []; S.track = null;
    S.planning++;
    setRoute(best.r); fitRoute(best.r); setMsg(null); $("startBtn").disabled = false; afterRoute(S.planning);
  }

  function drawRoute(points) {
    const src = map.getSource("route");
    if (src) src.setData(points.length > 1 ? { type: "Feature", geometry: { type: "LineString", coordinates: points.map((p) => [p.lon, p.lat]) } } : empty);
  }
  function drawPoints() {
    const src = map.getSource("pts");
    if (!src) return;
    const f = S.stops.map((s, i) => ({ type: "Feature", properties: { n: String(i + 1) }, geometry: { type: "Point", coordinates: [s.lon, s.lat] } }));
    if (S.dest) f.push({ type: "Feature", properties: {}, geometry: { type: "Point", coordinates: [S.dest.lon, S.dest.lat] } });
    src.setData({ type: "FeatureCollection", features: f });
  }
  function circle(c, r, props) {
    const ring = [];
    for (let a = 0; a <= 360; a += 10) { const p = N.destPoint(c, r, a); ring.push([p.lon, p.lat]); }
    return { type: "Feature", properties: props, geometry: { type: "Polygon", coordinates: [ring] } };
  }
  function drawNogos() {
    const src = map.getSource("nogo");
    if (src) src.setData({ type: "FeatureCollection", features: S.nogos.map((n, i) => circle(n, n.r, { i })).concat(S.tempNogos.map((n) => circle(n, n.r, {}))) });
  }
  function fitRoute(r) {
    const b = new maplibregl.LngLatBounds();
    r.points.forEach((p) => b.extend([p.lon, p.lat]));
    S.alts.forEach((a) => a.points.forEach((p) => b.extend([p.lon, p.lat])));
    const land = innerWidth > innerHeight && innerHeight < 600;
    const cardH = land ? 0 : Math.min($("card").offsetHeight || 320, innerHeight * 0.62);
    const pad = land ? { top: 40, bottom: 30, left: 470, right: 150 } : { top: 70, bottom: cardH + 30, left: 40, right: 40 };
    try { map.fitBounds(b, { padding: pad, bearing: 0, pitch: 0, duration: 700, maxZoom: 16 }); } catch (e) { /* μικρή οθόνη */ }
  }

  // ---------- Κάρτα ----------
  function showCard() {
    $("card").classList.remove("hidden");
    $("rightCol").classList.add("hidden");
    closePop();
  }
  function hideCard() {
    $("card").classList.add("hidden");
    $("rightCol").classList.remove("hidden");
  }
  function fmtDur(s) {
    const m = Math.round(s / 60);
    return m < 60 ? `${m} λεπτά` : `${Math.floor(m / 60)} ώ ${m % 60} λ`;
  }
  function renderCard() {
    const r = S.route;
    $("cardTime").textContent = r ? fmtDur(r.duration) : "—";
    $("cardDist").textContent = r ? Phrases.short(r.length) : "";
    $("cardDist").classList.toggle("hidden", !r);
    $("cardName").textContent = S.dest ? S.dest.name : "";
    $("cardCoords").textContent = S.dest ? `${S.dest.lat.toFixed(6)}, ${S.dest.lon.toFixed(6)}` : "";
    $("revBtn").classList.toggle("hidden", !S.track);
    const tw = r ? N.twistiness(r.points) : 0;
    const stars = tw < 60 ? 1 : tw < 120 ? 2 : tw < 200 ? 3 : tw < 300 ? 4 : 5;
    $("stars").textContent = r ? "●".repeat(stars) + "○".repeat(5 - stars) : "";
    $("stars").title = "Στροφές";
    const st = $("cardStops");
    st.innerHTML = "";
    S.stops.forEach((s, i) => {
      const el = document.createElement("span");
      el.innerHTML = `<b>${i + 1}</b>${esc(s.name)}<button aria-label="Αφαίρεση">✕</button>`;
      el.querySelector("button").onclick = () => { S.stops.splice(i, 1); plan(); };
      st.appendChild(el);
    });
    if (S.track) { const el = document.createElement("span"); el.textContent = "Ακολουθεί έτοιμη διαδρομή (περίπου)"; st.appendChild(el); }
    document.querySelectorAll(".type[data-type]").forEach((b) => b.classList.toggle("on", b.dataset.type === P.type));
    $("avMotoBtn").classList.toggle("on", P.avMoto);
    $("avTollBtn").classList.toggle("on", P.avToll);
    drawElevation(r);
    const info = $("cardInfo");
    info.innerHTML = "";
    if (r) {
      if (S.fuel === null) info.insertAdjacentHTML("beforeend", '<div class="muted">⛽ Τα βενζινάδικα δεν φόρτωσαν.</div>');
      else if (S.fuel.length) {
        let gap = S.fuel[0].along, prev = S.fuel[0].along;
        S.fuel.forEach((f) => { gap = Math.max(gap, f.along - prev); prev = f.along; });
        gap = Math.max(gap, r.length - prev);
        const el = document.createElement("div");
        el.className = "fuel" + (S.fuelShown ? " on" : "");
        el.textContent = `⛽ ${S.fuel.length} στη διαδρομή · πρώτο σε ${Phrases.short(S.fuel[0].along)} · μεγαλύτερο κενό ${Phrases.short(gap)}`;
        el.onclick = () => { S.fuelShown = !S.fuelShown; drawFuel(); renderCard(); };
        info.appendChild(el);
      }
      const bad = S.weather.map((w) => ({ w, why: SV.weatherBad(w) })).find((x) => x.why);
      if (bad) info.insertAdjacentHTML("beforeend", `<div style="color:var(--warn)">🌧 ${esc(bad.why)} σε ${Phrases.short(bad.w.at)} (όταν θα περνάς)</div>`);
      else if (S.weather.length) {
        const t = S.weather.map((w) => w.temp);
        info.insertAdjacentHTML("beforeend", `<div class="muted">☀ Καλός καιρός στη διαδρομή · ${Math.round(Math.min(...t))}–${Math.round(Math.max(...t))}°</div>`);
      }
      if (r.source === "tomtom" && r.noTraffic) {
        const d = Math.round((r.duration - r.noTraffic) / 60);
        info.insertAdjacentHTML("beforeend", `<div class="muted">🚦 ${d > 0 ? `+${d}′ από την κίνηση` : "Χωρίς κίνηση"}</div>`);
      }
    }
    $("startBtn").textContent = r ? "Ξεκίνα" : "Υπολογισμός…";
  }
  function setMsg(t, err) {
    const m = $("cardMsg");
    m.textContent = t || "";
    m.classList.toggle("hidden", !t);
    m.classList.toggle("err", !!err);
    if (err) $("startBtn").textContent = "Ξεκίνα";
  }
  document.querySelectorAll(".type[data-type]").forEach((b) => b.onclick = () => {
    if (b.dataset.type === "traffic" && !P.ttKey) { toast("Για «Κίνηση» βάλε δωρεάν κλειδί TomTom: Ρυθμίσεις → Κλειδιά.", 4500); return; }
    setPref("type", b.dataset.type);
    plan();
  });
  $("avMotoBtn").onclick = () => { setPref("avMoto", !P.avMoto); };
  $("avTollBtn").onclick = () => { setPref("avToll", !P.avToll); };
  $("cardClose").onclick = closeCard;
  function closeCard() {
    S.dest = null; S.stops = []; S.via = []; S.track = null; S.route = null; S.alts = []; S.fuel = []; S.fuelShown = false; S.planning++;
    drawRoute([]); drawPoints(); drawAlts(); drawFuel(); hideCard();
    history.replaceState(null, "", location.pathname);
  }
  $("cardHandle").onclick = () => $("card").classList.toggle("collapsed");
  ["cardStops", "elev", "cardInfo", "nogoRow"].forEach((id) => $(id).classList.add("collapsible"));
  $("favBtn").onclick = () => { if (S.dest) App.addFavorite(S.dest); };
  $("addStopBtn").onclick = () => { toast("Κράτα πατημένο το σημείο στον χάρτη → «+ Στάση».", 3500); };
  $("altBtn").onclick = () => {
    if (!S.route) return;
    if (S.alts.length) { toast("Πάτα μια γκρι γραμμή για να τη διαλέξεις."); return; }
    toast("Ψάχνω εναλλακτικές…");
    loadAlternatives(S.planning, S.me || S.route.points[0], true);
  };
  $("nogoBtn").onclick = () => $("nogoRow").classList.toggle("hidden");
  $("nogoRow").querySelectorAll("button").forEach((b) => b.onclick = () => {
    if (!S.dest) return;
    S.nogos.push({ lat: S.dest.lat, lon: S.dest.lon, r: +b.dataset.r });
    store.set("nogos", S.nogos);
    $("nogoRow").classList.add("hidden");
    toast("Η διαδρομή θα αποφεύγει αυτή την περιοχή. Πάτα πάνω της για διαγραφή.", 4000);
    drawNogos();
  });
  $("revBtn").onclick = () => { if (S.track) App.followTrack(S.track.slice().reverse(), S.dest.name); };
  $("saveRouteBtn").onclick = () => {
    if (!S.dest) return;
    const name = prompt("Όνομα διαδρομής:", S.dest.name);
    if (!name) return;
    const list = store.get("saved", []);
    list.unshift({ id: Date.now(), name, type: P.type, dest: S.dest, stops: S.stops, via: S.via, track: S.track ? Gpx.sample(S.track, 400) : null,
      km: S.route ? Math.round(S.route.length / 100) / 10 : null });
    store.set("saved", list.slice(0, 100));
    toast("Αποθηκεύτηκε στις διαδρομές (Ρυθμίσεις → Σελιδοδείκτες και διαδρομές).", 3500);
  };
  $("shareBtn").onclick = async () => {
    if (!S.dest) return;
    const url = shareUrl();
    try {
      if (navigator.share) await navigator.share({ title: `Moto Nav: ${S.dest.name}`, url });
      else { await navigator.clipboard.writeText(url); toast("Ο σύνδεσμος αντιγράφηκε."); }
    } catch (e) { /* ακύρωση */ }
  };
  $("gpxBtn").onclick = () => { if (S.route) App.exportGpx(S.dest ? S.dest.name : "Διαδρομή", S.route.points); };

  function drawElevation(r) {
    const cv = $("elev");
    const ctx = cv.getContext("2d");
    const w = cv.clientWidth || 300, h = cv.clientHeight || 86, dpr = devicePixelRatio || 1;
    cv.width = w * dpr; cv.height = h * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    const pts = [];
    if (r) r.elevations.forEach((e, i) => { if (e != null) pts.push([r.cum[i], e]); });
    if (pts.length < 2) { cv.style.display = "none"; return; }
    cv.style.display = "block";
    let lo = Math.min(...pts.map((p) => p[1])), hi = Math.max(...pts.map((p) => p[1]));
    if (hi - lo < 20) hi = lo + 20;
    const cs = getComputedStyle(document.documentElement);
    const X = (d) => (d / r.length) * w, Y = (e) => h - 22 - ((e - lo) / (hi - lo)) * (h - 28);
    ctx.beginPath(); ctx.moveTo(0, h - 22);
    pts.forEach((p) => ctx.lineTo(X(p[0]), Y(p[1])));
    ctx.lineTo(w, h - 22); ctx.closePath();
    ctx.fillStyle = accentNow() + "33"; ctx.fill();
    ctx.beginPath();
    pts.forEach((p, i) => (i ? ctx.lineTo(X(p[0]), Y(p[1])) : ctx.moveTo(X(p[0]), Y(p[1]))));
    ctx.strokeStyle = accentNow(); ctx.lineWidth = 2.5; ctx.stroke();
    let up = 0, down = 0;
    for (let i = 1; i < pts.length; i++) { const d = pts[i][1] - pts[i - 1][1]; if (d > 0) up += d; else down -= d; }
    ctx.fillStyle = cs.getPropertyValue("--text"); ctx.font = "600 16px system-ui";
    ctx.fillText(`↑ ${Math.round(up)} m  ↓ ${Math.round(down)} m`, 0, h - 2);
    ctx.fillStyle = cs.getPropertyValue("--muted");
    const range = `${Math.round(lo)} m – ${Math.round(hi)} m`;
    ctx.fillText(range, w - ctx.measureText(range).width, h - 2);
  }

  // ---------- Σύμβολα στον χάρτη (βενζινάδικα, μέρη, κάμερες, αγαπημένα) ----------
  let fuelMarkers = [], spotMarkers = [], camMarkers = [], favMarkers = [];
  function clear(list) { list.forEach((m) => m.remove()); list.length = 0; }
  function drawFuel() {
    clear(fuelMarkers);
    let list = [];
    if (S.nav && S.state) list = (S.fuel || []).filter((f) => f.along > S.state.progress).slice(0, 1);
    else if (S.fuelShown) list = S.fuel || [];
    list.forEach((f) => {
      const el = document.createElement("div");
      el.className = "fuelm"; el.textContent = "⛽"; el.title = f.name;
      fuelMarkers.push(new maplibregl.Marker({ element: el }).setLngLat([f.lon, f.lat]).addTo(map));
    });
  }
  let spots = [], spotCells = {};
  async function loadSpots() {
    if (!P.showSpots || map.getZoom() < 11) { drawSpots(); return; }
    const b = map.getBounds();
    const cells = [];
    for (let i = Math.floor(b.getSouth() / 0.25); i <= Math.floor(b.getNorth() / 0.25); i++)
      for (let j = Math.floor(b.getWest() / 0.25); j <= Math.floor(b.getEast() / 0.25); j++) cells.push([i, j]);
    if (cells.length > 6) return;
    await Promise.all(cells.map(async ([i, j]) => {
      const k = i + "," + j;
      if (spotCells[k]) return;
      spotCells[k] = "loading";
      try { spotCells[k] = await SV.spotsCell(i, j); } catch (e) { delete spotCells[k]; }
    }));
    spots = Object.values(spotCells).filter(Array.isArray).flat();
    drawSpots();
  }
  function drawSpots() {
    clear(spotMarkers);
    if (!P.showSpots || map.getZoom() < 11) return;
    const b = map.getBounds();
    spots.filter((s) => !P.hiddenSpots.includes(s.k) && b.contains([s.lon, s.lat])).slice(0, 250).forEach((s) => {
      const el = document.createElement("div");
      el.className = "spot";
      el.textContent = SV.SPOT_KINDS[s.k].emoji;
      el.onclick = (e) => { e.stopPropagation(); openPop({ lat: s.lat, lon: s.lon }, [s.n || SV.SPOT_KINDS[s.k].label, s.ele && s.ele + " m"].filter(Boolean).join(" · ")); };
      spotMarkers.push(new maplibregl.Marker({ element: el }).setLngLat([s.lon, s.lat]).addTo(map));
    });
  }
  map.on("zoomend", drawSpots);
  let cameras = [];
  fetch("cameras.json").then((r) => r.json()).then((c) => { cameras = c; drawCameras(); }).catch(() => {});
  function drawCameras() {
    clear(camMarkers);
    if (!P.cameras) return;
    cameras.forEach((c) => {
      const el = document.createElement("div");
      el.className = "camm"; el.innerHTML = I.camera; el.title = c.label || "Κάμερα";
      camMarkers.push(new maplibregl.Marker({ element: el }).setLngLat([c.lo, c.la]).addTo(map));
    });
  }
  function drawFavs() {
    clear(favMarkers);
    if (!P.showFavs) return;
    App.lists().forEach((l) => l.items.forEach((it) => {
      const el = document.createElement("div");
      el.className = "favm"; el.style.background = l.color; el.title = it.name;
      el.onclick = (e) => { e.stopPropagation(); openPop({ lat: it.lat, lon: it.lon }, it.name); };
      favMarkers.push(new maplibregl.Marker({ element: el }).setLngLat([it.lon, it.lat]).addTo(map));
    }));
  }

  // ---------- Πλοήγηση ----------
  $("startBtn").onclick = () => startNav(false);
  // Παρατεταμένο πάτημα στο «Ξεκίνα»: προσομοίωση.
  let startPress = 0;
  $("startBtn").addEventListener("touchstart", () => { startPress = setTimeout(() => { startPress = -1; startNav(true); }, 650); }, { passive: true });
  $("startBtn").addEventListener("touchend", (e) => { if (startPress === -1) e.preventDefault(); clearTimeout(startPress); });
  $("startBtn").addEventListener("contextmenu", (e) => { e.preventDefault(); startNav(true); });
  $("stopBtn").onclick = () => stopNav(true);
  $("muteBtn").onclick = () => {
    S.muted = !S.muted;
    $("muteBtn").innerHTML = S.muted ? I.voice_off : I.voice;
    $("muteBtn").classList.toggle("pri", !S.muted);
    if (S.muted) { speechSynthesis.cancel(); if (audio) audio.pause(); }
  };
  $("reportBtn").onclick = () => $("report").classList.remove("hidden");
  $("reportClose").onclick = () => $("report").classList.add("hidden");
  $("closedBtn").onclick = () => {
    $("report").classList.add("hidden");
    if (!S.route || !S.state) return;
    const at = pointAt(S.route, Math.min(S.route.length - 1, S.state.progress + 150));
    S.tempNogos.push({ lat: at.lat, lon: at.lon, r: 60 });
    drawNogos();
    reroute(Phrases.L("Κλειστός δρόμος. Ψάχνω άλλο δρόμο.", "Road closed. Finding another way."), true);
  };
  $("rerouteBtn").onclick = () => { $("report").classList.add("hidden"); reroute(); };

  async function startNav(sim) {
    if (!S.route || S.nav) return;
    S.nav = true;
    S.sim = !!sim;
    document.body.classList.add("nav");
    showLean();
    if (P.lean) enableSensors();
    S.engine = new Engine(S.route);
    S.stage = {}; S.said = {}; S.follow = true; S.anim = null; S.lastAlong = null; S.navBearing = null; S.state = null; S.lastLimit = null;
    S.alts = []; drawAlts();
    $("card").classList.add("hidden");
    $("rightCol").classList.remove("hidden");
    ["recBtn", "searchBtn", "settingsBtn"].forEach((id) => $(id).classList.add("hidden"));
    $("stopBtn").classList.remove("hidden");
    $("instr").classList.remove("hidden");
    $("navLeft").classList.remove("hidden");
    $("navPills").classList.remove("hidden");
    $("simPill").classList.toggle("hidden", !S.sim);
    unlockAudio();
    const start = P.startPhrase && P.startPhrase !== DEF.startPhrase ? P.startPhrase : Phrases.L("Πάμε.", "Let's go.");
    say(start.replace("{προορισμός}", Phrases.name(S.dest.name)), true);
    try { if (navigator.wakeLock) S.wake = await navigator.wakeLock.request("screen"); } catch (e) { /* */ }
    if (!S.sim) { startGps(); if (P.autoRecord && !S.rec) startRecording(); }
    if (S.me && !S.sim) navTick();
    render(null);
    requestAnimationFrame(frame);
    if (S.sim) simLoop();
    drawFuel();
  }
  document.addEventListener("visibilitychange", async () => {
    if (document.visibilityState === "visible" && S.nav && navigator.wakeLock) { try { S.wake = await navigator.wakeLock.request("screen"); } catch (e) { /* */ } }
  });

  function stopNav(user) {
    const wasSim = S.sim;
    S.nav = false; S.sim = false; S.engine = null; S.state = null;
    document.body.classList.remove("nav");
    showLean();
    S.tempNogos = []; drawNogos();
    if (S.wake) { try { S.wake.release(); } catch (e) { /* */ } S.wake = null; }
    speechSynthesis.cancel();
    ["instr", "navLeft", "navPills", "simPill", "report", "stopBtn"].forEach((id) => $(id).classList.add("hidden"));
    ["recBtn", "searchBtn", "settingsBtn"].forEach((id) => $(id).classList.remove("hidden"));
    $("glow").classList.add("hidden"); $("limit").classList.add("hidden"); $("gauge").classList.remove("over");
    map.easeTo({ pitch: 0, bearing: 0, padding: { top: 0, bottom: 0, left: 0, right: 0 }, duration: 500 });
    if (S.rec && !wasSim && P.autoRecord) stopRecording();
    drawFuel();
    if (user && S.route) { showCard(); drawRoute(S.route.points); fitRoute(S.route); }
    else closeCard();
  }

  function navTick() {
    const me = S.me;
    if (!S.engine || !me) return;
    if (S.stops.length && Geo.distance(me, S.stops[0]) < 50) {
      const s = S.stops.shift();
      say(Phrases.L(`Στάση: ${s.name}.`, `Stop: ${Phrases.name(s.name)}.`));
      drawPoints();
    }
    const st = S.engine.update(me, me.acc, me.heading, me.speed);
    S.state = st;
    if (st.arrived) {
      say(Phrases.L("Έφτασες στον προορισμό σου.", "You have arrived at your destination."));
      render(st);
      S.engine = null;
      setTimeout(() => S.nav && stopNav(false), 4000);
      return;
    }
    if (st.offRoute && !S.rerouting && performance.now() - S.lastReroute > 3000) reroute();
    const now = performance.now();
    const cur = currentAnim(now);
    // Σταματημένος/αργός: το GPS «χορεύει» λίγα μέτρα – το βελάκι δεν πάει πίσω και δεν τρέμει.
    let along = st.progress;
    if (st.onRoute && S.lastAlong != null && along < S.lastAlong && S.lastAlong - along < 25) along = S.lastAlong;
    if (st.onRoute && S.lastAlong != null && (me.speed || 0) < 1.5 && Math.abs(along - S.lastAlong) < 6) along = S.lastAlong;
    if (st.onRoute) S.lastAlong = along;
    S.anim = { from: cur, to: st.onRoute ? { along } : { p: { lat: me.lat, lon: me.lon } }, t0: now, dur: 950 };
    announce(st);
    alerts(st);
    render(st);
    showSpeed();
  }
  function currentAnim(now) {
    const a = S.anim;
    if (!a) return null;
    const k = Math.min(1, (now - a.t0) / a.dur);
    if (a.to.along != null && a.from && a.from.along != null) return { along: a.from.along + (a.to.along - a.from.along) * k };
    return k >= 1 ? a.to : a.from || a.to;
  }
  let lastTrim = 0, zNow = null;
  function frame(now) {
    if (!S.nav) return;
    requestAnimationFrame(frame);
    if (!S.anim || !S.route) return;
    const cur = currentAnim(now);
    if (!cur) return;
    let p, bearing;
    if (cur.along != null) {
      p = pointAt(S.route, cur.along);
      const ahead = pointAt(S.route, Math.min(S.route.length, cur.along + 25));
      bearing = Geo.distance(p, ahead) > 1 ? Geo.bearing(p, ahead) : (S.me && S.me.heading) || 0;
      if (now - lastTrim > 90) { drawRoute(remaining(S.route, cur.along)); lastTrim = now; }
    } else { p = cur.p; bearing = (S.me && S.me.heading) || 0; }
    // Ομαλή στροφή (χωρίς τρεμόπαιγμα από το GPS)
    if (S.navBearing != null) { const d = ((bearing - S.navBearing + 540) % 360) - 180; bearing = (S.navBearing + d * (Math.abs(d) > 60 ? 0.25 : 0.12) + 360) % 360; }
    S.navBearing = bearing;
    puck.setLngLat([p.lon, p.lat]).setRotation(bearing).addTo(map);
    setHeading(bearing);
    if (S.follow) {
      const v = ((S.me && S.me.speed) || 0) * 3.6;
      const z = v < 35 ? 17 : v < 60 ? 16.5 : v < 85 ? 15.9 : v < 110 ? 15.3 : 14.8;
      const near = S.state && S.state.distToNext < 250 && v < 90;
      const target = near ? Math.max(z, 16.8) : z;
      zNow = zNow == null ? target : zNow + (target - zNow) * 0.03;
      const land = innerWidth > innerHeight;
      const want = { center: [p.lon, p.lat], bearing, pitch: 56, zoom: zNow,
        padding: { top: innerHeight * 0.6, bottom: 0, left: land ? innerWidth * 0.35 : 0, right: 0 } };
      // Επιστροφή στο βελάκι: ομαλά μέσα σε ~0,8″ αντί για απότομο πήδημα.
      const k = S.followIn ? Math.min(1, (now - S.followIn) / 800) : 1;
      if (k < 1) {
        const c = map.getCenter(), e = k * k * (3 - 2 * k);
        const db = ((bearing - map.getBearing() + 540) % 360) - 180;
        want.center = [c.lng + (p.lon - c.lng) * e, c.lat + (p.lat - c.lat) * e];
        want.bearing = map.getBearing() + db * e; want.pitch = map.getPitch() + (56 - map.getPitch()) * e;
        want.zoom = map.getZoom() + (zNow - map.getZoom()) * e;
      } else S.followIn = 0;
      map.jumpTo(want);
    }
  }
  function showOverview() {
    if (!S.route || !S.state) return;
    S.follow = false;
    const pts = remaining(S.route, S.state.progress);
    const b = new maplibregl.LngLatBounds();
    pts.forEach((p) => b.extend([p.lon, p.lat]));
    const land = innerWidth > innerHeight;
    try {
      map.fitBounds(b, { padding: land ? { top: 40, bottom: 40, left: 440, right: 150 } : { top: 210, bottom: 190, left: 40, right: 40 },
        bearing: 0, pitch: 0, duration: 900 });
    } catch (e) { /* */ }
    pauseFollow();
  }

  async function reroute(phrase, offline) {
    if (S.rerouting) return;
    S.rerouting = true;
    S.lastReroute = performance.now();
    say(phrase || Phrases.L("Υπολογίζω νέα διαδρομή.", "Recalculating."));
    try {
      const from = S.me;
      const type = offline && P.type === "traffic" ? "fast" : P.type;
      let r;
      if (S.track) {
        // Έτοιμη διαδρομή: ξανά στο πιο κοντινό σημείο της μπροστά σου.
        const near = S.via.map((p, i) => ({ i, d: Geo.distance(from, p) })).sort((a, b) => a.d - b.d)[0];
        const rest = S.via.slice(near ? near.i + 1 : 0);
        r = await SV.route([from].concat(rest, [S.dest]), opts({ type, quick: true }));
      } else r = await SV.route(waypoints(from), opts({ type, quick: true }));
      if (!S.nav) return;
      S.route = r;
      S.engine = new Engine(r);
      S.stage = {}; S.anim = null; S.lastAlong = null;
      drawRoute(r.points);
      SV.fuelOnRoute(r).then((f) => { if (S.route === r) { S.fuel = f; drawFuel(); } }).catch(() => {});
    } catch (e) { toast(e.message); }
    finally { S.rerouting = false; }
  }

  function announce(st) {
    if (!st.onRoute) return;
    const m = st.next, d = st.distToNext, key = m.index, done = S.stage[key] || 0;
    const v = Math.max(8, (S.me && S.me.speed) || 0);
    const far = Math.min(2000, Math.max(400, v * 45)), near = Math.min(600, Math.max(120, v * 15)), nowD = Math.min(120, Math.max(25, v * 4));
    const thenTxt = st.then ? Phrases.L(", και μετά ", ", then ") + Phrases.action(st.then) : "";
    const street = m.street ? ", " + Phrases.name(m.street) : "";
    if (m.turn === "ARRIVE") {
      if (d <= near && done < 2) { say(Phrases.L("Σε ", "In ") + Phrases.distance(d) + Phrases.L(", φτάνεις στον προορισμό.", ", you arrive at your destination.")); S.stage[key] = 2; }
      return;
    }
    if (d <= nowD && done < 3) { say(Phrases.L("Τώρα, ", "Now, ") + Phrases.action(m) + thenTxt); S.stage[key] = 3; }
    else if (d <= near && done < 2) { say(Phrases.L("Σε ", "In ") + Phrases.distance(d) + ", " + Phrases.action(m) + street + thenTxt); S.stage[key] = 2; }
    else if (d <= far && d > near * 1.5 && done < 1) { say(Phrases.L("Σε ", "In ") + Phrases.distance(d) + ", " + Phrases.action(m)); S.stage[key] = 1; }
  }

  /** Κάμερες, βενζίνη, καιρός, στάση: τα pills κάτω από την οδηγία και οι φωνές τους. */
  function alerts(st) {
    const pills = [];
    const me = S.me;
    if (P.cameras && cameras.length && me) {
      const c = cameras.map((c) => ({ c, d: Geo.distance(me, { lat: c.la, lon: c.lo }) })).filter((x) => x.d < 600)
        .map((x) => Object.assign(x, { on: N.nearestOnRoute(S.route, { lat: x.c.la, lon: x.c.lo }) }))
        .filter((x) => x.on.d < 40 && x.on.along > st.progress).sort((a, b) => a.d - b.d)[0];
      if (c) {
        const title = c.c.kind === "red" ? Phrases.L("Κάμερα φαναριού", "Red light camera") : Phrases.L("Κάμερα", "Speed camera");
        pills.push(`<div class="npill cam">📷 ${esc(title)} σε ${Phrases.short(c.on.along - st.progress)}</div>`);
        if (!S.said["cam" + c.c.id]) { S.said["cam" + c.c.id] = 1; say(title + Phrases.L(" σε ", " in ") + Phrases.distance(c.on.along - st.progress)); }
      }
    }
    if (S.stops.length) {
      const d = Geo.distance(me, S.stops[0]);
      pills.push(`<div class="npill" id="stopPill">📍 ${Phrases.L("Στάση 1", "Stop 1")}: ${esc(S.stops[0].name)} · ${Phrases.short(d)} <button class="chip" id="skipStop">${Phrases.L("Παράλειψη", "Skip")}</button></div>`);
    }
    const f = (S.fuel || []).find((x) => x.along > st.progress);
    if (f && f.along - st.progress < 3000) {
      pills.push(`<div class="npill">⛽ ${esc(f.name)} σε ${Phrases.short(f.along - st.progress)}</div>`);
      if (P.fuelAlert && f.along - st.progress < 800 && !S.said["fuel" + f.along]) { S.said["fuel" + f.along] = 1; }
    }
    if (P.weatherAlert && S.weather.length) {
      const bad = S.weather.find((w) => w.at > st.progress && w.at - st.progress < 15000 && SV.weatherBad(w));
      if (bad) {
        const why = SV.weatherBad(bad);
        pills.push(`<div class="npill warn">🌧 ${esc(why)} σε ${Phrases.short(bad.at - st.progress)}</div>`);
        if (!S.said["wx" + bad.at]) { S.said["wx" + bad.at] = 1; say(Phrases.L(`Προσοχή, ${why} σε `, "Caution, bad weather in ") + Phrases.distance(bad.at - st.progress) + "."); }
      }
    }
    const box = $("navPills");
    box.innerHTML = pills.join("");
    const top = $("instr").getBoundingClientRect();
    box.style.top = (top.bottom + 8) + "px";
    const sk = $("skipStop");
    if (sk) sk.onclick = () => { S.stops.shift(); drawPoints(); reroute(Phrases.L("Πάμε στην επόμενη στάση.", "Heading to the next stop.")); };
    drawFuel();
  }

  function render(st) {
    const r = S.route;
    if (!r) return;
    const s = st || { next: r.maneuvers[0], distToNext: r.maneuvers[0].at, remaining: r.length, progress: 0, then: null };
    // Χρώμα ταμπέλας ανά δρόμο (όπως στην Android): πράσινη αυτοκινητόδρομος, μπλε κύρια, πορτοκαλί χώμα, μπορντό άφιξη.
    let sign = null;
    if (P.roadColors && !S.rerouting) {
      if (s.next.turn === "ARRIVE" || (s.then && s.then.turn === "ARRIVE")) sign = "#66023C";
      else sign = { MOTORWAY: "#24A046", MAIN: "#377DF6", DIRT: "#F58142" }[N.kindAt(r, s.next.at + 30)] || null;
    }
    $("instr").classList.toggle("signed", !!sign);
    $("instr").style.setProperty("--sign", sign || "var(--surface)");
    $("instr").style.setProperty("--sign2", sign ? (sign === "#377DF6" ? "#316BCE" : darker(sign, 0.14)) : "var(--surface2)");
    $("instrIcon").innerHTML = arrowSvg(s.next.turn, sign ? "#FFFFFF" : null);
    $("instrDist").textContent = Phrases.short(s.distToNext);
    $("instrText").textContent = (s.next.street ? Phrases.title(s.next) + " · " + s.next.street : Phrases.title(s.next));
    const then = $("instrThen");
    if (s.then) {
      $("instrThenTxt").textContent = `Έπειτα μετά από ${Phrases.short(s.then.at - s.next.at)}`;
      $("instrThenIcon").innerHTML = arrowSvg(s.then.turn, sign ? "#FFFFFF" : null);
      then.classList.remove("hidden");
    } else then.classList.add("hidden");
    const left = r.length > 0 ? r.duration * (s.remaining / r.length) : 0;
    $("eta").textContent = new Date(Date.now() + left * 1000).toLocaleTimeString("el-GR", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
    $("remT").textContent = left < 3600 ? `${Math.max(1, Math.round(left / 60))}′` : `${Math.floor(left / 3600)}:${String(Math.round((left % 3600) / 60)).padStart(2, "0")}`;
    $("remD").textContent = Phrases.short(s.remaining);
    $("prog").style.width = `${Math.round((s.progress / r.length) * 100)}%`;
  }
  function arrowSvg(turn, color) { return I.maneuver(turn, color || accentNow()); }
  // ---------- Προσομοίωση ----------
  async function simLoop() {
    let along = 0, r = null;
    while (S.nav && S.sim) {
      if (S.route !== r) { along = S.me && r ? N.nearestOnRoute(S.route, S.me).along : 0; r = S.route; }
      const kmh = Math.min(90, Math.max(30, limitAt(r, along) || 50));
      along = Math.min(r.length, along + (kmh / 3.6) * S.simSpeed);
      const p = pointAt(r, along), ahead = pointAt(r, Math.min(r.length, along + 20));
      onFix({ latitude: p.lat, longitude: p.lon, accuracy: 4, speed: kmh / 3.6, heading: Geo.bearing(p, ahead), altitude: null });
      await sleep(1000);
    }
  }
  $("simSpeed").onclick = () => { S.simSpeed = S.simSpeed === 1 ? 2 : S.simSpeed === 2 ? 4 : 1; $("simSpeed").textContent = S.simSpeed + "×"; };
  $("simStop").onclick = () => stopNav(true);

  // ---------- Φωνή ----------
  let greekVoice = null, englishVoice = null, audio = null, audioQueue = Promise.resolve();
  function pickVoices() {
    const vs = speechSynthesis.getVoices();
    greekVoice = vs.find((v) => /^el/i.test(v.lang)) || null;
    englishVoice = vs.find((v) => /^en-GB/i.test(v.lang)) || vs.find((v) => /^en/i.test(v.lang)) || null;
  }
  if ("speechSynthesis" in window) { pickVoices(); speechSynthesis.onvoiceschanged = pickVoices; }
  function unlockAudio() {
    try {
      audio = audio || new Audio();
      audio.src = "data:audio/mp3;base64,SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA//tQxAADB8AhSmxhIIEVCSiJrDCQBTcu3UrAIwUdkRgQbFAZC1CQEwTJ9mjRvBA4UOLD8nKVOWfh+UlK3z/177OXrfOdKl7pyn3Xf//WreyTRUoAWgBgkOAGbZHBgG1OF6zM82DWbZaUmMBptgQhGjsyYqc9ae9XFz280948NMBWInljyzsNRFLPWdnZGWrddDsjK1unuSrVN9jJsK8KuQtQCtMBjCEtImISdNKJOopIpBFpNSMbIHCSRpRR5iakjTiyzLhchUUBwCgyKiweBv/7UsQbg8isVNoMPMjAAAA0gAAABEVFGmgqK////9bP/6XCykxBTUUzLjEwMKqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq";
      audio.play().catch(() => {});
    } catch (e) { /* */ }
    try { speechSynthesis.speak(new SpeechSynthesisUtterance(" ")); } catch (e) { /* */ }
  }
  function say(text, force) {
    if (!P.voice || (S.muted && !force)) return;
    if (P.voiceEngine === "eleven" && P.elevenKey && P.elevenVoice) {
      audioQueue = audioQueue.then(async () => {
        try {
          const url = await SV.elevenSpeak(P.elevenKey, P.elevenVoice, text, P.english);
          audio = audio || new Audio();
          audio.src = url;
          await audio.play();
          await new Promise((res) => { audio.onended = res; audio.onerror = res; });
          URL.revokeObjectURL(url);
        } catch (e) { phoneSay(text); }
      });
      return;
    }
    phoneSay(text);
  }
  function phoneSay(text) {
    if (!("speechSynthesis" in window)) return;
    const u = new SpeechSynthesisUtterance(text);
    u.lang = P.english ? "en-GB" : "el-GR";
    const v = P.english ? englishVoice : greekVoice;
    if (v) u.voice = v;
    speechSynthesis.speak(u);
  }

  // ---------- Καταγραφή διαδρομής (ημερολόγιο) ----------
  function startRecording() {
    S.rec = { start: Date.now(), points: [], paused: false, maxKmh: 0, dist: 0 };
    $("recDot").classList.remove("hidden");
    $("recBtn").classList.add("rec-on");
    updateRecPill();
  }
  function record(p) {
    const r = S.rec;
    if (!r || r.paused || S.sim || p.acc > 40) return;
    const last = r.points[r.points.length - 1];
    if (last && Geo.distance(last, p) < 10) return;
    if (last) r.dist += Geo.distance(last, p);
    r.maxKmh = Math.max(r.maxKmh, (p.speed || 0) * 3.6);
    r.points.push({ lat: +p.lat.toFixed(6), lon: +p.lon.toFixed(6), t: Date.now(), e: p.alt != null ? Math.round(p.alt) : null });
    updateRecPill();
  }
  function updateRecPill() {
    if (!S.rec) return;
    const min = Math.round((Date.now() - S.rec.start) / 60000);
    $("recTxt").textContent = `${Phrases.short(S.rec.dist)} · ${min}′`;
    $("recDot").textContent = S.rec.paused ? "⏸" : "●";
    $("recDot").classList.toggle("paused", S.rec.paused);
    $("recPause").textContent = S.rec.paused ? "▶" : "⏸";
  }
  function stopRecording() {
    const r = S.rec;
    S.rec = null;
    $("recPill").classList.add("hidden");
    $("recDot").classList.add("hidden");
    $("recBtn").classList.remove("rec-on");
    if (!r || r.points.length < 5 || r.dist < 300) return;
    const trips = store.get("trips", []);
    trips.unshift({ id: r.start, start: r.start, end: Date.now(), dist: r.dist, maxKmh: Math.round(r.maxKmh), lean: { l: Math.round(leanMax.l), r: Math.round(leanMax.r) },
      name: S.dest ? S.dest.name : "Βόλτα", points: r.points });
    leanMax = { l: 0, r: 0 };
    store.set("trips", trips.slice(0, 40));
    toast(`Η διαδρομή γράφτηκε στο ημερολόγιο (${Phrases.short(r.dist)}).`);
  }
  $("recBtn").onclick = () => { if (S.rec) stopRecording(); else { startGps(); startRecording(); toast("Καταγραφή: ξεκίνησε."); } };
  $("recPause").onclick = () => { if (S.rec) { S.rec.paused = !S.rec.paused; updateRecPill(); } };
  // Η καταγραφή φαίνεται ως κόκκινη τελεία δίπλα στην ώρα· πάτημα → παύση / τέλος.
  document.querySelector(".pills .pill").onclick = () => { if (S.rec) $("recPill").classList.toggle("hidden"); };
  $("recStop").onclick = stopRecording;

  // ---------- Κοινοποίηση με σύνδεσμο ----------
  function fromHash() {
    if (!location.hash || location.hash.length < 4) return;
    const h = new URLSearchParams(location.hash.slice(1));
    const ll = (s) => { const [a, b] = s.split(",").map(Number); return isFinite(a) && isFinite(b) ? { lat: a, lon: b } : null; };
    const to = h.get("to") && ll(h.get("to"));
    if (!to) return;
    S.dest = Object.assign(to, { name: h.get("n") || "Προορισμός" });
    S.stops = (h.get("s") || "").split(";").filter(Boolean).map(ll).filter(Boolean).map((p, i) => Object.assign(p, { name: `Στάση ${i + 1}` }));
    S.via = (h.get("v") || "").split(";").filter(Boolean).map(ll).filter(Boolean);
    if (S.via.length) S.track = S.via.concat([S.dest]);
    if (["fast", "twisty", "dirt"].includes(h.get("t"))) P.type = h.get("t");
    plan();
  }
  function shareUrl() {
    const f = (p) => `${p.lat.toFixed(5)},${p.lon.toFixed(5)}`;
    const h = new URLSearchParams();
    h.set("to", f(S.dest)); h.set("n", S.dest.name);
    if (S.stops.length) h.set("s", S.stops.map(f).join(";"));
    if (S.via.length) h.set("v", Gpx.sample(S.via, 20).map(f).join(";"));
    h.set("t", P.type);
    return `${location.origin}${location.pathname}#${h.toString()}`;
  }

  // ---------- Εγκατάσταση στο iPhone ----------
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const standalone = navigator.standalone || matchMedia("(display-mode: standalone)").matches;
  if (ios && !standalone && !store.get("installHidden", false)) $("install").classList.remove("hidden");
  $("installClose").onclick = () => { $("install").classList.add("hidden"); store.set("installHidden", true); };
  addEventListener("resize", () => $("gauge").classList.toggle("land", innerWidth > innerHeight));
  $("gauge").classList.toggle("land", innerWidth > innerHeight);

  // ---------- Για τις ρυθμίσεις (settings.js) ----------
  window.App = {
    P, S, DEF, store, setPref, toast, esc, map, plan, setDest, say, startGps, enableSensors, shareUrl, drawFavs, Phrases,
    resetLean() { leanZero = leanZero + leanNow; leanNow = 0; store.set("leanZero", leanZero); leanMax = { l: 0, r: 0 }; },
    lists() { return store.get("lists", [{ name: "Αγαπημένα", color: "#F59E0B", items: [] }]); },
    saveLists(l) { store.set("lists", l); drawFavs(); },
    drawPuck, darker, accentNow: () => accentNow(),
    addFavorite(p) { window.Settings.addFavorite(p); },
    followTrack(points, name) {
      if (points.length < 2) return;
      const pts = Gpx.sample(points, 22);
      S.track = points;
      S.dest = Object.assign({}, pts[pts.length - 1], { name: name || "Διαδρομή" });
      S.via = pts.slice(0, -1); S.stops = [];
      plan();
    },
    openRoute(it) {
      S.dest = it.dest; S.stops = (it.stops || []).slice(); S.via = (it.via || []).slice(); S.track = it.track || null;
      if (it.type) P.type = it.type;
      plan();
    },
    currentRoute() { return S.route; },
    exportGpx(name, points) {
      const blob = new Blob([Gpx.write(name, points)], { type: "application/gpx+xml" });
      const file = new File([blob], `${(name || "motonav").replace(/[^\p{L}\p{N} _-]/gu, "").trim() || "motonav"}.gpx`, { type: "application/gpx+xml" });
      if (navigator.canShare && navigator.canShare({ files: [file] })) return navigator.share({ files: [file], title: name }).catch(() => {});
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob); a.download = file.name; a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    },
    closeSheets() { $("search").classList.add("hidden"); $("settings").classList.add("hidden"); },
  };
  // Τα ίδια εικονίδια με την Android εφαρμογή.
  const setIcon = (id, name) => { const el = $(id); if (el && I[name]) el.innerHTML = I[name]; };
  [["searchBtn", "search"], ["settingsBtn", "settings"], ["stopBtn", "close"], ["reportBtn", "warning"], ["muteBtn", "voice"], ["favBtn", "star"],
    ["addStopBtn", "add"], ["cardClose", "close"], ["avMotoBtn", "motorway"], ["avTollBtn", "toll"], ["recBtn", "rec"], ["searchBack", "back"],
    ["setBack", "back"], ["installClose", "close"]].forEach(([id, n]) => setIcon(id, n));
  document.querySelectorAll(".type[data-type]").forEach((b) => { b.innerHTML = I["route_" + b.dataset.type] || b.innerHTML; });
  document.querySelector(".trip-row span").firstChild.replaceWith(Object.assign(document.createElement("i"), { className: "ico", innerHTML: I.flag }));
  applyPrefs("_init");
  // Android/υπολογιστής: οι αισθητήρες δεν θέλουν άδεια, ανοίγουν αμέσως. iPhone: από Ρυθμίσεις → Όργανα (ή μόνα τους αν έχεις δώσει άδεια).
  const needsPermission = window.DeviceOrientationEvent && typeof DeviceOrientationEvent.requestPermission === "function";
  if (!needsPermission) enableSensors();
  else if (store.get("sensorsGranted", false)) document.addEventListener("click", function once() {
    document.removeEventListener("click", once); enableSensors();
  });
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
  startGps();
})();

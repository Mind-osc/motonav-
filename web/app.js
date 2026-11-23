/*
 * Moto Nav web (PWA) για iPhone και υπολογιστή.
 * Χάρτης: OpenFreeMap (MapLibre GL JS). Διαδρομές: BRouter (brouter.de, online). Αναζήτηση: Photon (OSM).
 * Δεν επηρεάζει την Android εφαρμογή: είναι ξεχωριστός φάκελος και ανεβαίνει μόνο στο GitHub Pages.
 */
(function () {
  "use strict";
  const { Geo, parseBRouter, pickTwisty, pointAt, remaining, Engine, Phrases, Gpx } = window.MotoNav;
  const BROUTER = "https://brouter.de/brouter";
  const PHOTON = "https://photon.komoot.io";
  const STYLE = "https://tiles.openfreemap.org/styles/dark";
  const ACCENT = "#A78BFA";
  const $ = (id) => document.getElementById(id);
  const store = {
    get(k, d) { try { const v = localStorage.getItem("motonav." + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem("motonav." + k, JSON.stringify(v)); } catch (e) { /* ιδιωτική περιήγηση */ } },
  };

  // ---------- Κατάσταση ----------
  const S = {
    me: null,            // {lat, lon, acc, heading, speed}
    dest: null,          // {lat, lon, name}
    stops: [],           // στάσεις που φαίνονται/ανακοινώνονται
    via: [],             // ενδιάμεσα σημεία από GPX (δεν φαίνονται)
    type: store.get("type", "fast"),
    avMoto: store.get("avMoto", false),
    avToll: store.get("avToll", false),
    route: null,
    planning: 0,
    nav: false,
    engine: null,
    stage: {},
    lastReroute: 0,
    rerouting: false,
    voice: store.get("voice", true),
    muted: false,
    follow: true,
    wake: null,
    anim: null,          // {from, to, t0} για ομαλή κίνηση του βελακιού
  };

  // ---------- Χάρτης ----------
  const map = new maplibregl.Map({
    container: "map",
    style: STYLE,
    center: store.get("center", [23.7275, 37.9838]),
    zoom: store.get("zoom", 11),
    attributionControl: { compact: true },
    dragRotate: true,
    pitchWithRotate: true,
  });
  map.on("moveend", () => {
    if (S.nav) return;
    const c = map.getCenter();
    store.set("center", [c.lng, c.lat]);
    store.set("zoom", map.getZoom());
  });
  map.on("dragstart", () => { if (S.nav) { S.follow = false; showLocBtn(true); } });

  const empty = { type: "FeatureCollection", features: [] };
  map.on("load", () => {
    map.addSource("route", { type: "geojson", data: empty });
    map.addSource("pts", { type: "geojson", data: empty });
    map.addLayer({ id: "route-casing", type: "line", source: "route",
      layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": ACCENT, "line-width": ["interpolate", ["linear"], ["zoom"], 10, 7, 16, 16] } });
    map.addLayer({ id: "route-line", type: "line", source: "route",
      layout: { "line-cap": "round", "line-join": "round" },
      paint: { "line-color": "#FFFFFF", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 4, 16, 11] } });
    map.addLayer({ id: "pts", type: "circle", source: "pts",
      paint: { "circle-radius": 11, "circle-color": ACCENT, "circle-stroke-color": "#0E0F13", "circle-stroke-width": 3 } });
    map.addLayer({ id: "pts-n", type: "symbol", source: "pts", filter: ["has", "n"],
      layout: { "text-field": ["get", "n"], "text-font": ["Noto Sans Bold"], "text-size": 12, "text-allow-overlap": true },
      paint: { "text-color": "#0E0F13" } });
    fromHash();
  });

  // Βελάκι θέσης (στην πλοήγηση) και τελεία (εκτός πλοήγησης).
  const puckEl = document.createElement("div");
  puckEl.className = "puck";
  puckEl.innerHTML = '<svg viewBox="0 0 26 26"><path d="M13 2.5 L21.5 22.5 Q21.8 23.4 20.9 23 L13 18.8 L5.1 23 Q4.2 23.4 4.5 22.5 Z" fill="#E11D48" stroke="#120F1A" stroke-width="1.4" stroke-linejoin="round"/></svg>';
  const dotEl = document.createElement("div");
  dotEl.className = "dot";
  const puck = new maplibregl.Marker({ element: puckEl, rotationAlignment: "map", pitchAlignment: "map" });
  const dot = new maplibregl.Marker({ element: dotEl });

  // ---------- Μηνύματα ----------
  let toastTimer = 0;
  function toast(msg, ms = 2600) {
    const t = $("toast");
    t.textContent = msg;
    t.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), ms);
  }

  // ---------- GPS ----------
  let watchId = null;
  function startGps() {
    if (watchId != null || !navigator.geolocation) return;
    watchId = navigator.geolocation.watchPosition(onFix, (e) => {
      if (e.code === 1) toast("Δεν επιτρέπεται η θέση. Άνοιξέ τη στις Ρυθμίσεις → Safari → Τοποθεσία.", 5000);
    }, { enableHighAccuracy: true, maximumAge: 1000, timeout: 20000 });
  }
  function onFix(pos) {
    const c = pos.coords;
    const prev = S.me;
    let heading = c.heading != null && !isNaN(c.heading) && (c.speed || 0) > 1 ? c.heading : null;
    if (heading == null && prev && Geo.distance(prev, { lat: c.latitude, lon: c.longitude }) > 4) {
      heading = Geo.bearing(prev, { lat: c.latitude, lon: c.longitude });
    }
    S.me = { lat: c.latitude, lon: c.longitude, acc: c.accuracy, heading: heading != null ? heading : prev && prev.heading,
      speed: c.speed != null && c.speed >= 0 ? c.speed : 0, t: performance.now() };
    if (S.nav) navTick();
    else {
      dot.setLngLat([S.me.lon, S.me.lat]).addTo(map);
      if (wantCenter) { wantCenter = false; map.easeTo({ center: [S.me.lon, S.me.lat], zoom: Math.max(map.getZoom(), 14) }); }
    }
  }
  let wantCenter = false;
  $("locBtn").onclick = () => {
    startGps();
    if (S.nav) { S.follow = true; showLocBtn(false); return; }
    if (S.me) map.easeTo({ center: [S.me.lon, S.me.lat], zoom: Math.max(map.getZoom(), 15), bearing: 0, pitch: 0 });
    else wantCenter = true;
  };
  function showLocBtn(on) { $("fabs").classList.toggle("hidden", !on); }

  // ---------- Αναζήτηση ----------
  let qTimer = 0, qSeq = 0;
  $("q").addEventListener("input", () => {
    clearTimeout(qTimer);
    const q = $("q").value.trim();
    if (q.length < 2) { $("results").classList.add("hidden"); return; }
    qTimer = setTimeout(() => search(q), 350);
  });
  $("q").addEventListener("keydown", (e) => { if (e.key === "Enter") { clearTimeout(qTimer); search($("q").value.trim()); } });
  async function search(q) {
    const seq = ++qSeq;
    const box = $("results");
    // Συντεταγμένες «37.98, 23.72»
    const m = q.match(/^\s*(-?\d+(?:\.\d+)?)\s*[, ]\s*(-?\d+(?:\.\d+)?)\s*$/);
    if (m) { showResults([{ lat: +m[1], lon: +m[2], name: q, sub: "Συντεταγμένες" }]); return; }
    const c = S.me || { lat: map.getCenter().lat, lon: map.getCenter().lng };
    try {
      const r = await fetch(`${PHOTON}/api/?q=${encodeURIComponent(q)}&limit=8&lat=${c.lat}&lon=${c.lon}`);
      const j = await r.json();
      if (seq !== qSeq) return;
      showResults(j.features.map((f) => {
        const p = f.properties;
        const name = p.name || [p.street, p.housenumber].filter(Boolean).join(" ") || p.city || "Σημείο";
        const sub = [p.street && p.name ? p.street : null, p.city || p.town || p.village, p.state, p.country].filter(Boolean).join(", ");
        return { lat: f.geometry.coordinates[1], lon: f.geometry.coordinates[0], name, sub };
      }));
    } catch (e) {
      box.innerHTML = '<button disabled>Χωρίς σύνδεση: η αναζήτηση θέλει internet.</button>';
      box.classList.remove("hidden");
    }
  }
  function showResults(list) {
    const box = $("results");
    box.innerHTML = "";
    if (!list.length) { box.innerHTML = "<button disabled>Δεν βρέθηκε τίποτα.</button>"; box.classList.remove("hidden"); return; }
    list.forEach((it) => {
      const b = document.createElement("button");
      b.innerHTML = `${escapeHtml(it.name)}<small>${escapeHtml(it.sub || "")}</small>`;
      b.onclick = () => { box.classList.add("hidden"); $("q").blur(); setDest(it); };
      box.appendChild(b);
    });
    box.classList.remove("hidden");
  }
  function escapeHtml(s) { return String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c])); }

  async function reverseName(p) {
    try {
      const r = await fetch(`${PHOTON}/reverse?lat=${p.lat}&lon=${p.lon}&limit=1`);
      const j = await r.json();
      const f = j.features && j.features[0];
      if (!f) return null;
      const q = f.properties;
      return q.name || [q.street, q.housenumber].filter(Boolean).join(" ") || q.city || null;
    } catch (e) { return null; }
  }

  // ---------- Παρατεταμένο πάτημα στον χάρτη ----------
  let pressTimer = 0, pressAt = null, pressStart = null;
  function pressBegin(lngLat, x, y) {
    clearTimeout(pressTimer);
    pressStart = { x, y };
    pressTimer = setTimeout(() => openPop({ lat: lngLat.lat, lon: lngLat.lng }), 550);
  }
  function pressCancel() { clearTimeout(pressTimer); }
  map.on("touchstart", (e) => {
    if (e.originalEvent.touches.length !== 1) return pressCancel();
    const t = e.originalEvent.touches[0];
    pressBegin(e.lngLat, t.clientX, t.clientY);
  });
  map.on("touchmove", (e) => {
    const t = e.originalEvent.touches[0];
    if (!pressStart || !t || Math.hypot(t.clientX - pressStart.x, t.clientY - pressStart.y) > 10) pressCancel();
  });
  map.on("touchend", pressCancel);
  map.on("touchcancel", pressCancel);
  map.on("contextmenu", (e) => openPop({ lat: e.lngLat.lat, lon: e.lngLat.lng }));
  map.on("click", () => $("pop").classList.add("hidden"));

  async function openPop(p) {
    if (S.nav) return;
    pressAt = p;
    $("popTitle").textContent = `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}`;
    $("popStop").classList.toggle("hidden", !S.dest);
    $("pop").classList.remove("hidden");
    if (navigator.vibrate) navigator.vibrate(20);
    const n = await reverseName(p);
    if (n && pressAt === p) { p.name = n; $("popTitle").textContent = n; }
  }
  $("popGo").onclick = () => { $("pop").classList.add("hidden"); if (pressAt) setDest(pressAt); };
  $("popStop").onclick = () => {
    $("pop").classList.add("hidden");
    if (!pressAt) return;
    S.stops.push({ lat: pressAt.lat, lon: pressAt.lon, name: pressAt.name || `Στάση ${S.stops.length + 1}` });
    plan();
  };

  // ---------- Προορισμός και διαδρομή ----------
  function setDest(p) {
    S.dest = { lat: p.lat, lon: p.lon, name: p.name || `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}` };
    S.stops = [];
    S.via = [];
    $("q").value = "";
    plan();
  }

  function waypoints(from) {
    return [from].concat(S.via, S.stops, [S.dest]);
  }

  function originForPlan() {
    if (S.me) return S.me;
    return null;
  }

  async function plan() {
    if (!S.dest) return;
    const seq = ++S.planning;
    showCard();
    setMsg("Υπολογίζω διαδρομή…");
    $("startBtn").disabled = true;
    startGps();
    let from = originForPlan();
    if (!from) {
      // Περιμένουμε λίγο το GPS· αλλιώς ξεκινάμε από το κέντρο του χάρτη.
      for (let i = 0; i < 16 && !S.me; i++) await sleep(250);
      from = S.me || { lat: map.getCenter().lat, lon: map.getCenter().lng };
      if (!S.me) toast("Χωρίς θέση GPS: η διαδρομή ξεκινάει από το κέντρο του χάρτη.");
    }
    try {
      const r = await computeRoute(waypoints(from), S.type);
      if (seq !== S.planning) return;
      S.route = r;
      drawRoute(r.points);
      drawPoints();
      fitRoute(r);
      renderCard();
      setMsg(null);
      $("startBtn").disabled = false;
    } catch (e) {
      if (seq !== S.planning) return;
      S.route = null;
      drawRoute([]);
      drawPoints();
      renderCard();
      setMsg(e.message || "Δεν βγήκε διαδρομή.", true);
    }
  }

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  function lonlats(wps) { return wps.map((p) => `${p.lon.toFixed(6)},${p.lat.toFixed(6)}`).join("|"); }

  async function brouter(wps, profile, params, alt = 0) {
    const extra = Object.entries(params).map(([k, v]) => `&profile:${k}=${v}`).join("");
    const url = `${BROUTER}?lonlats=${lonlats(wps)}&profile=${encodeURIComponent(profile)}&alternativeidx=${alt}&format=geojson&timode=2${extra}`;
    let res;
    try { res = await fetch(url); } catch (e) { throw new Error("Χωρίς σύνδεση: οι διαδρομές στο iPhone θέλουν internet."); }
    const text = await res.text();
    if (!res.ok || text.trim()[0] !== "{") throw new Error(explain(text));
    return parseBRouter(JSON.parse(text));
  }
  function explain(t) {
    if (/not mapped|operation killed|no route|target island|from-position not mapped|to-position not mapped/i.test(t)) return "Δεν βρέθηκε δρόμος ως εκεί (σημείο μακριά από δρόμο ή σε νησί).";
    if (/profile/i.test(t)) return "Πρόβλημα με το προφίλ διαδρομής. Ξαναδοκίμασε.";
    if (/busy|too many|503|limit/i.test(t)) return "Ο server διαδρομών είναι απασχολημένος. Ξαναδοκίμασε σε λίγο.";
    return "Δεν βγήκε διαδρομή: " + String(t).slice(0, 120);
  }

  // Το δικό μας προφίλ χωματόδρομων ανεβαίνει μία φορά στο BRouter και παίρνει προσωρινό όνομα.
  let dirtId = null;
  async function dirtProfile(force) {
    if (dirtId && !force) return dirtId;
    const text = await (await fetch("moto-dirt.brf")).text();
    const res = await fetch(`${BROUTER}/profile`, { method: "POST", body: text });
    const j = await res.json();
    if (!j.profileid) throw new Error("Το προφίλ «Χώμα» δεν ανέβηκε: " + (j.error || "άγνωστο λάθος"));
    dirtId = j.profileid;
    return dirtId;
  }

  async function computeRoute(wps, type, quick) {
    const common = {};
    if (S.avToll) common.avoid_toll = 1;
    if (type === "fast") {
      const p = Object.assign({ vmax: 110 }, common);
      if (S.avMoto) p.avoid_motorways = 1;
      try { return await brouter(wps, "car-fast", p); }
      catch (e) { if (/Χωρίς σύνδεση|Δεν βρέθηκε/.test(e.message)) throw e; return brouter(wps, "car-vario", p); }
    }
    if (type === "twisty") {
      const p = Object.assign({ vmax: 80, avoid_motorways: 1 }, common);
      // Στην πλοήγηση (νέα διαδρομή) μία μόνο, για να βγει γρήγορα.
      const tries = await Promise.allSettled((quick ? [0] : [0, 1, 2, 3]).map((a) => brouter(wps, "car-fast", p, a)));
      const ok = tries.filter((t) => t.status === "fulfilled").map((t) => t.value);
      if (!ok.length) throw tries[0].reason;
      return pickTwisty(ok);
    }
    // Χώμα
    let id = await dirtProfile(false);
    try { return await brouter(wps, id, common); }
    catch (e) {
      if (!/προφίλ/.test(e.message)) throw e;
      id = await dirtProfile(true);
      return brouter(wps, id, common);
    }
  }

  function drawRoute(points) {
    const src = map.getSource("route");
    if (!src) return;
    src.setData(points.length > 1 ? { type: "Feature", geometry: { type: "LineString", coordinates: points.map((p) => [p.lon, p.lat]) } } : empty);
  }
  function drawPoints() {
    const src = map.getSource("pts");
    if (!src) return;
    const f = S.stops.map((s, i) => ({ type: "Feature", properties: { n: String(i + 1) }, geometry: { type: "Point", coordinates: [s.lon, s.lat] } }));
    if (S.dest) f.push({ type: "Feature", properties: {}, geometry: { type: "Point", coordinates: [S.dest.lon, S.dest.lat] } });
    src.setData({ type: "FeatureCollection", features: f });
  }
  function fitRoute(r) {
    const b = new maplibregl.LngLatBounds();
    r.points.forEach((p) => b.extend([p.lon, p.lat]));
    const cardH = $("card").offsetHeight || 300;
    map.fitBounds(b, { padding: { top: 90, bottom: Math.min(cardH + 30, innerHeight * 0.6), left: 40, right: 40 }, bearing: 0, pitch: 0, duration: 700 });
  }

  // ---------- Κάρτα ----------
  function showCard() {
    $("card").classList.remove("hidden");
    $("top").classList.add("hidden");
    $("results").classList.add("hidden");
    showLocBtn(false);
  }
  function hideCard() {
    $("card").classList.add("hidden");
    $("top").classList.remove("hidden");
    showLocBtn(true);
  }
  function fmtDur(s) {
    const m = Math.round(s / 60);
    if (m < 60) return `${m} λεπτά`;
    return `${Math.floor(m / 60)} ώ ${m % 60} λ`;
  }
  function renderCard() {
    const r = S.route;
    $("cardTitle").innerHTML = r ? `<span class="green">${fmtDur(r.duration)}</span> · ${Phrases.short(r.length)}` : "—";
    $("cardSub").textContent = S.dest ? S.dest.name : "";
    const st = $("cardStops");
    st.innerHTML = "";
    S.stops.forEach((s, i) => {
      const el = document.createElement("span");
      el.innerHTML = `<b>${i + 1}</b>${escapeHtml(s.name)}<button aria-label="Αφαίρεση">✕</button>`;
      el.querySelector("button").onclick = () => { S.stops.splice(i, 1); plan(); };
      st.appendChild(el);
    });
    if (S.via.length) {
      const el = document.createElement("span");
      el.textContent = "Ακολουθεί GPX (περίπου)";
      st.appendChild(el);
    }
    document.querySelectorAll(".type").forEach((b) => b.classList.toggle("on", b.dataset.type === S.type));
    $("avMoto").checked = S.avMoto;
    $("avToll").checked = S.avToll;
    drawElevation(r);
  }
  function setMsg(t, err) {
    const m = $("cardMsg");
    m.textContent = t || "";
    m.classList.toggle("hidden", !t);
    m.classList.toggle("err", !!err);
  }
  document.querySelectorAll(".type").forEach((b) => b.onclick = () => {
    S.type = b.dataset.type;
    store.set("type", S.type);
    plan();
  });
  $("avMoto").onchange = () => { S.avMoto = $("avMoto").checked; store.set("avMoto", S.avMoto); plan(); };
  $("avToll").onchange = () => { S.avToll = $("avToll").checked; store.set("avToll", S.avToll); plan(); };
  $("cardClose").onclick = () => {
    S.dest = null; S.stops = []; S.via = []; S.route = null; S.planning++;
    drawRoute([]); drawPoints(); hideCard(); history.replaceState(null, "", location.pathname);
  };

  function drawElevation(r) {
    const cv = $("elev");
    const ctx = cv.getContext("2d");
    const w = cv.clientWidth || 300, h = 60, dpr = devicePixelRatio || 1;
    cv.width = w * dpr; cv.height = h * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    if (!r) return;
    const pts = [];
    r.elevations.forEach((e, i) => { if (e != null) pts.push([r.cum[i], e]); });
    if (pts.length < 2) { cv.style.display = "none"; return; }
    cv.style.display = "block";
    let lo = Math.min(...pts.map((p) => p[1])), hi = Math.max(...pts.map((p) => p[1]));
    if (hi - lo < 20) hi = lo + 20;
    const X = (d) => (d / r.length) * w, Y = (e) => h - 14 - ((e - lo) / (hi - lo)) * (h - 20);
    ctx.beginPath();
    ctx.moveTo(0, h - 14);
    pts.forEach((p) => ctx.lineTo(X(p[0]), Y(p[1])));
    ctx.lineTo(w, h - 14);
    ctx.closePath();
    ctx.fillStyle = "rgba(167,139,250,.18)";
    ctx.fill();
    ctx.beginPath();
    pts.forEach((p, i) => (i ? ctx.lineTo(X(p[0]), Y(p[1])) : ctx.moveTo(X(p[0]), Y(p[1]))));
    ctx.strokeStyle = ACCENT; ctx.lineWidth = 2; ctx.stroke();
    let up = 0, down = 0;
    for (let i = 1; i < pts.length; i++) { const d = pts[i][1] - pts[i - 1][1]; if (d > 0) up += d; else down -= d; }
    ctx.fillStyle = "#A4AAB6"; ctx.font = "12px system-ui";
    ctx.fillText(`↑ ${Math.round(up)} m  ↓ ${Math.round(down)} m`, 0, h - 1);
    const range = `${Math.round(lo)}–${Math.round(hi)} m`;
    ctx.fillText(range, w - ctx.measureText(range).width, h - 1);
  }

  // ---------- Πλοήγηση ----------
  $("startBtn").onclick = startNav;
  $("stopBtn").onclick = () => stopNav(true);
  $("muteBtn").onclick = () => {
    S.muted = !S.muted;
    $("muteBtn").textContent = S.muted ? "🔇" : "🔊";
    if (S.muted) speechSynthesis.cancel();
  };

  async function startNav() {
    if (!S.route) return;
    S.nav = true;
    S.engine = new Engine(S.route);
    S.stage = {};
    S.follow = true;
    S.anim = null;
    $("card").classList.add("hidden");
    $("top").classList.add("hidden");
    $("instr").classList.remove("hidden");
    $("navbar").classList.remove("hidden");
    showLocBtn(false);
    dot.remove();
    // Στο iPhone η φωνή «ξεκλειδώνει» μόνο μέσα σε πάτημα κουμπιού.
    say(`Πάμε για ${S.dest.name}.`, true);
    try { if (navigator.wakeLock) S.wake = await navigator.wakeLock.request("screen"); } catch (e) { /* όχι διαθέσιμο */ }
    startGps();
    if (S.me) navTick();
    render(null);
    requestAnimationFrame(frame);
  }
  document.addEventListener("visibilitychange", async () => {
    if (document.visibilityState === "visible" && S.nav && navigator.wakeLock) {
      try { S.wake = await navigator.wakeLock.request("screen"); } catch (e) { /* */ }
    }
  });

  function stopNav(user) {
    S.nav = false;
    S.engine = null;
    if (S.wake) { try { S.wake.release(); } catch (e) { /* */ } S.wake = null; }
    speechSynthesis.cancel();
    $("instr").classList.add("hidden");
    $("navbar").classList.add("hidden");
    puck.remove();
    map.easeTo({ pitch: 0, bearing: 0, padding: { top: 0, bottom: 0, left: 0, right: 0 }, duration: 500 });
    if (S.me) dot.setLngLat([S.me.lon, S.me.lat]).addTo(map);
    if (user && S.route) { showCard(); drawRoute(S.route.points); fitRoute(S.route); }
    else { S.dest = null; S.stops = []; S.via = []; S.route = null; drawRoute([]); drawPoints(); hideCard(); }
  }

  function navTick() {
    const me = S.me;
    if (!S.engine || !me) return;
    // Στάση: μόλις την περάσεις (< 50 m), φεύγει.
    if (S.stops.length && Geo.distance(me, S.stops[0]) < 50) {
      const s = S.stops.shift();
      say(`Στάση: ${s.name}.`);
      drawPoints();
    }
    const st = S.engine.update(me, me.acc, me.heading, me.speed);
    S.state = st;
    if (st.arrived) {
      say("Έφτασες στον προορισμό σου.");
      render(st);
      setTimeout(() => S.nav && stopNav(false), 4000);
      S.engine = null;
      return;
    }
    if (st.offRoute && !S.rerouting && performance.now() - S.lastReroute > 3000) reroute();
    // Ομαλή κίνηση: από εκεί που είναι τώρα το βελάκι ως τη νέα θέση, σε ~1 δευτερόλεπτο.
    const now = performance.now();
    const cur = currentAnim(now);
    const target = st.onRoute ? { along: st.progress } : { p: { lat: me.lat, lon: me.lon } };
    S.anim = { from: cur, to: target, t0: now, dur: 950 };
    announce(st);
    render(st);
  }

  function currentAnim(now) {
    const a = S.anim;
    if (!a) return null;
    const k = Math.min(1, (now - a.t0) / a.dur);
    if (a.to.along != null && a.from && a.from.along != null) return { along: a.from.along + (a.to.along - a.from.along) * k };
    return k >= 1 ? a.to : a.from || a.to;
  }

  let lastTrim = 0;
  function frame(now) {
    if (!S.nav) return;
    requestAnimationFrame(frame);
    if (!S.anim || !S.route) return;
    const cur = currentAnim(now);
    if (!cur) return;
    let p, bearing;
    if (cur.along != null) {
      p = pointAt(S.route, cur.along);
      const ahead = pointAt(S.route, Math.min(S.route.length, cur.along + 15));
      bearing = Geo.distance(p, ahead) > 1 ? Geo.bearing(p, ahead) : (S.me && S.me.heading) || 0;
      // Η γραμμή ξεκινάει ακριβώς από το βελάκι.
      if (now - lastTrim > 90) { drawRoute(remaining(S.route, cur.along)); lastTrim = now; }
    } else {
      p = cur.p;
      bearing = (S.me && S.me.heading) || 0;
    }
    puck.setLngLat([p.lon, p.lat]).setRotation(bearing).addTo(map);
    if (S.follow) {
      const v = (S.me && S.me.speed) || 0;
      const zoom = v * 3.6 < 35 ? 17 : v * 3.6 < 60 ? 16.5 : v * 3.6 < 85 ? 15.9 : v * 3.6 < 110 ? 15.3 : 14.8;
      const near = S.state && S.state.distToNext < 250 && v * 3.6 < 90;
      map.jumpTo({ center: [p.lon, p.lat], bearing, pitch: 56, zoom: smoothZoom(near ? Math.max(zoom, 16.8) : zoom),
        padding: { top: innerHeight * 0.6, bottom: 0, left: 0, right: 0 } });
    }
  }
  let zNow = null;
  function smoothZoom(target) {
    zNow = zNow == null ? target : zNow + (target - zNow) * 0.03;
    return zNow;
  }

  async function reroute() {
    S.rerouting = true;
    S.lastReroute = performance.now();
    say("Υπολογίζω νέα διαδρομή.");
    try {
      const r = await computeRoute(waypoints(S.me), S.type, true);
      if (!S.nav) return;
      S.route = r;
      S.engine = new Engine(r);
      S.stage = {};
      S.anim = null;
      drawRoute(r.points);
    } catch (e) {
      toast(e.message);
    } finally {
      S.rerouting = false;
    }
  }

  function announce(st) {
    if (!st.onRoute) return;
    const m = st.next, d = st.distToNext;
    const key = m.index;
    const done = S.stage[key] || 0;
    const v = Math.max(8, (S.me && S.me.speed) || 0);
    const far = Math.min(2000, Math.max(400, v * 45));
    const near = Math.min(600, Math.max(120, v * 15));
    const nowD = Math.min(120, Math.max(25, v * 4));
    const thenTxt = st.then ? `, και μετά ${Phrases.action(st.then)}` : "";
    if (m.turn === "ARRIVE") {
      if (d <= near && done < 2) { say(`Σε ${Phrases.distance(d)}, φτάνεις στον προορισμό.`); S.stage[key] = 2; }
      return;
    }
    if (d <= nowD && done < 3) { say(`Τώρα, ${Phrases.action(m)}${thenTxt}`); S.stage[key] = 3; }
    else if (d <= near && done < 2) { say(`Σε ${Phrases.distance(d)}, ${Phrases.action(m)}${thenTxt}`); S.stage[key] = 2; }
    else if (d <= far && d > near * 1.5 && done < 1) { say(`Σε ${Phrases.distance(d)}, ${Phrases.action(m)}`); S.stage[key] = 1; }
  }

  function render(st) {
    const r = S.route;
    if (!r) return;
    const s = st || { next: r.maneuvers[0], distToNext: r.maneuvers[0].at, remaining: r.length, progress: 0, then: null };
    $("instrIcon").innerHTML = arrowSvg(s.next.turn);
    $("instrDist").textContent = Phrases.short(s.distToNext);
    $("instrText").textContent = Phrases.title(s.next);
    const then = $("instrThen");
    if (s.then) { then.textContent = `Έπειτα: ${Phrases.action(s.then)}`; then.classList.remove("hidden"); }
    else then.classList.add("hidden");
    const left = r.length > 0 ? r.duration * (s.remaining / r.length) : 0;
    const eta = new Date(Date.now() + left * 1000);
    $("eta").textContent = eta.toLocaleTimeString("el-GR", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
    $("remT").textContent = left < 3600 ? `${Math.max(1, Math.round(left / 60))}′` : `${Math.floor(left / 3600)}:${String(Math.round((left % 3600) / 60)).padStart(2, "0")}`;
    $("remD").textContent = Phrases.short(s.remaining);
    $("speed").textContent = S.me ? Math.round((S.me.speed || 0) * 3.6) : 0;
  }

  function arrowSvg(turn) {
    const rot = { STRAIGHT: 0, SLIGHT_LEFT: -40, LEFT: -90, SHARP_LEFT: -135, SLIGHT_RIGHT: 40, RIGHT: 90, SHARP_RIGHT: 135,
      KEEP_LEFT: -25, KEEP_RIGHT: 25, EXIT_LEFT: -45, EXIT_RIGHT: 45, UTURN: 180, ROUNDABOUT: 0, ROUNDABOUT_LEFT: 0, ARRIVE: 0 }[turn] || 0;
    if (turn === "ARRIVE") return '<svg viewBox="0 0 64 64"><path d="M18 56V10l30 10-30 10" fill="none" stroke="#A78BFA" stroke-width="6" stroke-linejoin="round"/></svg>';
    if (turn === "ROUNDABOUT" || turn === "ROUNDABOUT_LEFT") return '<svg viewBox="0 0 64 64"><circle cx="32" cy="30" r="12" fill="none" stroke="#A78BFA" stroke-width="6"/><path d="M32 58V42" stroke="#A78BFA" stroke-width="6" stroke-linecap="round"/></svg>';
    return `<svg viewBox="0 0 64 64"><g transform="rotate(${rot} 32 36)"><path d="M32 58V14" stroke="#A78BFA" stroke-width="7" stroke-linecap="round"/><path d="M18 26L32 10 46 26" fill="none" stroke="#A78BFA" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/></g></svg>`;
  }

  // ---------- Φωνή ----------
  let greek = null;
  function pickVoice() {
    const vs = speechSynthesis.getVoices();
    greek = vs.find((v) => /^el/i.test(v.lang)) || null;
  }
  if ("speechSynthesis" in window) { pickVoice(); speechSynthesis.onvoiceschanged = pickVoice; }
  function say(text, force) {
    if (!("speechSynthesis" in window) || !S.voice || (S.muted && !force)) return;
    const u = new SpeechSynthesisUtterance(text);
    u.lang = "el-GR";
    if (greek) u.voice = greek;
    u.rate = 1.0;
    speechSynthesis.speak(u);
  }
  $("voiceOn").checked = S.voice;
  $("voiceOn").onchange = () => { S.voice = $("voiceOn").checked; store.set("voice", S.voice); };

  // ---------- Αποθήκευση, κοινοποίηση, GPX ----------
  function saved() { return store.get("saved", []); }
  $("saveBtn").onclick = () => {
    if (!S.dest) return;
    const name = prompt("Όνομα διαδρομής:", S.dest.name);
    if (!name) return;
    const list = saved();
    list.unshift({ id: Date.now(), name, type: S.type, dest: S.dest, stops: S.stops, via: S.via });
    store.set("saved", list.slice(0, 100));
    toast("Αποθηκεύτηκε.");
  };
  function renderSaved() {
    const box = $("savedList");
    const list = saved();
    box.innerHTML = list.length ? "" : '<p class="muted">Καμία ακόμα. Πάτα ★ σε μια διαδρομή.</p>';
    list.forEach((it) => {
      const el = document.createElement("div");
      el.className = "item";
      el.innerHTML = `<div>${escapeHtml(it.name)}<small>${{ fast: "Γρήγορη", twisty: "Στροφές", dirt: "Χώμα" }[it.type] || ""}${it.stops && it.stops.length ? ` · ${it.stops.length} στάσεις` : ""}</small></div><button>Άνοιγμα</button><button aria-label="Διαγραφή">🗑</button>`;
      const [open, del] = el.querySelectorAll("button");
      open.onclick = () => {
        $("menu").classList.add("hidden");
        S.dest = it.dest; S.stops = (it.stops || []).slice(); S.via = (it.via || []).slice(); S.type = it.type || S.type;
        plan();
      };
      del.onclick = () => {
        if (!confirm(`Διαγραφή «${it.name}»;`)) return;
        store.set("saved", saved().filter((x) => x.id !== it.id));
        renderSaved();
      };
      box.appendChild(el);
    });
  }

  function shareUrl() {
    const f = (p) => `${p.lat.toFixed(5)},${p.lon.toFixed(5)}`;
    const h = new URLSearchParams();
    h.set("to", f(S.dest));
    h.set("n", S.dest.name);
    if (S.stops.length) h.set("s", S.stops.map(f).join(";"));
    if (S.via.length) h.set("v", Gpx.sample(S.via, 20).map(f).join(";"));
    h.set("t", S.type);
    return `${location.origin}${location.pathname}#${h.toString()}`;
  }
  function fromHash() {
    if (!location.hash || location.hash.length < 4) return;
    const h = new URLSearchParams(location.hash.slice(1));
    const ll = (s) => { const [a, b] = s.split(",").map(Number); return isFinite(a) && isFinite(b) ? { lat: a, lon: b } : null; };
    const to = h.get("to") && ll(h.get("to"));
    if (!to) return;
    S.dest = Object.assign(to, { name: h.get("n") || "Προορισμός" });
    S.stops = (h.get("s") || "").split(";").filter(Boolean).map(ll).filter(Boolean).map((p, i) => Object.assign(p, { name: `Στάση ${i + 1}` }));
    S.via = (h.get("v") || "").split(";").filter(Boolean).map(ll).filter(Boolean);
    if (["fast", "twisty", "dirt"].includes(h.get("t"))) S.type = h.get("t");
    plan();
  }
  $("shareBtn").onclick = async () => {
    if (!S.dest) return;
    const url = shareUrl();
    try {
      if (navigator.share) await navigator.share({ title: `Moto Nav: ${S.dest.name}`, url });
      else { await navigator.clipboard.writeText(url); toast("Ο σύνδεσμος αντιγράφηκε."); }
    } catch (e) { /* ακύρωση */ }
  };
  $("gpxBtn").onclick = async () => {
    if (!S.route) return;
    const name = S.dest ? S.dest.name : "Διαδρομή";
    const blob = new Blob([Gpx.write(name, S.route.points)], { type: "application/gpx+xml" });
    const file = new File([blob], `${name.replace(/[^\p{L}\p{N} _-]/gu, "").trim() || "motonav"}.gpx`, { type: "application/gpx+xml" });
    try {
      if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], title: name }); return; }
    } catch (e) { return; }
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = file.name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  };
  $("importBtn").onclick = () => $("gpxFile").click();
  $("gpxFile").onchange = async () => {
    const f = $("gpxFile").files[0];
    $("gpxFile").value = "";
    if (!f) return;
    const g = Gpx.parse(await f.text());
    if (g.points.length < 2) { toast("Το αρχείο δεν έχει διαδρομή."); return; }
    $("menu").classList.add("hidden");
    // Το BRouter την ακολουθεί περνώντας από έως 20 σημεία της (για να έχει και οδηγίες).
    const pts = Gpx.sample(g.points, 22);
    S.dest = Object.assign({}, pts[pts.length - 1], { name: g.name });
    S.via = pts.slice(0, -1);
    S.stops = [];
    plan();
  };

  // ---------- Μενού ----------
  $("menuBtn").onclick = () => { renderSaved(); $("menu").classList.remove("hidden"); };
  $("menuClose").onclick = () => $("menu").classList.add("hidden");

  // ---------- Εγκατάσταση στο iPhone ----------
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const standalone = navigator.standalone || matchMedia("(display-mode: standalone)").matches;
  if (ios && !standalone && !store.get("installHidden", false)) $("install").classList.remove("hidden");
  $("installClose").onclick = () => { $("install").classList.add("hidden"); store.set("installHidden", true); };

  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => { /* */ });
  startGps();
})();

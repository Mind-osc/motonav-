/*
 * Moto Nav web: οι Ρυθμίσεις με την ίδια σειρά και τα ίδια ονόματα με την Android εφαρμογή,
 * χωρίς όσα δεν γίνονται σε browser (παρέα, offline χάρτες, χειριστήριο, ενημερώσεις APK).
 */
(function () {
  "use strict";
  const A = window.App, SV = window.MotoServices, N = window.MotoNav;
  const { P, store, setPref, toast, esc } = A;
  const $ = (id) => document.getElementById(id);
  const VERSION = 4;
  // Είχε ανοίξει την εφαρμογή πριν; (πρώτη φορά: δεν δείχνουμε «Τι νέο»)
  const existingUser = store.get("center", null) != null || store.get("saved", null) != null;

  const ACCENTS = [["Μωβ", "#A78BFA"], ["Κόκκινο", "#FF7A7A"], ["Πορτοκαλί", "#FB923C"], ["Κίτρινο", "#FACC15"], ["Πράσινο", "#4ADE80"],
    ["Κυανό", "#22D3EE"], ["Μπλε", "#60A5FA"], ["Ροζ", "#F472B6"], ["Λευκό/Γκρι", "#E5E7EB"]];
  const LIST_COLORS = ["#F59E0B", "#EF4444", "#22C55E", "#3B82F6", "#A855F7", "#EC4899", "#14B8A6", "#F97316"];

  let page = "main";
  const TITLES = { main: "Ρυθμίσεις", look: "Εμφάνιση", theme: "Θέμα και χρώματα", marker: "Βελάκι και γραμμή", map: "Χάρτης", gauges: "Όργανα", cameras: "Κάμερες και ειδοποιήσεις", voice: "Φωνή πλοήγησης",
    trips: "Ημερολόγιο διαδρομών", bookmarks: "Σελιδοδείκτες και διαδρομές", keys: "Κλειδιά", backup: "Αντίγραφο ασφαλείας", about: "Πηγές και άδειες" };

  $("settingsBtn").onclick = () => open("main");
  const PARENT = { theme: "look", marker: "look" };
  $("setBack").onclick = () => { if (page === "main") $("settings").classList.add("hidden"); else open(PARENT[page] || "main"); };
  function open(p) {
    page = p;
    $("settings").classList.remove("hidden");
    $("setTitle").textContent = TITLES[p];
    const body = $("setBody");
    body.innerHTML = "";
    body.scrollTop = 0;
    PAGES[p](body);
  }

  // ---------- Μικρά δομικά στοιχεία (όπως LinkRow / SwitchRow της Android) ----------
  function group(body) { const g = document.createElement("div"); g.className = "group"; body.appendChild(g); return g; }
  function link(g, title, sub, onClick) {
    const b = document.createElement("button");
    b.className = "row";
    b.innerHTML = `<span class="t">${esc(title)}${sub ? `<small>${esc(sub)}</small>` : ""}</span><span class="chev">›</span>`;
    b.onclick = onClick;
    g.appendChild(b);
    return b;
  }
  function sw(g, title, sub, key, onChange) {
    const l = document.createElement("label");
    l.className = "row";
    l.innerHTML = `<span class="t">${esc(title)}${sub ? `<small>${esc(sub)}</small>` : ""}</span><input type="checkbox"${P[key] ? " checked" : ""}>`;
    l.querySelector("input").onchange = (e) => { setPref(key, e.target.checked); if (onChange) onChange(e.target.checked); };
    g.appendChild(l);
    return l;
  }
  function pad(g, html) { const d = document.createElement("div"); d.className = "pad"; d.innerHTML = html; g.appendChild(d); return d; }
  function choices(el, items, current, onPick) {
    const row = document.createElement("div");
    row.className = "chips wrap";
    items.forEach(([label, value]) => {
      const b = document.createElement("button");
      b.className = "chip" + (value === current ? " on" : "");
      b.textContent = label;
      b.onclick = () => { onPick(value); row.querySelectorAll(".chip").forEach((x) => x.classList.remove("on")); b.classList.add("on"); };
      row.appendChild(b);
    });
    el.appendChild(row);
    return row;
  }

  const PAGES = {
    main(body) {
      let g = group(body);
      link(g, "Εμφάνιση", "Θέμα, χρώματα, βελάκι, γραμμή, μέγεθος, ώρα και μπαταρία", () => open("look"));
      link(g, "Χάρτης", "Εναλλακτικές, στροφές, αποφυγές, μέρη για μηχανόβιους", () => open("map"));
      link(g, "Όργανα", "Κλισιόμετρο, πυξίδα", () => open("gauges"));
      link(g, "Κάμερες και ειδοποιήσεις", "Κάμερες, φωτισμός ορίου, φωνή ορίου, βενζίνη, καιρός", () => open("cameras"));
      link(g, "Φωνή πλοήγησης", "Ελληνικά ή English · φωνή του κινητού ή ElevenLabs", () => open("voice"));
      g = group(body);
      link(g, "Ημερολόγιο διαδρομών", "Καταγραφή με παύση, χιλιόμετρα, κλίσεις, GPX", () => open("trips"));
      link(g, "Σελιδοδείκτες και διαδρομές", "Λίστες μερών με χρώματα, αποθηκευμένες διαδρομές, GPX/KML/GeoJSON", () => open("bookmarks"));
      g = group(body);
      link(g, "Κλειδιά", "TomTom (κίνηση), ElevenLabs (φωνές)", () => open("keys"));
      link(g, "Αντίγραφο ασφαλείας", "Όλα σε ένα αρχείο, επαναφορά με ένα πάτημα", () => open("backup"));
      g = group(body);
      link(g, "Πηγές και άδειες", "Από πού έρχονται χάρτες, διαδρομές, κίνηση", () => open("about"));
      g = group(body);
      const coffee = link(g, "☕ Κέρασέ με έναν καφέ", "Η εφαρμογή είναι και θα μείνει δωρεάν", () => window.open("https://paypal.me/STAVROSMALAGARDIS", "_blank"));
      coffee.querySelector(".chev").textContent = "↗";
      link(g, "Τι νέο υπάρχει", "Οι αλλαγές της web έκδοσης", () => whatsNew(true));
      body.insertAdjacentHTML("beforeend", `<div class="foot">Streetwolf web · έκδοση ${VERSION}. Για Android υπάρχει η πλήρης εφαρμογή
        (offline, παρέα, CarPlay/Android Auto): <a href="https://github.com/Mind-osc/motonav-" target="_blank" rel="noopener">github.com/Mind-osc/motonav-</a></div>`);
    },

    look(body) {
      let g = group(body);
      link(g, "Θέμα και χρώματα", "Σκούρο / μαύρο / ανοιχτό / αυτόματο, χρώμα εφαρμογής", () => open("theme"));
      link(g, "Βελάκι και γραμμή", "Σχήμα, μέγεθος, χρώματα, περίγραμμα, δική σου εικόνα", () => open("marker"));
      g = group(body);
      const d = pad(g, `<div>Μέγεθος κουμπιών και κειμένων: <b id="uiv">${Math.round(P.uiScale * 100)}%</b></div><input type="range" min="0.75" max="1.4" step="0.05" style="width:100%;accent-color:var(--accent)">`);
      const r = d.querySelector("input");
      r.value = P.uiScale;
      r.oninput = () => { d.querySelector("#uiv").textContent = Math.round(r.value * 100) + "%"; };
      r.onchange = () => setPref("uiScale", +r.value);
      sw(g, "Χρώμα ταμπέλας ανά δρόμο", "Πράσινη: αυτοκινητόδρομος · Μπλε: εθνική/κύρια · Πορτοκαλί: χώμα · Μπορντό: τελευταία στροφή/άφιξη", "roadColors");
      sw(g, "Δική μας ώρα και μπαταρία", "Πάνω πάνω: ώρα, υψόμετρο, καιρός, GPS, μπαταρία", "customStatus");
    },

    marker(body) {
      const m = Object.assign({}, P.marker);
      const save = () => { setPref("marker", Object.assign({}, m)); preview(); };
      const pv = document.createElement("div");
      pv.style.cssText = "display:grid;place-items:center;height:150px;background:var(--surface2);border-radius:20px;margin:12px 0;position:relative;overflow:hidden";
      body.appendChild(pv);
      function preview() {
        const fill = m.fill || A.darker(A.accentNow(), 0.4);
        const sz = Math.round(Math.max(30, Math.min(140, m.size)) * 0.9);
        pv.innerHTML = `<div style="position:absolute;left:50%;top:0;bottom:0;width:${10 + m.lineOutline * 3}px;transform:translateX(-50%);background:${m.lineOutlineColor || A.accentNow()};border-radius:8px"></div>
          <div style="position:absolute;left:50%;top:0;bottom:0;width:10px;transform:translateX(-50%);background:${m.lineColor || "#fff"};border-radius:6px"></div>
          <div style="position:relative;width:${sz}px;height:${sz}px">${window.MotoIcons.puck(Object.assign({}, m, { fill }))}</div>`;
      }
      preview();
      let g = group(body);
      let d = pad(g, "<div>Σχήμα</div>");
      choices(d, [["Κλασικό", "CLASSIC"], ["Γυαλιστερό", "GLOSSY"], ["Μυτερό", "SHARP"], ["Κύκλος", "CIRCLE"], ["Σιρίτι", "CHEVRON"], ["Κόκκινο (εικόνα)", "RED_IMAGE"], ["Δική σου εικόνα", "CUSTOM"]],
        m.shape, (v) => {
          if (v === "CUSTOM" && !m.image) { pick.click(); return; }
          m.shape = v; save();
        });
      const pick = document.createElement("input");
      pick.type = "file"; pick.accept = "image/*"; pick.className = "hidden";
      pick.onchange = () => {
        const f = pick.files[0];
        if (!f) return;
        const img = new Image();
        img.onload = () => {
          const c = document.createElement("canvas");
          const k = 160 / Math.max(img.width, img.height);
          c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
          c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
          m.image = c.toDataURL("image/png"); m.shape = "CUSTOM"; save(); open("marker");
        };
        img.src = URL.createObjectURL(f);
      };
      d.appendChild(pick);
      if (m.image) { const b = document.createElement("button"); b.className = "chip"; b.style.marginTop = "8px"; b.textContent = "Άλλη εικόνα…"; b.onclick = () => pick.click(); d.appendChild(b); }
      d = pad(g, `<div>Μέγεθος: <b id="mv">${m.size}</b></div><input type="range" min="30" max="140" step="2" style="width:100%;accent-color:var(--accent)">`);
      const rs = d.querySelector("input");
      rs.value = m.size;
      rs.oninput = () => { m.size = +rs.value; d.querySelector("#mv").textContent = m.size; preview(); };
      rs.onchange = save;
      const colors = (label, key, def) => {
        const dd = pad(g, `<div>${label}</div><div class="swatches"></div>`);
        const box = dd.querySelector(".swatches");
        [[def, null]].concat(["#E11D48", "#F97316", "#FACC15", "#22C55E", "#06B6D4", "#3B82F6", "#8B5CF6", "#EC4899", "#FFFFFF", "#111318"].map((c) => [c, c])).forEach(([c, v], i) => {
          const b = document.createElement("button");
          b.className = "swatch" + (m[key] === v ? " on" : "");
          b.style.background = c; b.title = i === 0 ? "Του θέματος" : c;
          if (i === 0) b.style.outline = "2px dashed var(--muted)";
          b.onclick = () => { m[key] = v; save(); box.querySelectorAll(".swatch").forEach((x) => x.classList.remove("on")); b.classList.add("on"); };
          box.appendChild(b);
        });
      };
      colors("Χρώμα βελακιού", "fill", A.darker(A.accentNow(), 0.4));
      g = group(body);
      const ol = document.createElement("label");
      ol.className = "row";
      ol.innerHTML = `<span class="t">Περίγραμμα βελακιού</span><input type="checkbox"${m.outline ? " checked" : ""}>`;
      ol.querySelector("input").onchange = (e) => { m.outline = e.target.checked; save(); };
      g.appendChild(ol);
      const dd = pad(g, '<div>Χρώμα περιγράμματος</div><div class="swatches"></div>');
      ["#FFFFFF", "#111318", "#FACC15", "#E11D48", "#22C55E", "#3B82F6"].forEach((c) => {
        const b = document.createElement("button");
        b.className = "swatch" + (m.outlineColor === c ? " on" : "");
        b.style.background = c;
        b.onclick = () => { m.outlineColor = c; save(); dd.querySelectorAll(".swatch").forEach((x) => x.classList.remove("on")); b.classList.add("on"); };
        dd.querySelector(".swatches").appendChild(b);
      });
      g = group(body);
      colors("Χρώμα γραμμής", "lineColor", "#FFFFFF");
      colors("Περίγραμμα γραμμής", "lineOutlineColor", A.accentNow());
      d = pad(g, `<div>Πάχος περιγράμματος γραμμής: <b id="lo">${m.lineOutline}</b></div><input type="range" min="0" max="5" step="0.5" style="width:100%;accent-color:var(--accent)">`);
      const lo = d.querySelector("input");
      lo.value = m.lineOutline;
      lo.oninput = () => { m.lineOutline = +lo.value; d.querySelector("#lo").textContent = m.lineOutline; preview(); };
      lo.onchange = save;
      const reset = document.createElement("button");
      reset.className = "chip"; reset.textContent = "Επαναφορά";
      reset.onclick = () => { setPref("marker", Object.assign({}, A.DEF.marker)); open("marker"); };
      body.appendChild(reset);
    },

    theme(body) {
      const g = group(body);
      const d = pad(g, '<div>Θέμα</div>');
      choices(d, [["Σκούρο", "dark"], ["Μαύρο", "black"], ["Ανοιχτό", "light"], ["Αυτόματο", "auto"]], P.theme, (v) => setPref("theme", v));
      const d2 = pad(g, '<div>Χρώμα εφαρμογής</div><div class="swatches"></div>');
      const sw2 = d2.querySelector(".swatches");
      ACCENTS.forEach(([name, c]) => {
        const b = document.createElement("button");
        b.className = "swatch" + (P.accent === c ? " on" : "");
        b.style.background = c; b.title = name; b.setAttribute("aria-label", name);
        b.onclick = () => { setPref("accent", c); A.drawPuck(); sw2.querySelectorAll(".swatch").forEach((x) => x.classList.remove("on")); b.classList.add("on"); };
        sw2.appendChild(b);
      });
    },

    map(body) {
      let g = group(body);
      sw(g, "Εναλλακτικές διαδρομές", "Γκρι γραμμές με τη διαφορά σε λεπτά (μόνο λογικές, για διαδρομές > 5 km)", "alternatives");
      sw(g, "Πολλές στροφές", "Στον τύπο «Στροφές»: ακόμα πιο στριφτοί δρόμοι, ακόμα κι αν αργείς λίγο παραπάνω", "moreCurves");
      g = group(body);
      pad(g, '<div class="muted small">ΑΠΟΦΥΓΕΣ · αυτοκινητόδρομοι και διόδια είναι στην κάρτα της διαδρομής</div>');
      sw(g, "Αποφυγή πλοίων", "Χωρίς φέρι στη διαδρομή", "avFerry");
      sw(g, "Αποφυγή κύριων δρόμων", "Λιγότερες εθνικές και κύριες οδούς (λιγότερη κίνηση)", "avMain");
      sw(g, "Αποφυγή πολύ στενών δρόμων", "Λιγότερα αγροτικά, δρομάκια εξυπηρέτησης και πεζόδρομους", "avNarrow");
      sw(g, "Αποφυγή χωματόδρομων", "Όσο γίνεται χωρίς χώμα και χαλίκι (όχι στον τύπο «Χώμα»)", "avUnpaved");
      const n = A.S.nogos.length;
      if (n) link(g, `Περιοχές αποφυγής (${n})`, "Διαγραφή όλων", () => {
        if (!confirm("Διαγραφή όλων των περιοχών αποφυγής;")) return;
        A.S.nogos.length = 0; store.set("nogos", []); toast("Διαγράφηκαν."); open("map");
      });
      g = group(body);
      sw(g, "Μέρη για μηχανόβιους", "Διάλεξε παρακάτω τι θες να φαίνεται, από ζουμ 11", "showSpots", () => open("map"));
      if (P.showSpots) {
        const d = pad(g, "");
        const row = document.createElement("div");
        row.className = "chips wrap";
        Object.entries(SV.SPOT_KINDS).forEach(([k, v]) => {
          const b = document.createElement("button");
          const on = !P.hiddenSpots.includes(k);
          b.className = "chip" + (on ? " on" : "");
          b.textContent = `${v.emoji} ${v.label}`;
          b.onclick = () => {
            const hidden = P.hiddenSpots.includes(k) ? P.hiddenSpots.filter((x) => x !== k) : P.hiddenSpots.concat([k]);
            setPref("hiddenSpots", hidden);
            b.classList.toggle("on", !hidden.includes(k));
          };
          row.appendChild(b);
        });
        d.appendChild(row);
      }
      sw(g, "Αγαπημένα στον χάρτη", "Τελείες με το χρώμα της λίστας τους", "showFavs");
    },

    gauges(body) {
      let g = group(body);
      sw(g, "Κλισιόμετρο", "Πόσο γέρνεις (πάνω από την ταχύτητα). Το κινητό στερεωμένο στη βάση.", "lean", async (on) => {
        if (on && !(await A.enableSensors())) setPref("lean", false);
      });
      link(g, "Μηδενισμός κλισιόμετρου", "Με τη μηχανή όρθια, πάτα εδώ", () => { A.resetLean(); toast("Μηδενίστηκε."); });
      link(g, "Πυξίδα όταν είσαι σταματημένος", "Το iPhone ζητάει άδεια για προσανατολισμό", async () => { if (await A.enableSensors()) toast("Η πυξίδα δουλεύει."); });
      body.insertAdjacentHTML("beforeend", '<div class="foot">Στην κίνηση η πυξίδα παίρνει την κατεύθυνση από το GPS. Πάτημα στο κουτί της ταχύτητας: κέντρο στη θέση σου· στην πλοήγηση, όλη η διαδρομή που μένει.</div>');
    },

    cameras(body) {
      const g = group(body);
      sw(g, "Κάμερες", "Σταθερές κάμερες της Αττικής, με φωνή όταν πλησιάζεις", "cameras");
      sw(g, "Φωτισμός όταν περνάς το όριο", "Το κόκκινο πάνω στην οθόνη· κλείσ' το αν σε ενοχλεί (μένει το κόκκινο νούμερο)", "overGlow");
      sw(g, "Φωνή για το όριο", "Λέει το νέο όριο όταν αλλάζει", "limitVoice");
      sw(g, "Βενζίνη στη διαδρομή", "Το επόμενο βενζινάδικο κάτω από την οδηγία", "fuelAlert");
      sw(g, "Καιρός στη διαδρομή", "Βροχή, χιόνι, αέρας ή παγωνιά την ώρα που θα περνάς", "weatherAlert");
    },

    voice(body) {
      let g = group(body);
      sw(g, "Φωνή πλοήγησης", "Οδηγίες, κάμερες, όριο, καιρός", "voice");
      const d = pad(g, '<div>Γλώσσα οδηγιών</div><div class="muted small">Στα αγγλικά τα ονόματα δρόμων λέγονται με λατινικούς χαρακτήρες.</div>');
      choices(d, [["Ελληνικά", false], ["English", true]], P.english, (v) => setPref("english", v));
      const test = document.createElement("button");
      test.className = "chip"; test.style.marginTop = "10px"; test.textContent = "Άκουσέ τη";
      test.onclick = () => A.say(P.english ? "In 300 metres, turn right onto Leoforos Syngrou." : "Σε 300 μέτρα, στρίψε δεξιά στη Λεωφόρο Συγγρού.", true);
      d.appendChild(test);
      g = group(body);
      const d2 = pad(g, '<div>Φωνή</div><div class="muted small">Η φωνή του κινητού δουλεύει χωρίς τίποτα. Το ElevenLabs θέλει δωρεάν κλειδί (Ρυθμίσεις → Κλειδιά).</div>');
      choices(d2, [["Του κινητού", "phone"], ["ElevenLabs", "eleven"]], P.voiceEngine, (v) => { setPref("voiceEngine", v); open("voice"); });
      if (P.voiceEngine === "eleven") {
        if (!P.elevenKey) pad(g, '<div class="muted">Βάλε πρώτα κλειδί ElevenLabs: Ρυθμίσεις → Κλειδιά.</div>');
        else {
          const box = pad(g, '<div class="muted small">Δείχνουμε μόνο φωνές που παίζουν στο δωρεάν πακέτο. Πάτημα = επιλογή.</div><div class="list" style="margin-top:8px">Φορτώνει…</div>');
          SV.elevenVoices(P.elevenKey).then((vs) => {
            const list = box.querySelector(".list");
            list.innerHTML = "";
            vs.forEach((v) => {
              const b = document.createElement("button");
              b.className = "item";
              b.innerHTML = `<span class="t">${v.id === P.elevenVoice ? "✓ " : ""}${esc(v.name)}<small>${esc(v.desc)}</small></span>${v.preview ? '<span class="act">▶</span>' : ""}`;
              b.onclick = (e) => {
                if (e.target.classList.contains("act")) { new Audio(v.preview).play(); return; }
                setPref("elevenVoice", v.id); open("voice");
              };
              list.appendChild(b);
            });
          }).catch((e) => { box.querySelector(".list").textContent = e.message; });
        }
      }
      g = group(body);
      const d3 = pad(g, '<div>Φράση στο «Ξεκίνα»</div><div class="muted small">Το {προορισμός} γίνεται το όνομα του προορισμού.</div><input class="field" style="margin-top:8px">');
      const inp = d3.querySelector("input");
      inp.value = P.startPhrase;
      inp.onchange = () => setPref("startPhrase", inp.value.trim() || A.DEF.startPhrase);
    },

    trips(body) {
      const trips = store.get("trips", []);
      const g = group(body);
      sw(g, "Καταγραφή σε κάθε πλοήγηση", "Γράφει μόνη της κάθε διαδρομή που οδηγείς", "autoRecord");
      body.insertAdjacentHTML("beforeend", '<div class="foot">Χειροκίνητη καταγραφή: το κουμπί ⏺ στον χάρτη (με παύση). Στο iPhone γράφει όσο η οθόνη είναι ανοιχτή.</div>');
      if (!trips.length) { body.insertAdjacentHTML("beforeend", '<div class="foot">Καμία διαδρομή ακόμα.</div>'); return; }
      const total = trips.reduce((a, t) => a + t.dist, 0);
      body.insertAdjacentHTML("beforeend", `<div class="h3">${trips.length} διαδρομές · ${A.Phrases.short(total)}</div>`);
      const list = document.createElement("div");
      list.className = "list";
      trips.forEach((t) => {
        const min = Math.round((t.end - t.start) / 60000);
        const avg = min > 0 ? Math.round(t.dist / 1000 / (min / 60)) : 0;
        const el = document.createElement("div");
        el.className = "item";
        el.innerHTML = `<span class="t">${esc(t.name)}<small>${new Date(t.start).toLocaleDateString("el-GR")} · ${A.Phrases.short(t.dist)} · ${min}′ · μέση ${avg} · μέγ. ${t.maxKmh} km/h${t.lean && (t.lean.l || t.lean.r) ? ` · κλίση ${t.lean.l}°/${t.lean.r}°` : ""}</small></span>
          <button class="act">Χάρτης</button><button class="act">GPX</button><button class="act" aria-label="Διαγραφή">🗑</button>`;
        const [show, gpx, del] = el.querySelectorAll("button");
        show.onclick = () => { $("settings").classList.add("hidden"); A.followTrack(t.points, t.name); };
        gpx.onclick = () => A.exportGpx(t.name, t.points);
        del.onclick = () => { if (confirm("Διαγραφή;")) { store.set("trips", store.get("trips", []).filter((x) => x.id !== t.id)); open("trips"); } };
        list.appendChild(el);
      });
      body.appendChild(list);
    },

    bookmarks(body) {
      const lists = A.lists();
      body.insertAdjacentHTML("beforeend", '<div class="h3">Λίστες μερών</div>');
      lists.forEach((l, li) => {
        const g = group(body);
        const head = pad(g, `<div style="display:flex;align-items:center;gap:10px"><span style="width:18px;height:18px;border-radius:50%;background:${l.color};display:inline-block"></span><b style="flex:1">${esc(l.name)}</b><span class="muted">${l.items.length}</span></div>`);
        const acts = document.createElement("div");
        acts.className = "chips wrap";
        const mk = (t, f) => { const b = document.createElement("button"); b.textContent = t; b.onclick = f; acts.appendChild(b); };
        mk("Χρώμα", () => { l.color = LIST_COLORS[(LIST_COLORS.indexOf(l.color) + 1) % LIST_COLORS.length]; A.saveLists(lists); open("bookmarks"); });
        mk("Μετονομασία", () => { const n = prompt("Όνομα λίστας:", l.name); if (n) { l.name = n; A.saveLists(lists); open("bookmarks"); } });
        if (l.items.length) mk("GPX", () => exportWpts(l));
        if (lists.length > 1) mk("Διαγραφή", () => { if (confirm(`Διαγραφή «${l.name}»;`)) { lists.splice(li, 1); A.saveLists(lists); open("bookmarks"); } });
        head.appendChild(acts);
        l.items.forEach((it, ii) => {
          const r = document.createElement("div");
          r.className = "row";
          r.innerHTML = `<span class="t">${esc(it.name)}</span><button class="chip">Πάμε</button><button class="chip" aria-label="Διαγραφή">🗑</button>`;
          const [go, del] = r.querySelectorAll("button");
          go.onclick = () => { $("settings").classList.add("hidden"); A.setDest(it); };
          del.onclick = () => { l.items.splice(ii, 1); A.saveLists(lists); open("bookmarks"); };
          g.appendChild(r);
        });
      });
      const nl = document.createElement("button");
      nl.className = "chip"; nl.textContent = "+ Νέα λίστα";
      nl.onclick = () => {
        const n = prompt("Όνομα λίστας:");
        if (!n) return;
        lists.push({ name: n, color: LIST_COLORS[lists.length % LIST_COLORS.length], items: [] });
        A.saveLists(lists); open("bookmarks");
      };
      body.appendChild(nl);

      body.insertAdjacentHTML("beforeend", '<div class="h3">Αποθηκευμένες διαδρομές</div>');
      const saved = store.get("saved", []);
      const list = document.createElement("div");
      list.className = "list";
      if (!saved.length) list.innerHTML = '<div class="foot">Καμία ακόμα. Πάτα 💾 στην κάρτα μιας διαδρομής.</div>';
      saved.forEach((it) => {
        const el = document.createElement("div");
        el.className = "item";
        const type = { fast: "Γρήγορη", twisty: "Στροφές", dirt: "Χώμα", traffic: "Κίνηση" }[it.type] || "";
        el.innerHTML = `<span class="t">${esc(it.name)}<small>${type}${it.km ? ` · ${String(it.km).replace(".", ",")} km` : ""}${it.stops && it.stops.length ? ` · ${it.stops.length} στάσεις` : ""}</small></span>
          <button class="act">Ακολούθησε</button><button class="act" aria-label="Διαγραφή">🗑</button>`;
        const [go, del] = el.querySelectorAll("button");
        go.onclick = () => { $("settings").classList.add("hidden"); if (it.track) A.followTrack(it.track, it.name); else A.openRoute(it); };
        del.onclick = () => { if (confirm(`Διαγραφή «${it.name}»;`)) { store.set("saved", store.get("saved", []).filter((x) => x.id !== it.id)); open("bookmarks"); } };
        list.appendChild(el);
      });
      body.appendChild(list);

      const g = group(body);
      const inp = document.createElement("input");
      inp.type = "file"; inp.accept = ".gpx,.kml,.geojson,.json,application/gpx+xml,application/vnd.google-earth.kml+xml,application/geo+json,application/xml,text/xml"; inp.className = "hidden";
      inp.onchange = async () => {
        const f = inp.files[0];
        if (!f) return;
        try {
          const r = N.Gpx.parseAny(await f.text(), f.name);
          if (r.points.length < 2) { toast("Το αρχείο δεν έχει διαδρομή."); return; }
          $("settings").classList.add("hidden");
          A.followTrack(r.points, r.name);
        } catch (e) { toast("Δεν διαβάστηκε το αρχείο."); }
      };
      g.appendChild(inp);
      link(g, "📂 Άνοιγμα GPX / KML / GeoJSON", "Η διαδρομή ακολουθείται από εκεί που είσαι", () => inp.click());
    },

    keys(body) {
      const g = group(body);
      const mk = (title, sub, key, href) => {
        const d = pad(g, `<div>${esc(title)}</div><div class="muted small">${sub} · <a href="${href}" target="_blank" rel="noopener">δωρεάν κλειδί</a></div><input class="field" style="margin-top:8px" autocomplete="off" spellcheck="false">`);
        const inp = d.querySelector("input");
        inp.value = P[key];
        inp.placeholder = "Επικόλληση κλειδιού";
        inp.onchange = () => { setPref(key, inp.value.trim()); toast("Αποθηκεύτηκε."); };
      };
      mk("TomTom", "Κίνηση live και χρόνος άφιξης με κίνηση (τύπος 🚦)", "ttKey", "https://my.tomtom.com/keys");
      mk("ElevenLabs", "Πιο φυσικές φωνές πλοήγησης", "elevenKey", "https://elevenlabs.io/app/settings/api-keys");
      body.insertAdjacentHTML("beforeend", '<div class="foot">Τα κλειδιά μένουν μόνο σε αυτόν τον browser, δεν πάνε πουθενά αλλού.</div>');
    },

    backup(body) {
      const g = group(body);
      link(g, "Αποθήκευση αντιγράφου", "Ρυθμίσεις, κλειδιά, αγαπημένα, διαδρομές, ημερολόγιο", () => {
        const data = {};
        for (let i = 0; i < localStorage.length; i++) {
          const k = localStorage.key(i);
          if (k.startsWith("motonav.") && !k.startsWith("motonav.spots.")) data[k] = localStorage.getItem(k);
        }
        const blob = new Blob([JSON.stringify({ app: "motonav-web", v: 1, data })], { type: "application/json" });
        const file = new File([blob], `motonav-web-${new Date().toISOString().slice(0, 10)}.json`, { type: "application/json" });
        if (navigator.canShare && navigator.canShare({ files: [file] })) { navigator.share({ files: [file] }).catch(() => {}); return; }
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob); a.download = file.name; a.click();
      });
      const inp = document.createElement("input");
      inp.type = "file"; inp.accept = "application/json,.json"; inp.className = "hidden";
      inp.onchange = async () => {
        const f = inp.files[0];
        if (!f) return;
        try {
          const j = JSON.parse(await f.text());
          if (j.app !== "motonav-web") throw new Error();
          Object.entries(j.data).forEach(([k, v]) => localStorage.setItem(k, v));
          toast("Έγινε επαναφορά. Ξαναφορτώνει…");
          setTimeout(() => location.reload(), 1200);
        } catch (e) { toast("Δεν είναι αντίγραφο της web έκδοσης."); }
      };
      g.appendChild(inp);
      link(g, "Επαναφορά από αντίγραφο", "Αντικαθιστά ό,τι υπάρχει εδώ", () => inp.click());
      body.insertAdjacentHTML("beforeend", '<div class="foot">Τα στοιχεία μένουν σε αυτόν τον browser. Αν σβήσεις τα δεδομένα του Safari, χάνονται: κράτα αντίγραφο.</div>');
    },

    about(body) {
      body.insertAdjacentHTML("beforeend", `<div class="foot">
        <b>Χάρτης:</b> <a href="https://openfreemap.org" target="_blank" rel="noopener">OpenFreeMap</a>, δεδομένα © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>.<br>
        <b>Διαδρομές:</b> <a href="https://brouter.de" target="_blank" rel="noopener">BRouter</a> (online). Κίνηση: TomTom (με δικό σου κλειδί).<br>
        <b>Αναζήτηση:</b> Photon (komoot). <b>Βενζινάδικα, μέρη:</b> Overpass (OSM).<br>
        <b>Καιρός:</b> <a href="https://open-meteo.com" target="_blank" rel="noopener">Open-Meteo</a>. <b>Φωνές:</b> του κινητού ή ElevenLabs.<br>
        <b>Κάμερες:</b> σταθερές κάμερες Αττικής από δημόσιες πηγές.<br><br>
        Η web έκδοση δεν έχει offline χάρτες, παρέα και CarPlay. Στο iPhone η πλοήγηση δουλεύει όσο η οθόνη είναι ανοιχτή και η εφαρμογή μπροστά.
      </div>`);
    },
  };

  // ---------- Αγαπημένα: διάλεξε λίστα ----------
  function addFavorite(p) {
    const lists = A.lists();
    const name = prompt("Όνομα:", p.name || "Αγαπημένο");
    if (!name) return;
    let li = 0;
    if (lists.length > 1) {
      const pick = prompt("Σε ποια λίστα; " + lists.map((l, i) => `${i + 1}. ${l.name}`).join(" · "), "1");
      li = Math.max(0, Math.min(lists.length - 1, (parseInt(pick, 10) || 1) - 1));
    }
    lists[li].items.unshift({ lat: p.lat, lon: p.lon, name });
    A.saveLists(lists);
    toast(`Στη λίστα «${lists[li].name}».`);
  }
  function exportWpts(l) {
    const esc2 = (s) => String(s).replace(/[<&>"]/g, (c) => ({ "<": "&lt;", "&": "&amp;", ">": "&gt;", '"': "&quot;" }[c]));
    const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Streetwolf web" xmlns="http://www.topografix.com/GPX/1/1">` +
      l.items.map((it) => `<wpt lat="${it.lat}" lon="${it.lon}"><name>${esc2(it.name)}</name></wpt>`).join("") + "</gpx>\n";
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([xml], { type: "application/gpx+xml" }));
    a.download = `${l.name}.gpx`;
    a.click();
  }

  // ---------- «Τι νέο υπάρχει» (μία φορά μετά από αλλαγή) ----------
  const NEWS = [
    { id: 4, title: "Πιο μαζεμένη οθόνη", added: [
      "Μικρότερη ταμπέλα οδηγιών, όπως στην εφαρμογή",
      "Καταγραφή: κόκκινη τελεία δίπλα στην ώρα · πάτα την για παύση ή τέλος",
      "Κλισιόμετρο: πιο σταθερό, φαίνεται μόνο στην πλοήγηση, και δεν σκεπάζει πια τα κουμπιά",
      "Μέγεθος κουμπιών και κειμένων: 90% από προεπιλογή",
    ] },
    { id: 3, title: "Ακόμα πιο ίδια με την Android", added: [
      "Τα ίδια εικονίδια και τα ίδια βέλη στροφών με την εφαρμογή",
      "Εμφάνιση → Βελάκι και γραμμή: σχήμα, μέγεθος, χρώματα, περίγραμμα, δική σου εικόνα",
      "Μέγεθος κουμπιών και κειμένων, χρώμα ταμπέλας ανά δρόμο, ώρα και μπαταρία",
      "Οι πηγές του χάρτη (το λευκό ⓘ κάτω δεξιά) πήγαν στις Ρυθμίσεις → Πηγές και άδειες",
      "Πυξίδα: γυρίζει και όταν το κινητό είναι όρθιο στη βάση",
    ] },
    { id: 2, title: "Ίδια με την Android εφαρμογή", added: [
      "Ίδια μενού και ίδια διάταξη: ώρα/υψόμετρο, καιρός, ταχύτητα με πυξίδα, κάρτα διαδρομής, Ρυθμίσεις",
      "Τύποι Γρήγορη / Στροφές / Χώμα / Κίνηση (TomTom), εναλλακτικές, περιοχές αποφυγής, κυκλική βόλτα",
      "Αποφυγές: πλοία, κύριοι δρόμοι, στενοί δρόμοι, χωματόδρομοι · «Πολλές στροφές»",
      "Βενζινάδικα και καιρός στη διαδρομή, κάμερες Αττικής, όριο ταχύτητας με φωτισμό",
      "Μέρη για μηχανόβιους, αγαπημένα σε λίστες με χρώματα, αποθηκευμένες διαδρομές, GPX/KML/GeoJSON",
      "Ημερολόγιο διαδρομών με καταγραφή, κλισιόμετρο, θέματα και χρώματα, φωνή ελληνικά ή English, ElevenLabs",
      "Στην πλοήγηση: ⚠ κλειστός δρόμος / νέα διαδρομή, παράλειψη στάσης, προσομοίωση (κράτα πατημένο το «Ξεκίνα»)",
    ] },
    { id: 1, title: "Πρώτη web έκδοση", added: ["Moto Nav για iPhone και υπολογιστή"] },
  ];
  function whatsNew(force) {
    const seen = store.get("newsSeen", 0);
    const latest = NEWS[0].id;
    if (!force && (seen >= latest || (seen === 0 && !existingUser))) { store.set("newsSeen", latest); return; }
    const items = force ? NEWS.slice(0, 1) : NEWS.filter((n) => n.id > seen);
    const ov = $("whatsNew");
    ov.innerHTML = `<div class="box"><div style="font-size:24px;font-weight:700;margin-bottom:10px">Τι νέο υπάρχει</div>${items.map((n) => `
      <div style="color:var(--accent);font-weight:700;margin:10px 0 4px">${esc(n.title)}</div>
      <div class="muted small">Νέα</div>${n.added.map((a) => `<div style="margin:4px 0">•  ${esc(a)}</div>`).join("")}`).join("")}
      <button class="btn pri" style="width:100%;margin-top:16px">Κλείσιμο</button></div>`;
    ov.classList.remove("hidden", "closing");
    const close = () => { ov.classList.add("closing"); setTimeout(() => ov.classList.add("hidden"), 360); store.set("newsSeen", latest); };
    ov.querySelector("button").onclick = close;
    ov.onclick = (e) => { if (e.target === ov) close(); };
  }
  setTimeout(() => whatsNew(false), 800);

  window.Settings = { open, addFavorite };
})();

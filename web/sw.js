// Moto Nav web: κρατάει την εφαρμογή (όχι τον χάρτη) για γρήγορο άνοιγμα. Νέα έκδοση → αλλάζει το VERSION.
const VERSION = "motonav-web-3";
const SHELL = ["./", "index.html", "style.css", "icons.js", "nav.js", "services.js", "app.js", "settings.js", "manifest.webmanifest", "moto-dirt.brf", "cameras.json",
  "fonts/barlow_condensed_semibold.ttf", "icons/icon-192.png", "icons/apple-touch-icon.png", "icons/puck_red.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
// Δικά μας αρχεία: πρώτα το δίκτυο (για να έρχονται οι αλλαγές), αλλιώς από την cache.
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== location.origin) return;
  e.respondWith(
    fetch(e.request).then((res) => {
      const copy = res.clone();
      caches.open(VERSION).then((c) => c.put(e.request, copy));
      return res;
    }).catch(() => caches.match(e.request).then((r) => r || caches.match("index.html")))
  );
});

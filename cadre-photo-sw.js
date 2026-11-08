// Cadre photo – service worker (version 3.5)
// Partage le dépôt avec d'autres applis : il ne s'occupe QUE des fichiers « cadre-photo* »
// et ne touche jamais aux caches des autres applis.
const PREFIX = "cadre-photo-";
const CACHE = PREFIX + "3.5";
const SHELL = ["./cadre-photo.html", "./cadre-photo.webmanifest", "./cadre-photo-icon-192.png", "./cadre-photo-icon-512.png"];
const mine = url => url.origin === self.location.origin && url.pathname.split("/").pop().startsWith("cadre-photo");

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => Promise.allSettled(SHELL.map(u => c.add(u)))).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k.startsWith(PREFIX) && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const req = e.request, url = new URL(req.url);
  if (req.method !== "GET" || !mine(url)) return;
  if (url.pathname.endsWith(".html")) {
    // page : réseau d'abord pour recevoir les mises à jour, cache si hors ligne
    e.respondWith(fetch(req).then(r => {
      if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return r;
    }).catch(() => caches.match(req).then(r => r || caches.match("./cadre-photo.html"))));
  } else {
    e.respondWith(caches.match(req).then(r => r || fetch(req).then(n => {
      if (n.ok) { const copy = n.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return n;
    })));
  }
});

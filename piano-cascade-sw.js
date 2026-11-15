// Service worker de Piano Cascade : ne gère que les adresses piano-cascade*
const CACHE = "piano-cascade-1.5.0";
const CORE = ["./piano-cascade.html", "./piano-cascade.webmanifest", "./piano-cascade-icon-192.png", "./piano-cascade-icon-512.png", "./piano-cascade-apple-touch-icon.png"];
const CDN = /^https:\/\/(cdnjs\.cloudflare\.com|cdn\.jsdelivr\.net|fonts\.googleapis\.com|fonts\.gstatic\.com)\//;
const MINE = url => url.origin === location.origin && /\/piano-cascade[^/]*$/.test(url.pathname);

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith("piano-cascade-") && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // la page : réseau d'abord (pour recevoir les mises à jour), cache si hors ligne
  if (req.mode === "navigate" && MINE(url)){
    e.respondWith(fetch(req).then(r => { const cp = r.clone(); caches.open(CACHE).then(c => c.put("./piano-cascade.html", cp)); return r; })
      .catch(() => caches.match("./piano-cascade.html")));
    return;
  }
  // fichiers de l'app, bibliothèques et polices : cache d'abord
  if (MINE(url) || CDN.test(req.url)){
    e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(r => {
      if (r && (r.ok || r.type === "opaque")){ const cp = r.clone(); caches.open(CACHE).then(c => c.put(req, cp)); }
      return r;
    })));
  }
});

// Service worker de Trajet VE — enregistré avec la portée "./trajet-ve.html" :
// il ne contrôle que cette page, jamais les autres sites du dépôt.
// Changer CACHE à chaque nouvelle version (même numéro que CHANGELOG dans trajet-ve.html).
const CACHE = "trajetve-2.17";
const PREFIX = "trajetve-";
const SHELL = [
  "./trajet-ve.html", "./trajetve.webmanifest",
  "./trajetve-icon-192.png", "./trajetve-icon-512.png", "./trajetve-icon-maskable-512.png",
  "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js",
  "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css"
];
// Fichiers du dépôt que ce service worker a le droit de gérer
const isOwn = url => url.origin === location.origin && /\/(trajet-ve\.html|trajetve[^/]*)$/.test(url.pathname);

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => Promise.allSettled(SHELL.map(u => c.add(u)))));
  self.skipWaiting();
});

self.addEventListener("activate", e => {
  // ne supprime que les anciens caches de Trajet VE, jamais ceux des autres sites
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k.startsWith(PREFIX) && k !== CACHE).map(k => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // Fichiers de l'appli : réseau d'abord (mises à jour immédiates), cache si hors ligne
  if (isOwn(url)) {
    e.respondWith(
      fetch(req).then(res => { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return res; })
        .catch(() => caches.match(req, { ignoreSearch: true }).then(r => r || caches.match("./trajet-ve.html")))
    );
    return;
  }
  // Bibliothèque Leaflet : cache d'abord
  if (url.hostname === "cdnjs.cloudflare.com") {
    e.respondWith(caches.match(req).then(r => r || fetch(req)));
  }
  // Tout le reste (API, tuiles, Firebase, autres sites) passe directement sur le réseau
});

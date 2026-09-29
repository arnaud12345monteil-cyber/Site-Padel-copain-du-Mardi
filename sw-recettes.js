// Service worker du Carnet de recettes.
// But : rendre l'appli installable (icône + lancement plein écran) et utilisable
// hors-ligne en secours. Le contenu réel (les recettes) vient de Firebase en
// temps réel, donc on privilégie toujours le réseau pour nos propres fichiers
// et on ne sert le cache qu'en dernier recours (hors-ligne).
const CACHE_NAME = 'carnet-recettes-v1';
const APP_SHELL = [
  './Carnet-recettes.html',
  './manifest-recettes.json',
  './icon1/icon-192.png',
  './icon1/icon-512.png',
  './icon1/icon-192-maskable.png',
  './icon1/icon-512-maskable.png',
  './icon1/apple-touch-icon.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
      .catch(() => {}) // ne bloque jamais l'installation si un fichier manque
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  const isOwnOrigin = url.origin === self.location.origin;

  if (isOwnOrigin) {
    // Nos propres pages/fichiers : réseau en priorité (toujours la dernière version
    // du code), cache seulement si hors-ligne.
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(req, copy)).catch(() => {});
          return res;
        })
        .catch(() => caches.match(req))
    );
  } else {
    // Ressources externes (polices, CDN Firebase) : cache en priorité,
    // réseau en secours — elles changent rarement et accélèrent le chargement.
    event.respondWith(
      caches.match(req).then((cached) => cached || fetch(req).then((res) => {
        const copy = res.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(req, copy)).catch(() => {});
        return res;
      }).catch(() => cached))
    );
  }
});

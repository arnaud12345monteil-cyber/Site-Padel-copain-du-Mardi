// Service worker de l'appli Padel Mardi.
// But : rendre l'appli installable (icône + lancement plein écran) et utilisable
// hors-ligne en secours. Le contenu réel (présences, classements, réservations...)
// vient de Firebase en temps réel, donc on privilégie toujours le réseau pour les
// pages et on ne sert le cache qu'en dernier recours (hors-ligne).
const CACHE_NAME = 'padel-mardi-v2';
const APP_SHELL = [
  './Padel-tracke-firebase.html',
  './padel-inscription.html',
  './padel-matchs.html',
  './padel-bots.html',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-192-maskable.png',
  './icons/icon-512-maskable.png',
  './icons/apple-touch-icon.png',
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
    // Ressources externes (polices, CDN Firebase/Chart.js) : cache en priorité,
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

// Service worker de l'appli Tension. Portée limitée à ./tension* : n'interfère pas avec les autres sites du dépôt.
const PREFIX = 'tension-';
const CACHE = PREFIX + 'v2.0.0';
const SHELL = ['tension.html', 'tension-chart.umd.min.js', 'tension-firebase.js', 'tension-manifest.webmanifest',
  'tension-icon-192.png', 'tension-icon-512.png', 'tension-icon-maskable-512.png', 'tension-apple-touch-icon.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  // Ne supprime que les anciens caches de CETTE appli.
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k.startsWith(PREFIX) && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (req.mode === 'navigate') {
    // Réseau d'abord pour la page (mises à jour), cache si hors ligne.
    e.respondWith(fetch(req).then(r => { const c = r.clone(); caches.open(CACHE).then(x => x.put('tension.html', c)); return r; })
      .catch(() => caches.match('tension.html')));
    return;
  }
  // On ne met en cache que nos propres fichiers et les polices.
  // Les appels Firebase (Firestore, Auth) passent toujours par le réseau : c'est le SDK qui gère le hors-ligne.
  const own = url.origin === location.origin && url.pathname.split('/').pop().startsWith('tension');
  const fonts = url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com';
  if (!own && !fonts) return;
  e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(r => {
    if (r.ok) { const c = r.clone(); caches.open(CACHE).then(x => x.put(req, c)); }
    return r;
  })));
});

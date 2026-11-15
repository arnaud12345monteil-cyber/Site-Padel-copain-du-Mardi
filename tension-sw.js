// Service worker de l'appli Tension. Portée limitée à ./tension* : n'interfère pas avec les autres sites du dépôt.
const PREFIX = 'tension-';
const CACHE = PREFIX + 'v1.0.0';
const SHELL = ['tension.html', 'tension-chart.umd.min.js', 'tension-manifest.webmanifest',
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
  if (req.mode === 'navigate') {
    // Réseau d'abord pour la page (mises à jour), cache si hors ligne.
    e.respondWith(fetch(req).then(r => { const c = r.clone(); caches.open(CACHE).then(x => x.put('tension.html', c)); return r; })
      .catch(() => caches.match('tension.html')));
    return;
  }
  e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(r => {
    if (r.ok || r.type === 'opaque') { const c = r.clone(); caches.open(CACHE).then(x => x.put(req, c)); }
    return r;
  }).catch(() => hit)));
});

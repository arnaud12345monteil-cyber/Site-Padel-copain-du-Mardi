// Service worker de Palanquée.
// La version vient de l'URL d'enregistrement (palanquee-sw.js?v=1.1.1) : il suffit de changer APP_VERSION
// dans plongee-seances.html pour que l'appli installée se mette à jour.
const VERSION = new URL(self.location).searchParams.get('v') || 'dev';
const CACHE = 'palanquee-' + VERSION;
const CORE = [
  './plongee-seances.html',
  './palanquee-manifest.webmanifest',
  './icons/palanquee-192.png',
  './icons/palanquee-512.png',
  './icons/palanquee-apple-touch.png'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c =>
    Promise.all(CORE.map(u => c.add(new Request(u, {cache: 'reload'})).catch(() => {})))
  ));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k.startsWith('palanquee-') && k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('message', e => { if (e.data === 'skipWaiting') self.skipWaiting(); });

const putInCache = (req, res) => {
  if (res && res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
  return res;
};

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // Page : réseau d'abord (pour avoir la dernière version), cache si hors connexion.
  if (req.mode === 'navigate' || (url.origin === location.origin && url.pathname.endsWith('.html'))) {
    e.respondWith(
      fetch(req).then(res => putInCache(req, res))
        .catch(() => caches.match(req, {ignoreSearch: true}).then(m => m || caches.match('./plongee-seances.html')))
    );
    return;
  }

  // Fichiers propres à Palanquée et SDK Firebase (versionné) : cache d'abord.
  const own = url.origin === location.origin && /\/(palanquee-[^/]*|icons\/palanquee-[^/]*)$/.test(url.pathname);
  if (own || url.hostname === 'www.gstatic.com') {
    e.respondWith(caches.match(req).then(m => m || fetch(req).then(res => putInCache(req, res))));
  }
  // Tout le reste (Firestore, fichiers des autres sites) passe directement au réseau.
});

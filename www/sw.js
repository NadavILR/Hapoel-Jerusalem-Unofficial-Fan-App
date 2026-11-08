// Service worker: works offline after the first visit.
// data.json is network-first (fresh results when online, last copy when offline);
// everything else is cache-first with a background refresh.
const CACHE = 'hpj-v1.60';
const CORE = [
  './', './index.html', './manifest.webmanifest', './data.json', './crests.json', './photos.json',
  './icons/icon-192.png', './icons/icon-512.png', './icons/notify-badge.png',
  './version.js', './vendor/capacitor.js', './vendor/app.css', './vendor/noto-sans-hebrew.css', './bg/pattern-light.jpg', './bg/pattern-dark.jpg', './vendor/fonts/noto-sans-hebrew-hebrew.woff2', './vendor/fonts/noto-sans-hebrew-latin.woff2', './vendor/fonts/noto-sans-hebrew-latin-ext.woff2', './vendor/react.production.min.js', './vendor/react-dom.production.min.js', './vendor/htm.umd.js'
];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => Promise.allSettled(CORE.map(u => c.add(u)))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const sameOrigin = url.origin === location.origin;
    if (!sameOrigin) return; // photos/crests from other sites: let the browser handle them
  if (sameOrigin && (url.pathname.endsWith('/data.json') || req.mode === 'navigate')) {
    const key = req.mode === 'navigate' ? './index.html' : './data.json';
    // no-store: always take the newest version from the server (GitHub Pages caches HTML for 10 minutes)
    e.respondWith(fetch(req, { cache: 'no-store' }).then(r => {
      if (r.ok) { const cp = r.clone(); caches.open(CACHE).then(c => c.put(key, cp)); }
      return r;
    }).catch(() => caches.match(key)));
    return;
  }
  e.respondWith(caches.match(req).then(hit => {
    const net = fetch(req).then(r => {
      if (r.ok || r.type === 'opaque') { const cp = r.clone(); caches.open(CACHE).then(c => c.put(req, cp)); }
      return r;
    }).catch(() => hit);
    return hit || net;
  }));
});

// Tapping a notification opens (or focuses) the app.
self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
    for (const c of list) if ('focus' in c) return c.focus();
    return self.clients.openWindow('./');
  }));
});

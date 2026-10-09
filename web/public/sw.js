// Coin Tracker Service Worker
// Provides offline support with cache-first strategy for static assets
// and network-first for API calls

const CACHE_NAME = 'coin-tracker-v1';
const STATIC_CACHE = 'coin-tracker-static-v1';
const DYNAMIC_CACHE = 'coin-tracker-dynamic-v1';

// Assets to cache on install (same-origin only)
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/login.html',
  '/admin.html',
  '/css/style.css',
  '/css/login.css',
  '/css/admin.css',
  '/js/app.js',
  '/js/login.js',
  '/js/admin.js',
  '/images/coin.ico',
  '/images/coin.png',
  '/images/bkash.png',
  '/images/nagad.png',
  '/images/rocket.png',
];

// Cross-origin assets to cache separately (with individual error handling)
const CROSS_ORIGIN_ASSETS = [
  'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap',
  'https://cdn.jsdelivr.net/npm/chart.js'
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then((cache) => {
        console.log('[SW] Caching static assets');
        return cache.addAll(STATIC_ASSETS.map(url => new Request(url, { credentials: 'same-origin' })));
      })
      .then(() => {
        // Cache cross-origin assets separately (don't fail install if they fail)
        return Promise.allSettled(
          CROSS_ORIGIN_ASSETS.map(url => 
            caches.open(STATIC_CACHE).then(cache => 
              fetch(new Request(url, { mode: 'cors', credentials: 'omit' }))
                .then(response => {
                  if (response.ok) return cache.put(url, response);
                })
                .catch(() => { /* ignore cross-origin failures */ })
            )
          )
        );
      })
      .then(() => self.skipWaiting())
  );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => {
        return Promise.all(
          cacheNames
            .filter((name) => name !== STATIC_CACHE && name !== DYNAMIC_CACHE)
            .map((name) => caches.delete(name))
        );
      })
      .then(() => self.clients.claim())
  );
});

// Fetch event - serve from cache or network
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-GET requests
  if (request.method !== 'GET') {
    return;
  }

  // Skip chrome-extension and other non-http(s) schemes
  if (!url.protocol.startsWith('http')) {
    return;
  }

  // Handle Firebase API calls - network first, fallback to cache
  if (url.hostname.includes('firebase') || url.hostname.includes('googleapis')) {
    event.respondWith(networkFirstStrategy(request));
    return;
  }

  // Handle static assets - cache first
  const requestPath = url.pathname;
  if (STATIC_ASSETS.some(asset => {
    const assetUrl = new URL(asset, self.location.origin);
    return assetUrl.pathname === requestPath;
  })) {
    event.respondWith(cacheFirstStrategy(request));
    return;
  }

  // Default: network first for HTML, cache first for others
  if (request.headers.get('accept')?.includes('text/html')) {
    event.respondWith(networkFirstStrategy(request));
  } else {
    event.respondWith(cacheFirstStrategy(request));
  }
});

// Cache-first strategy: serve from cache, fallback to network
async function cacheFirstStrategy(request) {
  const cachedResponse = await caches.match(request);
  if (cachedResponse) {
    return cachedResponse;
  }

  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      const cache = await caches.open(DYNAMIC_CACHE);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    // Return offline fallback for navigation requests
    if (request.mode === 'navigate') {
      return caches.match('/index.html');
    }
    throw error;
  }
}

// Network-first strategy: try network, fallback to cache
async function networkFirstStrategy(request) {
  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      const cache = await caches.open(DYNAMIC_CACHE);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }
    // Return offline fallback for navigation requests
    if (request.mode === 'navigate') {
      return caches.match('/index.html');
    }
    throw error;
  }
}

// Handle messages from clients
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});
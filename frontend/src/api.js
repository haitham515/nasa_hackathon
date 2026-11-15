// Thin client for the Spring Boot API. All calls go through /api (proxied in dev, nginx in Docker).

async function request(path, options = {}) {
  const res = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body.error) message = body.error
    } catch { /* not JSON */ }
    throw new Error(message)
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  crew: () => request('/crew'),
  timeline: (pid) => request(`/crew/${pid}/timeline`),
  alerts: (limit = 60) => request(`/alerts?limit=${limit}`),
  alert: (id) => request(`/alerts/${id}`),
  acknowledge: (id) => request(`/alerts/${id}/acknowledge`, { method: 'POST' }),
  replayStatus: () => request('/replay/status'),
  startReplay: (speed, simulate) =>
    request('/replay/start', { method: 'POST', body: JSON.stringify({ speed, simulate }) }),
  stopReplay: () => request('/replay/stop', { method: 'POST' }),
  resetReplay: () => request('/replay/reset', { method: 'POST' }),
  meta: () => request('/meta'),
}

export const exportUrls = {
  nasa: '/api/export/nasa-biomarker-results.csv',
  simulated: '/api/export/simulated-vitals.csv',
}

/** Subscribe to the live event stream. Returns an unsubscribe function. */
export function subscribe(handlers) {
  const es = new EventSource('/api/stream')
  for (const [event, fn] of Object.entries(handlers)) {
    es.addEventListener(event, (e) => {
      try {
        fn(JSON.parse(e.data))
      } catch {
        fn(null)
      }
    })
  }
  es.onerror = () => handlers.connection?.(false)
  es.onopen = () => handlers.connection?.(true)
  return () => es.close()
}

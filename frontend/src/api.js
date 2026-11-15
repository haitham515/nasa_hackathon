// Client for the Spring Boot API (proxied at /api in dev, nginx in Docker).

async function request(path, options = {}) {
  const res = await fetch(`/api${path}`, { headers: { 'Content-Type': 'application/json' }, ...options })
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body.error) message = body.error
    } catch { /* not JSON */ }
    throw new Error(message)
  }
  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

const post = (path, body) => request(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined })

export const api = {
  snapshot: () => request('/telemetry/snapshot'),
  streamStatus: () => request('/telemetry/stream'),
  event: (id, sampleId) => request(`/telemetry/events/${id}${sampleId ? `?sampleId=${sampleId}` : ''}`),
  analyze: (id, sampleId) => post(`/telemetry/events/${id}/analysis${sampleId ? `?sampleId=${sampleId}` : ''}`),
  pause: () => post('/telemetry/stream/pause'),
  resume: () => post('/telemetry/stream/resume'),
  inject: (metric) => post('/telemetry/stream/inject', { metric }),
}

/** Subscribe to the live Server-Sent Events stream. Returns an unsubscribe function. */
export function subscribe(handlers) {
  const es = new EventSource('/api/stream')
  for (const [event, fn] of Object.entries(handlers)) {
    if (event === 'connection') continue
    es.addEventListener(event, (e) => {
      let data = null
      try { data = JSON.parse(e.data) } catch { /* ignore malformed */ }
      fn(data)
    })
  }
  es.onopen = () => handlers.connection?.(true)
  es.onerror = () => handlers.connection?.(false)
  return () => es.close()
}

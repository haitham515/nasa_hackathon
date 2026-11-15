import { useCallback, useEffect, useRef, useState } from 'react'
import { api, subscribe } from '../api.js'

const MAX_POINTS = 900
const STALE_AFTER_MS = 6000

/**
 * Live telemetry state for the single astronaut: initial snapshot over REST, then
 * per-sample updates over Server-Sent Events, kept in per-metric rolling windows.
 */
export function useTelemetry() {
  const [astronaut, setAstronaut] = useState(null)
  const [metrics, setMetrics] = useState([])
  const [series, setSeries] = useState({})
  const [events, setEvents] = useState({})          // id -> event
  const [stream, setStream] = useState(null)
  const [connected, setConnected] = useState(true)
  const [clockOffset, setClockOffset] = useState(0) // serverTime - clientTime
  const [lastSampleAt, setLastSampleAt] = useState(null)
  const [error, setError] = useState(null)
  const windowsRef = useRef({})

  const load = useCallback(async () => {
    try {
      const snap = await api.snapshot()
      setAstronaut(snap.astronaut)
      setClockOffset(new Date(snap.astronaut.serverTime).getTime() - Date.now())
      setMetrics(snap.metrics)
      windowsRef.current = Object.fromEntries(snap.metrics.map((m) => [m.key, m.windowSeconds * 1000]))
      setSeries(Object.fromEntries(Object.entries(snap.windows).map(([k, list]) => [k, list.map(toPoint)])))
      setEvents(Object.fromEntries(snap.recentEvents.map((e) => [e.id, e])))
      setStream(snap.stream)
      setError(null)
    } catch (e) {
      setError(`Cannot reach the backend: ${e.message}`)
    }
  }, [])

  useEffect(() => {
    load()
    let wasDisconnected = false
    const off = subscribe({
      telemetry: (data) => {
        if (!data?.samples?.length) return
        setSeries((prev) => appendSamples(prev, data.samples, windowsRef.current))
        setLastSampleAt(Date.now())
      },
      anomaly: (data) => {
        if (data?.event) setEvents((prev) => ({ ...prev, [data.event.id]: data.event }))
      },
      stream: (data) => data && setStream((s) => ({ ...s, ...data })),
      connection: (ok) => {
        setConnected(ok)
        if (!ok) wasDisconnected = true
        else if (wasDisconnected) { wasDisconnected = false; load() }
      },
    })
    const poll = setInterval(() => api.streamStatus().then(setStream).catch(() => {}), 5000)
    return () => { off(); clearInterval(poll) }
  }, [load])

  const now = useNow()
  const stale = lastSampleAt != null && now - lastSampleAt > STALE_AFTER_MS

  let linkState = 'ACTIVE'
  if (!connected || error) linkState = 'LINK_LOST'
  else if (stream?.paused) linkState = 'PAUSED'
  else if (stream && (stream.error || !stream.running)) linkState = 'SOURCE_DOWN'
  else if (stale || (stream && stream.backend_ok === false)) linkState = 'SIGNAL_LOST'

  const pause = useCallback(async () => setStream(await api.pause()), [])
  const resume = useCallback(async () => setStream(await api.resume()), [])
  const inject = useCallback((metric) => api.inject(metric), [])

  return {
    astronaut, metrics, series, events, stream, linkState, error, clockOffset, now,
    pause, resume, inject, reload: load,
  }
}

function toPoint(s) {
  return { ...s, t: new Date(s.recordedAt).getTime() }
}

function appendSamples(prev, samples, windows) {
  const next = { ...prev }
  const touched = new Set()
  for (const s of samples) {
    const list = touched.has(s.metric) ? next[s.metric] : [...(next[s.metric] ?? [])]
    list.push(toPoint(s))
    next[s.metric] = list
    touched.add(s.metric)
  }
  for (const k of touched) {
    const list = next[k]
    const latest = list[list.length - 1].t
    const windowMs = windows[k] ?? 300_000
    let start = 0
    while (start < list.length && list[start].t < latest - windowMs) start++
    next[k] = list.slice(Math.max(start, list.length - MAX_POINTS))
  }
  return next
}

/** Re-renders every second (for clocks and staleness). */
export function useNow(intervalMs = 1000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return now
}

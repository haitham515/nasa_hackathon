import { useCallback, useEffect, useRef, useState } from 'react'
import { api, subscribe } from './api.js'
import Header from './components/Header.jsx'
import CrewBoard from './components/CrewBoard.jsx'
import TimelinePanel from './components/TimelinePanel.jsx'
import AlertsPanel from './components/AlertsPanel.jsx'
import InfoDrawer from './components/InfoDrawer.jsx'

export default function App() {
  const [crew, setCrew] = useState([])
  const [selected, setSelected] = useState('C001')
  const [timeline, setTimeline] = useState(null)
  const [alerts, setAlerts] = useState([])
  const [selectedAlert, setSelectedAlert] = useState(null)
  const [alertDetail, setAlertDetail] = useState(null)
  const [replay, setReplay] = useState(null)
  const [clock, setClock] = useState(null)
  const [meta, setMeta] = useState(null)
  const [showInfo, setShowInfo] = useState(false)
  const [connected, setConnected] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const selectedRef = useRef(selected)
  selectedRef.current = selected
  const refreshTimer = useRef(null)

  const refresh = useCallback(async () => {
    try {
      const [c, a, r, t] = await Promise.all([
        api.crew(), api.alerts(), api.replayStatus(), api.timeline(selectedRef.current),
      ])
      setCrew(c)
      setAlerts(a)
      setReplay(r)
      const day = r?.engine?.day_offset
      if (day != null) {
        const phase = day < 0 ? 'PRE_FLIGHT' : r.engine.segment === 'IN_FLIGHT' ? 'IN_FLIGHT' : 'POST_FLIGHT'
        setClock((c) => c ?? { dayOffset: day, phase })
      }
      setTimeline(t)
      setError(null)
    } catch (e) {
      setError(`Backend unreachable: ${e.message}`)
    }
  }, [])

  // Coalesce bursts of live events into one refresh.
  const scheduleRefresh = useCallback(() => {
    clearTimeout(refreshTimer.current)
    refreshTimer.current = setTimeout(refresh, 200)
  }, [refresh])

  useEffect(() => {
    refresh()
    api.meta().then(setMeta).catch(() => {})
    const off = subscribe({
      reading: (e) => {
        if (e) setClock((c) => (!c || e.dayOffset >= c.dayOffset ? { dayOffset: e.dayOffset, phase: e.missionPhase } : c))
        scheduleRefresh()
      },
      alert: scheduleRefresh,
      replay: (e) => {
        if (e?.type === 'started' || e?.type === 'reset') {
          setClock(null)
          setSelectedAlert(null)
          setAlertDetail(null)
        }
        scheduleRefresh()
      },
      connection: setConnected,
    })
    return () => { off(); clearTimeout(refreshTimer.current) }
  }, [refresh, scheduleRefresh])

  useEffect(() => {
    api.timeline(selected).then(setTimeline).catch(() => {})
  }, [selected])

  useEffect(() => {
    if (!selectedAlert) return
    api.alert(selectedAlert).then(setAlertDetail).catch(() => setAlertDetail(null))
  }, [selectedAlert, alerts])

  const run = async (fn) => {
    setBusy(true)
    try {
      await fn()
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
      refresh()
    }
  }

  const selectedCrew = crew.find((c) => c.participantId === selected)
  const baselineHr = selectedCrew?.hrBpm != null && selectedCrew?.hrDeltaBpm != null
    ? selectedCrew.hrBpm - selectedCrew.hrDeltaBpm : null

  return (
    <div className="app">
      <Header replay={replay} clock={clock} connected={connected} busy={busy}
        onStart={(speed, simulate) => run(() => api.startReplay(speed, simulate))}
        onStop={() => run(api.stopReplay)}
        onReset={() => run(api.resetReplay)}
        onInfo={() => setShowInfo(true)} />

      {error && <div className="error-bar" role="alert">{error}</div>}

      <CrewBoard crew={crew} selected={selected} onSelect={setSelected} />

      <div className="main">
        <TimelinePanel participantId={selected} timeline={timeline} baselineHr={baselineHr} />
        <AlertsPanel alerts={alerts} selectedId={selectedAlert} detail={alertDetail}
          onSelect={(a) => { setSelectedAlert(a.id); setSelected(a.participantId) }}
          onAcknowledge={(id) => run(() => api.acknowledge(id))} />
      </div>

      <p className="disclaimer">
        Exploratory prototype for NASA Space Apps 2026. Biomarkers: NASA OSDR OSD-575 (real). Heart rate / HRV: SIMULATED.
        Notifications are not diagnoses.
      </p>

      {showInfo && <InfoDrawer meta={meta} onClose={() => setShowInfo(false)} />}
    </div>
  )
}

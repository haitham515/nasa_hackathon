import { useState } from 'react'
import { useTelemetry } from './hooks/useTelemetry.js'
import MissionHeader from './components/MissionHeader.jsx'
import TelemetryChart from './components/TelemetryChart.jsx'
import DiagnosticPanel from './components/DiagnosticPanel.jsx'

const FEATURED_STORAGE_KEY = 'astra.featuredMetric'

function readFeatured() {
  try { return localStorage.getItem(FEATURED_STORAGE_KEY) ?? 'heart_rate' } catch { return 'heart_rate' }
}

export default function App() {
  const t = useTelemetry()
  const [selection, setSelection] = useState(null) // { eventId, sampleId }
  const [notice, setNotice] = useState(null)

  const select = (sample) => {
    if (sample?.eventId) setSelection({ eventId: sample.eventId, sampleId: sample.id ?? null })
  }

  const runAction = async (fn, message) => {
    try {
      await fn()
      if (message) setNotice(message)
    } catch (e) {
      setNotice(`Action failed: ${e.message}`)
    }
    setTimeout(() => setNotice(null), 4000)
  }

  const [featuredKey, setFeaturedKey] = useState(readFeatured)
  const chooseFeatured = (key) => {
    setFeaturedKey(key)
    try { localStorage.setItem(FEATURED_STORAGE_KEY, key) } catch { /* storage unavailable: keep in memory */ }
  }
  const featured = t.metrics.find((m) => m.key === featuredKey) ?? t.metrics[0]
  const others = t.metrics.filter((m) => m !== featured)

  return (
    <div className="shell">
      <MissionHeader astronaut={t.astronaut} linkState={t.linkState} stream={t.stream} now={t.now}
        clockOffset={t.clockOffset}
        onPause={() => runAction(t.pause, 'Stream paused. Open anomaly episodes were closed.')}
        onResume={() => runAction(t.resume, 'Telemetry is live again.')}
        onInject={(m) => runAction(() => t.inject(m), 'Test anomaly injected; watch the graph.')} />

      {t.error && <div className="error-bar" role="alert">{t.error}</div>}
      {notice && <div className="notice" role="status">{notice}</div>}

      <div className="workspace">
        <main className="charts">
          {t.metrics.length > 0 && (
            <div className="featured-picker" role="radiogroup" aria-label="Metric shown in the main graph">
              <span className="featured-label">Main graph</span>
              {t.metrics.map((m) => (
                <button key={m.key} role="radio" aria-checked={m === featured} className="seg"
                  onClick={() => chooseFeatured(m.key)}>
                  {m.label}
                </button>
              ))}
            </div>
          )}
          {featured && <TelemetryChart key={featured.key} large metric={featured} samples={t.series[featured.key] ?? []}
            events={t.events} selected={selection} onSelect={select} />}
          <div className="biomarker-grid">
            {others.map((m) => (
              <TelemetryChart key={m.key} metric={m} samples={t.series[m.key] ?? []} events={t.events}
                selected={selection} onSelect={select} onEnlarge={() => chooseFeatured(m.key)} />
            ))}
          </div>
          <p className="disclaimer">
            SIMULATED telemetry for a fictional astronaut. Thresholds are illustrative. AI output is decision support for a
            flight surgeon, not a diagnosis.
          </p>
        </main>

        <DiagnosticPanel selection={selection} onClear={() => setSelection(null)} onSelect={select}
          astronaut={t.astronaut} metrics={t.metrics} series={t.series} events={t.events} stream={t.stream} />
      </div>

    </div>
  )
}

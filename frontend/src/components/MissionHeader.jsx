import { useState } from 'react'
import { formatMet, formatUtcTime, INJECT_OPTIONS } from '../labels.js'

const LINK = {
  ACTIVE: { label: '24/7 ACTIVE', cls: 'pill-live', hint: 'Telemetry streaming' },
  PAUSED: { label: 'PAUSED', cls: 'pill-paused', hint: 'Stream paused for testing' },
  SIGNAL_LOST: { label: 'SIGNAL LOST', cls: 'pill-lost', hint: 'No new samples received' },
  SOURCE_DOWN: { label: 'SOURCE DOWN', cls: 'pill-lost', hint: 'Telemetry generator unreachable' },
  LINK_LOST: { label: 'LINK LOST', cls: 'pill-lost', hint: 'Dashboard disconnected from the backend, retrying' },
}

export default function MissionHeader({ astronaut, linkState, stream, now, clockOffset, onPause, onResume, onInject }) {
  const [injectMetric, setInjectMetric] = useState('heart_rate')
  const [busy, setBusy] = useState(false)
  const serverNow = now + clockOffset
  const met = astronaut ? serverNow - new Date(astronaut.missionStart).getTime() : null
  const link = LINK[linkState] ?? LINK.ACTIVE
  const paused = !!stream?.paused

  const act = async (fn) => {
    setBusy(true)
    try { await fn() } finally { setBusy(false) }
  }

  return (
    <header className="mission-header">
      <div className="profile">
        <div className="avatar" aria-hidden="true">{astronaut?.id?.slice(-2) ?? '--'}</div>
        <div>
          <div className="profile-name">{astronaut?.displayName ?? 'Loading astronaut…'}</div>
          <div className="profile-meta">{astronaut ? `${astronaut.id} · ${astronaut.role} · ${astronaut.mission}` : ''}</div>
        </div>
      </div>

      <div className="clock-block">
        <span className="k">Mission elapsed time</span>
        <span className="v">MET {formatMet(met)}</span>
      </div>
      <div className="clock-block">
        <span className="k">UTC</span>
        <span className="v">{formatUtcTime(serverNow)}</span>
      </div>

      <div className={`link-pill ${link.cls}`} title={link.hint} role="status" aria-live="polite">
        <span className="dot" />{link.label}
      </div>
      <span className="sim-tag" title="All telemetry on this dashboard is generated">SIMULATED</span>

      <div className="header-controls">
        <button className={`btn ${paused ? 'primary' : ''}`} disabled={busy}
          onClick={() => act(paused ? onResume : onPause)} aria-pressed={!paused}>
          {paused ? '▶ Go live' : '❚❚ Pause'}
        </button>
        <div className="inject-group" role="group" aria-labelledby="inject-label"
          title="Testing tool: pick an anomaly type, then press Inject to add it to the simulated stream">
          <label id="inject-label" htmlFor="inject-type" className="inject-label">Test anomaly</label>
          <select id="inject-type" value={injectMetric} onChange={(e) => setInjectMetric(e.target.value)} disabled={busy || paused}>
            {INJECT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          <button className="btn" disabled={busy || paused} onClick={() => act(() => onInject(injectMetric))}>
            ⚡ Inject
          </button>
        </div>
      </div>
    </header>
  )
}

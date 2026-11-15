import { useState } from 'react'
import { missionDayLabel, PHASE_LABEL } from '../labels.js'

export default function Header({ replay, clock, connected, onStart, onStop, onReset, onInfo, busy }) {
  const [speed, setSpeed] = useState(1)
  const [simulate, setSimulate] = useState(true)
  const engine = replay?.engine ?? {}
  const running = !!engine.running
  const progress = engine.total_ticks ? Math.round((engine.tick / engine.total_ticks) * 100) : 0
  const simOn = running ? engine.simulate : simulate

  return (
    <header className="header">
      <div className="brand">
        <svg width="30" height="30" viewBox="0 0 32 32" aria-hidden="true">
          <rect width="32" height="32" rx="7" fill="var(--surface-2)" />
          <path d="M4 17h6l2.5-6 4 12 3-8 1.5 2H28" fill="none" stroke="var(--series-nasa)" strokeWidth="2.4"
            strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <div>
          <h1>ASTRA CARDIO</h1>
          <small>Crew cardiovascular monitoring · exploratory prototype</small>
        </div>
      </div>

      <div className="clock" aria-live="polite">
        <span className="k">Mission time</span>
        <span className="v">{missionDayLabel(clock?.dayOffset)}</span>
        <span className="k">{clock?.phase ? PHASE_LABEL[clock.phase] : 'Not started'}</span>
      </div>

      <div className="badges">
        <span className={`badge ${running ? 'live' : 'off'}`}>
          <span className="dot" />{running ? `REPLAY ×${engine.speed} · ${progress}%` : 'Replay idle'}
        </span>
        <span className="badge nasa"><span className="dot" />NASA OSD-575 biomarkers</span>
        {simOn && <span className="badge sim"><span className="dot" />SIMULATED heart rate / HRV</span>}
        {!connected && <span className="badge off"><span className="dot" />Live link lost, retrying</span>}
      </div>

      <div className="controls">
        <label className="control-label">
          Speed
          <select value={speed} onChange={(e) => setSpeed(Number(e.target.value))} disabled={running}>
            {[0.5, 1, 2, 4].map((s) => <option key={s} value={s}>×{s}</option>)}
          </select>
        </label>
        <label className="control-label">
          <input type="checkbox" checked={simulate} onChange={(e) => setSimulate(e.target.checked)} disabled={running} />
          Simulated vitals
        </label>
        {running
          ? <button className="btn" onClick={onStop} disabled={busy}>Stop</button>
          : <button className="btn primary" onClick={() => onStart(speed, simulate)} disabled={busy}>▶ Start replay</button>}
        <button className="btn" onClick={onReset} disabled={busy}>Reset</button>
        <button className="btn" onClick={onInfo}>Data &amp; limits</button>
      </div>
    </header>
  )
}

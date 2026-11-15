import { useEffect } from 'react'
import { exportUrls } from '../api.js'

export default function InfoDrawer({ meta, onClose }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const card = meta?.modelCard ?? {}
  const ev = card.evaluation ?? {}
  const method = card.method ?? {}

  return (
    <>
      <div className="drawer-backdrop" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="Data and limits">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Data, method &amp; limits</h2>
          <button className="btn" onClick={onClose}>Close</button>
        </div>

        <div className="split">
          <div className="box">
            <h3><span className="nasa-tag">NASA</span> Real data</h3>
            <p>{meta?.dataset?.name}</p>
            <p>{meta?.dataset?.samples} samples · {meta?.dataset?.participants} crew · timepoints {meta?.dataset?.timepoints?.join(', ')}</p>
            <p>{meta?.dataset?.normalisation}</p>
            <a href={meta?.dataset?.url} target="_blank" rel="noreferrer">NASA OSDR {meta?.dataset?.study}</a>
          </div>
          <div className="box sim">
            <h3><span className="sim-tag">SIMULATED</span> Generated data</h3>
            <p>{meta?.simulation?.what}. {meta?.simulation?.why}.</p>
            <p>{meta?.simulation?.anchoredTo}.</p>
            <a href={meta?.simulation?.paper} target="_blank" rel="noreferrer">Inspiration4 paper (Nature 2024)</a>
          </div>
        </div>

        <div>
          <h3>How the AI works</h3>
          <ul>
            <li>Each astronaut is compared with their <b>own</b> pre-flight baseline ({method.baseline_timepoints?.join(' + ')}). {method.baseline_rationale}.</li>
            <li>Features: {method.features}.</li>
            <li>{method.algorithm} · {method.n_estimators} trees · contamination {method.contamination} ({method.contamination_meaning}).</li>
            <li>{method.score}.</li>
            <li>Status rules: <b>Attention</b> if the model flags a panel or ≥ 2 biomarkers deviate strongly (|z| ≥ 3). <b>Review required</b> if flagged and (≥ 2 strong deviations or the previous panel was also flagged). Simulated vitals: Review after 3 atypical readings in a row.</li>
            <li>Scoring runs locally in the AI service, with no ground link needed.</li>
          </ul>
        </div>

        <div>
          <h3>Evaluation (honest)</h3>
          <ul>
            <li>Flagged samples: {ev.flagged_samples?.join(', ') || '—'}</li>
            <li>Leave-one-astronaut-out: {ev.lopo_flags_recovered} flags recovered (agreement {ev.lopo_flag_agreement}).</li>
            <li>{ev.note}</li>
          </ul>
        </div>

        <div>
          <h3>Limits</h3>
          <ul>
            {(card.limitations ?? []).map((l) => <li key={l}>{l}</li>)}
            <li>No heart rate, blood pressure or ECG exists in the NASA file used; those charts only show SIMULATED data.</li>
          </ul>
        </div>

        <div>
          <h3>Export</h3>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <a className="btn" href={exportUrls.nasa} download>NASA results (CSV)</a>
            <a className="btn" href={exportUrls.simulated} download>SIMULATED vitals (CSV)</a>
          </div>
          <p className="fine">The two files are kept separate on purpose: simulated values never mix with NASA measurements.</p>
        </div>

        <p className="fine">{meta?.disclaimer}</p>
      </aside>
    </>
  )
}

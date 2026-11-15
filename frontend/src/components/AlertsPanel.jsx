import StatusBadge, { SourceTag } from './StatusBadge.jsx'
import { biomarkerLabel, PHASE_LABEL } from '../labels.js'

export default function AlertsPanel({ alerts, selectedId, detail, onSelect, onAcknowledge }) {
  return (
    <section className="panel" aria-label="Alerts">
      <div className="panel-head">
        <div>
          <h2>Alerts &amp; what to do</h2>
          <p className="panel-sub">Newest first. Software notifications, not diagnoses.</p>
        </div>
      </div>

      {detail && <AlertDetail detail={detail} onAcknowledge={onAcknowledge} />}

      <div className="alerts" role="list" style={{ marginTop: detail ? 14 : 0 }}>
        {!alerts.length && <p className="empty">No alerts. Start the replay to stream data.</p>}
        {alerts.map((a) => (
          <button key={a.id} role="listitem"
            className={`alert-item ${a.status} ${a.acknowledged ? 'ack' : ''} ${selectedId === a.id ? 'selected' : ''}`}
            onClick={() => onSelect(a)}>
            <span className="l1">
              <span>{a.participantId} · {a.timepoint === 'IN_FLIGHT' ? 'In flight' : a.timepoint} · <SourceTag source={a.source} /></span>
              <StatusBadge status={a.status} />
            </span>
            <span className="ttl">{a.title}</span>
          </button>
        ))}
      </div>
    </section>
  )
}

function AlertDetail({ detail, onAcknowledge }) {
  const { alert: a, details, biomarkers, dataQualityNotes, guidance, disclaimer } = detail
  const contributions = details?.contributions ?? []
  const byKey = Object.fromEntries((biomarkers ?? []).map((b) => [b.key, b]))
  const maxZ = Math.max(3, ...contributions.map((c) => Math.abs(c.z)))
  const isVitals = a.stream === 'VITALS'

  return (
    <div className="detail" aria-live="polite">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center' }}>
        <StatusBadge status={a.status} big />
        <SourceTag source={a.source} />
      </div>
      <div>
        <strong>{a.title}</strong>
        <p className="msg">{a.message}</p>
      </div>

      <dl className="kv">
        <dt>Participant</dt><dd>{a.participantId}</dd>
        <dt>Timepoint</dt><dd>{a.timepoint === 'IN_FLIGHT' ? 'In flight' : a.timepoint}</dd>
        <dt>Condition</dt><dd>{PHASE_LABEL[a.missionPhase] ?? a.missionPhase}</dd>
        <dt>Anomaly score</dt><dd>{a.anomalyScore.toFixed(3)} (atypical above 0)</dd>
        {isVitals && details && <>
          <dt>Heart rate change</dt><dd>{details.hr_delta_bpm > 0 ? '+' : ''}{details.hr_delta_bpm} bpm vs {details.baseline_hr_bpm} bpm</dd>
          <dt>HRV change</dt><dd>{details.hrv_delta_pct > 0 ? '+' : ''}{details.hrv_delta_pct}% vs {details.baseline_hrv_ms} ms</dd>
        </>}
      </dl>

      {!isVitals && contributions.length > 0 && (
        <div className="contrib" aria-label="Biomarkers driving this result">
          <span className="fine">Deviation from personal baseline (|z|), top 5</span>
          {contributions.slice(0, 5).map((c) => {
            const b = byKey[c.analyte]
            return (
              <div className="contrib-row" key={c.analyte}
                title={b ? `${Math.round(b.concentration).toLocaleString()} ${b.unit} · ${b.percent.toFixed(0)}% of crew pre-flight mean` : ''}>
                <span>{biomarkerLabel(c.analyte)}{c.at_assay_upper_limit ? ' *' : ''}</span>
                <span className="contrib-bar"><span style={{ width: `${(Math.abs(c.z) / maxZ) * 100}%` }} /></span>
                <span className="z">{c.direction === 'above' ? '↑' : '↓'} z {c.z > 0 ? '+' : ''}{c.z}</span>
              </div>
            )
          })}
          {contributions.slice(0, 5).some((c) => byKey[c.analyte]) && (
            <span className="fine">
              Values: {contributions.slice(0, 3).map((c) => byKey[c.analyte]
                && `${biomarkerLabel(c.analyte)} ${Math.round(byKey[c.analyte].concentration).toLocaleString()} ${byKey[c.analyte].unit}`).filter(Boolean).join(' · ')}
            </span>
          )}
        </div>
      )}

      {dataQualityNotes?.map((n) => <p className="note" key={n}>{n}</p>)}

      {guidance && (
        <div className="guidance">
          <h4>What to do: {guidance.title}</h4>
          <p>{guidance.body}</p>
          <span className="src">Source: <a href={guidance.sourceUrl} target="_blank" rel="noreferrer">{guidance.sourceName}</a></span>
        </div>
      )}

      <p className="fine">{disclaimer}</p>
      {!a.acknowledged
        ? <button className="btn" onClick={() => onAcknowledge(a.id)}>Acknowledge</button>
        : <span className="fine">Acknowledged</span>}
    </div>
  )
}

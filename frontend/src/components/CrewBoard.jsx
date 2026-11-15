import StatusBadge, { SourceTag } from './StatusBadge.jsx'
import { biomarkerLabel } from '../labels.js'

function prettySummary(text) {
  // "haptoglobin above baseline (z +6.6)" -> "Haptoglobin above baseline (z +6.6)"
  if (!text) return ''
  const [first, ...rest] = text.split(' ')
  return [biomarkerLabel(first), ...rest].join(' ')
}

export default function CrewBoard({ crew, selected, onSelect }) {
  return (
    <section className="crew" aria-label="Crew status">
      {crew.map((c) => (
        <button key={c.participantId} className={`crew-card ${selected === c.participantId ? 'selected' : ''}`}
          onClick={() => onSelect(c.participantId)} aria-pressed={selected === c.participantId}>
          <div className="top">
            <span className="name">{c.participantId}</span>
            <StatusBadge status={c.biomarkerStatus} big />
          </div>
          <div className="meta">
            <SourceTag source="NASA" /> Biomarkers{c.biomarkerTimepoint ? ` · last sample ${c.biomarkerTimepoint}` : ''}
          </div>
          <div className="summary">{prettySummary(c.biomarkerSummary)}</div>
          <div className="vitals">
            <span>
              <SourceTag source="SIMULATED" />{' '}
              <span className="num">{c.hrBpm != null ? `${Math.round(c.hrBpm)} bpm` : '—'}</span>
              {c.hrDeltaBpm != null && <> ({c.hrDeltaBpm > 0 ? '+' : ''}{Math.round(c.hrDeltaBpm)})</>}
            </span>
            <StatusBadge status={c.vitalsStatus} />
          </div>
        </button>
      ))}
    </section>
  )
}

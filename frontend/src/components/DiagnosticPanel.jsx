import { useCallback, useEffect, useState } from 'react'
import { Line, LineChart, ReferenceArea, ReferenceLine, ReferenceDot, ResponsiveContainer, XAxis, YAxis } from 'recharts'
import { api } from '../api.js'
import StatusBadge from './StatusBadge.jsx'
import { formatMet, formatUtc, formatUtcTime, formatValue, normalRangeText, STATUS } from '../labels.js'
import { downloadIncidentReport } from '../pdf/incidentReport.js'

export default function DiagnosticPanel({ selection, onClear, onSelect, astronaut, metrics, series, events, stream }) {
  return (
    <aside className="diag" aria-label="AI diagnostic panel">
      {selection
        ? <SelectedEvent key={`${selection.eventId}-${selection.sampleId}`} selection={selection} onClear={onClear}
            astronaut={astronaut} liveEvent={events[selection.eventId]} />
        : <Standby metrics={metrics} series={series} events={events} stream={stream} onSelect={onSelect} />}
    </aside>
  )
}

// ---------------------------------------------------------------- standby

function Standby({ metrics, series, events, stream, onSelect }) {
  const all = Object.values(events).sort((a, b) => b.id - a.id)
  const open = all.filter((e) => e.open)
  const recent = all.filter((e) => !e.open).slice(0, 8)
  const label = (key) => metrics.find((m) => m.key === key)
  return (
    <div className="diag-body">
      <div className="diag-title">
        <h2>Flight surgeon console</h2>
        <span className="fine">Standby: click an Attention or Review marker on a graph to analyse it.</span>
      </div>

      <section>
        <h3>Live summary</h3>
        <ul className="summary-list">
          {metrics.map((m) => {
            const last = series[m.key]?.at(-1)
            return (
              <li key={m.key}>
                <span className="s-label">{m.label}</span>
                <span className="s-val">{formatValue(m.key, last?.value)} <small>{m.unit}</small></span>
                <StatusBadge status={last?.status} />
                <span className="s-range">normal {normalRangeText(m)}</span>
              </li>
            )
          })}
        </ul>
      </section>

      <EventList title={`Ongoing anomalies (${open.length})`} list={open} label={label} onSelect={onSelect} empty="None right now." />
      <EventList title="Recent anomalies" list={recent} label={label} onSelect={onSelect} empty="No anomaly recorded yet." />

      <section className="ai-info">
        <h3>AI analysis engine</h3>
        <p className="fine">
          {stream?.ai_provider === 'openai'
            ? <>OpenAI <b>{stream.ai_model}</b>, with a rule-based fallback.</>
            : <>Rule-based clinical knowledge base (no OpenAI key configured).</>}
        </p>
      </section>
    </div>
  )
}

function EventList({ title, list, label, onSelect, empty }) {
  return (
    <section>
      <h3>{title}</h3>
      {!list.length && <p className="empty">{empty}</p>}
      <div className="event-list">
        {list.map((e) => {
          const m = label(e.metric)
          return (
            <button key={e.id} className={`event-row ${e.worstStatus}`} onClick={() => onSelect({ id: null, eventId: e.id, metric: e.metric })}>
              <StatusBadge status={e.worstStatus} />
              <span>{m?.label ?? e.metric}: {formatValue(e.metric, e.peakValue)} {m?.unit}</span>
              <span className="fine">{formatUtcTime(new Date(e.peakAt).getTime())}</span>
            </button>
          )
        })}
      </div>
    </section>
  )
}

// ---------------------------------------------------------------- selected event

function SelectedEvent({ selection, onClear, astronaut, liveEvent }) {
  const [detail, setDetail] = useState(null)
  const [analysis, setAnalysis] = useState(null)
  const [aiState, setAiState] = useState('loading') // loading | ready | error
  const [aiError, setAiError] = useState(null)
  const [pdfBusy, setPdfBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    api.event(selection.eventId, selection.sampleId).then((d) => !cancelled && setDetail(d)).catch(() => {})
    return () => { cancelled = true }
    // Refetch when the live event escalates or closes, so the episode summary stays current.
  }, [selection.eventId, selection.sampleId, liveEvent?.worstStatus, liveEvent?.open])

  // The backend returns the cached analysis unless the event escalated since; then it regenerates.
  const generate = useCallback(async (isCancelled = () => false) => {
    setAiState('loading')
    setAiError(null)
    try {
      const result = await api.analyze(selection.eventId, selection.sampleId)
      if (!isCancelled()) { setAnalysis(result); setAiState('ready') }
    } catch (e) {
      if (!isCancelled()) { setAiError(e.message); setAiState('error') }
    }
  }, [selection.eventId, selection.sampleId])

  useEffect(() => {
    let cancelled = false
    generate(() => cancelled)
    return () => { cancelled = true }
  }, [generate])

  if (!detail) return <div className="diag-body"><p className="empty">Loading event…</p></div>

  const { event, metric, selected, context, trend } = detail
  const point = selected ?? { value: event.peakValue, recordedAt: event.peakAt, status: event.worstStatus }
  const pointT = new Date(point.recordedAt).getTime()
  const met = astronaut ? pointT - new Date(astronaut.missionStart).getTime() : null
  const ctx = context.map((s) => ({ ...s, t: new Date(s.recordedAt).getTime() }))
  const stale = analysis && STATUS_RANK[(liveEvent ?? event).worstStatus] > STATUS_RANK[analysis.severity]

  return (
    <div className="diag-body">
      <div className="diag-title">
        <h2>{metric.label} anomaly</h2>
        <button className="btn" onClick={onClear} aria-label="Close and return to live summary">✕ Close</button>
      </div>

      <section className={`event-summary ${point.status}`}>
        <div className="es-top">
          <StatusBadge status={point.status} big />
          <span className="sim-tag">SIMULATED</span>
        </div>
        <div className="es-value">
          <span className="num">{formatValue(metric.key, point.value)}</span> <span className="unit">{metric.unit}</span>
        </div>
        <dl className="kv">
          <dt>Limit breached</dt><dd>{event.direction === 'HIGH' ? '>' : '<'} {event.breachedLimit} {metric.unit} ({STATUS[event.worstStatus].label.toLowerCase()} limit)</dd>
          <dt>Normal range</dt><dd>{normalRangeText(metric)}</dd>
          <dt>Recorded</dt><dd>{formatUtc(pointT)}</dd>
          <dt>Mission time</dt><dd>MET {formatMet(met)}</dd>
          <dt>Episode</dt><dd>{formatUtcTime(new Date(event.startedAt).getTime())} → {event.endedAt ? formatUtcTime(new Date(event.endedAt).getTime()) : 'ongoing'} · peak {formatValue(metric.key, event.peakValue)} {metric.unit} · worst {STATUS[event.worstStatus].label}</dd>
          <dt>Trend (±90 s)</dt><dd>min {formatValue(metric.key, trend.min)} · max {formatValue(metric.key, trend.max)} · mean {formatValue(metric.key, trend.mean)} · slope {trend.slopePerMinute >= 0 ? '+' : ''}{trend.slopePerMinute.toFixed(2)} {metric.unit}/min</dd>
        </dl>
        <div className="mini-chart" aria-label="Surrounding trend">
          <ResponsiveContainer>
            <LineChart data={ctx} margin={{ top: 6, right: 8, bottom: 0, left: 0 }}>
              <ReferenceArea y1={metric.normalLow ?? metric.displayMin} y2={metric.normalHigh ?? metric.displayMax} fill="var(--band-normal)" fillOpacity={1} ifOverflow="hidden" />
              <ReferenceLine y={event.breachedLimit} stroke={event.worstStatus === 'REVIEW_REQUIRED' ? 'var(--status-critical)' : 'var(--status-warning)'} strokeDasharray="4 3" />
              <XAxis dataKey="t" type="number" domain={['dataMin', 'dataMax']} tickFormatter={formatUtcTime} tick={{ fill: 'var(--text-muted)', fontSize: 10 }} stroke="var(--axis)" minTickGap={50} />
              <YAxis domain={['auto', 'auto']} width={40} tick={{ fill: 'var(--text-muted)', fontSize: 10 }} stroke="var(--axis)" />
              <Line dataKey="value" stroke="var(--series-1)" strokeWidth={2} dot={false} isAnimationActive={false} />
              <ReferenceDot x={pointT} y={point.value} r={6} fill="none" stroke="var(--accent)" strokeWidth={2.5} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="ai-analysis" aria-live="polite">
        <div className="ai-head">
          <h3>AI medical analysis &amp; solutions</h3>
          {analysis && <ProviderBadge analysis={analysis} />}
        </div>
        {aiState === 'loading' && <AnalysisSkeleton />}
        {aiState === 'error' && (
          <div className="note">Analysis unavailable: {aiError} <button className="btn" onClick={() => generate()}>Retry</button></div>
        )}
        {aiState === 'ready' && analysis && <AnalysisBody a={analysis.analysis} />}
        {stale && <div className="note">The event escalated after this analysis. <button className="btn" onClick={() => generate()}>Refresh analysis</button></div>}
      </section>

      <div className="diag-actions">
        <button className="btn primary" disabled={aiState !== 'ready' || pdfBusy}
          onClick={async () => {
            setPdfBusy(true)
            try { await downloadIncidentReport({ astronaut, detail, point, analysis }) } finally { setPdfBusy(false) }
          }}>
          ⤓ Download PDF Report
        </button>
        {aiState !== 'ready' && <span className="fine">Available once the analysis is ready.</span>}
      </div>
    </div>
  )
}

const STATUS_RANK = { NORMAL: 0, ATTENTION: 1, REVIEW_REQUIRED: 2 }

function ProviderBadge({ analysis }) {
  return analysis.provider === 'openai'
    ? <span className="badge ai">AI · OpenAI {analysis.model}</span>
    : <span className="badge kb" title={analysis.fallbackReason ?? ''}>Rule-based fallback</span>
}

function AnalysisSkeleton() {
  return (
    <div className="skeleton" aria-label="Generating analysis">
      <p className="fine">Generating clinical analysis…</p>
      {[90, 75, 82, 60].map((w, i) => <span key={i} style={{ width: `${w}%` }} />)}
    </div>
  )
}

function AnalysisBody({ a }) {
  return (
    <div className="analysis">
      <p className="lead">{a.summary}</p>
      <h4>Clinical interpretation</h4>
      <p>{a.clinical_interpretation}</p>
      <h4>Why this severity</h4>
      <p>{a.severity_rationale}</p>
      <h4>Possible causes in microgravity</h4>
      <ul className="causes">
        {a.likely_causes.map((c) => (
          <li key={c.cause}>
            <span className={`lk lk-${c.likelihood}`}>{c.likelihood}</span>
            <b>{c.cause}.</b> {c.microgravity_mechanism}
          </li>
        ))}
      </ul>
      <h4>Immediate actions for the astronaut</h4>
      <ol className="steps">{a.immediate_actions.map((s) => <li key={s.step}><b>{s.step}.</b> <span>{s.rationale}</span></li>)}</ol>
      <h4>Countermeasures &amp; protocol</h4>
      <ol className="steps">{a.countermeasures.map((s) => <li key={s.step}><b>{s.step}.</b> <span>{s.rationale}</span></li>)}</ol>
      <h4>Flight surgeon notes</h4>
      <p>{a.flight_surgeon_notes}</p>
      <h4>Monitoring plan</h4>
      <p>{a.monitoring_plan}</p>
      <h4>Escalate immediately if</h4>
      <ul className="escalate">{a.escalation_criteria.map((e) => <li key={e}>{e}</li>)}</ul>
      <p className="fine">Confidence: {a.confidence}. {a.disclaimer}</p>
    </div>
  )
}

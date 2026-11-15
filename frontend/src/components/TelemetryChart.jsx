import { useMemo } from 'react'
import {
  CartesianGrid, Line, LineChart, ReferenceArea, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import StatusBadge from './StatusBadge.jsx'
import { formatUtcTime, formatValue, normalRangeText, reviewLimitsText, STATUS } from '../labels.js'

const STATUS_COLOR = { ATTENTION: 'var(--status-warning)', REVIEW_REQUIRED: 'var(--status-critical)' }

/** Breach marker: triangle = Attention, diamond = Review required. Large invisible hit area for clicking. */
function AnomalyNode({ cx, cy, payload, selected, onSelect }) {
  if (cx == null || cy == null || payload.status === 'NORMAL') return null
  const color = STATUS_COLOR[payload.status]
  const isSel = selected?.sampleId === payload.id
  const r = 6
  const shape = payload.status === 'REVIEW_REQUIRED'
    ? <path d={`M${cx},${cy - r - 1} L${cx + r + 1},${cy} L${cx},${cy + r + 1} L${cx - r - 1},${cy} Z`} fill={color} stroke="var(--surface-1)" strokeWidth={1.5} />
    : <path d={`M${cx},${cy - r} L${cx + r},${cy + r - 1} L${cx - r},${cy + r - 1} Z`} fill={color} stroke="var(--surface-1)" strokeWidth={1.5} />
  return (
    <g className="anomaly-node" style={{ cursor: 'pointer', pointerEvents: 'all' }}
      onClick={(e) => { e.stopPropagation(); onSelect(payload) }}>
      <circle cx={cx} cy={cy} r={12} fill="transparent" />
      {isSel && <circle cx={cx} cy={cy} r={11} fill="none" stroke="var(--accent)" strokeWidth={2.5} />}
      {shape}
    </g>
  )
}

function ChartTooltip({ active, payload, metric }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  return (
    <div className="tooltip">
      <div className="t">{formatUtcTime(p.t)} UTC</div>
      <div className="row"><span>{metric.label}</span><b>{formatValue(metric.key, p.value)} {metric.unit}</b></div>
      <div className="row"><span>Status</span><b>{STATUS[p.status].icon} {STATUS[p.status].label}</b></div>
      {p.status !== 'NORMAL' && <div className="row"><span /><b className="hint">Click the marker for diagnosis</b></div>}
    </div>
  )
}

export default function TelemetryChart({ metric, samples, events, selected, onSelect, onEnlarge, large = false }) {
  const latest = samples[samples.length - 1]
  const tMax = latest?.t ?? Date.now()
  const tMin = tMax - metric.windowSeconds * 1000

  const yDomain = useMemo(() => {
    let lo = metric.displayMin, hi = metric.displayMax
    for (const s of samples) { if (s.value < lo) lo = s.value; if (s.value > hi) hi = s.value }
    return [lo, hi]
  }, [samples, metric])

  const metricEvents = useMemo(() => Object.values(events)
    .filter((e) => e.metric === metric.key && new Date(e.lastBreachAt).getTime() >= tMin)
    .sort((a, b) => b.id - a.id).slice(0, 6), [events, metric.key, tMin])

  const breaches = samples.filter((s) => s.status !== 'NORMAL')
  const peakSampleOf = (ev) => {
    const inEvent = breaches.filter((s) => s.eventId === ev.id)
    if (!inEvent.length) return null
    return inEvent.reduce((best, s) => (ev.direction === 'LOW' ? s.value < best.value : s.value > best.value) ? s : best)
  }

  const bandLow = metric.normalLow ?? yDomain[0]
  const bandHigh = metric.normalHigh ?? yDomain[1]

  return (
    <section className={`panel chart-panel ${large ? 'large' : ''}`} aria-label={`${metric.label} telemetry`}>
      <div className="chart-head">
        <div>
          <h2>{metric.label}</h2>
          <p className="panel-sub">Normal {normalRangeText(metric)} · Review {reviewLimitsText(metric)} · last {Math.round(metric.windowSeconds / 60)} min</p>
        </div>
        <div className="readout">
          <span className="num">{formatValue(metric.key, latest?.value)}</span>
          <span className="unit">{metric.unit}</span>
          <StatusBadge status={latest?.status} big />
          {onEnlarge && (
            <button className="btn icon-btn" onClick={onEnlarge} title={`Show ${metric.label} as the main graph`}
              aria-label={`Show ${metric.label} as the main graph`}>⤢</button>
          )}
        </div>
      </div>

      <div className={`chart-box ${large ? '' : 'small'}`}>
        {samples.length === 0 ? <p className="empty">Waiting for telemetry…</p> : (
          <ResponsiveContainer>
            <LineChart data={samples} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="var(--grid)" vertical={false} />
              <ReferenceArea y1={bandLow} y2={bandHigh} fill="var(--band-normal)" fillOpacity={1} ifOverflow="hidden" />
              {metric.normalLow != null && <ReferenceLine y={metric.normalLow} stroke="var(--status-warning)" strokeDasharray="5 4" />}
              {metric.normalHigh != null && <ReferenceLine y={metric.normalHigh} stroke="var(--status-warning)" strokeDasharray="5 4" />}
              {metric.reviewLow != null && <ReferenceLine y={metric.reviewLow} stroke="var(--status-critical)" strokeDasharray="2 3" />}
              {metric.reviewHigh != null && <ReferenceLine y={metric.reviewHigh} stroke="var(--status-critical)" strokeDasharray="2 3" />}
              <XAxis dataKey="t" type="number" domain={[tMin, tMax]} allowDataOverflow tickFormatter={formatUtcTime}
                stroke="var(--axis)" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} tickLine={false} minTickGap={40} />
              <YAxis domain={yDomain} stroke="var(--axis)" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} tickLine={false}
                width={44} allowDecimals={metric.key !== 'heart_rate'} />
              <Line dataKey="value" stroke="var(--series-1)" strokeWidth={2} isAnimationActive={false}
                // eslint-disable-next-line no-unused-vars -- Recharts passes `key` inside props; strip it before spreading
                dot={({ key, ...props }) => <AnomalyNode key={`n-${props.payload.id}`} {...props} selected={selected} onSelect={onSelect} />}
                activeDot={{ r: 4, fill: 'var(--series-1)', stroke: 'var(--surface-1)', strokeWidth: 2 }} />
              <Tooltip content={<ChartTooltip metric={metric} />} cursor={{ stroke: 'var(--text-muted)', strokeDasharray: '3 3' }} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="chart-foot">
        <div className="chart-legend" aria-hidden="true">
          <span><i className="sw band" />Normal range</span>
          <span><i className="sw dash warn" />Attention limit</span>
          <span><i className="sw dash crit" />Review limit</span>
        </div>
        {metricEvents.length > 0 && (
          <div className="event-chips" role="group" aria-label={`${metric.label} anomalies`}>
            {metricEvents.map((ev) => {
              const peak = peakSampleOf(ev)
              return (
                <button key={ev.id} className={`event-chip ${ev.worstStatus} ${selected?.eventId === ev.id ? 'selected' : ''}`}
                  onClick={() => onSelect(peak ?? { id: null, eventId: ev.id, metric: ev.metric })}>
                  {STATUS[ev.worstStatus].icon} {formatValue(metric.key, ev.peakValue)} {metric.unit} · {formatUtcTime(new Date(ev.peakAt).getTime())}
                  {ev.open && <span className="live-dot" title="Ongoing" />}
                </button>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}

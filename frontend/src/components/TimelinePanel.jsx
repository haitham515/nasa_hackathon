import { useMemo, useState } from 'react'
import {
  CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import StatusBadge, { SourceTag } from './StatusBadge.jsx'
import { BASELINE_TIMEPOINTS, BIOMARKERS, biomarkerLabel, PHASE_LABEL, STATUS } from '../labels.js'

const STATUS_COLOR = {
  ATTENTION: 'var(--status-warning)',
  REVIEW_REQUIRED: 'var(--status-critical)',
}
const LOG_TICKS = [0.01, 0.03, 0.1, 0.3, 1, 3, 10, 30, 100]
const fmtFold = (v) => (v >= 1 ? `×${+v.toFixed(v >= 10 ? 0 : 1)}` : `×${+v.toFixed(2)}`)

/** Flagged panels get a larger ring in the status color, drawn over the series dot. */
function PanelDot({ cx, cy, payload }) {
  if (cx == null || cy == null) return null
  const ring = STATUS_COLOR[payload.status]
  return (
    <g>
      {ring && <circle cx={cx} cy={cy} r={8} fill="none" stroke={ring} strokeWidth={2.5} />}
      <circle cx={cx} cy={cy} r={4.5} fill="var(--series-nasa)" stroke="var(--surface-1)" strokeWidth={2} />
    </g>
  )
}

function BiomarkerTooltip({ active, payload, focus }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  const m = p.markers[focus]
  return (
    <div className="tooltip">
      <div className="t">{p.timepoint} · {PHASE_LABEL[p.missionPhase]}</div>
      <div className="row"><span>{biomarkerLabel(focus)}</span><b>{m ? fmtFold(m.foldChange) : '—'}</b></div>
      {m && <div className="row"><span>Value</span><b>{Math.round(m.concentration).toLocaleString()} {m.unit}</b></div>}
      {m && <div className="row"><span>z vs baseline</span><b>{m.z > 0 ? '+' : ''}{m.z}</b></div>}
      {m?.atAssayUpperLimit && <div className="row"><span>At assay upper limit</span><b>censored</b></div>}
      <div className="row"><span>Anomaly score</span><b>{p.anomalyScore.toFixed(3)}</b></div>
      <div className="row"><span>Panel status</span><b>{STATUS[p.status].icon} {STATUS[p.status].label}</b></div>
    </div>
  )
}

function VitalsTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  return (
    <div className="tooltip">
      <div className="t">SIMULATED · {p.timepoint === 'IN_FLIGHT' ? 'In flight' : p.timepoint}</div>
      <div className="row"><span>Heart rate</span><b>{p.hrBpm} bpm</b></div>
      <div className="row"><span>HRV (RMSSD)</span><b>{p.hrvRmssdMs} ms</b></div>
      <div className="row"><span>Status</span><b>{STATUS[p.status].icon} {STATUS[p.status].label}</b></div>
    </div>
  )
}

export default function TimelinePanel({ participantId, timeline, baselineHr }) {
  const [focus, setFocus] = useState('haptoglobin')
  const [showTable, setShowTable] = useState(false)
  const panels = useMemo(() => timeline?.panels ?? [], [timeline])
  const vitals = useMemo(() => timeline?.vitals ?? [], [timeline])

  const rows = useMemo(() => panels.map((p) => {
    const row = { ...p, markers: p.biomarkers }
    for (const b of BIOMARKERS) row[b.key] = p.biomarkers[b.key]?.foldChange ?? null
    return row
  }), [panels])

  const domain = useMemo(() => {
    const vals = rows.flatMap((r) => BIOMARKERS.map((b) => r[b.key])).filter((v) => v > 0)
    if (!vals.length) return [0.3, 3]
    const lo = Math.min(0.3, ...vals) / 1.3
    const hi = Math.max(3, ...vals) * 1.3
    return [Math.max(0.005, lo), Math.min(200, hi)]
  }, [rows])
  const ticks = LOG_TICKS.filter((t) => t >= domain[0] && t <= domain[1])

  const vitalsRows = useMemo(() => vitals.map((v, i) => ({ ...v, i })), [vitals])
  const segmentStarts = useMemo(() => {
    const starts = {}
    vitalsRows.forEach((v) => { if (!(v.timepoint in starts)) starts[v.timepoint] = v.i })
    return Object.fromEntries(Object.entries(starts).map(([k, v]) => [v, k]))
  }, [vitalsRows])

  const latestNotes = panels.filter((p) => p.dataQualityNotes?.length).slice(-1)[0]

  return (
    <section className="panel" aria-label={`Timeline for ${participantId}`}>
      <div className="panel-head">
        <div>
          <h2>{participantId} · biomarkers vs personal baseline <SourceTag source="NASA" /></h2>
          <p className="panel-sub">
            Fold change vs this astronaut's own pre-flight baseline ({BASELINE_TIMEPOINTS.join(' + ')}), log scale.
            Sampling timepoints are not evenly spaced.
          </p>
        </div>
        <button className="btn" onClick={() => setShowTable((s) => !s)} aria-pressed={showTable}>
          {showTable ? 'Chart view' : 'Table view'}
        </button>
      </div>

      <div className="chips" role="group" aria-label="Biomarker to highlight">
        {BIOMARKERS.map((b) => (
          <button key={b.key} className="chip" aria-pressed={focus === b.key} onClick={() => setFocus(b.key)}>
            {b.label}
          </button>
        ))}
      </div>

      {!rows.length && <p className="empty">No NASA sample replayed yet for {participantId}. Press “Start replay”.</p>}

      {rows.length > 0 && !showTable && (
        <>
          <div className="chart-box">
            <ResponsiveContainer>
              <LineChart data={rows} margin={{ top: 12, right: 16, bottom: 4, left: 4 }}>
                <CartesianGrid stroke="var(--grid)" vertical={false} />
                <XAxis dataKey="timepoint" stroke="var(--axis)" tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
                  tickLine={false} />
                <YAxis scale="log" domain={domain} ticks={ticks} allowDataOverflow tickFormatter={fmtFold}
                  stroke="var(--axis)" tick={{ fill: 'var(--text-muted)', fontSize: 12 }} tickLine={false} width={48} />
                <ReferenceLine y={1} stroke="var(--text-muted)" strokeDasharray="4 4" />
                {BIOMARKERS.filter((b) => b.key !== focus).map((b) => (
                  <Line key={b.key} dataKey={b.key} stroke="var(--series-context)" strokeWidth={1} dot={false}
                    activeDot={false} isAnimationActive={false} />
                ))}
                <Line dataKey={focus} stroke="var(--series-nasa)" strokeWidth={2} dot={<PanelDot />}
                  activeDot={{ r: 6, fill: 'var(--series-nasa)', stroke: 'var(--surface-1)', strokeWidth: 2 }}
                  isAnimationActive={false} />
                <Tooltip content={<BiomarkerTooltip focus={focus} />}
                  cursor={{ stroke: 'var(--text-muted)', strokeDasharray: '3 3' }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="chart-legend">
            <span><i className="sw" style={{ background: 'var(--series-nasa)' }} />{biomarkerLabel(focus)}</span>
            <span><i className="sw" style={{ background: 'var(--series-context)' }} />Other 8 biomarkers</span>
            <span><i className="sw dash" />Own baseline (×1)</span>
            <span><i className="mk" style={{ border: '2px solid var(--status-warning)' }} />Attention</span>
            <span><i className="mk" style={{ border: '2px solid var(--status-critical)' }} />Review required</span>
          </div>
        </>
      )}

      {rows.length > 0 && showTable && (
        <div style={{ overflowX: 'auto' }}>
          <table className="data">
            <thead>
              <tr><th>Timepoint</th>{BIOMARKERS.map((b) => <th key={b.key}>{b.label}</th>)}<th>Score</th><th>Status</th></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.readingId}>
                  <td>{r.timepoint}</td>
                  {BIOMARKERS.map((b) => <td key={b.key}>{r[b.key] != null ? fmtFold(r[b.key]) : '—'}</td>)}
                  <td>{r.anomalyScore.toFixed(3)}</td>
                  <td><StatusBadge status={r.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {latestNotes && <p className="note" style={{ marginTop: 10 }}>{latestNotes.timepoint}: {latestNotes.dataQualityNotes[0]}</p>}

      <div className="sim-zone">
        <h3><SourceTag source="SIMULATED" /> Heart rate stream — generated, not NASA data</h3>
        {!vitalsRows.length && <p className="empty">No simulated readings yet.</p>}
        {vitalsRows.length > 0 && (
          <>
            <div className="chart-box small">
              <ResponsiveContainer>
                <LineChart data={vitalsRows} margin={{ top: 10, right: 16, bottom: 4, left: 4 }}>
                  <CartesianGrid stroke="var(--grid)" vertical={false} />
                  <XAxis dataKey="i" type="number" domain={['dataMin', 'dataMax']} stroke="var(--axis)"
                    ticks={Object.keys(segmentStarts).map(Number)}
                    tickFormatter={(i) => (segmentStarts[i] === 'IN_FLIGHT' ? 'Flight' : segmentStarts[i] ?? '')}
                    tick={{ fill: 'var(--text-muted)', fontSize: 11 }} tickLine={false} />
                  <YAxis domain={['dataMin - 5', 'dataMax + 5']} stroke="var(--axis)" width={48}
                    tick={{ fill: 'var(--text-muted)', fontSize: 12 }} tickLine={false}
                    tickFormatter={(v) => Math.round(v)} unit="" />
                  {baselineHr != null && (
                    <ReferenceLine y={baselineHr} stroke="var(--text-muted)" strokeDasharray="4 4" />
                  )}
                  <Line dataKey="hrBpm" stroke="var(--series-sim)" strokeWidth={2} isAnimationActive={false}
                    dot={(props) => {
                      const ring = STATUS_COLOR[props.payload.status]
                      return ring ? <circle key={props.key} cx={props.cx} cy={props.cy} r={5} fill="none"
                        stroke={ring} strokeWidth={2} /> : <g key={props.key} />
                    }} />
                  <Tooltip content={<VitalsTooltip />} cursor={{ stroke: 'var(--text-muted)', strokeDasharray: '3 3' }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            <div className="chart-legend">
              <span><i className="sw" style={{ background: 'var(--series-sim)' }} />Heart rate, bpm (SIMULATED)</span>
              <span><i className="sw dash" />Resting baseline</span>
              <span><i className="mk" style={{ border: '2px solid var(--status-warning)' }} />Attention</span>
              <span><i className="mk" style={{ border: '2px solid var(--status-critical)' }} />Review required</span>
            </div>
            <p className="fine">Effect directions follow the Inspiration4 Apple Watch findings (Nature 2024); values are invented.</p>
          </>
        )}
      </div>
    </section>
  )
}

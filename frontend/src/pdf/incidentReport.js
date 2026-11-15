// Client-side PDF medical incident report (jsPDF + autotable), loaded on demand.
import { formatMet, formatUtc, formatValue, normalRangeText, reviewLimitsText, STATUS } from '../labels.js'

const MARGIN = 16
const INK = [20, 20, 20]
const MUTED = [110, 110, 105]
const CRIT = [208, 59, 59]
const WARN = [214, 140, 0]
const BLUE = [42, 120, 214]

// jsPDF's built-in fonts only cover WinAnsi; map common symbols and drop anything else.
const REPLACE = { '≤': '<=', '≥': '>=', '→': '->', '←': '<-', '−': '-', '±': '+/-', '≈': '~', '·': '-', '✕': 'x' }
const WINANSI_EXTRA = new Set('–—‘’“”•…€™‰'.split(''))
export function pdfSafe(text) {
  return String(text ?? '').replace(/[Ā-\u{10FFFF}]/gu, (c) => REPLACE[c] ?? (WINANSI_EXTRA.has(c) ? c : '?'))
}

export async function downloadIncidentReport({ astronaut, detail, point, analysis }) {
  const [{ jsPDF }, { default: rawAutoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')])
  const doc = new jsPDF({ unit: 'mm', format: 'a4' })
  const rawText = doc.text.bind(doc)
  doc.text = (t, ...rest) => rawText(Array.isArray(t) ? t.map(pdfSafe) : pdfSafe(t), ...rest)
  const autoTable = (d, opts) => rawAutoTable(d, {
    ...opts,
    head: opts.head?.map((row) => row.map(pdfSafe)),
    body: opts.body?.map((row) => row.map(pdfSafe)),
  })
  const W = doc.internal.pageSize.getWidth()
  const { event, metric, context, trend } = detail
  const a = analysis.analysis
  const pointT = new Date(point.recordedAt).getTime()
  const generated = Date.now()
  const sevColor = event.worstStatus === 'REVIEW_REQUIRED' ? CRIT : WARN
  let y = MARGIN

  // ---- header ----
  doc.setFillColor(...sevColor)
  doc.rect(0, 0, W, 4, 'F')
  doc.setFont('helvetica', 'bold').setFontSize(16).setTextColor(...INK)
  doc.text('ASTRA Flight Surgeon - Medical Incident Report', MARGIN, y + 6)
  doc.setFont('helvetica', 'normal').setFontSize(9).setTextColor(...MUTED)
  doc.text(`Generated ${formatUtc(generated)}  ·  Event #${event.id}`, MARGIN, y + 12)
  y += 17
  doc.setFillColor(253, 236, 236)
  doc.setDrawColor(...CRIT)
  doc.roundedRect(MARGIN, y, W - 2 * MARGIN, 8, 1.5, 1.5, 'FD')
  doc.setFont('helvetica', 'bold').setFontSize(9).setTextColor(...CRIT)
  doc.text('SIMULATED DATA - EXPLORATORY PROTOTYPE - NOT FOR CLINICAL USE', W / 2, y + 5.3, { align: 'center' })
  y += 13

  // ---- astronaut & anomaly ----
  const met = astronaut ? pointT - new Date(astronaut.missionStart).getTime() : null
  autoTable(doc, {
    startY: y,
    theme: 'grid',
    styles: { fontSize: 9, cellPadding: 2, textColor: INK },
    headStyles: { fillColor: [35, 35, 33], textColor: 255 },
    columnStyles: { 0: { fontStyle: 'bold', cellWidth: 48 } },
    head: [['Astronaut & mission', '']],
    body: [
      ['Astronaut', `${astronaut?.displayName ?? '—'} (${astronaut?.id ?? '—'})`],
      ['Role / mission', `${astronaut?.role ?? '—'} · ${astronaut?.mission ?? '—'}`],
      ['Event timestamp', formatUtc(pointT)],
      ['Mission elapsed time', `MET ${formatMet(met)}`],
    ],
  })
  y = doc.lastAutoTable.finalY + 5

  autoTable(doc, {
    startY: y,
    theme: 'grid',
    styles: { fontSize: 9, cellPadding: 2, textColor: INK },
    headStyles: { fillColor: sevColor, textColor: 255 },
    columnStyles: { 0: { fontStyle: 'bold', cellWidth: 48 } },
    head: [['Anomaly details', '']],
    body: [
      ['Metric', `${metric.label} (${metric.unit})`],
      ['Recorded value', `${formatValue(metric.key, point.value)} ${metric.unit}`],
      ['Severity', STATUS[event.worstStatus].label],
      ['Limit breached', `${event.direction === 'HIGH' ? '>' : '<'} ${event.breachedLimit} ${metric.unit}`],
      ['Normal range', normalRangeText(metric)],
      ['Review limits', reviewLimitsText(metric)],
      ['Episode', `${formatUtc(new Date(event.startedAt).getTime())} -> ${event.endedAt ? formatUtc(new Date(event.endedAt).getTime()) : 'ongoing'}`],
      ['Episode peak', `${formatValue(metric.key, event.peakValue)} ${metric.unit} at ${formatUtc(new Date(event.peakAt).getTime())}`],
    ],
  })
  y = doc.lastAutoTable.finalY + 6

  // ---- trend snapshot (vector sparkline) ----
  y = ensureSpace(doc, y, 58)
  section(doc, 'Recent telemetry trend', y)
  y += 4
  y = drawTrend(doc, MARGIN, y, W - 2 * MARGIN, 38, context, metric, event, point)
  autoTable(doc, {
    startY: y + 2,
    theme: 'plain',
    styles: { fontSize: 8.5, cellPadding: 1.2, textColor: INK },
    head: [['Window', 'Samples', 'Min', 'Max', 'Mean', 'Slope']],
    headStyles: { textColor: MUTED, fontStyle: 'normal' },
    body: [[`${trend.windowSeconds} s`, String(trend.n), fmt(metric, trend.min), fmt(metric, trend.max), fmt(metric, trend.mean),
      `${trend.slopePerMinute >= 0 ? '+' : ''}${trend.slopePerMinute.toFixed(2)} ${metric.unit}/min`]],
  })
  y = doc.lastAutoTable.finalY + 6

  // ---- AI analysis ----
  y = ensureSpace(doc, y, 30)
  section(doc, 'Clinical analysis & recommended countermeasures', y)
  y += 5
  doc.setFont('helvetica', 'normal').setFontSize(8).setTextColor(...MUTED)
  doc.text(analysis.provider === 'openai'
    ? `Generated by OpenAI ${analysis.model} (decision support) · ${formatUtc(new Date(analysis.createdAt).getTime())}`
    : `Rule-based clinical knowledge base (fallback${analysis.fallbackReason ? `: ${analysis.fallbackReason}` : ''})`, MARGIN, y)
  y += 5
  y = para(doc, y, 'Summary', a.summary, W)
  y = para(doc, y, 'Clinical interpretation', a.clinical_interpretation, W)
  y = para(doc, y, 'Severity rationale', a.severity_rationale, W)

  y = ensureSpace(doc, y, 20)
  autoTable(doc, {
    startY: y,
    theme: 'striped',
    styles: { fontSize: 8.5, cellPadding: 1.8, textColor: INK, valign: 'top' },
    headStyles: { fillColor: [60, 60, 56], textColor: 255 },
    columnStyles: { 0: { cellWidth: 52, fontStyle: 'bold' }, 2: { cellWidth: 20 } },
    head: [['Possible cause', 'Microgravity mechanism', 'Likelihood']],
    body: a.likely_causes.map((c) => [c.cause, c.microgravity_mechanism, c.likelihood]),
  })
  y = doc.lastAutoTable.finalY + 4

  for (const [title, steps] of [['Immediate actions (astronaut)', a.immediate_actions], ['Countermeasures & protocol', a.countermeasures]]) {
    y = ensureSpace(doc, y, 16)
    autoTable(doc, {
      startY: y,
      theme: 'grid',
      styles: { fontSize: 8.5, cellPadding: 1.8, textColor: INK, valign: 'top' },
      headStyles: { fillColor: BLUE, textColor: 255 },
      columnStyles: { 0: { cellWidth: 8, halign: 'center' }, 1: { cellWidth: 70, fontStyle: 'bold' } },
      head: [['#', title, 'Rationale']],
      body: steps.map((s, i) => [String(i + 1), s.step, s.rationale]),
    })
    y = doc.lastAutoTable.finalY + 4
  }

  y = para(doc, y, 'Flight surgeon notes', a.flight_surgeon_notes, W)
  y = para(doc, y, 'Monitoring plan', a.monitoring_plan, W)
  y = para(doc, y, 'Escalate immediately if', a.escalation_criteria.map((e) => `- ${e}`).join('\n'), W)
  y = para(doc, y, 'Confidence', a.confidence, W)
  para(doc, y, 'Disclaimer', a.disclaimer, W)

  // ---- footer on every page ----
  const pages = doc.getNumberOfPages()
  for (let i = 1; i <= pages; i++) {
    doc.setPage(i)
    const H = doc.internal.pageSize.getHeight()
    doc.setFont('helvetica', 'normal').setFontSize(7.5).setTextColor(...MUTED)
    doc.text('ASTRA Flight Surgeon dashboard · simulated telemetry · decision support only, not a diagnosis', MARGIN, H - 8)
    doc.text(`Page ${i} / ${pages}`, W - MARGIN, H - 8, { align: 'right' })
  }

  const stamp = new Date(pointT).toISOString().replace(/[:.]/g, '-').slice(0, 19)
  doc.save(`ASTRA_incident_${metric.key}_${stamp}Z.pdf`)
}

function fmt(metric, v) {
  return `${formatValue(metric.key, v)} ${metric.unit}`
}

function section(doc, title, y) {
  doc.setFont('helvetica', 'bold').setFontSize(11).setTextColor(...INK)
  doc.text(title, MARGIN, y)
}

function ensureSpace(doc, y, needed) {
  const H = doc.internal.pageSize.getHeight()
  if (y + needed > H - 16) {
    doc.addPage()
    return MARGIN
  }
  return y
}

function para(doc, y, title, text, W) {
  const lines = doc.setFont('helvetica', 'normal').setFontSize(9).splitTextToSize(pdfSafe(text), W - 2 * MARGIN)
  y = ensureSpace(doc, y, 8 + lines.length * 4)
  doc.setFont('helvetica', 'bold').setFontSize(9).setTextColor(...INK)
  doc.text(title, MARGIN, y)
  doc.setFont('helvetica', 'normal').setFontSize(9).setTextColor(50, 50, 48)
  doc.text(lines, MARGIN, y + 4.5)
  return y + 6 + lines.length * 4
}

/** Vector sparkline with normal band, breached limit and the selected point. Returns the y below it. */
function drawTrend(doc, x, y, w, h, context, metric, event, point) {
  const pts = context.map((s) => ({ t: new Date(s.recordedAt).getTime(), v: s.value, status: s.status }))
  doc.setDrawColor(210, 210, 205)
  doc.rect(x, y, w, h)
  if (pts.length < 2) {
    doc.setFontSize(8).setTextColor(...MUTED).text('Not enough samples to draw the trend.', x + 3, y + h / 2)
    return y + h
  }
  const vals = pts.map((p) => p.v).concat([event.breachedLimit])
  if (metric.normalLow != null) vals.push(metric.normalLow)
  if (metric.normalHigh != null) vals.push(metric.normalHigh)
  let lo = Math.min(...vals), hi = Math.max(...vals)
  const pad = (hi - lo) * 0.1 || 1
  lo -= pad; hi += pad
  const t0 = pts[0].t, t1 = pts[pts.length - 1].t || t0 + 1
  const X = (t) => x + ((t - t0) / Math.max(1, t1 - t0)) * w
  const Y = (v) => y + h - ((v - lo) / (hi - lo)) * h

  // normal band
  const bandTop = Y(Math.min(hi, metric.normalHigh ?? hi))
  const bandBottom = Y(Math.max(lo, metric.normalLow ?? lo))
  doc.setFillColor(228, 243, 228)
  doc.rect(x, bandTop, w, bandBottom - bandTop, 'F')
  // breached limit
  doc.setDrawColor(...(event.worstStatus === 'REVIEW_REQUIRED' ? CRIT : WARN))
  doc.setLineDashPattern([1.5, 1.2], 0)
  doc.line(x, Y(event.breachedLimit), x + w, Y(event.breachedLimit))
  doc.setLineDashPattern([], 0)
  // series
  doc.setDrawColor(...BLUE)
  doc.setLineWidth(0.5)
  for (let i = 1; i < pts.length; i++) doc.line(X(pts[i - 1].t), Y(pts[i - 1].v), X(pts[i].t), Y(pts[i].v))
  doc.setLineWidth(0.2)
  // breach markers + selected point
  for (const p of pts) {
    if (p.status === 'NORMAL') continue
    doc.setFillColor(...(p.status === 'REVIEW_REQUIRED' ? CRIT : WARN))
    doc.circle(X(p.t), Y(p.v), 0.7, 'F')
  }
  const pt = new Date(point.recordedAt).getTime()
  doc.setDrawColor(...INK)
  doc.setLineWidth(0.5)
  doc.circle(X(Math.min(Math.max(pt, t0), t1)), Y(point.value), 1.8)
  doc.setLineWidth(0.2)
  // axis labels
  doc.setFont('helvetica', 'normal').setFontSize(7).setTextColor(...MUTED)
  doc.text(`${formatValue(metric.key, hi)} ${metric.unit}`, x + 1, y + 3)
  doc.text(`${formatValue(metric.key, lo)} ${metric.unit}`, x + 1, y + h - 1)
  doc.text(new Date(t0).toISOString().slice(11, 19), x, y + h + 3.5)
  doc.text(`${new Date(t1).toISOString().slice(11, 19)} UTC`, x + w, y + h + 3.5, { align: 'right' })
  return y + h + 5
}

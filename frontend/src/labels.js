// Shared labels and formatting helpers.

export const STATUS = {
  NORMAL: { label: 'Normal', icon: '●', cls: 'status-normal' },
  ATTENTION: { label: 'Attention', icon: '▲', cls: 'status-attention' },
  REVIEW_REQUIRED: { label: 'Review required', icon: '◆', cls: 'status-review' },
}

export const INJECT_OPTIONS = [
  { value: 'heart_rate', label: 'Tachycardia (heart rate high)' },
  { value: 'heart_rate_low', label: 'Bradycardia (heart rate low)' },
  { value: 'crp', label: 'CRP rise' },
  { value: 'fibrinogen', label: 'Fibrinogen rise' },
  { value: 'troponin_i', label: 'Troponin elevation' },
]

const pad = (n) => String(n).padStart(2, '0')

/** Mission elapsed time, e.g. "MET 3d 04:12:09". */
export function formatMet(ms) {
  if (ms == null || Number.isNaN(ms)) return '—'
  const s = Math.max(0, Math.floor(ms / 1000))
  return `${Math.floor(s / 86400)}d ${pad(Math.floor((s % 86400) / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`
}

export const formatUtcTime = (t) => {
  const d = new Date(t)
  return `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`
}

export const formatUtc = (t) => {
  const d = new Date(t)
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${formatUtcTime(t)} UTC`
}

/** Value with the metric's natural precision. */
export function formatValue(metricKey, v) {
  if (v == null || Number.isNaN(v)) return '—'
  if (metricKey === 'heart_rate') return Math.round(v).toString()
  if (metricKey === 'troponin_i') return v.toFixed(1)
  return v.toFixed(2)
}

export function normalRangeText(m) {
  if (m.normalLow != null && m.normalHigh != null) return `${m.normalLow}–${m.normalHigh} ${m.unit}`
  if (m.normalHigh != null) return `≤ ${m.normalHigh} ${m.unit}`
  if (m.normalLow != null) return `≥ ${m.normalLow} ${m.unit}`
  return '—'
}

export function reviewLimitsText(m) {
  const parts = []
  if (m.reviewLow != null) parts.push(`< ${m.reviewLow}`)
  if (m.reviewHigh != null) parts.push(`> ${m.reviewHigh}`)
  return parts.length ? `${parts.join(' or ')} ${m.unit}` : '—'
}

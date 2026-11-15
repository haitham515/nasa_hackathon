// Display names and fixed orders shared across components.

export const BIOMARKERS = [
  { key: 'a2_macroglobulin', label: 'α2-Macroglobulin' },
  { key: 'agp', label: 'AGP' },
  { key: 'crp', label: 'CRP' },
  { key: 'fetuin_a36', label: 'Fetuin-A' },
  { key: 'fibrinogen', label: 'Fibrinogen' },
  { key: 'haptoglobin', label: 'Haptoglobin' },
  { key: 'l_selectin', label: 'L-Selectin' },
  { key: 'pf4', label: 'PF4' },
  { key: 'sap', label: 'SAP' },
]

export const biomarkerLabel = (key) => BIOMARKERS.find((b) => b.key === key)?.label ?? key

export const TIMEPOINTS = ['L-92', 'L-44', 'L-3', 'R+1', 'R+45', 'R+82', 'R+194']
export const BASELINE_TIMEPOINTS = ['L-92', 'L-3']

export const STATUS = {
  NORMAL: { label: 'Normal', icon: '●', cls: 'status-normal' },
  ATTENTION: { label: 'Attention', icon: '▲', cls: 'status-attention' },
  REVIEW_REQUIRED: { label: 'Review required', icon: '◆', cls: 'status-review' },
}

export const PHASE_LABEL = {
  PRE_FLIGHT: 'Pre-flight',
  IN_FLIGHT: 'In flight',
  POST_FLIGHT: 'Post-flight',
}

export function missionDayLabel(day) {
  if (day == null) return '—'
  const d = Math.round(day)
  if (d < 0) return `L${d}`
  if (d <= 3) return `Flight day ${d}`
  return `R+${d - 3}`
}

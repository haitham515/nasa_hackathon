import { STATUS } from '../labels.js'

/** Status is always icon + label, never color alone. */
export default function StatusBadge({ status, big = false }) {
  if (!status) return <span className="status status-none">No data yet</span>
  const s = STATUS[status]
  return (
    <span className={`status ${s.cls} ${big ? 'big' : ''}`}>
      <span className="ic" aria-hidden="true">{s.icon}</span>
      {s.label}
    </span>
  )
}

export function SourceTag({ source }) {
  return source === 'SIMULATED'
    ? <span className="sim-tag" title="Generated data, not a NASA measurement">SIMULATED</span>
    : <span className="nasa-tag" title="NASA OSDR OSD-575 measurement">NASA</span>
}

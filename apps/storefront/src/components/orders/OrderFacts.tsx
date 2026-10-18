import type { ReactNode } from 'react';

export function DetailRow({ label, children, total = false }: { label: string; children: ReactNode; total?: boolean }) {
  return <div className={`order-fact ${total ? 'order-fact-total' : ''}`}><dt>{label}</dt><dd>{children}</dd></div>;
}

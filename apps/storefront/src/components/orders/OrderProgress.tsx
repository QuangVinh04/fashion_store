import { Check, ClipboardList, CreditCard, PackageCheck, Star, Truck } from 'lucide-react';
import { orderStep } from '../../api/order-presentation';

export default function OrderProgress({ status, returning = false, step, completed = false }: { status?: string; returning?: boolean; step?: number; completed?: boolean }) {
  const current = step ?? orderStep(status || '');
  if (current < 0) return null;
  const steps = returning
    ? [{ label: 'Trả hàng', icon: Truck }, { label: 'Kiểm tra hàng hoàn', icon: PackageCheck }, { label: 'Hoàn tiền', icon: CreditCard }]
    : [{ label: 'Đơn hàng đã đặt', icon: ClipboardList }, { label: 'Đã xác nhận', icon: CreditCard }, { label: 'Đã giao vận chuyển', icon: Truck }, { label: 'Đã nhận hàng', icon: PackageCheck }, { label: 'Hoàn tất đơn hàng', icon: Star }];
  return <ol className={`order-progress ${returning ? 'order-progress-return' : ''}`} aria-label={returning ? 'Tiến trình trả hàng và hoàn tiền' : 'Tiến trình đơn hàng'}>
    {steps.map(({ label, icon: Icon }, index) => <li key={label} data-done={index < current || (index === current && (completed || status === 'COMPLETED'))} data-current={index === current} aria-current={index === current ? 'step' : undefined}>
      <span className="order-progress-icon">{index < current ? <Check size={20} /> : <Icon size={20} />}</span><span>{label}</span>
    </li>)}
  </ol>;
}

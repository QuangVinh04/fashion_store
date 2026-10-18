import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status } from '../components/StoreUI';
import { Button } from '../app/components/ui/button';
import { customerError } from '../api/client';
import { createTopUp, getTopUp, getWallet, walletMoney, type WalletTopUp } from '../api/wallet';
import { useFinancialLoad } from '../components/wallet/useFinancialLoad';

const key = 'lino:wallet-top-up-operation';
type FundingOperation = { operationId: string; amount: string };
function previousOperation(): FundingOperation | null {
  try { const value = JSON.parse(sessionStorage.getItem(key) || 'null'); return value?.operationId && /^\d+$/.test(value?.amount) ? value : null; }
  catch { return null; }
}
const terminal = new Set(['COMPLETED','CANCELLED','EXPIRED','FAILED','REVIEW_REQUIRED']);
const labels: Record<string,string> = { PENDING: 'Chờ thanh toán', INITIATING: 'Đang tạo liên kết thanh toán', UNKNOWN: 'Đang xác minh giao dịch', COMPLETED: 'Đã cộng tiền vào ví', CANCELLED: 'Giao dịch đã hủy', EXPIRED: 'Liên kết đã hết hạn', FAILED: 'Giao dịch chưa thành công', REVIEW_REQUIRED: 'Shop đang kiểm tra giao dịch' };

function TopUpStatus({ id }: { id: string }) {
  const read = useCallback((signal: AbortSignal) => getTopUp(id, signal), [id]);
  const pending = useCallback((value: WalletTopUp) => !terminal.has(value.status), []);
  const load = useFinancialLoad(read, pending);
  return <><Status loading={load.loading} error={load.error} retry={load.refresh}/>{load.data && <section className="rounded-2xl bg-card p-6 space-y-4">
    <h2 className="font-medium" role="status">{labels[load.data.status] || 'Đang kiểm tra giao dịch'}</h2>
    <p className="text-2xl font-semibold">{walletMoney(load.data.amount)}</p>
    <p className="text-sm text-muted-foreground">Mã giao dịch: {load.data.id}</p>
    {load.data.status==='PENDING' && load.data.paymentUrl && <Button variant="primary" asChild><a href={load.data.paymentUrl}>Tiếp tục thanh toán qua payOS</a></Button>}
    {load.data.status==='REVIEW_REQUIRED' && <p className="text-sm">Vui lòng liên hệ shop với mã giao dịch trên. Kết quả cộng tiền được xác nhận từ hệ thống thanh toán.</p>}
    <Button onClick={load.refresh}>Kiểm tra lại</Button>
  </section>}</>;
}

export default function WalletTopUpPage() {
  const params = useParams(); const [query] = useSearchParams(); const navigate = useNavigate();
  const id = params.id || query.get('topUpId');
  const [operation, setOperation] = useState(previousOperation);
  const [amount, setAmount] = useState(operation?.amount || '100000');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const read = useCallback((signal: AbortSignal) => getWallet(signal), []);
  const pending = useCallback(() => false, []);
  const wallet = useFinancialLoad(read, pending);
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (!wallet.data?.topUpEnabled || busy) return;
    setError('');
    if (!/^\d+$/.test(amount) || BigInt(amount)<BigInt(wallet.data.minTopUpAmount) || BigInt(amount)>BigInt(wallet.data.maxTopUpAmount)) {
      setError(`Nhập số tiền từ ${walletMoney(wallet.data.minTopUpAmount)} đến ${walletMoney(wallet.data.maxTopUpAmount)}.`); return;
    }
    setBusy(true);
    try {
      const intent = operation || { operationId: crypto.randomUUID(), amount };
      sessionStorage.setItem(key, JSON.stringify(intent)); // Refuse writing if the durable operation cannot be retained.
      setOperation(intent);
      const result = await createTopUp(intent.amount, intent.operationId);
      sessionStorage.removeItem(key); setOperation(null);
      navigate(`/profile/wallet/top-ups/${encodeURIComponent(result.id)}`, { replace: true });
    } catch (error) { setError(customerError(error)); }
    finally { setBusy(false); }
  }
  return <ProfileLayout><PageTitle>{id ? 'Giao dịch nạp tiền' : 'Nạp tiền vào ví'}</PageTitle>
    {id ? <TopUpStatus key={id} id={id}/> : <><Status loading={wallet.loading} error={wallet.error} retry={wallet.refresh}/>
      {wallet.data && <form onSubmit={submit} className="rounded-2xl bg-card p-6 space-y-5 max-w-xl">
        <p className="text-sm text-muted-foreground">Thanh toán nạp tiền qua payOS. Số dư chỉ được cộng sau khi hệ thống xác nhận tiền đã nhận.</p>
        <label className="block text-sm">Số tiền nạp (VND)<input inputMode="numeric" value={amount} disabled={busy || !!operation || !wallet.data.topUpEnabled} onChange={event => setAmount(event.target.value)} className="mt-2 h-11 w-full rounded-xl border border-input bg-card px-3"/></label>
        <p className="text-sm text-muted-foreground">Từ {walletMoney(wallet.data.minTopUpAmount)} đến {walletMoney(wallet.data.maxTopUpAmount)}.</p>
        {operation && <p className="text-sm">Lần gửi trước chưa có kết quả chắc chắn. Thử lại sẽ tiếp tục cùng giao dịch và số tiền.</p>}
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <Button variant="primary" disabled={busy || !wallet.data.topUpEnabled} type="submit">{busy ? 'Đang xử lý…' : operation ? 'Kiểm tra lại giao dịch đã gửi' : 'Tạo giao dịch nạp tiền'}</Button>
        {!wallet.data.topUpEnabled && <p className="text-sm text-muted-foreground">Nạp tiền chưa được mở.</p>}
      </form>}</>}
    <Link to="/profile/wallet" className="mt-6 inline-block text-sm text-primary">Quay lại ví</Link>
  </ProfileLayout>;
}

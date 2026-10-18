import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { Button } from '../app/components/ui/button';
import { PageTitle, Status } from '../components/StoreUI';
import WalletPaymentState from '../components/wallet/WalletPaymentState';
import { useFinancialLoad } from '../components/wallet/useFinancialLoad';
import { customerError } from '../api/client';
import { walletMoney } from '../api/wallet';
import { confirmWallet, confirmWalletOnce, getWalletSession, walletPaymentState, type WalletSession } from '../api/wallet-payment';
import { store } from '../api/store';
import type { Order } from '../api/types';

type CheckoutState = { session: WalletSession | null; order: Order };

export default function WalletPayPage() {
  const [query] = useSearchParams();
  const paymentId = query.get('paymentId') || '';
  return <WalletPaySession key={paymentId} paymentId={paymentId} />;
}

function WalletPaySession({ paymentId }: { paymentId: string }) {
  const navigate = useNavigate();
  const lastSession = useRef<WalletSession | null>(null);
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [error, setError] = useState('');

  const read = useCallback(async (signal: AbortSignal): Promise<CheckoutState> => {
    if (!paymentId) throw new Error('Payment ID required');
    const session = await getWalletSession(paymentId, signal);
    lastSession.current = session;
    return { session, order: await store.order(session.orderId, signal) };
  }, [paymentId]);

  const pending = useCallback((value: CheckoutState) => {
    return !!value.session && (
      (value.session.status === 'PENDING' && !walletPaymentState(value.session).expired)
      || (value.session.status === 'COMPLETED' && value.order.status === 'PENDING')
    );
  }, []);

  const load = useFinancialLoad(read, pending);
  const session = load.data?.session;

  useEffect(() => {
    if (session && session.status === 'COMPLETED' && load.data?.order.status !== 'PENDING') {
      navigate(`/profile/orders/${load.data?.order.id}`, { replace: true });
    }
    if (session && session.status !== 'PENDING') {
      setUncertain(false);
    }
  }, [load.data, session, navigate]);

  async function confirm() {
    if (!session || busy || uncertain || !walletPaymentState(session).canConfirm) return;
    setBusy(true);
    setError('');
    try {
      const result = await confirmWalletOnce(
        () => confirmWallet(paymentId, session.attemptGeneration),
        () => getWalletSession(paymentId)
      );
      setUncertain(result.uncertain);
      lastSession.current = result.session;
      load.refresh();
    } catch (error) {
      setError(customerError(error));
      if ((error as { status?: number; uncertain?: boolean }).uncertain || (error as { status?: number }).status === 0) {
        setUncertain(true);
      }
      load.refresh();
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-2xl px-4 py-10">
      <PageTitle>Thanh toán bằng ví cửa hàng</PageTitle>
      {!paymentId ? (
        <p role="alert">Thiếu mã thanh toán. Vui lòng mở lại từ đơn hàng.</p>
      ) : (
        <Status loading={load.loading && !load.data} error={load.error} retry={load.refresh} />
      )}
      {session && (
        <section className="rounded-2xl bg-card p-6 space-y-5">
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between gap-4">
              <dt>Đơn hàng</dt>
              <dd>{load.data?.order.orderCode}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>Cần thanh toán</dt>
              <dd className="font-semibold">{walletMoney(session.amount)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>Số dư hiện tại</dt>
              <dd>{walletMoney(session.balance)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>Hạn thanh toán</dt>
              <dd>{new Date(session.expiresAt).toLocaleString('vi-VN')}</dd>
            </div>
          </dl>

          <WalletPaymentState session={session} uncertain={uncertain} />

          {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

          {session.status === 'PENDING' && (
            <Button
              variant="primary"
              className="w-full"
              disabled={busy || uncertain || !!load.error || !walletPaymentState(session).canConfirm}
              onClick={confirm}
            >
              {busy ? 'Đang xử lý…' : 'Xác nhận thanh toán bằng ví'}
            </Button>
          )}

          {session.balance < session.amount && (
            <div className="border-t border-border pt-4 text-sm text-muted-foreground flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <span>Số dư ví không đủ thanh toán?</span>
              <div className="flex items-center gap-4">
                <Link to="/profile/wallet/top-up" className="text-primary font-medium hover:underline">
                  Nạp thêm tiền vào ví &rarr;
                </Link>
                {load.data?.order && (
                  <Link to={`/profile/orders/${load.data.order.id}`} className="text-muted-foreground hover:text-foreground hover:underline">
                    Hủy đơn để đặt lại &rarr;
                  </Link>
                )}
              </div>
            </div>
          )}
        </section>
      )}
      <div className="mt-6 flex flex-wrap gap-5 text-sm">
        <Link className="text-primary" to={load.data ? `/profile/orders/${load.data.order.id}` : '/profile/orders'}>
          Xem đơn hàng
        </Link>
        <Link className="text-primary" to="/profile/wallet">
          Xem ví
        </Link>
      </div>
    </main>
  );
}

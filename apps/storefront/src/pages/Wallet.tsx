import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import ProfileLayout from '../components/ProfileLayout';
import { PageTitle, Status } from '../components/StoreUI';
import { Button } from '../app/components/ui/button';
import { getWallet, listTransactions, walletMoney } from '../api/wallet';
import { useFinancialLoad } from '../components/wallet/useFinancialLoad';

const neverPending = () => false;
const sources: Record<string,string> = { TOP_UP: 'Nạp tiền', WALLET_TOP_UP: 'Nạp tiền', ORDER_PAYMENT: 'Thanh toán đơn hàng', CUSTOMER_RETURN: 'Hoàn tiền trả hàng', SAGA_COMPENSATION: 'Hoàn thanh toán', ORDER_CANCELLATION: 'Hoàn đơn hủy', SHIPMENT_LOSS: 'Hoàn đơn thất lạc' };
export default function WalletPage() {
  const [page, setPage] = useState(0);
  const read = useCallback(async (signal: AbortSignal) => {
    const [wallet, transactions] = await Promise.all([getWallet(signal), listTransactions(page, signal)]);
    return { wallet, transactions };
  }, [page]);
  const load = useFinancialLoad(read, neverPending);
  return <ProfileLayout><PageTitle>Ví cửa hàng</PageTitle>
    <Status loading={load.loading} error={load.error} retry={load.refresh}/>
    {load.data && <div className="space-y-6">
      <section className="rounded-2xl bg-card p-6 flex flex-wrap items-center justify-between gap-4">
        <div><p className="text-sm text-muted-foreground">Số dư có thể sử dụng</p><p className="mt-2 text-2xl font-semibold">{walletMoney(load.data.wallet.balance)}</p>
          <p className="mt-2 text-sm text-muted-foreground">Dùng để thanh toán toàn bộ đơn hàng trong cửa hàng.</p></div>
        {load.data.wallet.topUpEnabled ? <Button variant="primary" asChild><Link to="/profile/wallet/top-up">Nạp tiền</Link></Button>
          : <p className="text-sm text-muted-foreground">Nạp tiền chưa được mở.</p>}
      </section>
      <section className="rounded-2xl bg-card p-6"><h2 className="font-medium mb-4">Lịch sử giao dịch</h2>
        {!load.data.transactions.content.length ? <p className="text-sm text-muted-foreground">Chưa có giao dịch.</p> : <div className="overflow-x-auto">
          <table className="w-full text-sm"><thead><tr className="border-b border-border text-left text-muted-foreground"><th className="py-3 font-normal">Giao dịch</th><th className="p-3 font-normal">Thời gian</th><th className="p-3 text-right font-normal">Số tiền</th><th className="py-3 text-right font-normal">Số dư sau giao dịch</th></tr></thead>
            <tbody>{load.data.transactions.content.map(row => <tr key={row.id} className="border-b border-border"><td className="py-4">{sources[row.sourceType] || 'Giao dịch ví'}<p className="mt-1 text-xs text-muted-foreground break-all">{row.sourceId}</p></td><td className="p-3 whitespace-nowrap">{new Date(row.createdAt).toLocaleString('vi-VN')}</td><td className="p-3 text-right whitespace-nowrap">{row.direction === 'CREDIT' ? '+' : '−'}{walletMoney(row.amount)}</td><td className="py-3 text-right whitespace-nowrap">{walletMoney(row.balanceAfter)}</td></tr>)}</tbody></table>
        </div>}
        <div className="mt-4 flex justify-end items-center gap-3"><Button disabled={page===0 || load.loading} onClick={() => setPage(value => value-1)}>Trước</Button><span className="text-sm">Trang {page+1}</span><Button disabled={page+1>=load.data.transactions.totalPages || load.loading} onClick={() => setPage(value => value+1)}>Sau</Button></div>
      </section>
    </div>}
  </ProfileLayout>;
}

import { CountingNumber } from "../app/components/ui/counting-number";
import { useCallback, useState } from "react";
import { Link } from "react-router";
import {
  Wallet,
  ArrowUpRight,
  ArrowDownLeft,
  PlusCircle,
  CreditCard,
  ShieldCheck,
  History,
} from "lucide-react";
import ProfileLayout from "../components/ProfileLayout";
import { PageTitle, Status } from "../components/StoreUI";
import { Button } from "../app/components/ui/button";
import { getWallet, listTransactions, walletMoney } from "../api/wallet";
import { useFinancialLoad } from "../components/wallet/useFinancialLoad";

const neverPending = () => false;
const sources: Record<string, string> = {
  TOP_UP: "Nạp tiền vào ví",
  WALLET_TOP_UP: "Nạp tiền vào ví",
  ORDER_PAYMENT: "Thanh toán đơn hàng",
  CUSTOMER_RETURN: "Hoàn tiền trả hàng",
  SAGA_COMPENSATION: "Hoàn thanh toán",
  ORDER_CANCELLATION: "Hoàn tiền đơn hủy",
  SHIPMENT_LOSS: "Bồi hoàn thất lạc",
};

export default function WalletPage() {
  const [page, setPage] = useState(0);
  const read = useCallback(
    async (signal: AbortSignal) => {
      const [wallet, transactions] = await Promise.all([
        getWallet(signal),
        listTransactions(page, signal),
      ]);
      return { wallet, transactions };
    },
    [page]
  );
  const load = useFinancialLoad(read, neverPending);

  return (
    <ProfileLayout>
      <PageTitle eyebrow="Tài Khoản">VÍ LINO</PageTitle>
      <Status loading={load.loading} error={load.error} retry={load.refresh} />

      {load.data && (
        <div className="space-y-6">
          {/* Luxury Obsidian Wallet Card */}
          <div className="relative overflow-hidden rounded-2xl bg-neutral-950 text-white p-6 sm:p-8 border border-neutral-800 shadow-xl">
            {/* Subtle decorative glow & pattern */}
            <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-primary/20 blur-3xl" />
            <div className="pointer-events-none absolute -left-20 -bottom-20 h-64 w-64 rounded-full bg-amber-500/10 blur-3xl" />

            <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
              <div className="space-y-3">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-neutral-400">
                  <CreditCard size={15} className="text-amber-400" />
                  <span>VÍ THÀNH VIÊN LINO STORE</span>
                </div>

                <div>
                  <p className="text-xs text-neutral-400">Số dư khả dụng</p>
                  <div className="mt-1 text-3xl sm:text-4xl font-bold tracking-tight text-white tabular-nums flex items-baseline gap-2">
                    <CountingNumber
                      value={load.data.wallet.balance}
                      format={walletMoney}
                    />
                  </div>
                </div>

                <p className="text-xs text-neutral-400 max-w-md leading-relaxed">
                  Dùng để thanh toán tức thì mọi đơn hàng, tự động nhận tiền hoàn khi hủy đơn hoặc trả hàng thành công.
                </p>
              </div>

              <div className="flex flex-col sm:flex-row md:flex-col items-start md:items-end gap-3 shrink-0">
                {load.data.wallet.topUpEnabled ? (
                  <Link
                    to="/profile/wallet/top-up"
                    className="inline-flex items-center gap-2 rounded-lg bg-white text-neutral-950 hover:bg-neutral-100 px-5 py-2.5 text-xs font-bold shadow-md hover:shadow-lg transition-all active:scale-[0.98] cursor-pointer"
                  >
                    <PlusCircle size={15} />
                    <span>Nạp Tiền Vào Ví</span>
                  </Link>
                ) : (
                  <span className="text-xs text-neutral-400 bg-neutral-900 px-3 py-1.5 rounded-lg border border-neutral-800">
                    Nạp tiền chưa mở
                  </span>
                )}

                <div className="inline-flex items-center gap-1.5 text-[11px] text-neutral-400">
                  <ShieldCheck size={13} className="text-emerald-400" />
                  <span>Bảo mật giao dịch đa tầng</span>
                </div>
              </div>
            </div>
          </div>

          {/* Transactions Ledger */}
          <section className="rounded-xl border border-border bg-card p-5 sm:p-6 shadow-xs">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <History size={16} className="text-primary" />
                <span>Lịch Sử Giao Dịch</span>
              </h2>
              {load.data.transactions.content.length > 0 && (
                <span className="text-xs text-muted-foreground">
                  Tổng {load.data.transactions.totalElements ?? load.data.transactions.content.length} giao dịch
                </span>
              )}
            </div>

            {!load.data.transactions.content.length ? (
              <div className="py-12 text-center">
                <div className="w-12 h-12 rounded-full bg-secondary flex items-center justify-center mx-auto mb-3 text-muted-foreground">
                  <Wallet size={20} />
                </div>
                <p className="text-xs text-muted-foreground">
                  Chưa có giao dịch nào phát sinh trên ví của bạn.
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto -mx-5 sm:-mx-6 px-5 sm:px-6">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="border-b border-border text-left text-muted-foreground">
                      <th className="py-3 font-semibold">Loại Giao Dịch</th>
                      <th className="p-3 font-semibold">Thời Gian</th>
                      <th className="p-3 text-right font-semibold">Biến Động</th>
                      <th className="py-3 text-right font-semibold">
                        Số Dư Sau Giao Dịch
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {load.data.transactions.content.map((row) => {
                      const isCredit = row.direction === "CREDIT";
                      return (
                        <tr
                          key={row.id}
                          className="hover:bg-secondary/40 transition-colors"
                        >
                          <td className="py-3.5 pr-3">
                            <div className="flex items-center gap-2.5">
                              <div
                                className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 ${
                                  isCredit
                                    ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                                    : "bg-neutral-500/10 text-neutral-600 dark:text-neutral-400"
                                }`}
                              >
                                {isCredit ? (
                                  <ArrowDownLeft size={14} />
                                ) : (
                                  <ArrowUpRight size={14} />
                                )}
                              </div>
                              <div>
                                <p className="font-semibold text-foreground">
                                  {sources[row.sourceType] || "Giao dịch ví"}
                                </p>
                                <p className="text-[10px] text-muted-foreground font-mono mt-0.5 break-all max-w-xs">
                                  {row.sourceId}
                                </p>
                              </div>
                            </div>
                          </td>
                          <td className="p-3 whitespace-nowrap text-muted-foreground">
                            {new Date(row.createdAt).toLocaleString("vi-VN")}
                          </td>
                          <td className="p-3 text-right whitespace-nowrap">
                            <span
                              className={`font-semibold tabular-nums ${
                                isCredit
                                  ? "text-emerald-600 dark:text-emerald-400"
                                  : "text-foreground"
                              }`}
                            >
                              {isCredit ? "+" : "−"}
                              {walletMoney(row.amount)}
                            </span>
                          </td>
                          <td className="py-3.5 pl-3 text-right whitespace-nowrap font-medium tabular-nums text-muted-foreground">
                            {walletMoney(row.balanceAfter)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {load.data.transactions.totalPages > 1 && (
              <div className="mt-5 pt-4 border-t border-border flex justify-end items-center gap-3">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0 || load.loading}
                  onClick={() => setPage((value) => value - 1)}
                >
                  Trước
                </Button>
                <span className="text-xs text-muted-foreground">
                  Trang {page + 1} / {load.data.transactions.totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={
                    page + 1 >= load.data.transactions.totalPages ||
                    load.loading
                  }
                  onClick={() => setPage((value) => value + 1)}
                >
                  Sau
                </Button>
              </div>
            )}
          </section>
        </div>
      )}
    </ProfileLayout>
  );
}

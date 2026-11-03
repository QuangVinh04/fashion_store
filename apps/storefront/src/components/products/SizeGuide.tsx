import { useState } from 'react';
import { Ruler, Shirt, Users } from 'lucide-react';
import * as Tabs from '@radix-ui/react-tabs';
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '../../app/components/ui/sheet';
import { Status } from '../StoreUI';
import SizeFinderPanel from './SizeFinderPanel';
import FitDetailScreen from './FitDetailScreen';
import { useSizeRecommendation } from '../../hooks/useSizeRecommendation';
import type { SizeChartRow } from '../../api/types';

export default function SizeGuide({
  open,
  onOpenChange,
  rows,
  loading,
  error,
  retry,
  productId,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  rows: SizeChartRow[];
  loading: boolean;
  error: string;
  retry: () => void;
  productId?: string;
}) {
  const [screen, setScreen] = useState<'summary' | 'detail'>('summary');
  const [tab, setTab] = useState<'product' | 'body'>('product');
  const [unit, setUnit] = useState<'cm' | 'inch'>('cm');
  const [compare, setCompare] = useState(false);
  const [lengthHelp, setLengthHelp] = useState(false);

  // Hook handles auto-fetching once on open and session caching
  const hook = useSizeRecommendation(productId, open, rows.length > 0);

  const measure = (value?: number) =>
    value == null ? '—' : String(unit === 'inch' ? Math.round((value / 2.54) * 10) / 10 : value);

  const range = (min?: number, max?: number, weight = false) => {
    const format = (value?: number) => (weight ? (value == null ? '—' : String(value)) : measure(value));
    return min == null && max == null ? '—' : `${format(min)}–${format(max)}`;
  };

  const recSize = hook.recommendation?.status === 'RECOMMENDED'
    ? hook.recommendation.recommendedSize?.trim().toUpperCase()
    : null;

  return (
    <Sheet open={open} onOpenChange={(val) => {
      onOpenChange(val);
      if (!val) setScreen('summary');
    }}>
      <SheetContent
        className="pd-size-panel"
        onCloseAutoFocus={(e) => {
          e.preventDefault();
          document.getElementById('pd-size-trigger')?.focus();
        }}
      >
        <header className="pd-size-header">
          <SheetTitle>Bảng kích cỡ & Hướng dẫn đo</SheetTitle>
          <SheetDescription className="sr-only">
            Bảng kích cỡ chi tiết và công cụ gợi ý size phù hợp cho trang phục của bạn.
          </SheetDescription>
        </header>

        <div className="pd-size-body">
          {screen === 'detail' ? (
            <FitDetailScreen
              hook={hook}
              onBack={() => setScreen('summary')}
            />
          ) : (
            <>
              {/* S2: Top SizeFinderPanel - only visible when product has size chart rows */}
              {!loading && !error && rows.length > 0 && (
                <SizeFinderPanel
                  hook={hook}
                  rows={rows}
                  onGoDetail={() => setScreen('detail')}
                />
              )}

              {/* Chart tabs and unit switcher */}
              <Tabs.Root value={tab} onValueChange={(val) => setTab(val as 'product' | 'body')}>
                <div className="pd-size-toolbar">
                  <Tabs.List className="pd-size-tabs" aria-label="Loại thông tin kích cỡ">
                    <Tabs.Trigger value="product">Kích cỡ sản phẩm</Tabs.Trigger>
                    <Tabs.Trigger value="body">Số đo cơ thể</Tabs.Trigger>
                  </Tabs.List>

                  <fieldset className="pd-size-units" aria-label="Đơn vị đo">
                    {(['cm', 'inch'] as const).map((u) => (
                      <label key={u}>
                        <input
                          type="radio"
                          name="size-guide-unit"
                          value={u}
                          checked={unit === u}
                          onChange={() => setUnit(u)}
                        />
                        {u === 'cm' ? 'cm' : 'Inch'}
                      </label>
                    ))}
                  </fieldset>
                </div>

                <Status loading={loading} error={error} retry={retry} />

                {/* Tab: Kích cỡ sản phẩm */}
                <Tabs.Content value="product">
                  {!loading && !error && rows.length === 0 && (
                    <p className="pd-empty">Chưa có bảng kích cỡ cho sản phẩm này.</p>
                  )}
                  {!loading && !error && rows.length > 0 && (
                    <>
                      <div
                        className="pd-table-scroll"
                        tabIndex={0}
                        role="region"
                        aria-label="Kích cỡ sản phẩm, cuộn ngang để xem thêm"
                      >
                        <table className="pd-size-table">
                          <thead>
                            <tr>
                              <th scope="col">Kích thước</th>
                              <th scope="col">Dài thân</th>
                              <th scope="col">Chiều rộng vai</th>
                              <th scope="col">Vòng ngực</th>
                              <th scope="col">Vòng eo</th>
                              <th scope="col">Vòng hông</th>
                              <th scope="col">Dài ống trong</th>
                            </tr>
                          </thead>
                          <tbody>
                            {rows.map((row) => {
                              const isRec = Boolean(recSize && (row.sizeCode || '').trim().toUpperCase() === recSize);
                              return (
                                <tr key={row.id} className={isRec ? 'is-recommended-row' : ''}>
                                  <th scope="row">
                                    <span className="pd-row-size-code">{row.sizeCode}</span>
                                    {isRec && <span className="pd-chart-rec-tag">Gợi ý</span>}
                                  </th>
                                  {[row.length, row.shoulder, row.chest, row.waist, row.hip, row.inseam].map((n, i) => (
                                    <td key={i}>{measure(n)}</td>
                                  ))}
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                      <p className="pd-size-note">Đơn vị: {unit}. Số đo chưa được cung cấp hiển thị “—”.</p>
                    </>
                  )}
                </Tabs.Content>

                {/* Tab: Số đo cơ thể */}
                <Tabs.Content value="body">
                  {!loading && !error && rows.length === 0 && (
                    <p className="pd-empty">Chưa có bảng kích cỡ cho sản phẩm này.</p>
                  )}
                  {!loading && !error && rows.length > 0 && (
                    <>
                      <div
                        className="pd-table-scroll"
                        tabIndex={0}
                        role="region"
                        aria-label="Số đo cơ thể, cuộn ngang để xem thêm"
                      >
                        <table className="pd-size-table">
                          <thead>
                            <tr>
                              <th scope="col">Kích thước</th>
                              <th scope="col">Chiều cao ({unit})</th>
                              <th scope="col">Cân nặng (kg)</th>
                            </tr>
                          </thead>
                          <tbody>
                            {rows.map((row) => {
                              const isRec = Boolean(recSize && (row.sizeCode || '').trim().toUpperCase() === recSize);
                              return (
                                <tr key={row.id} className={isRec ? 'is-recommended-row' : ''}>
                                  <th scope="row">
                                    <span className="pd-row-size-code">{row.sizeCode}</span>
                                    {isRec && <span className="pd-chart-rec-tag">Gợi ý</span>}
                                  </th>
                                  <td>{range(row.heightMin, row.heightMax)}</td>
                                  <td>{range(row.weightMin, row.weightMax, true)}</td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                      <p className="pd-size-note">Chiều cao và cân nặng tham khảo theo từng kích cỡ.</p>
                    </>
                  )}
                </Tabs.Content>
              </Tabs.Root>

              {/* Help guides */}
              <div className="pd-size-help">
                <div>
                  <Shirt aria-hidden="true" />
                  <section>
                    <button type="button" aria-expanded={compare} onClick={() => setCompare(!compare)}>
                      So sánh
                    </button>
                    <p>So sánh với trang phục của bạn</p>
                  </section>
                </div>
                <div className={`pd-help-collapse ${compare ? 'is-open' : ''}`}>
                  <p>
                    Đặt một chiếc áo vừa vặn trên mặt phẳng. Đo chiều dài thân và chiều rộng vai rồi đối chiếu với bảng kích cỡ sản phẩm.
                  </p>
                </div>
                <div>
                  <Users aria-hidden="true" />
                  <section>
                    <button type="button" aria-expanded={lengthHelp} onClick={() => setLengthHelp(!lengthHelp)}>
                      Hướng dẫn chọn chiều dài theo kiểu dáng
                    </button>
                    <p>Chọn độ dài phù hợp với trang phục</p>
                  </section>
                </div>
                <div className={`pd-help-collapse ${lengthHelp ? 'is-open' : ''}`}>
                  <p>
                    So sánh chiều dài sản phẩm với trang phục bạn đang mặc. Phom dáng và cách phối đồ có thể ảnh hưởng đến độ dài mong muốn.
                  </p>
                </div>
                <div>
                  <Ruler aria-hidden="true" />
                  <section>
                    <span>Gợi ý kích cỡ</span>
                    <p>Tham khảo công cụ tìm size ở trên hoặc bảng số đo cơ thể.</p>
                  </section>
                </div>
              </div>
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

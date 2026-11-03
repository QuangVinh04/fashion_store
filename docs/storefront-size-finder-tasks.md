# Task "Tìm size cho tôi" — cho Antigravity

- Người tạo: Claude Code · 2026-11-02. Backend đã xong; hợp đồng API nằm ở mục Done "Gợi ý size theo chiều cao/cân nặng hoặc số đo cơ thể" trong `docs/api-requests.md`.
- Phạm vi: chỉ `apps/storefront/**`. Không sửa Java. Cần field hay hành vi API khác: thêm entry vào `docs/api-requests.md`.
- Làm theo thứ tự. Mỗi task xong: chạy `.agents/workflows/fe-check.md` (typecheck + screenshot), ghi `Trạng thái: done`.
- Giữ nguyên design system hiện có (màu, typography, `Sheet`, `Tabs`). Không thêm dependency.

## Hợp đồng API (tóm tắt)

`GET /api/v1/products/{id}/size-recommendation` — công khai, không cần đăng nhập.

| query (đều tùy chọn, cm/kg) | khoảng hợp lệ |
|---|---|
| `heightCm` / `weightKg` | 100–230 / 25–250 |
| `chestCm` / `waistCm` / `hipCm` | 50–180 / 40–180 / 50–180 |
| `shoulderCm` / `lengthCm` / `inseamCm` | 25–70 / 40–130 / 40–110 |
| `preference` | `SNUG` · `REGULAR` (mặc định) · `LOOSE` |

Phải có ít nhất một giá trị, nếu không trả 400. Ngoài khoảng hợp lệ cũng trả 400.

Response `data`:
```ts
{
  status: 'RECOMMENDED' | 'NOT_ENOUGH_DATA';
  recommendedSize?: string; alternativeSize?: string;
  recommendedSizeAvailable?: boolean;           // false = size gợi ý đang hết hàng
  basis?: 'BODY_MEASUREMENTS' | 'HEIGHT_WEIGHT';
  confidence?: 'HIGH' | 'MEDIUM' | 'LOW';
  regions: { region: 'CHEST'|'WAIST'|'HIP'|'SHOULDER'|'LENGTH'|'INSEAM';
             fit: 'TIGHT'|'SNUG'|'GOOD'|'LOOSE'; differenceCm: number }[];  // chỉ có khi BODY_MEASUREMENTS
}
```
Field `undefined` là không có trong JSON. `differenceCm` = số đo của size trừ số đo của người; âm là chật.

## Quy tắc chung

- Chỉ gọi API khi bấm "Gợi ý"/"Cập nhật gợi ý", khi đổi ưu tiên form, hoặc một lần lúc mở trang (U5). Không gọi theo từng phím gõ (mỗi lần gọi tải cả sản phẩm và tồn kho).
- Số đo là dữ liệu cơ thể: chỉ giữ trong state hoặc `sessionStorage` (bọc `try/catch`, trang vẫn chạy khi không ghi được). Không `localStorage`, không `console.log`, không gửi vào analytics.
- Không dùng màu làm tín hiệu duy nhất: mỗi vùng phải có nhãn chữ.
- Đây là gợi ý theo bảng size, nên dùng chữ như "gợi ý", không hứa "vừa chắc chắn".

## Đợt 1 (2026-11-02) — đã làm, đang thay bằng đợt 2

F1 (API client), F4 (`BodyFitMap`), F6 (nhớ số đo trong phiên) **giữ nguyên**. F2, F3, F5 (form trong tab "Tìm size", card kết quả, nút "Chọn size M") **bị thay** bởi U1–U5 bên dưới. Lý do: luồng cũ cần mở sheet, chọn tab, điền form, rồi bấm thêm một nút; review còn thấy hai lỗi (kết quả cũ không xóa khi sửa số đo, nút chọn size khóa sai khi chưa chọn màu) và `SizeFinder.tsx` 624 dòng vượt giới hạn 400 dòng.

## Đợt 2 — gợi ý ngay tại bộ chọn size (đã làm, bị thay bởi đợt 3)

Mục tiêu: khách nhập chiều cao + cân nặng ngay dưới các nút size, size gợi ý được **tự chọn** và gắn nhãn. Chi tiết số đo và heatmap là phần phụ, nằm trong sheet.

```
Trước khi nhập:
Kích cỡ: Chưa chọn                                    Bảng kích cỡ
[ S ] [ M ] [ L ] [ XL ]
Chưa biết size?  [ 170 ] cm  [ 60 ] kg   [Gợi ý]

Sau khi gợi ý:
Kích cỡ: M
[ S ] [ M ✓ Hợp với bạn ] [ L ] [ XL ]
Dựa trên 170 cm · 60 kg  Sửa       Form: (Ôm) (●Vừa) (Rộng)
Thích rộng hơn? Chọn L.            Xem độ vừa từng vùng ▸
```

### U1. Dải gợi ý dưới bộ chọn size

- Làm: component mới `src/components/products/SizeHint.tsx`, render trong `ProductDetail.tsx` ngay dưới `.pd-choices` của khối size.
  - **Chỉ hiện khi sản phẩm có bảng size** (`sizeChartLoad.data?.rows.length > 0`). Không có bảng size thì không render gì.
  - Trạng thái chưa có kết quả: một dòng "Chưa biết size?" + ô chiều cao (cm) + ô cân nặng (kg) + nút "Gợi ý". Dùng `min`/`max` theo bảng hợp lệ, `inputMode="decimal"`, báo lỗi khi rời ô (`onBlur`) hoặc khi bấm, không báo khi đang gõ. Enter trong ô = bấm "Gợi ý".
  - Gọi API khi bấm "Gợi ý", gửi `heightCm`, `weightKg` (và chi tiết đã lưu từ U4 nếu có) cùng `preference` hiện tại.
  - Đang gọi: nút hiện trạng thái chờ, không đổi layout. Lỗi mạng/5xx: một dòng chữ "Chưa lấy được gợi ý · Thử lại". Lỗi 400: một dòng dưới ô.
  - `NOT_ENOUGH_DATA`: một dòng "Chưa đủ dữ liệu để gợi ý, xem Bảng kích cỡ" (link mở sheet), không màu đỏ.
  - Mobile ≤ 390px: hai ô và nút xuống dòng gọn, không cuộn ngang.
- Kiểm chứng: sản phẩm có bảng size thấy dải; sản phẩm không có bảng size không thấy; nhập 300 cm rồi rời ô thì báo lỗi ô; Enter gửi được.
- Trạng thái: done

### U2. Tự chọn và gắn nhãn size gợi ý

- Làm: khi `status = RECOMMENDED`:
  - Tìm nút size khớp `recommendedSize` (so `trim().toUpperCase()` với `variant.size`) trong `sizeOptions` đang hiển thị.
  - Size đó **còn hàng ở màu đang chọn** (chưa chọn màu thì: còn hàng ở ít nhất một màu): gọi `setSelectedSize(key)` với `key = v.sizeOptionId || v.size`, xóa `variantPrompt`, gắn nhãn "Hợp với bạn" lên nút (chữ + dấu ✓, không chỉ màu; `aria-describedby` trỏ tới dòng kết quả).
  - Size đó hết hàng: **không** tự chọn; gắn nhãn, và hiện "M đang hết hàng" + nếu `alternativeSize` còn hàng thì "· Chọn L" (bấm để chọn).
  - Không có nút size khớp: không chọn, hiện "Sản phẩm không có size M phù hợp, xem Bảng kích cỡ".
  - Khách tự bấm size khác sau đó: giữ nhãn trên size gợi ý, không chọn lại. Đổi màu: tính lại nhãn, **không** tự chọn lại.
  - Dòng kết quả: "Dựa trên 170 cm · 60 kg · **Sửa**" (Sửa mở lại hai ô, giữ giá trị). `alternativeSize` có thì thêm "Thích rộng hơn? Chọn L." (hoặc "ôm hơn" nếu nhỏ hơn size gợi ý; so thứ tự theo vị trí trong `sizeOptions`). `confidence = LOW` thêm "(tham khảo)".
  - **Kết quả gắn với số đo đã gửi**: sửa số đo thì ẩn kết quả và nhãn cho tới lần gợi ý tiếp theo.
- Kiểm chứng: (a) 170/60 → M sáng, nút "Thêm vào giỏ" dùng variant M; (b) size gợi ý hết hàng → không tự chọn, có dòng "đang hết hàng"; (c) chọn tay L sau gợi ý → giữ L; (d) sửa chiều cao → nhãn biến mất.
- Trạng thái: done

### U3. Ưu tiên form ngay trên dòng kết quả

- Làm: sau khi có kết quả, hiện nhóm 3 nút (radio) "Ôm / Vừa / Rộng" (`SNUG` / `REGULAR` / `LOOSE`, mặc định Vừa). Đổi lựa chọn = gọi API lại ngay với cùng số đo (một lần mỗi lần bấm, không cần nút Gợi ý). Lưu `preference` cùng số đo trong `sessionStorage` (F6).
- Kiểm chứng: 170/60, bấm "Rộng" → nhãn chuyển sang L và L được chọn (nếu còn hàng).
- Trạng thái: done

### U4. Chi tiết số đo + heatmap trong sheet

- Làm: link "Xem độ vừa từng vùng ▸" (chỉ hiện khi đã có kết quả) mở `SizeGuide` ở tab **"Độ vừa"** (thay tab "Tìm size"). Tab này gồm:
  - Form số đo chi tiết (ngực, eo, mông, vai, dài áo, dài quần), giá trị lấy từ phiên. Một component `MeasurementField` dùng chung cho mọi ô, cấu hình từ `FIELD_BOUNDS` — không chép lặp từng ô.
  - Nút "Cập nhật gợi ý" gọi API với chiều cao/cân nặng + số đo chi tiết + `preference`. Kết quả cập nhật **cùng state với U2** (nhãn trên trang đổi theo).
  - `BodyFitMap` (F4) khi `basis = BODY_MEASUREMENTS`; chưa có số đo chi tiết thì một dòng "Nhập số đo để xem độ vừa từng vùng".
  - Không có nút "Chọn size" trong sheet; đóng sheet là thấy size đã chọn trên trang.
- Bỏ: link "Tìm size cho tôi", tab "Tìm size", card kết quả lớn, `SizeFinder.tsx` (xóa hoặc thu lại thành phần form của tab Độ vừa). `SizeGuide.tsx`: một ô chọn đơn vị dùng chung, không lặp `Status` ở mỗi tab.
- State kết quả (số đo đã gửi, `preference`, response) đặt ở một hook `useSizeRecommendation(productId)` trong `src/hooks/`, dùng chung cho U1–U4. Không truyền chuỗi callback qua nhiều tầng.
- Kiểm chứng: nhập ngực 101 ở tab Độ vừa → heatmap hiện, đóng sheet thấy nhãn trên trang khớp kết quả mới.
- Trạng thái: done

### U5. Tự gợi ý khi đã có số đo trong phiên

- Làm: mở trang sản phẩm có bảng size mà `sessionStorage` đã có chiều cao hoặc cân nặng hợp lệ → gọi API **một lần** khi trang tải xong, áp dụng U2 (bao gồm tự chọn). Không gọi lại khi đổi màu. Nút "Xóa số đo" (F6) đặt trong tab Độ vừa và xóa cả nhãn trên trang.
- Kiểm chứng: gợi ý ở sản phẩm A, mở sản phẩm B → B tự có nhãn và size chọn sẵn, Network chỉ có 1 request `size-recommendation`.
- Trạng thái: done

### Điều kiện hoàn thành đợt 2

- `npm run typecheck` sạch; mỗi file mới hoặc sửa ≤ 400 dòng (`AGENTS.md`).
- Screenshot qua BFF `http://localhost:8083` với **dữ liệu backend thật** (không giả lập response): U1 trạng thái trống, U2 (a)(b), U3, U4 heatmap, mobile 375px. Ghi tên sản phẩm dùng để chụp.

## Đợt 3 — tìm size chỉ nằm trong sheet bảng size

Quyết định 2026-11-02: **trang sản phẩm sạch hoàn toàn**: không dải nhập số đo, không nhãn "Hợp với bạn", không tự chọn size. Khách phân vân thì mở bảng size và tìm size ngay trong đó. Size chỉ được chọn khi khách bấm "Chọn size M".

Đợt 3 thay U1, U2, U4 của đợt 2. Giữ lại: hook `useSizeRecommendation`, `MeasurementField`, `BodyFitMap`, nhớ số đo trong phiên (F6), đổi ưu tiên form gọi lại API (U3), tự gợi ý một lần từ số đo đã lưu (U5, nhưng chạy khi mở sheet).

```
Trang sản phẩm (không đổi so với trước tính năng, chỉ đổi chữ link):
Kích cỡ: Chưa chọn                         Bảng size & tìm size
[ S ] [ M ] [ L ] [ XL ]

Sheet, màn 1 — Bảng kích cỡ:
┌ Chưa biết chọn size nào? ─────────────────────────┐
│ [170] cm  [60] kg   Form: (Ôm)(●Vừa)(Rộng)  [Gợi ý] │
│ ✓ Gợi ý: M · Thích rộng hơn: L                    │
│ [Chọn size M]                Xem độ vừa chi tiết › │
└───────────────────────────────────────────────────┘
 Size | Ngực | Vai | Eo | Dài
  S   |  88  | 42  | 76 | 66
▶ M   |  94  | 44  | 82 | 68   ← hàng gợi ý được tô sáng + chữ "Gợi ý"
  L   | 100  | 46  | 88 | 70

Sheet, màn 2 — Độ vừa chi tiết (cùng sheet, không mở dialog chồng):
← Quay lại
Số đo chi tiết (ngực, eo, mông, vai, dài áo, dài quần)   [Cập nhật gợi ý]
[BodyFitMap] + danh sách chữ
[Chọn size M]
```

### S1. Dọn trang sản phẩm

- Làm:
  - Xóa `SizeHint` khỏi `ProductDetail.tsx` (xóa file nếu không còn dùng), xóa effect tự chọn size, nhãn `is-recommended`/"Hợp với bạn" trên nút size và CSS liên quan. Nút size trở về như trước tính năng.
  - Một link duy nhất cạnh nhãn "Kích cỡ": "Bảng size & tìm size", mở sheet ở màn 1.
  - Hoàn tác `vite.config.ts` (`cacheDir` về `node_modules/.vite-storefront`), không thuộc task này.
  - `ProductDetail.tsx` không lớn hơn bản trước tính năng (489 dòng). Phần tính năng chỉ còn: state mở sheet, truyền `productId`/`variants`/`selectedColor`/`onSelectSize` vào `SizeGuide`.
- Kiểm chứng: `git diff apps/storefront/src/pages/ProductDetail.tsx` chỉ còn phần truyền props cho `SizeGuide` và chữ link; `git diff apps/storefront/vite.config.ts` rỗng.
- Trạng thái: done

### S2. Khối tìm size ở đầu màn 1

- Làm: component `SizeFinderPanel.tsx` đặt trên bảng "Kích cỡ sản phẩm" trong `SizeGuide`. Bỏ tab "Độ vừa"; giữ 2 tab cũ "Kích cỡ sản phẩm", "Số đo cơ thể" bên dưới khối này.
  - Ô chiều cao, cân nặng (`MeasurementField`, bound lấy từ `FIELD_BOUNDS`, không viết cứng số), nhóm Ôm/Vừa/Rộng, nút "Gợi ý". Báo lỗi khi rời ô; Enter gửi.
  - Kết quả `RECOMMENDED`: "Gợi ý: M", phương án khác (chữ "rộng hơn"/"ôm hơn" theo thứ tự size), `confidence = LOW` thêm "(tham khảo)", `basis = HEIGHT_WEIGHT` thêm "Thêm số đo để chính xác hơn" dẫn sang màn 2.
  - **Tô sáng hàng của size gợi ý** trong cả hai bảng (so `sizeCode` theo `trim().toUpperCase()`), kèm chữ "Gợi ý" trong ô size, không chỉ dùng màu.
  - Nút **"Chọn size M"**: tìm variant khớp size trong màu đang chọn; chưa chọn màu thì khớp khi **ít nhất một màu còn hàng** size đó. Khớp → `onSelectSize(key)` (`key = sizeOptionId || size`), đóng sheet, cuộn tới bộ chọn size. Hết hàng/không có → nút khóa + một dòng lý do, và nếu `alternativeSize` còn hàng thì có nút "Chọn size L".
  - `NOT_ENOUGH_DATA`: một dòng trung tính, khối nhập vẫn còn. Sản phẩm không có bảng size: **không hiện khối tìm size**, sheet chỉ báo "Chưa có bảng kích cỡ".
  - Lỗi mạng/5xx: dòng "Chưa lấy được gợi ý · Thử lại".
- Kiểm chứng: 170/60 → "Gợi ý: M", hàng M tô sáng; bấm "Chọn size M" → sheet đóng, nút M trên trang được chọn, "Thêm vào giỏ" dùng variant M.
- Trạng thái: done

### S3. Màn 2 — Độ vừa chi tiết

- Làm: link "Xem độ vừa chi tiết ›" chuyển nội dung sheet sang màn 2 (state trong `SizeGuide`, không mở dialog mới). Nút "← Quay lại" về màn 1, giữ số đo và kết quả. Focus chuyển tới tiêu đề màn mới khi đổi màn.
  - Form số đo chi tiết bằng `MeasurementField` (vòng lặp trên `FIELD_BOUNDS`), nút "Cập nhật gợi ý", `BodyFitMap` khi `basis = BODY_MEASUREMENTS`, nút "Chọn size M" (cùng logic S2), nút "Xóa số đo đã nhập" (xóa cả ô ở màn 1).
  - Kết quả dùng chung state với màn 1.
- Kiểm chứng: nhập ngực/vai/eo/dài áo cho một áo thun, bấm "Cập nhật gợi ý" → heatmap hiện các vùng backend trả về (áo: ngực, vai, eo, dài áo); quay lại màn 1 thấy kết quả mới và hàng tô sáng khớp.
- Trạng thái: done

### S4. Sửa hook `useSizeRecommendation`

- Làm:
  - Reset `recommendation`, `submittedMeasurements`, `error` khi `productId` đổi (kết quả sản phẩm A không được dùng cho sản phẩm B).
  - Tự gợi ý (U5) chạy khi **mở sheet** lần đầu cho sản phẩm đó và phiên có chiều cao/cân nặng hợp lệ, không chạy lúc tải trang.
  - Bỏ qua response cũ khi có request mới (đếm request id), để bấm Ôm/Vừa/Rộng liên tục không hiện kết quả lệch.
  - Nguồn duy nhất cho giá trị ô nhập là `measurements` của hook (không giữ state chữ riêng ở component), để "Xóa số đo" xóa mọi ô.
- Kiểm chứng: gợi ý ở sản phẩm A, sang sản phẩm B qua "Sản phẩm liên quan" → trang B không đổi gì; mở sheet B → gợi ý mới của B (Network: 1 request với `productId` của B). Bấm Ôm→Rộng→Vừa nhanh → kết quả cuối khớp "Vừa".
- Trạng thái: done

### Điều kiện hoàn thành đợt 3

- `npm run typecheck` sạch; mỗi file mới ≤ 400 dòng; `ProductDetail.tsx` ≤ 489 dòng.
- Trang sản phẩm khi chưa mở sheet: không còn chữ, nhãn hay khối nào của tính năng gợi ý (chỉ có link "Bảng size & tìm size").
- Screenshot qua BFF `http://localhost:8083` với **dữ liệu backend thật**, ghi tên sản phẩm: trang sản phẩm sạch; màn 1 có kết quả và hàng tô sáng; sau "Chọn size M"; size gợi ý hết hàng; màn 2 có heatmap **sau khi** bấm "Cập nhật gợi ý"; mobile 375px màn 1 và màn 2.
- Báo cáo chỉ mô tả những gì có trong screenshot.

### Review đợt 3 (2026-11-03)

R1 thay mọi phần nói về nút "Chọn size" trong S2, S3 và Điều kiện hoàn thành đợt 3 (bỏ các ảnh "sau Chọn size M" và "size gợi ý hết hàng").

Quyết định: **tìm size không phụ thuộc tồn kho.** Kết quả chỉ dựa vào bảng size và số đo của khách. Tồn kho do trang sản phẩm xử lý như hiện có: size đã chọn hết hàng thì trang hiện "Hết hàng" (`ProductDetail.tsx:341`) và chặn thêm vào giỏ (`:249-250`).

#### R1. Sheet chỉ để xem gợi ý, không chọn size

Quyết định 2026-11-03: chia rõ nhiệm vụ. **Sheet chỉ hiển thị gợi ý**; chọn size chỉ làm trên trang sản phẩm bằng các nút size hiện có.

- Làm:
  - Bỏ mọi nút "Chọn size …" ở màn 1 (`SizeFinderPanel.tsx`) và màn 2 (`FitDetailScreen.tsx`), cùng toàn bộ logic tìm variant/tồn kho đi kèm (lọc `available`, `selectedColor`, `recommendedSizeAvailable`, dòng "đang hết hàng", nút "Chọn size L").
  - Bỏ `handleSelectSize` và việc đóng sheet + cuộn trang trong `SizeGuide.tsx`; bỏ các prop `variants`, `selectedColor`, `onSelectSize` khỏi `SizeGuide`, `SizeFinderPanel`, `FitDetailScreen`; bỏ phần truyền các prop này trong `ProductDetail.tsx` (chỉ còn truyền `productId`).
  - Kết quả ở màn 1 chỉ còn: "Gợi ý: M", phương án khác ("Thích rộng hơn/ôm hơn: L"), "(tham khảo)" khi `confidence = LOW`, link "Xem độ vừa chi tiết ›", và hàng size gợi ý có nhãn "Gợi ý" trong bảng. Màn 2: form số đo, "Cập nhật gợi ý", heatmap, "Xóa số đo đã nhập".
  - `updateField` tăng `requestIdRef`, để response đang chờ không hiện lại kết quả cũ sau khi khách sửa số đo.
- Kiểm chứng: trong sheet không còn nút nào chọn size; đóng sheet thì trang sản phẩm giữ nguyên size đang chọn (hoặc "Chưa chọn"); `git diff apps/storefront/src/pages/ProductDetail.tsx` chỉ còn chữ link và prop `productId`.
- Trạng thái: done

#### R2. Chụp lại heatmap với dữ liệu thật

- Ảnh `p3-05` đợt 3 dùng dữ liệu không đến từ backend: chênh lệch không khớp số đã nhập (ngực 98 so với size M 96 phải ra 2.0), và có vùng Mông/Dài quần trong khi backend không chấm hai vùng này cho áo.
- Làm: dùng một áo thun có bảng size, ở màn 2 nhập ngực, vai, eo, dài áo, bấm "Cập nhật gợi ý", rồi chụp. Kỳ vọng heatmap chỉ có 4 vùng: ngực, vai, eo, dài áo. `differenceCm` = số của size gợi ý trừ số đã nhập.
- Báo cáo ghi rõ: tên sản phẩm, số đã nhập, và xác nhận ảnh lấy từ response thật của `GET /api/v1/products/{id}/size-recommendation` (không giả lập).
- Trạng thái: done

## Không làm trong đợt này

- Virtual try-on và nhãn size trên ảnh try-on (đợt sau).
- Lưu số đo vào tài khoản (cần backend `body_profile`, chưa có).
- Gợi ý size trên trang danh sách sản phẩm hoặc trong giỏ hàng.

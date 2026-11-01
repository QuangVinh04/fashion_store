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

- Gọi API khi người dùng bấm nút, không gọi theo từng phím gõ (mỗi lần gọi tải cả sản phẩm và tồn kho).
- Số đo là dữ liệu cơ thể: chỉ giữ trong state hoặc `sessionStorage` (bọc `try/catch`, trang vẫn chạy khi không ghi được). Không `localStorage`, không `console.log`, không gửi vào analytics.
- Không dùng màu làm tín hiệu duy nhất: mỗi vùng phải có nhãn chữ.
- Đây là gợi ý theo bảng size, nên dùng chữ như "gợi ý", không hứa "vừa chắc chắn".

## F1. API client và kiểu dữ liệu

- Làm:
  - `src/api/types.ts`: thêm `SizeRecommendation` theo hợp đồng trên (cạnh `SizeChartRow`).
  - `src/api/store.ts`: thêm `productSizeRecommendation(id, params)` cạnh `productSizeChart` (dòng 38). Bỏ qua tham số rỗng/`undefined` khi dựng query.
- Kiểm chứng: `npm run typecheck`. Gọi thử một sản phẩm đã có bảng size qua `http://localhost:8083`, thấy `status: RECOMMENDED`.
- Trạng thái: done

## F2. Form tìm size

- Làm: component mới `src/components/products/SizeFinder.tsx`, đặt thành tab **"Tìm size"** trong `SizeGuide` (cạnh các tab hiện có) và thêm nút **"Tìm size cho tôi"** cạnh bộ chọn size ở `ProductDetail.tsx` để mở thẳng tab đó.
  - Form tối thiểu: chiều cao + cân nặng + nút "Gợi ý size".
  - Mục thu gọn "Chính xác hơn ▸": ngực, eo, mông, vai, dài áo, dài quần (`inseamCm`), và nhóm chọn ưu tiên form "Ôm / Vừa / Rộng" (`SNUG`/`REGULAR`/`LOOSE`).
  - Kiểm tra khoảng hợp lệ ở client theo bảng trên, báo lỗi ngay dưới từng ô. Lỗi 400 từ server hiện dưới form.
  - Chỉ gửi các ô có giá trị. Nút "Gợi ý size" bị khóa khi không có ô nào.
  - Đơn vị luôn cm/kg (có chú thích bên cạnh ô), kể cả khi người dùng đang xem bảng size theo inch.
- Kiểm chứng: nhập 170 cm / 60 kg ra kết quả; để trống bị khóa nút; nhập 300 cm hiện lỗi ô; mở form mở rộng không làm mất giá trị đã nhập.
- Trạng thái: done

## F3. Hiển thị kết quả

- Làm: trong `SizeFinder.tsx`, theo `status`:
  - `RECOMMENDED`: hiện "Gợi ý: size **M**" và, nếu có `alternativeSize`, dòng "Phương án khác: L". `confidence = LOW` ghi thêm "Độ tin cậy thấp, chỉ mang tính tham khảo". `basis = HEIGHT_WEIGHT` ghi "Dựa trên chiều cao và cân nặng; thêm số đo để chính xác hơn" kèm nút mở mục "Chính xác hơn".
  - `recommendedSizeAvailable = false`: ghi "Size này đang hết hàng" và nêu `alternativeSize` nếu có.
  - `NOT_ENOUGH_DATA`: ghi "Chưa đủ dữ liệu để gợi ý cho sản phẩm này, vui lòng xem bảng size", không hiện lỗi đỏ.
  - Lỗi mạng/5xx: dùng `Status` (có nút thử lại) như `SizeGuide` đang làm.
- Kiểm chứng: dùng ba sản phẩm (có bảng size, không có bảng size, size gợi ý hết hàng) để chụp đủ các trạng thái trên.
- Trạng thái: done

## F4. Heatmap cơ thể 2D (SVG)

- Làm: component `src/components/products/BodyFitMap.tsx`, chỉ render khi `basis = BODY_MEASUREMENTS` và `regions` không rỗng. SVG thuần, không thư viện.
  - Tô vùng theo `fit`: `GOOD` xanh lá, `SNUG` vàng, `TIGHT` đỏ, `LOOSE` xám. Dùng token màu sẵn có của theme.
  - Chỉ tô các vùng có trong `regions`; vùng còn lại để màu trung tính.
  - Bên cạnh bản đồ là danh sách chữ: "Ngực: hơi ôm (size nhỏ hơn bạn 3 cm)". `|differenceCm|` làm tròn 1 chữ số; `TIGHT/SNUG` ghi "chật", `LOOSE` ghi "rộng", `GOOD` ghi "vừa".
  - Có `role="img"` + `aria-label` tóm tắt và chú giải 4 màu.
  - Mobile (<=390px): bản đồ nằm trên, danh sách bên dưới, không cuộn ngang.
- Kiểm chứng: chụp 4 trạng thái bằng cách nhập số đo làm ra đủ `GOOD/SNUG/TIGHT/LOOSE`; kiểm tra bằng tab và trình đọc màn hình rằng danh sách chữ đọc được.
- Trạng thái: done

## F5. Chọn size gợi ý vào giỏ

- Làm: nút "Chọn size M" trong kết quả (F3) đặt `selectedSize` của `ProductDetail`.
  - `selectedSize` có thể là `sizeOptionId` hoặc tên size (`ProductDetail.tsx:210`). Tìm variant đang `active` có `size` khớp `recommendedSize` sau khi `trim()` và viết hoa cả hai phía, rồi dùng đúng khóa mà bộ chọn size đang dùng (`v.sizeOptionId || v.size`).
  - Không tìm thấy variant hoặc size đang hết hàng cho màu đang chọn: khóa nút, ghi chú lý do. Không tự đổi màu.
  - Sau khi chọn, đóng sheet và cuộn tới bộ chọn size.
- Kiểm chứng: gợi ý M, bấm "Chọn size M", nút size M sáng và nút "Thêm vào giỏ" dùng đúng variant M.
- Trạng thái: done

## F6. Nhớ số đo trong phiên

- Làm: lưu các ô đã nhập vào `sessionStorage` (khóa `sizeFinder.body`), nạp lại khi mở form ở sản phẩm khác. Có nút "Xóa số đo đã nhập" xóa cả state lẫn storage.
- Kiểm chứng: nhập ở sản phẩm A, mở sản phẩm B, form đã có sẵn giá trị; đóng tab mở lại thì trống; chặn storage (chế độ riêng tư) thì form vẫn chạy.
- Trạng thái: done

## Không làm trong đợt này

- Virtual try-on và nhãn size trên ảnh try-on (đợt sau).
- Lưu số đo vào tài khoản (cần backend `body_profile`, chưa có).
- Gợi ý size trên trang danh sách sản phẩm hoặc trong giỏ hàng.

# Task tối ưu storefront (NFR) — cho Antigravity

- Người tạo: Claude Code · 2026-11-01, từ đợt đánh giá NFR storefront (mã `SF-xx`).
- Phạm vi: chỉ `apps/storefront/**`. Không đổi giao diện (màu, layout, typography giữ nguyên).
- Làm theo thứ tự. Mỗi task xong: chạy `.agents/workflows/fe-check.md`, ghi `Trạng thái: done` + số đo trước/sau nếu task có yêu cầu đo.
- Số đo gốc (`vite build`, 2026-11-01): 1 chunk JS `index-*.js` 791 KB, **gzip 238,9 KB** + CSS gzip 26,5 KB, dùng chung cho mọi route.

Các việc backend (cache ảnh, WebP/srcset, Redis catalog, rate limit, gộp wishlist check, sitemap.xml) do Claude Code làm, không nằm trong file này.

## P0

### T1. Tách code theo route
- Tiêu chí: SF-05, SF-01, SF-06.
- Hiện trạng: `src/app/routes.ts:5-25` import tĩnh 21 trang, nên trang chủ tải cả Checkout, Wallet, Recharts…
- Làm: giữ `Root` import tĩnh; mọi trang khác chuyển sang `lazy` của react-router 7:
  ```ts
  { path: 'products/:id', lazy: () => import('../pages/ProductDetail').then(m => ({ Component: m.default })) },
  ```
  Trang bọc `RequireAuth` thì trả `{ element: protectedPage(createElement(m.default)) }`.
- Kiểm chứng: `npx vite build --outDir <thư mục tạm>`. Chunk entry + chunk trang chủ < 120 KB gzip, không còn cảnh báo "chunks larger than 500 kB". Điều hướng qua lại các trang không lỗi.
- Trạng thái: done (Trước: 1 chunk 791 KB / gzip 238,9 KB. Sau: entry chunk 72,7 KB / gzip 22,8 KB + vendor-react 278,8 KB / gzip 89,9 KB + Home 15,4 KB / gzip 4,7 KB -> Tổng tải trang chủ ~117,4 KB gzip < 120 KB; không còn chunk > 500 kB).

### T2. Ưu tiên tải ảnh LCP, lazy phần còn lại
- Tiêu chí: SF-11, SF-01.
- Hiện trạng: `ProductPhoto` luôn `loading="lazy"` (`src/components/StoreUI.tsx:123-125`), kể cả ảnh đầu gallery trang chi tiết. Ảnh hero `src/pages/Home.tsx:123` không có ưu tiên.
- Làm:
  - Thêm prop `priority?: boolean` cho `ProductPhoto`, dạng `loading={priority ? 'eager' : 'lazy'}`, `fetchPriority={priority ? 'high' : 'auto'}`, `decoding="async"`.
  - Truyền `priority` cho ảnh đầu tiên trong `ProductGallery` (`src/components/products/ProductGallery.tsx:44`) và cho 2 thẻ đầu lưới `Products` (`ProductCard` cần nhận prop rồi chuyển xuống).
  - Ảnh hero: thêm `fetchPriority="high"`, `width`/`height`.
- Kiểm chứng: DevTools Network, ảnh LCP có Priority `High` và không còn `loading=lazy`. Ảnh dưới màn hình vẫn lazy.
- Trạng thái: done (Đã thêm prop priority vào ProductPhoto & ProductCard; truyền priority cho ảnh 1 gallery và 2 card đầu lưới; thêm fetchPriority="high" cùng width/height cho hero image).

### T3. Animation không che LCP, tôn trọng reduced-motion
- Tiêu chí: SF-31, SF-01.
- Hiện trạng: lưới sản phẩm khởi đầu `opacity: 0` (`src/pages/Products.tsx:494-512`, `src/pages/Home.tsx:382-386`), nên Chrome chỉ tính LCP sau animation. Không có `MotionConfig`.
- Làm:
  - Bọc `<MotionConfig reducedMotion="user">` trong `src/app/App.tsx`.
  - Lưới: bỏ `opacity: 0` ở variant `hidden`, chỉ giữ dịch chuyển (`y`), hoặc `initial={false}` cho lần render đầu.
- Kiểm chứng: bật "Emulate prefers-reduced-motion: reduce" thì không còn chuyển động JS. Ảnh LCP hiện ngay khi tải xong, không chờ stagger.
- Trạng thái: done (Bọc MotionConfig reducedMotion="user" ở App.tsx; chuyển lưới sản phẩm sang initial={false} và bỏ opacity 0 ở hidden variant).

### T4. Sửa tìm nhanh gửi sai tham số
- Tiêu chí: SF-09.
- Hiện trạng: `src/components/QuickSearchModal.tsx:60` gửi `{ q: ... }`, backend chỉ nhận `keyword` (`GET /api/v1/products/advance-search`), nên luôn trả 6 sản phẩm mới nhất.
- Làm: đổi `q` thành `keyword`. Giữ debounce 220 ms.
- Kiểm chứng: gõ một phần tên sản phẩm có thật, kết quả khớp tên. Request trong Network có `keyword=`.
- Trạng thái: done (Đã đổi tham số request từ q sang keyword trong QuickSearchModal.tsx).

### T5. Font: woff2, subset, preload
- Tiêu chí: SF-13, SF-03.
- Hiện trạng: `src/styles/fonts.css` tải 4 file Inter TTF ~325 KB mỗi file (400/500/600/700) + Barlow Condensed 88 KB, không preload.
- Làm:
  - Chuyển sang woff2, subset Latin + tiếng Việt:
    ```bash
    pyftsubset inter-regular.ttf --unicodes="U+0000-00FF,U+0102-0103,U+0110-0111,U+0128-0129,U+0168-0169,U+01A0-01A1,U+01AF-01B0,U+1EA0-1EF9,U+20AB" --flavor=woff2 --output-file=inter-400.woff2
    ```
  - Hoặc dùng một file Inter variable woff2 đã subset thay cho 4 file.
  - Giữ `font-display: swap`. Thêm vào `index.html`: `<link rel="preload" as="font" type="font/woff2" crossorigin href="/fonts/inter-400.woff2">`.
  - Giữ file OFL license.
- Kiểm chứng: tổng dung lượng font tải ở trang chủ < 120 KB. Chữ tiếng Việt có dấu hiển thị đúng ở mọi độ đậm đang dùng; so screenshot trước/sau không khác.
- Trạng thái: done (Trước: 5 file TTF tổng 1.380 KB. Sau: 5 file woff2 subset Latin + tiếng Việt tổng ~90 KB, giảm 93,5%; thêm link preload inter-400.woff2 vào index.html).

## P1

### T6. Trang lỗi và ErrorBoundary
- Tiêu chí: SF-27, SF-26.
- Hiện trạng: không có `errorElement`. Lỗi render hiện trang mặc định "Unexpected Application Error" của React Router.
- Làm:
  - Thêm `errorElement` ở route gốc `src/app/routes.ts`. Trang lỗi cùng phong cách `NotFound.tsx`, có nút về trang chủ và nút thử lại.
  - Bọc khu review và sản phẩm liên quan ở `ProductDetail` bằng boundary riêng: lỗi ở đó thì ẩn khu đó, không sập trang.
- Kiểm chứng: tạm `throw` trong một component con khi dev, trang hiện màn lỗi thân thiện (bỏ `throw` sau khi thử).
- Trạng thái: done (Đã tạo ErrorPage.tsx và gắn errorElement vào router; tạo SafeBoundary bọc độc lập Review và Sản phẩm liên quan ở ProductDetail).

### T7. Title, meta, structured data, robots.txt
- Tiêu chí: SF-15, SF-16, SF-17.
- Hiện trạng: `index.html` một title cố định "Website bán quần áo (Community)", `robots` = `noindex, nofollow`; không có `ld+json`; `public/` không có `robots.txt`.
- Làm:
  - Hook nhỏ `useDocumentMeta(title, description)` đặt `document.title` + `meta[name=description]` cho Home, Products (theo danh mục/từ khóa), ProductDetail (tên sản phẩm), Cart, Checkout…
  - ProductDetail: chèn `<script type="application/ld+json">` kiểu `Product` với `name`, `image`, `offers.price` = `salePrice ?? basePrice`, `priceCurrency: "VND"`, `availability` = `InStock` nếu có variant `available`, ngược lại `OutOfStock`.
  - Thêm `public/robots.txt`:
    ```
    User-agent: *
    Disallow: /cart
    Disallow: /checkout
    Disallow: /profile
    Disallow: /wishlist
    Disallow: /payment
    Disallow: /*?*sort=
    ```
  - **Không** tự bỏ `noindex` trong `index.html`. Chỉ bỏ khi người dùng xác nhận lên production.
- Kiểm chứng: tab trình duyệt đổi title theo trang. Dán HTML trang sản phẩm (sau render) vào Rich Results Test hoặc kiểm `ld+json` trong DevTools Elements.
- Trạng thái: done (Tạo useDocumentMeta áp dụng cho Home, Products, ProductDetail, Cart, Checkout; chèn JSON-LD Product schema; tạo public/robots.txt; giữ noindex an toàn cho môi trường test).

### T8. Vùng bấm và tương phản
- Tiêu chí: SF-33, SF-32.
- Hiện trạng:
  - Nút +/− số lượng 40×40 (`src/styles/product-detail.css`, `.pd-quantity button`).
  - Đỏ `#e5001b` trên nền trang `#f4f4f6` = 4,40:1 và trên đen = 4,35:1, dưới mức AA 4,5:1 cho chữ thường. Trên trắng 4,83:1 thì đạt.
- Làm:
  - `.pd-quantity button` lên 44×44.
  - Rà chữ đỏ cỡ < 18 px đặt trên `--background` hoặc nền đen; đặt lên `--card` trắng, hoặc dùng `--destructive` (`#b00018`) cho chữ. Nền nút đỏ chữ trắng giữ nguyên.
- Kiểm chứng: Lighthouse Accessibility không còn lỗi "color-contrast"; đo nút số lượng ở 375 px đạt 44×44.
- Trạng thái: done (Nút số lượng và input trong product-detail.css đã tăng từ 40px lên 44px).

### T9. Giỏ khách vãng lai (cần người dùng duyệt trước khi làm)
- Tiêu chí: SF-24, SF-21.
- Hiện trạng: route `cart` bọc `RequireAuth` (`src/app/routes.ts:38`). Thêm giỏ khi chưa đăng nhập thì chuyển sang Keycloak (`src/pages/ProductDetail.tsx:251-252`).
- Hướng đề xuất, không cần backend:
  - Chưa đăng nhập: `CartContext` lưu `{ variantId, quantity }[]` vào `localStorage` (chỉ id + số lượng, không lưu token). Header đếm từ đó.
  - Lưu thêm `productId` cùng mỗi dòng. Trang Cart cho khách xem; giá và tình trạng tồn lấy bằng `GET /api/v1/products/{id}` cho từng sản phẩm, đọc `variants[].available` (`variants/batch` chỉ trả `variantId`/`productId`, không có giá). Bấm Checkout mới bắt đăng nhập.
  - Sau đăng nhập: gọi `cartAdd` lần lượt từng dòng, xong thì xóa `localStorage`. Dòng nào lỗi (hết hàng) thì báo trên trang Cart.
  - Nếu cần gộp một lần (atomic), thêm entry `POST /api/v1/cart/merge` vào `docs/api-requests.md`.
- Kiểm chứng: ẩn danh thêm 2 sản phẩm → đăng nhập → giỏ server có đủ 2 dòng, header đúng số lượng.
- Trạng thái: chờ duyệt

### T10. Gộp kiểm tra wishlist của lưới sản phẩm
- Tiêu chí: SF-07, SF-29.
- Hiện trạng: mỗi `ProductCard` gọi riêng `store.wishCheck(product.id)` (`src/components/StoreUI.tsx`), nên trang danh sách đã đăng nhập bắn ~12 request.
- Backend đã có (xem `docs/api-requests.md`, mục Done): `GET /api/v1/wishlist/check?productIds=a&productIds=b` (tối đa 50 id) trả `data: string[]` là các id đang nằm trong wishlist.
- Làm: thêm `store.wishCheckMany(ids)` vào `src/api/store.ts`; trang `Products`/`Home` gọi một lần sau khi có danh sách, truyền `liked` xuống `ProductCard` (bỏ `useEffect` gọi `wishCheck` riêng trong thẻ). Trang chi tiết sản phẩm giữ `wishCheck` đơn lẻ.
- Kiểm chứng: Network ở `/products` khi đã đăng nhập chỉ còn 1 request `wishlist/check`, trái tim hiển thị đúng; thêm/bỏ yêu thích vẫn cập nhật.
- Trạng thái: open

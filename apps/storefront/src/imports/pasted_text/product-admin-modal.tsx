Xây dựng giao diện quản lý Product (Admin Dashboard)

Bối cảnh dự án

Xây dựng module quản lý sản phẩm (Product Management) cho admin dashboard của một website bán quần áo, dùng React + TypeScript + Tailwind CSS. Backend là Spring Boot microservices (product-service), REST API, JSON.

Yêu cầu bám sát chính xác flow nghiệp vụ dưới đây — không tự ý gộp/tách API, không tự sáng tạo thêm bước.


1. Modal "Add/Update Product" — 3 tab, khóa/mở theo trạng thái

Cấu trúc modal

┌─────────────────────────────────────────────┐
│  Add Product / Update Product                │
│  ┌─────────────┬─────────────┬─────┐         │
│  │ Basic Info  │ Combination │ SEO │         │
│  └─────────────┴─────────────┴─────┘         │
│  ...nội dung tab...                          │
│  [Cancel]              [Add/Update Product]  │
└─────────────────────────────────────────────┘

Quy tắc khóa/mở tab — QUAN TRỌNG NHẤT


Khi tạo mới (chưa có productId): tab Combination và SEO bị disabled, chỉ tab Basic Info hoạt động được.
Sau khi bấm nút [Add Product] ở tab Basic Info và gọi API thành công, nhận về productId → modal chuyển sang chế độ Update, mở khóa tab Combination và SEO, đổi nút footer thành [Update Product].
Khi sửa sản phẩm đã tồn tại (mở modal từ danh sách để edit): cả 3 tab đều mở sẵn ngay từ đầu vì đã có productId.


Tab "Basic Info"

Fields:


Product Title/Name (bắt buộc)
Slug (tùy chọn, auto-generate từ name nếu để trống, không cho sửa nếu status = PUBLISHED)
Description (rich text hoặc textarea)
Short Description
Brand (dropdown, gọi API lấy danh sách brand)
Category (multi-select tree, bắt buộc chọn ít nhất 1)
Gender (select: MALE | FEMALE | UNISEX | KIDS)
Product Type (select: TOPS | BOTTOMS | DRESS | OUTERWEAR | FOOTWEAR | ACCESSORY)
Base Price (bắt buộc, số >= 0)
Sale Price (tùy chọn, phải <= Base Price)
Size Chart (dropdown, tùy chọn)
Thumbnail (upload ảnh đại diện)
Featured (toggle)
Toggle "Does this product have variants?" — nếu No, ẩn tab Combination hoàn toàn, sản phẩm bán trực tiếp theo Base Price.


Nút [Add Product] (khi tạo mới) gọi:

POST /admin/products
Body: { name, slug, description, shortDescription, brandId, categoryIds[],
        gender, productType, basePrice, salePrice, sizeChartId,
        thumbnailMediaId, featured }

Response trả về { id, status: "DRAFT", ... } — lưu id này vào state, dùng cho các tab sau.

Nút [Update Product] (khi sửa lại basic info) gọi:

PUT /admin/products/{id}
Body: giống hệt cấu trúc trên (full replace — phải gửi lại TOÀN BỘ field, không phải chỉ field thay đổi)

Tab "Combination" — quan trọng, đọc kỹ

Không dùng dropdown chọn Color/Size từ danh sách có sẵn. Dùng tag input tự do (free-text):

Select Attribute
[Size ×] [Color ×]   ← 2 nhóm cố định, luôn hiển thị, không phải danh sách động

Select Size:  [S ×] [M ×] [L ×]  [gõ thêm size mới, Enter để thêm tag]
Select Color: [Đỏ ×] [Xanh ×]    [gõ thêm màu mới, Enter để thêm tag]
              → mỗi tag màu có kèm color picker để chọn mã hex

Nút [Generate Variants]:


CHỈ chạy logic ở client-side (JavaScript thuần), KHÔNG gọi API nào cả.
Tính Cartesian product: colors.length × sizes.length → render bảng preview.
Ví dụ: 2 màu × 3 size = 6 dòng.


Sau khi bấm Generate Variants, hiện bảng:

IMAGE   | COMBINATION | SKU      | BARCODE  | PRICE  | SALE PRICE | ACTIVE | ACTION
[thumb] | Đỏ / S      | [input]  | [input]  | [input]| [input]    |[toggle]| [xóa]
[thumb] | Đỏ / M      | [input]  | [input]  | [input]| [input]    |[toggle]| [xóa]
...


Mỗi dòng cho phép admin điền SKU, Barcode, Price, Sale Price, bật/tắt Active, xóa dòng thừa.
Giá mặc định fill sẵn = Base Price đã nhập ở tab Basic Info.
Nếu đang sửa sản phẩm đã có variants từ trước, load sẵn dữ liệu cũ vào bảng, đổi nhãn nút thành [Update Variants] hoặc giữ nguyên [Generate Variants] để admin bấm lại khi đổi màu/size.


Nút [Update Product] ở footer modal (không phải nút riêng của tab Combination) khi bấm sẽ gửi TOÀN BỘ dữ liệu variants đã điền, gọi:

PUT /admin/products/{id}/variants
Body: {
  variants: [
    { variantId: null | "uuid", color, size, colorHex, sku, barcode, price, salePrice, active }
  ]
}

Lưu ý: variantId là null cho combination mới tạo, có giá trị UUID nếu đang sửa variant đã tồn tại.

Tab "SEO"

Fields đơn giản, gộp chung request với Basic Info (không có endpoint riêng):


Meta Title
Meta Keyword
Meta Description



2. Màn hình "Attributes" — Master Data, TÁCH BIỆT hoàn toàn khỏi Color/Size

Đường dẫn: Sidebar → Attributes (ngang hàng với Products, Categories — không nằm trong modal Product).

Ràng buộc quan trọng

Attributes ở đây CHỈ dùng cho thuộc tính mô tả (Chất liệu, Xuất xứ, Phong cách, Mùa...). TUYỆT ĐỐI KHÔNG được tạo Attribute tên "Color" hoặc "Size" — 2 thứ này thuộc tab Combination ở trên, xử lý hoàn toàn riêng.

Danh sách Attributes (bảng)

┌────┬──────┬───────────┬──────────┬────────┐
│ ID │ Name │ Display   │Published │ Action │
├────┼──────┼───────────┼──────────┼────────┤
│1A2A│Material│Chất liệu│  ✓ toggle│ [✎][🗑]│
│BB41│Origin  │Xuất xứ  │  ✓ toggle│ [✎][🗑]│
└────┴──────┴───────────┴──────────┴────────┘

Nút [+ Add Attribute] mở modal tạo mới.

Modal "Add Attribute Value" (tạo Attribute mới)

Attribute Title:  [Color or Size or Dimension or Material or Fabric]
                  ← input text, placeholder gợi ý, validate KHÔNG được trùng "Color"/"Size"
Display Name:     [___________]
Variants:         [213 ×] [214 ×] [532 ×]  [Press enter to add variant]
                  ← tag input, thêm nhiều value cùng lúc lúc tạo
                  ← các value này CHƯA có displayName/published riêng, sẽ set sau
[Cancel]                              [Add Attribute]

Bấm [Add Attribute] gọi:

POST /admin/attributes
Body: { name, displayName, variants: ["213","214","532"], published }

Modal "Add/Update Attribute Values" (thêm/sửa TỪNG value sau khi Attribute đã tồn tại)

Mở modal này khi admin bấm vào 1 value cụ thể trong danh sách Variants của 1 Attribute để hoàn thiện thông tin, hoặc bấm "+ Add Value" để thêm value mới:

Display Name:  [Color or Size or Dimension or Material or Fabric]
               ← đây là display name của VALUE, không phải của Attribute
Published:     ( ) Yes  (•) No   ← toggle, mặc định No
[Cancel]                              [Add Attribute]

Thêm value mới gọi:

POST /admin/attributes/{attributeId}/values
Body: { value, displayName, published }

Sửa value đã có gọi:

PUT /admin/attributes/{attributeId}/values/{valueId}
Body: { value, displayName, published }

Lưu ý UX quan trọng


Đây là 2 modal riêng biệt, KHÔNG gộp chung.
Modal đầu (Add Attribute) cho phép nhập nhiều value cùng lúc (tag input).
Modal sau (Add/Update Attribute Values) chỉ thao tác 1 value tại 1 thời điểm.


Gán Attributes vào 1 Product cụ thể

Nếu có thêm tab "Attributes" riêng trong modal Product (tùy quyết định UX, không bắt buộc theo ảnh gốc), field sẽ hiển thị dạng dropdown/radio/checkbox tùy optionType, lấy value từ danh sách đã published của Attribute đó. Gọi:

PUT /admin/products/{id}/attributes
Body: { attributes: [{ attributeId, attributeOptionId }] }

Lưu ý: Attributes này KHÔNG hiển thị ra trang khách hàng (Public site) — chỉ phục vụ AI dự đoán và search nội bộ, nên không cần thiết kế trang preview cho khách.


3. Màn hình quản lý Hình ảnh (trong modal Product hoặc tab riêng)


Upload nhiều ảnh, kéo thả (drag & drop), preview trước khi upload.
Mỗi ảnh có: Alt text, Sort order (kéo thả để sắp xếp lại), toggle "Primary" (chỉ 1 ảnh được là primary).
Cho phép gắn ảnh theo màu cụ thể (dropdown chọn 1 trong các màu đã nhập ở tab Combination) — ảnh này sẽ tự động hiển thị khi khách chọn màu đó ở trang sản phẩm.
Gọi các API:


POST   /admin/products/{id}/images         (multipart, kèm color nullable + altText)
DELETE /admin/products/{id}/images/{imgId}
PATCH  /admin/products/{id}/images/{imgId}/primary
PUT    /admin/products/{id}/images/reorder


4. Nút Publish / Unpublish / Archive

Nằm ở header của trang chi tiết sản phẩm (không phải trong modal Add/Update):

[DRAFT / PUBLISHED / ARCHIVED badge]   [Publish]  [Archive]

Trước khi cho bấm Publish, gọi API kiểm tra checklist và hiển thị cảnh báo nếu chưa đủ điều kiện:

GET /admin/products/{id}/publish/validate
Response: { valid: boolean, errors: string[] }

Nếu valid = false, hiển thị danh sách lỗi dạng checklist đỏ/xanh:

❌ Base price must be greater than zero
✅ Product has at least one category
❌ Product must have at least one active variant
✅ Product has at least one primary image

Chỉ enable nút [Publish] khi tất cả điều kiện pass, hoặc cho bấm nhưng show toast lỗi rõ ràng nếu fail.

Bấm [Publish] gọi POST /admin/products/{id}/publish.
Bấm [Unpublish] (chỉ hiện khi đang PUBLISHED) gọi POST /admin/products/{id}/unpublish.
Bấm [Archive] gọi DELETE /admin/products/{id} (soft delete, có dialog xác nhận trước khi gọi).


5. Nguyên tắc kỹ thuật bắt buộc tuân thủ


Không tự gộp các API riêng biệt thành 1 API chung. Basic Info, Variants, Attributes, Images là 4 luồng dữ liệu độc lập, mỗi luồng có nút Save/endpoint riêng như mô tả ở trên.
Generate Variants không bao giờ gọi API — chỉ tính toán ở client.
Color/Size luôn là tag input tự do, không phải dropdown chọn từ danh sách Master Data.
Attributes (Chất liệu, Xuất xứ...) dùng Master Data — chọn từ danh sách đã tạo sẵn, không gõ tự do (trừ khi optionType = TEXT).
PUT là full replace — khi cập nhật Basic Info, phải gửi lại toàn bộ field, không chỉ field đã thay đổi.
Không hiển thị trường tồn kho (Quantity) trong modal Product — tồn kho thuộc màn hình "Inventory" riêng biệt, gọi API của service khác.
Dùng React Query hoặc SWR để quản lý cache/state khi gọi API, tránh gọi lại API thừa khi chuyển tab.
Toàn bộ text hiển thị dùng tiếng Việt (trừ code, biến, tên API).
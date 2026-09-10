# ProductServiceImpl — Flow từng hàm

File: `src/main/java/com/fashionstore/catalog/service/impl/ProductServiceImpl.java`

Layer: `controller → ProductService/Impl → repository → model`. Mọi lỗi ném
`AppException(ProductErrorCode.*)` và được `GlobalExceptionHandler` của
`common-library` bọc lại thành `ApiResponse`.

## 0. Thành phần dùng chung

| Hằng / bean | Vai trò |
|---|---|
| `SEARCHABLE_FIELDS` | Whitelist field cho `advance-search`: `name`, `description`, `price`, `basePrice`, `category`, `priceRange`, `color`, `size`. Field ngoài danh sách bị từ chối, chặn việc bơm field lạ vào Specification. |
| `SEARCH_PATTERN` | `(\w+?)([<:>~!])(\p{Punct}?)(.*)(\p{Punct}?)` — tách một criterion thành `key` / `operation` / `prefix` / `value` / `suffix`. |
| `PUBLISHED_ONLY` | Specification `status = PUBLISHED`. Bắt buộc AND vào mọi nhánh của advance-search vì `/api/v1/products/**` là `permitAll()`. |
| `GTIN_PATTERN` | `\d{8}|\d{12}|\d{13}|\d{14}` — hình dạng barcode hợp lệ duy nhất của variant. |
| `record VariantOptionsValidation` | `colorsById` + `sizesById` đã tra cứu một lần, để bước ghi không query lại. |
| `record ProductValidation` | Gói toàn bộ kết quả validate của `createProduct`. |

Hai helper hạ tầng lặp lại ở nhiều flow:

- `getPageResponse(pageable, page)` — map `Page<Product>` → `List<ProductSummaryResponse>` rồi bọc vào
  `PageResponse` (`pageNo`, `pageSize`, `totalPage`, `items`). Lưu ý: `pageNo`/`pageSize` lấy từ
  `pageable` đầu vào chứ không lấy từ `Page`, và không có trường `totalItems`.
- `validatePrices(basePrice, salePrice)` — 3 luật: `basePrice >= 0`, `salePrice >= 0`,
  `salePrice <= basePrice`; vi phạm bất kỳ → `INVALID_PRICE`. Cả `null` đều được bỏ qua.

---

## 1. Nhóm đọc (public storefront)

### `getAllProducts(pageable)`
1. `productRepository.findAllByStatus(PUBLISHED, pageable)` — chỉ hàng đã publish.
2. `getPageResponse(...)`.

Không `@Transactional`, nên mapper chỉ được đọc những gì query đã fetch sẵn.

### `getAllProductsForBackoffice(pageable)`
`findAll(pageable)` — **không** lọc status, nên trả cả `DRAFT` và `ARCHIVED`. Đây là lý do method
này tách riêng: endpoint `/admin/products` chịu luật `ROLE_ADMIN` của `catalogFilterChain`.

### `getProductById(id)`
1. `findDetailProductById(id)` — query có fetch-join phần chi tiết (variants, images, attribute values, categories).
2. Không thấy → `PRODUCT_NOT_FOUND`.
3. `productMapper.toProductResponse(product)`.

Không lọc status: admin xem được cả sản phẩm chưa publish. (Endpoint `/api/v1/products/{id}` là
`permitAll()` — đọc theo id vẫn thấy hàng `DRAFT`, đây là hành vi hiện tại của code.)

### `getProductBySlug(slug)`
`findBySlugAndStatus(slug, PUBLISHED)` → không thấy → `PRODUCT_NOT_FOUND` → map. Có
`@Transactional(readOnly = true)` nên lazy collection còn mở trong lúc mapper chạy.

### `searchProducts(pageable, keyword)`
`findAllByNameContainingAndStatus(keyword, PUBLISHED, pageable)` — LIKE trên `name`, phân biệt
hoa/thường theo đúng ngữ nghĩa `Containing` của Spring Data.

### `getAllProductsByCategory(pageable, categoryId)`
1. `categoryRepository.findById(categoryId)` → không thấy → `CATEGORY_NOT_FOUND`.
2. Lấy `slug` của category vừa tìm rồi gọi `findAllByCategorySlugAndStatus(slug, PUBLISHED, pageable)`.

Bước 1 tồn tại chỉ để phân biệt "category không tồn tại" (404) với "category rỗng" (page rỗng);
truy vấn thật vẫn đi qua slug như hai method dưới.

### `getAllProductsByCategorySlug` / `getAllProductsByBrandSlug`
Một dòng: query theo slug + `PUBLISHED` rồi `getPageResponse`. Slug sai → trả page rỗng, không 404.

### `getProductVariants(productId)`
1. `existsById` → sai → `PRODUCT_NOT_FOUND` (kiểm tra tồn tại rẻ hơn load cả aggregate).
2. `productVariantRepository.findByProductId` → map từng `ProductVariantResponse`.

Trả **mọi** variant kể cả `active = false`.

### `getProductVariantSnapshot(variantId)` / `getProductVariantSnapshots(variantIds)`
Dùng cho cart/order lấy thông tin tối thiểu của biến thể mà không phải đọc DB catalog.

1. `findById` / `findAllById`.
2. `toVariantSnapshot(variant)` đọc thêm `variant.getProduct()` để lấy `productId` + `productName`,
   rồi build `ProductVariantSnapshotResponse`: sku, barcode, `sizeDisplay`, `colorDisplay`, colorHex,
   price, salePrice, active, optionSignature, displayName.

Khác biệt cần nhớ: bản một-id ném `PRODUCT_VARIANT_NOT_FOUND` khi thiếu; bản batch **im lặng bỏ qua**
id không tồn tại (`findAllById` chỉ trả cái tìm thấy), nên caller phải tự đối chiếu số lượng.

### `advanceSearchWithSpecifications(pageable, product[])`
Đầu vào là mảng chuỗi kiểu `name:áo*`, `price>200000`, `priceRange:100000-500000`.

1. Mảng `null`/rỗng → thoái lui về `findAllByStatus(PUBLISHED, pageable)`.
2. Với từng chuỗi:
   - `SEARCH_PATTERN.matcher(s)`; không khớp → `INVALID_SEARCH_CRITERIA`.
   - `group(1)` (field) phải nằm trong `SEARCHABLE_FIELDS`, nếu không → `INVALID_SEARCH_CRITERIA`.
   - `validateSearchValue(field, value)`: value rỗng → lỗi; `price`/`basePrice` phải parse được
     `BigDecimal`; `priceRange` chấp nhận dạng `min-max` (đúng 2 vế, `min <= max`) hoặc `500000+`.
     Mọi `NumberFormatException` quy về `INVALID_SEARCH_CRITERIA`.
   - `builder.with(field, operation, value, prefix, suffix)` — trong `ProductSpecificationsBuilder`,
     ký tự phép toán map sang `SearchOperation`; với `EQUALITY`, dấu `*` ở đầu/cuối value được
     chuyển thành `ENDS_WITH` / `STARTS_WITH` / `CONTAINS`.
3. `builder.build()` trả `null` khi không criterion nào sống sót → dùng `PUBLISHED_ONLY` một mình;
   ngược lại `criteria.and(PUBLISHED_ONLY)`. **Bộ lọc hiển thị không bao giờ bị bỏ qua.**
4. `productRepository.findAll(spec, pageable)` → `getPageResponse`.

---

## 2. `createProduct(request)` — `@Transactional`

```
validateProduct → buildProduct → brand → sizeChart → categories → images
   → variants → attributes → (publish?) → save → seedInventory → map
```

**Bước 1 — `validateProduct(request)`** gom mọi kiểm tra vào một chỗ và trả về `ProductValidation`
để bước ghi không phải query lại:

1. `slug = StringUtils.normalizeSlug(request.slug, request.name)` — thiếu slug thì sinh từ tên
   (`SlugUtils.makeSlug`). `existsBySlug` → trùng → `SLUG_ALREADY_EXISTED_OR_DUPLICATED`.
2. `basePrice = request.basePrice ?: request.price` (tương thích payload FE cũ) → `validatePrices`.
3. `validateRequiredCategoryIds(categoryIds)` — `findAllById` rồi so **cả rỗng lẫn số lượng**:
   danh sách rỗng hoặc có id không tồn tại → `CATEGORY_NOT_FOUND`. Nghĩa là tạo sản phẩm **bắt buộc**
   ít nhất một category.
4. `validateVariantRequestDuplicates(variants)` — mỗi request phải có `colorOptionId` + `sizeOptionId`
   (sau `cleanText`), nếu thiếu → `PRODUCT_OPTION_INVALID`; SKU trùng nhau **trong cùng payload** →
   `SKU_ALREADY_EXISTED_OR_DUPLICATED`.
5. `validateVariantOptions(variants, null)` — xem mục 5 bên dưới; `product = null` nghĩa là không
   có variant nào "của chính mình" được miễn trừ khi kiểm tra unique.
6. `validateAttributes(attributes)` — xem mục 7.
7. `publishImmediately = status == PUBLISHED || published == TRUE`.

**Bước 2 — `buildProduct(request)`**: dựng entity mới, luôn khởi tạo `status = DRAFT`,
`published = false` (publish là bước riêng, có luật riêng). `slug`/`basePrice` set ở đây là bản thô,
ngay sau đó bị ghi đè bằng giá trị đã chuẩn hoá trong `validation`.

**Bước 3 — quan hệ ngoài**
- `validateBrand(brandId)`: null/blank → `null` (brand là tuỳ chọn); id sai → `BRAND_NOT_FOUND`.
- `resolveSizeChartId(sizeChartId)`: null/blank → `null`; `existsById` sai → `SIZE_CHART_NOT_FOUND`.
  Chỉ lưu id, không map quan hệ.
- `assignCategories`: `clear()` rồi thêm lại `ProductCategory` cho từng category (bảng nối).

**Bước 4 — `assignImages(product, images)`**
1. `images == null` → **không đụng tới ảnh hiện có** (khác với list rỗng = xoá hết).
2. Vòng kiểm tra trước: mỗi `mediaId` phải tồn tại **và đang `ACTIVE`**
   (`existsByIdAndStatus`), sai → `MEDIA_FILE_NOT_FOUND`. Row `PENDING` là file mới cấp
   presigned URL nhưng chưa upload xong, gán vào sản phẩm sẽ ra ảnh hỏng. Kiểm hết rồi mới
   ghi, tránh sửa nửa vời.
3. `images.clear()` rồi thêm lại theo thứ tự payload; `sortOrder` mặc định là chỉ số `i`;
   `isPrimary` = cờ FE gửi **hoặc** phần tử đầu tiên — nên luôn có ảnh chính.

**Bước 5 — `synchronizeVariants(product, variants, validation.variantOptions())`** (mục 5).

**Bước 6 — `assignAttributes(product, attributes, validation.attributesById())`** (mục 7).

**Bước 7 — publish nếu được yêu cầu**: `publish(product)` chạy `validateProductBeforePublish` **trước
khi** save, nên sản phẩm không hợp lệ thì cả transaction rollback, không để lại bản DRAFT nửa vời.

**Bước 8 — `save` + `seedInventoryForVariants(savedProduct)`**: sau khi save, mỗi variant đã có id;
với từng variant gọi `inventoryService.ensureStock(variantId, productId)` để tạo dòng tồn kho
(`quantity = 0`, `reservedQuantity = 0`) nếu chưa có. Variant còn `id == null` bị bỏ qua.

**Bước 9** — `productMapper.toProductResponse(savedProduct)`.

---

## 3. `updateProduct(productId, request)` — `@Transactional`

1. `findDetailProductById` → `PRODUCT_NOT_FOUND`.
2. `validateUpdateProductRequest(request, product)`:
   - `name` bắt buộc (`PRODUCT_OPTION_INVALID`).
   - Nếu có `variants`: chạy lại `validateVariantRequestDuplicates`, và mọi `variant.id` gửi lên phải
     thuộc **chính sản phẩm này** — không thì `PRODUCT_VARIANT_NOT_FOUND` (chặn sửa chéo sản phẩm).
3. Slug: chuẩn hoá lại; nếu sản phẩm đang `PUBLISHED` mà slug đổi → `CANNOT_CHANGE_SLUG_WHEN_PUBLISHED`
   (giữ URL công khai ổn định). Slug mới trùng sản phẩm khác → `PRODUCT_ALREADY_EXIST`.
4. `validateBrand` (được phép bỏ brand bằng cách gửi null).
5. `validateRequiredCategoryIds(request.getCategoryIds())` — như create, vẫn bắt buộc ≥ 1 category.
   Gửi `null` sẽ NPE ở `findAllById`, nên FE phải luôn gửi danh sách đầy đủ.
6. `basePrice = basePrice ?: price` → `validatePrices`.
7. Set các trường scalar: name, slug, mô tả ngắn/dài, `featured`, gender, productType, basePrice,
   salePrice, sizeChartId (validate lại), meta SEO, brand. **Không** đổi `status` ở đây.
8. `variants != null` → `synchronizeVariants(product, variants)` (bản 2 tham số tự validate).
   Gửi `null` = giữ nguyên variant; gửi danh sách thiếu = các variant vắng mặt bị **tắt** (`active=false`),
   không xoá.
9. `assignCategories` → `assignImages` → `assignAttributes`.
10. `save` → `seedInventoryForVariants` (variant mới thêm cũng có ngay dòng tồn kho) → map.

---

## 4. `updateProductVariants` / `updateProductAttributes`

Hai endpoint hẹp cho backoffice, cùng một khuôn:

- `updateProductVariants(productId, request)`: load detail → chuẩn hoá `request.variants` về `List.of()`
  khi null (nghĩa là **tắt toàn bộ** variant hiện có, không phải giữ nguyên — khác với `updateProduct`)
  → `synchronizeVariants` → save → `seedInventoryForVariants` → map.
- `updateProductAttributes(productId, request)`: load detail → chuẩn hoá về `List.of()` (xoá sạch
  attribute values) → `assignAttributes` → save → map. Không đụng tồn kho.

---

## 5. Đồng bộ variant

### `validateVariantOptions(variantRequests, product)`

`product == null` khi tạo mới; khi sửa, `ownVariantIds` là tập id variant của chính sản phẩm để loại
khỏi kiểm tra trùng.

Vòng 1 — theo từng request:
1. **SKU**: `findBySku(sku)`; nếu có chủ sở hữu và chủ đó không thuộc `ownVariantIds` →
   `SKU_ALREADY_EXISTED_OR_DUPLICATED`. Tra theo repository chứ không chỉ trong danh sách active,
   vì unique index `uk_product_variant_sku` áp cho cả dòng đã tắt.
2. **Barcode** — đối xứng SKU, thêm hai luật: phải khớp `GTIN_PATTERN` (`BARCODE_INVALID`) và không
   được lặp trong cùng payload (`barcodesInRequest`, `BARCODE_ALREADY_EXISTED_OR_DUPLICATED`).
3. `validatePrices(request.price, request.salePrice)`.
4. **Media**: lấy `thumbnailMediaId`, fallback `mediaId`; tồn tại thì phải có trong media
   library và đang `ACTIVE`, sai → `MEDIA_FILE_NOT_FOUND`.

Vòng 2 — tra option theo lô:
5. Gom `colorOptionId` / `sizeOptionId` distinct rồi `findAllById` **một lần** cho mỗi loại (tránh
   N+1). Số lượng trả về khác số id yêu cầu → `OPTION_NOT_FOUND`.

Vòng 3 — chống trùng tổ hợp:
6. Với từng request dựng `buildOptionSignature` = `"COLOR:<colorId>|SIZE:<sizeId>"`. Trùng trong cùng
   payload → `PRODUCT_VARIANT_ALREADY_EXIST`. Phải chặn ở đây vì `synchronizeVariants` chụp
   `existingBySignature` một lần trước vòng lặp, hai request cùng signature sẽ cùng được coi là mới và
   `uk_product_variant_product_signature` chỉ nổ lúc flush.

Trả về `VariantOptionsValidation(colorsById, sizesById)` để bước ghi dùng lại.

### `synchronizeVariants(product, variantRequests, validation)`

1. Chụp hai index từ trạng thái hiện tại: `existingById` và `existingBySignature` (trùng signature thì
   giữ bản đầu tiên).
2. `requestedVariants` là `IdentityHashMap`-set — so sánh theo tham chiếu, đúng cả khi variant mới
   chưa có id.
3. Với mỗi request:
   - Lấy `ColorOption`/`SizeOption` từ map đã validate, dựng `signature`.
   - **Chọn variant đích**: có `id` → tra `existingById`; không có `id` → tra theo `signature`. Nhờ vậy
     FE gửi lại cùng tổ hợp màu/size mà không kèm id vẫn cập nhật đúng dòng cũ thay vì tạo trùng.
   - Không tìm thấy → tạo `ProductVariant` mới với `active = false` và gắn vào `product.getVariants()`.
   - Ghi đè toàn bộ trường: quan hệ option, `color`/`size`/`colorHex` denormalize, `optionSignature`,
     `displayName` = `"<màu> / <size>"`, sku, barcode, giá (`price` null → lấy `product.basePrice`),
     salePrice, thumbnail (`firstNonBlank(thumbnailMediaId, mediaId)`), `thumbnailUrl`.
   - `active`: chỉ set khi request nói rõ **hoặc** đây là variant mới — bỏ trống `active` khi sửa thì
     giữ nguyên trạng thái đang có.
4. Mọi variant hiện có **không** xuất hiện trong payload bị `setActive(false)`. Đây là soft-disable:
   không xoá dòng nào, nên đơn hàng cũ và tồn kho vẫn tham chiếu được.

### `seedInventoryForVariants(product)`
Duyệt variant, bỏ qua cái chưa có id, gọi `inventoryService.ensureStock(variantId, productId)`.
Idempotent — gọi lại sau mỗi lần update là an toàn.

---

## 6. Vòng đời sản phẩm

### `publish(product)` (private, dùng chung)
1. `validateProductBeforePublish` gom **tất cả** lỗi vào `List<String>`; nếu không rỗng, ném
   `PRODUCT_PUBLISH_INVALID` kèm lỗi đầu tiên (`errors.getFirst()`).
2. Luật kiểm tra:
   - `basePrice > 0`.
   - Có ≥ 1 category.
   - Có ≥ 1 variant `active`.
   - Với **mỗi variant active**: SKU bắt buộc và không trùng trong sản phẩm; giá hợp lệ
     (`validateVariantPrice`: `price != null`, `price >= 0`, `salePrice >= 0`, `salePrice <= price`);
     `optionSignature` bắt buộc và không trùng.
   - Variant đã tắt hoàn toàn bị bỏ qua — sản phẩm publish được dù còn variant lỗi nhưng đã tắt.
3. Đặt `status = PUBLISHED`, `published = true`, `publishedAt = now`, `deletedAt = null`.

### `publishProduct(productId)`
Load detail → đang `PUBLISHED` → `PRODUCT_PUBLISH_INVALID` ("already published"); đang `ARCHIVED` →
`PRODUCT_PUBLISH_INVALID` ("Archived products cannot be published", muốn bán lại phải mở lại trạng thái
trước) → `publish` → save → map.

### `unpublishProduct(productId)`
Không ở trạng thái `PUBLISHED` → `PRODUCT_NOT_PUBLISHED`. Ngược lại về `DRAFT`, `published = false`,
`publishedAt = null`.

### `archiveProduct(productId)` và `deleteProduct(productId)`
Cùng một hành vi: đã `ARCHIVED` → `PRODUCT_PUBLISH_INVALID`; nếu chưa thì `status = ARCHIVED`,
`published = false`, `deletedAt = now`. **Không có xoá cứng** — dữ liệu vẫn cần cho đơn hàng cũ.
Khác biệt duy nhất: `deleteProduct` load bằng `findById` (không fetch chi tiết) và trả `void`,
`archiveProduct` load bằng `findDetailProductById` và trả `ProductResponse`.

---

## 7. Thuộc tính sản phẩm

### `validateAttributes(requests)`
1. `null` → `List.of()`.
2. Mỗi phần tử phải có `attributeId` và `value` sau `cleanText` → thiếu → `PRODUCT_OPTION_INVALID`.
3. Gom `attributeId` distinct → `findAllById` một lần; số lượng lệch → `PRODUCT_ATTRIBUTE_NOT_FOUND`.
4. Trả `Map<String, ProductAttribute>` cho bước ghi.

### `assignAttributes(product, requests, attributesById)`
`requests == null` → không đụng gì. Ngược lại `attributeValues.clear()` rồi dựng lại toàn bộ
`ProductAttributeValue` với `value` đã trim và `normalizedValue = StringUtils.normalizeCode(value)`
(trim → thay khoảng trắng bằng `_` → in hoa) để lọc/so khớp không phụ thuộc cách gõ.

Đây là **replace-all**, không phải merge: payload thiếu một attribute = xoá attribute đó.

---

## 8. Quy ước lặp lại trong file

| Quy ước | Ý nghĩa |
|---|---|
| `null` vs list rỗng | `null` = "không đụng tới" (images, attributes, variants trong `updateProduct`); rỗng = "xoá / tắt hết". Ngoại lệ: `updateProductVariants` và `updateProductAttributes` quy `null` về rỗng. |
| Validate hết rồi mới ghi | Mọi hàm `validate*` chạy trọn vẹn trước khi chạm entity, để `@Transactional` không phải dựa vào rollback cho lỗi nghiệp vụ đoán trước được. |
| Tra cứu theo lô | `findAllById` cho category / option / attribute, so số lượng để phát hiện id sai — thay cho vòng lặp `findById`. |
| Không xoá cứng | Variant vắng mặt → `active=false`; sản phẩm xoá → `ARCHIVED` + `deletedAt`. |
| Denormalize có chủ đích | `color`, `size`, `colorHex`, `displayName`, `optionSignature` chép vào variant để đọc và so trùng không phải join sang bảng option. |

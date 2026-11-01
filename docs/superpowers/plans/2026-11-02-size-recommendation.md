# Size Recommendation (Gợi ý size) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Endpoint public trong catalog-service gợi ý size cho một sản phẩm từ chiều cao/cân nặng hoặc số đo cơ thể, kèm ưu tiên form và độ vừa từng vùng.

**Architecture:** Thuật toán thuần (`SizeFitCalculator`) trong `product/service/impl`, không đụng DB. `SizeRecommendationServiceImpl` nạp product (đã publish) + size chart, lọc row theo size của variant đang bán, đổi đơn vị, rồi gọi calculator. Controller riêng, `GET` + query param để rơi vào matcher `GET /api/v1/products/**` permitAll sẵn có ở cả gateway lẫn catalog — không sửa security, không migration.

**Tech Stack:** Java 21, Spring Boot 3.5, JUnit 5 + Mockito + AssertJ, MockMvc standalone.

**Spec:** mục Spec ngay dưới (thiết kế đã chốt trong hội thoại 2026-11-02: hướng C + D).

## Spec

- Input (đều tùy chọn, đơn vị cm/kg): `heightCm`, `weightKg`, `chestCm`, `waistCm`, `hipCm`, `shoulderCm`, `lengthCm`, `inseamCm`, `preference` ∈ `SNUG | REGULAR | LOOSE` (mặc định `REGULAR`). Không có giá trị nào → 400.
- `size_chart_row` lưu **số đo cơ thể** theo size (Javadoc `SizeChartRow`). Chỉ xét row có `sizeCode` khớp (trim + uppercase) với `size` của variant `active` của sản phẩm.
- Chế độ: có số đo trùng vùng có trọng số với chart → `BODY_MEASUREMENTS`; nếu không, có chiều cao/cân nặng và chart có range → `HEIGHT_WEIGHT`; còn lại → `status = NOT_ENOUGH_DATA` (HTTP 200, các field khác null, `regions = []`).
- Output: size gợi ý, size phương án 2 (có thể null), size gợi ý còn hàng hay không, basis, confidence, và với `BODY_MEASUREMENTS` là trạng thái từng vùng (`TIGHT | SNUG | GOOD | LOOSE`) + chênh lệch cm để FE tô heatmap.
- Không lưu số đo, không log số đo.

## Global Constraints

- Không migration, không sửa `SecurityConfig` (gateway lẫn catalog), không module mới.
- Code trong feature `product` của catalog-service; cặp `service/SizeRecommendationService` + `service/impl/SizeRecommendationServiceImpl`.
- DTO mới là `record`; enum lồng trong record dùng nó.
- Constructor injection `@RequiredArgsConstructor`; không magic number — hằng số có tên trong `SizeFitCalculator`.
- Agent không commit: cuối plan đề xuất commit message.
- Chạy Maven: `export JAVA_HOME="C:/Program Files/Java/jdk-21"` trước mọi lệnh `./mvnw`.

## Thuật toán (giá trị chốt)

Ký hiệu: `d = rowCm − userCm` (dương = size lớn hơn người). `offset`: `SNUG −2`, `REGULAR 0`, `LOOSE +4`. `d' = d − offset`.

| `d'` | fit |
|---|---|
| `< −4` | `TIGHT` |
| `−4 ≤ d' < −1` | `SNUG` |
| `−1 ≤ d' ≤ 4` | `GOOD` |
| `> 4` | `LOOSE` |

- Phạt vùng: `d' < 0 ? 1.5 × |d'| : d'` (chật tệ hơn rộng). Điểm row = Σ trọng số × phạt, chỉ trên vùng có cả ở row và request. Row không có vùng nào trùng bị bỏ.
- Trọng số theo `ProductType`:
  - `TOP` (`T_SHIRT, SHIRT, POLO, HOODIE, SWEATER, JACKET`): CHEST 3, SHOULDER 2, WAIST 1, LENGTH 1.
  - `BOTTOM` (`JEANS, PANTS, SHORTS, SKIRT`): WAIST 3, HIP 2, INSEAM 1, LENGTH 1.
  - `FULL` (còn lại và `null`): CHEST 2, WAIST 2, HIP 2, SHOULDER 1, LENGTH 1, INSEAM 1.
- Chọn điểm thấp nhất; hòa → `SNUG` lấy size nhỏ hơn, `REGULAR`/`LOOSE` lấy size lớn hơn. Phương án 2 = điểm thấp nhì nếu `≤ best + 6`, ngược lại null. Confidence `HIGH` khi ≥ 2 vùng được so và không vùng nào `TIGHT`, ngược lại `MEDIUM`.
- `HEIGHT_WEIGHT`: khoảng hở ngoài range `gap = max(0, min − v, v − max)`; điểm = `gapHeight / 5 + gapWeight / 3`; row không có range nào bị bỏ, row chỉ có một range dùng range đó. Hòa xử như trên; `LOOSE` lên thêm một size nếu có (khi đó phương án 2 = size trước khi lên). Phương án 2 (khi không `LOOSE`) = điểm thấp nhì nếu `≤ best + 1`. Confidence `MEDIUM` khi điểm best = 0, ngược lại `LOW`. `regions = []`.
- Thứ tự size: thang `XXS, XS, S, M, L, XL, XXL, XXXL` (`2XL` = `XXL`, `3XL` = `XXXL`), rồi size số theo giá trị, rồi theo chuỗi.
- Đơn vị chart: `unit` là `in`/`inch` (không phân biệt hoa thường) → nhân số đo và chiều cao với `2.54`; còn lại coi là cm. Cân nặng luôn kg.
- `differenceCm` trả về là `d` (chưa trừ offset), làm tròn 1 chữ số thập phân `HALF_UP`.

## Review Focus

1. Admin nhập `size_code` lệch chữ hoa/khoảng trắng (`" m "` vs `"M"`) → vẫn khớp — test ở Task 3.
2. Chart có row cho size sản phẩm không bán (XL) → không bao giờ gợi ý XL — test ở Task 3.
3. Chart theo inch → không gợi ý lệch 2,5 lần — test ở Task 3.
4. Áo (TOP) mà khách chỉ nhập vòng mông → không dùng mông, rơi về chiều cao/cân nặng hoặc `NOT_ENOUGH_DATA` — test ở Task 2.
5. Khách ngoài mọi range (rất cao/nặng) → vẫn trả size gần nhất với `LOW`, không lỗi — test ở Task 2.

## File Structure

| file | trách nhiệm |
|---|---|
| Create `product/dto/SizeRecommendationRequest.java` | record input + Bean Validation + enum `Preference` |
| Create `product/dto/SizeRecommendationResponse.java` | record output + enum `Status`, `Basis`, `Confidence` + factory `notEnoughData()` |
| Create `product/dto/RegionFitResponse.java` | record `(Region region, Fit fit, BigDecimal differenceCm)` + enum `Region`, `Fit` |
| Create `product/service/impl/SizeFitCalculator.java` | thuật toán thuần |
| Create `product/service/SizeRecommendationService.java` | interface |
| Create `product/service/impl/SizeRecommendationServiceImpl.java` | nạp dữ liệu, lọc, đơn vị, availability |
| Create `product/controller/SizeRecommendationController.java` | endpoint |
| Modify `product/exception/ProductErrorCode.java` | thêm `SIZE_RECOMMENDATION_INPUT_REQUIRED(1044, …)` |
| Modify `docs/api-requests.md`, `docs/services/catalog-service.md` | hợp đồng API cho FE |

Prefix đường dẫn: `services/catalog-service/src/main/java/com/fashionstore/catalog/`; test cùng package dưới `src/test/java/...`.

---

### Task 1: DTO + calculator chế độ số đo cơ thể

**Files:**
- Create: 3 DTO ở bảng trên, `product/service/impl/SizeFitCalculator.java`
- Test: `product/service/impl/SizeFitCalculatorTest.java`

**Interfaces:**
- Produces:
  - `record SizeRecommendationRequest(BigDecimal heightCm, BigDecimal weightKg, BigDecimal chestCm, BigDecimal waistCm, BigDecimal hipCm, BigDecimal shoulderCm, BigDecimal lengthCm, BigDecimal inseamCm, Preference preference)` với `enum Preference { SNUG, REGULAR, LOOSE }` và `Preference effectivePreference()` (null → `REGULAR`), `boolean hasAnyValue()`. Validation: `@DecimalMin/@DecimalMax` height 100–230, weight 25–250, chest 50–180, waist 40–180, hip 50–180, shoulder 25–70, length 40–130, inseam 40–110.
  - `record SizeRecommendationResponse(Status status, String recommendedSize, String alternativeSize, Boolean recommendedSizeAvailable, Basis basis, Confidence confidence, List<RegionFitResponse> regions)`; `enum Status { RECOMMENDED, NOT_ENOUGH_DATA }`, `enum Basis { BODY_MEASUREMENTS, HEIGHT_WEIGHT }`, `enum Confidence { HIGH, MEDIUM, LOW }`; `static SizeRecommendationResponse notEnoughData()`.
  - `record RegionFitResponse(Region region, Fit fit, BigDecimal differenceCm)`; `enum Region { CHEST, WAIST, HIP, SHOULDER, LENGTH, INSEAM }`, `enum Fit { TIGHT, SNUG, GOOD, LOOSE }`.
  - `final class SizeFitCalculator` (package-private), method `SizeRecommendationResponse recommend(List<SizeChartRow> rows, BigDecimal unitToCm, ProductType productType, Set<String> availableSizeCodes, SizeRecommendationRequest request)`. `rows` đã được lọc; `availableSizeCodes` đã normalize (trim + uppercase); `recommendedSizeAvailable = availableSizeCodes.contains(normalize(recommendedSize))`. `regions` theo thứ tự enum `Region`.

Fixture dùng chung (chart TOP, cm, `ProductType.T_SHIRT`, `unitToCm = 1`, `available = {S, M, L, XL}`), build bằng `SizeChartRow.builder()`:

| size | chest | shoulder | waist | height | weight |
|---|---|---|---|---|---|
| S | 88 | 42 | 76 | 160–168 | 50–58 |
| M | 94 | 44 | 82 | 167–174 | 57–65 |
| L | 100 | 46 | 88 | 173–180 | 64–73 |
| XL | 106 | 48 | 94 | 179–186 | 72–82 |

- [ ] **Step 1: Write the failing tests** trong `SizeFitCalculatorTest`

```java
@Test void measurementsWithinToleranceRecommendMWithHighConfidence()
// chest 95, shoulder 44, REGULAR
// → RECOMMENDED, "M", alternative null, BODY_MEASUREMENTS, HIGH, available true
// → regions == [CHEST GOOD -1.0, SHOULDER GOOD 0.0]

@Test void closeSecondBestBecomesAlternative()
// chest 97, shoulder 45, REGULAR → "L", alternative "M"
// (L = 3·3 + 2·1 = 11; M = 3·4.5 + 2·1.5 = 16.5 ≤ 17)

@Test void snugPreferenceShiftsTowardSmallerSizeButReportsRawDifference()
// chest 97, shoulder 45, SNUG → "M"; region CHEST fit GOOD, differenceCm -3.0

@Test void bodyLargerThanEverySizeStillRecommendsLargestAsTight()
// chest 115, REGULAR → "XL", MEDIUM, regions == [CHEST TIGHT -9.0]

@Test void equalScoresGoToTheLargerSizeForRegular()
// fixture riêng: S chest 90, M chest 95; user chest 92, REGULAR
// S d=-2 → 3·1.5·2 = 9 ; M d=+3 → 3·3 = 9 → hòa → "M"
```

- [ ] **Step 2: Run to verify fail**

Run: `./mvnw -o -pl services/catalog-service test -Dtest=SizeFitCalculatorTest`
Expected: FAIL biên dịch (chưa có class).

- [ ] **Step 3: Implement** DTO + `SizeFitCalculator` theo bảng **Thuật toán**. Hằng số có tên (`TIGHT_BELOW = -4`, `GOOD_FROM = -1`, `GOOD_UP_TO = 4`, `TIGHT_PENALTY_FACTOR = 1.5`, `ALTERNATIVE_MARGIN_MEASUREMENT = 6`, offset theo `Preference`). Tính bằng `BigDecimal` cho output, `double` nội bộ cho điểm là được. Tách hàm: chọn nhóm trọng số, tính `d` từng vùng, chấm điểm row, chọn best/alternative, sắp thứ tự size (dùng chung với Task 2).

- [ ] **Step 4: Run to verify pass** — cùng lệnh, Expected: PASS.

---

### Task 2: Calculator chế độ chiều cao/cân nặng + chọn chế độ

**Files:**
- Modify: `product/service/impl/SizeFitCalculator.java`
- Test: `product/service/impl/SizeFitCalculatorTest.java`

**Interfaces:**
- Consumes: Task 1 (cùng method `recommend`).
- Produces: không đổi signature.

- [ ] **Step 1: Write the failing tests** (fixture Task 1)

```java
@Test void heightWeightInsideOneRangeRecommendsItWithMediumConfidence()
// h 170, w 60 → "M", HEIGHT_WEIGHT, MEDIUM, alternative null, regions == []

@Test void heightWeightOnTwoRangesPicksBySizePreference()
// h 173.5, w 64.5 → REGULAR: "L" alt "M" ; SNUG: "M" alt "L"

@Test void loosePreferenceMovesUpOneSize()
// h 170, w 60, LOOSE → "L", alternative "M"

@Test void outsideEveryRangeRecommendsNearestWithLowConfidence()
// h 195, w 95 → "XL", LOW

@Test void measurementsTakePrecedenceOverHeightWeight()
// h 170, w 60, chest 101 → BODY_MEASUREMENTS, "L"

@Test void topIgnoresHipAndWithoutHeightWeightReportsNotEnoughData()
// T_SHIRT, chỉ hipCm 95 → NOT_ENOUGH_DATA, regions == []

@Test void rowsWithoutRangesCannotServeHeightWeight()
// fixture bỏ hết height/weight range, h 170 w 60 → NOT_ENOUGH_DATA
```

- [ ] **Step 2: Run to verify fail** — `-Dtest=SizeFitCalculatorTest`, Expected: các test mới FAIL.
- [ ] **Step 3: Implement** nhánh `HEIGHT_WEIGHT` và chọn chế độ theo Spec (hằng số `HEIGHT_GAP_SCALE_CM = 5`, `WEIGHT_GAP_SCALE_KG = 3`, `ALTERNATIVE_MARGIN_HEIGHT_WEIGHT = 1`).
- [ ] **Step 4: Run to verify pass** — Expected: toàn bộ `SizeFitCalculatorTest` PASS.

---

### Task 3: Service nạp dữ liệu

**Files:**
- Create: `product/service/SizeRecommendationService.java`, `product/service/impl/SizeRecommendationServiceImpl.java`
- Modify: `product/exception/ProductErrorCode.java` — `SIZE_RECOMMENDATION_INPUT_REQUIRED(1044, "Provide height, weight or at least one body measurement", HttpStatus.BAD_REQUEST)`
- Test: `product/service/impl/SizeRecommendationServiceImplTest.java` (Mockito, như `SizeChartServiceTest`)

**Interfaces:**
- Consumes: `ProductService.getProductById(String)` (chỉ product `PUBLISHED`, ném `PRODUCT_NOT_FOUND`, đã set `ProductVariantResponse.available`), `SizeChartRepository.findDetailById(String)`, `SizeFitCalculator.recommend(...)`.
- Produces: `SizeRecommendationResponse recommend(String productId, SizeRecommendationRequest request)` (`@Transactional(readOnly = true)`).

Luồng: `!request.hasAnyValue()` → `AppException(SIZE_RECOMMENDATION_INPUT_REQUIRED)`; `sizeChartId` null/blank hoặc chart không tồn tại → `notEnoughData()`; sizes bán = `size` của variant `active` (normalize); available = sizes của variant `available == TRUE`; row lọc theo sizes bán; không còn row → `notEnoughData()`; `unitToCm` theo Spec; gọi calculator với `product.getProductType()`. Calculator tạo bằng `new SizeFitCalculator()` trong field (không phải bean).

- [ ] **Step 1: Write the failing tests**

```java
@Test void emptyRequestIsRejected()
// assertThatThrownBy(...).isInstanceOf(AppException.class) với errorCode SIZE_RECOMMENDATION_INPUT_REQUIRED

@Test void productWithoutSizeChartReportsNotEnoughData()
@Test void missingSizeChartReportsNotEnoughData()   // findDetailById → Optional.empty()

@Test void onlySizesTheProductSellsAreRecommended()
// chart fixture S–XL, variant active chỉ M, L; h 162 w 52 → "M" (S bị loại)

@Test void sizeCodeMatchingIgnoresCaseAndWhitespace()
// row sizeCode " m ", variant size "M", h 170 w 60 → "m" khớp → status RECOMMENDED

@Test void soldOutRecommendedSizeIsFlagged()
// variant M active nhưng available false → recommendedSizeAvailable false

@Test void inchChartIsConvertedToCentimetres()
// chart unit "inch", row M chest 37, user chest 94 → region CHEST differenceCm 0.0
```

- [ ] **Step 2: Run to verify fail** — `-Dtest=SizeRecommendationServiceImplTest`, Expected: FAIL biên dịch.
- [ ] **Step 3: Implement** interface + impl + error code theo luồng trên.
- [ ] **Step 4: Run to verify pass** — Expected: PASS.

---

### Task 4: Controller + tài liệu API

**Files:**
- Create: `product/controller/SizeRecommendationController.java`
- Test: `product/controller/SizeRecommendationControllerTest.java` (MockMvc standalone như `ProductControllerTest`, nhớ `setValidator` / `LocalValidatorFactoryBean` nếu standalone không tự validate; `GlobalExceptionHandler` làm controller advice)
- Modify: `docs/api-requests.md` (mục **Done**), `docs/services/catalog-service.md` dòng 20 (thêm `/api/v1/products/{id}/size-recommendation`)

**Interfaces:**
- Consumes: `SizeRecommendationService.recommend(String, SizeRecommendationRequest)`.
- Produces: `GET /api/v1/products/{id}/size-recommendation` với query param trùng tên field request (`heightCm`, `weightKg`, `chestCm`, `waistCm`, `hipCm`, `shoulderCm`, `lengthCm`, `inseamCm`, `preference`), bind bằng `@Valid @ModelAttribute`, trả `ApiResponse<SizeRecommendationResponse>` message `"Get size recommendation successfully"`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void queryParametersAreBoundIntoTheRequest()
// GET ...?heightCm=170&weightKg=60&preference=SNUG → 200; ArgumentCaptor: heightCm 170, weightKg 60, SNUG

@Test void outOfRangeHeightIsBadRequest()
// heightCm=300 → 400, service không được gọi
```

- [ ] **Step 2: Run to verify fail** — `-Dtest=SizeRecommendationControllerTest`, Expected: FAIL biên dịch.
- [ ] **Step 3: Implement** controller (khuôn `SizeChartController`). Thêm entry **Done** vào `docs/api-requests.md` theo mẫu file: endpoint, param, response, ý nghĩa `fit` → màu (`GOOD` xanh, `SNUG` vàng, `TIGHT` đỏ, `LOOSE` xám), `NOT_ENOUGH_DATA` thì ẩn widget, gợi ý FE: form tối thiểu chiều cao + cân nặng, "Chính xác hơn ▸" mở số đo, nhớ số đo trong phiên.
- [ ] **Step 4: Run to verify pass** — Expected: PASS.
- [ ] **Step 5: Full module test** — `./mvnw -o -pl services/catalog-service clean test`, Expected: BUILD SUCCESS (kể cả `ModularityTest`, không thêm dòng vào `modulith-known-violations.txt`).
- [ ] **Step 6: Review** `git diff` của chính mình, rồi đề xuất commit message:

```text
feat(catalog): add size recommendation from body measurements or height/weight
```

## Ngoài phạm vi (cố ý bỏ)

- Lưu số đo (`body_profile`), hiệu chỉnh từ dữ liệu trả hàng (mục E), so sánh với áo ở nhà (mục F), cache Redis — làm sau khi có nhu cầu.
- Gắn nhãn size vào try-on — thuộc plan try-on.

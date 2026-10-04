# Dữ liệu sản phẩm test

Đã nhập vào database `catalog_database` local ngày 2026-09-27.

- 30 sản phẩm PUBLISHED, slug `test-product-01` đến `test-product-30`.
- 6 danh mục, 1 thương hiệu Fashion Test.
- 240 biến thể: 2 màu có sẵn × size S/M/L/XL × 30 sản phẩm.
- Giá 179.000–899.000 VND; 10 sản phẩm giảm 15%.
- Sản phẩm 01–28: mỗi biến thể có 20–45 sản phẩm tồn kho.
- Sản phẩm 29: mỗi biến thể tồn 3; sản phẩm 30: hết hàng.
- Tổng tồn kho ban đầu: 7.304, không có lượng giữ hàng.
- Dữ liệu chưa có ảnh. Storefront đang dùng API backend qua BFF tại `http://localhost:8083`; sản phẩm chưa có ảnh hiển thị ô “Chưa có ảnh”. Các trang cũ dùng mảng PRODUCTS không nằm trong router hiện tại.

## Nhập lại

Chạy tại thư mục gốc dự án trong PowerShell:

```powershell
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Get-Content ./scripts/seed-test-products.sql -Encoding UTF8 -Raw | docker exec -i fashion-store-catalog-postgres-1 psql -U root -d catalog_database -v ON_ERROR_STOP=1
```

Script chạy trong transaction và bỏ qua bản ghi đã tồn tại; không reset tồn kho khi chạy lại. Chỉ dùng trên database test local. Cần hai màu active có sẵn; lần nhập đầu dùng Den và Trang.

## Xác minh

`GET http://localhost:8080/api/v1/products?size=50` trả code 1000 và 30 sản phẩm.

Database có 240 biến thể và 240 bản ghi tồn kho: 232 ACTIVE, 8 OUT_OF_STOCK.

Đã sửa `MultipleBagFetchException` bằng entity graph chỉ fetch một collection và map chi tiết trong transaction. Migration V46 bổ sung các cột chi tiết còn thiếu và bảng product_attribute_option; entity option được ánh xạ đúng bảng.

Phân trang `size=12` được tách khỏi size quần áo: API advanced search dùng `sizeFilter=M` hoặc `search=size:M` để lọc size. Storefront đã dùng `search=size:M`.

Đã xác minh 30 API chi tiết qua BFF, mỗi sản phẩm trả 8 biến thể; API theo slug thành công. Thêm 2 biến thể sản phẩm 03 vào giỏ khách thành công với đơn giá sale 211.650 VND, sau đó đã xóa giỏ kiểm thử.

API mà storefront dùng (`/api/v1/products/advance-search`) trả 12 sản phẩm trang đầu, tổng 3 trang, 6 sản phẩm trang cuối. Lọc nữ + danh mục đầm/chân váy trả 5 sản phẩm; tìm hoodie trả 1; lọc size M hoạt động. Giỏ từ chối sản phẩm hết hàng và số lượng 4 khi tồn 3 với HTTP 400; giỏ kiểm thử đã được xóa.

137 test catalog và build frontend thành công; hai regression test mới kiểm tra việc tách size phân trang và size quần áo đã pass. Kết nối Browser trong phiên này gặp lỗi công cụ, nên kiểm tra bằng HTTP qua BFF, chưa kiểm tra trực quan bằng trình duyệt. Chưa xác minh checkout/thanh toán.

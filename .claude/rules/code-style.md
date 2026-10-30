# Code style Java

Luật chung nằm trong mục **Code style** của `AGENTS.md`. File này chỉ thêm luật riêng cho Java.

- **Inject qua constructor.** Dùng `@RequiredArgsConstructor` với field `private final`. Không dùng `@Autowired` trên field.
- **Luôn `import`.** Không viết tên package đầy đủ trong code (`com.fashionstore.catalog.product.dto.ProductAdvanceSearchRequest`). Chỉ dùng tên đầy đủ khi hai class trùng tên.
- **Không nuốt lỗi.** Không `catch (Exception)` rồi bỏ qua. Bắt exception cụ thể, hoặc bọc vào `AppException` với error code của feature. Bỏ qua có chủ đích: log và comment lý do.
- **Một lệnh một dòng.** Không viết `try { … } catch (…) { … }` hay `if (…) { … }` dồn trên một dòng.
- **Không trả `null` cho collection.** Trả `List.of()` / `Collections.emptyList()`. `Optional` chỉ dùng làm kiểu trả về khi không có giá trị là hợp lệ, không dùng cho field hay tham số.
- **DTO mới dùng `record`.** Không sửa DTO class cũ chỉ để chuyển sang `record`.
- **Stream khi đọc dễ hơn vòng lặp.** Chuỗi stream dài hơn ~4 bước hoặc có side effect: dùng vòng lặp hoặc tách hàm.
- **Log có tham số.** Dùng `log.info("Order {} paid", orderId)`, không nối chuỗi. Không `System.out`, không `printStackTrace()`.

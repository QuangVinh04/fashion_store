package com.fashionstore.order.controller;

import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.order.dto.AddToCartRequest;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.dto.UpdateCartRequest;
import com.fashionstore.order.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cart", description = "Giỏ hàng của người dùng đang đăng nhập. Mỗi user có đúng một giỏ ACTIVE.")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartController {

    CartService cartService;

    @Operation(
            summary = "Lấy giỏ hàng hiện tại",
            description = """
                    Tên/size/màu/sku được làm mới từ catalog mỗi lần đọc; catalog không trả lời được thì rơi về
                    snapshot đã lưu trong DB và `available` = `null` (nghĩa là *không xác định*, không phải hết hàng).
                    Chưa có giỏ thì trả về giỏ rỗng chứ không phải 404.""")
    @GetMapping
    public ApiResponse<CartResponse> getMyCart() {
        return ApiResponse.<CartResponse>builder()
                .message("Lay gio hang thanh cong")
                .data(cartService.getMyCart())
                .build();
    }

    @Operation(
            summary = "Thêm sản phẩm vào giỏ",
            description = """
                    Thêm cùng một variant hai lần thì cộng dồn số lượng vào một dòng. Giá/tên/size/màu được
                    chụp lại tại thời điểm này (có giá sale thì lấy giá sale) và chính bản chụp đó đi tiếp
                    vào checkout, order.

                    Mã lỗi: `1004` variant không tồn tại (404) · `1006` variant đã ngừng bán (409) ·
                    `1005` không đủ tồn kho (400) · `9006` chưa hỏi được catalog (502).""")
    @PostMapping("/items")
    public ApiResponse<CartResponse> addToCart(@Valid @RequestBody AddToCartRequest request) {
        return ApiResponse.<CartResponse>builder()
                .message("Them vao gio hang thanh cong")
                .data(cartService.addToCart(request))
                .build();
    }

    @Operation(
            summary = "Đổi số lượng một dòng giỏ hàng",
            description = """
                    Số lượng gửi lên là số lượng cuối cùng, không phải phần cộng thêm. Snapshot giá/tên cũng
                    được làm mới theo catalog trong cùng lần gọi.

                    Mã lỗi: `3001` dòng giỏ không tồn tại (404) · `3004` giỏ không còn ACTIVE (403) ·
                    `1006` (409) · `1005` (400) · `9006` (502).""")
    @PutMapping("/items/{id}")
    public ApiResponse<CartResponse> updateCartItem(
            @Parameter(description = "Id của dòng giỏ hàng (cart_item.id), không phải variantId") @PathVariable String id,
                                                    @Valid @RequestBody UpdateCartRequest request) {
        return ApiResponse.<CartResponse>builder()
                .message("Cap nhat gio hang thanh cong")
                .data(cartService.updateCartItem(id, request))
                .build();
    }

    @Operation(summary = "Xoá một dòng giỏ hàng",
            description = "Mã lỗi: `3001` dòng giỏ không tồn tại (404) · `3004` giỏ không còn ACTIVE (403).")
    @DeleteMapping("/items/{id}")
    public ApiResponse<CartResponse> removeCartItem(
            @Parameter(description = "Id của dòng giỏ hàng (cart_item.id)") @PathVariable String id) {
        return ApiResponse.<CartResponse>builder()
                .message("Xoa san pham khoi gio hang thanh cong")
                .data(cartService.removeCartItem(id))
                .build();
    }

    @Operation(summary = "Xoá toàn bộ giỏ hàng",
            description = "Mã lỗi: `3004` khi user chưa có giỏ ACTIVE nào (403).")
    @DeleteMapping
    public ApiResponse<CartResponse> clearCart() {
        return ApiResponse.<CartResponse>builder()
                .message("Xoa toan bo gio hang thanh cong")
                .data(cartService.clearCart())
                .build();
    }
}

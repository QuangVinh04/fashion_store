package com.fashionstore.order.cart.service.impl;

import com.fashionstore.order.cart.client.inventory.InventoryClient;
import com.fashionstore.order.cart.client.product.ProductClient;
import com.fashionstore.order.cart.dto.cart.AddToCartRequest;
import com.fashionstore.order.cart.dto.cart.CartResponse;
import com.fashionstore.order.cart.dto.cart.UpdateCartRequest;
import com.fashionstore.order.cart.dto.inventory.StockCheckItem;
import com.fashionstore.order.cart.dto.inventory.StockCheckItemResult;
import com.fashionstore.order.cart.dto.inventory.StockCheckResult;
import com.fashionstore.order.cart.dto.product.ProductVariantDto;
import com.fashionstore.order.cart.exception.CartErrorCode;
import com.fashionstore.order.cart.mapper.CartMapper;
import com.fashionstore.order.cart.mapper.CartMapperImpl;
import com.fashionstore.order.cart.model.Cart;
import com.fashionstore.order.cart.model.CartItem;
import com.fashionstore.order.cart.model.enumeration.CartStatus;
import com.fashionstore.order.cart.repository.CartItemRepository;
import com.fashionstore.order.cart.repository.CartRepository;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private InventoryClient inventoryClient;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        CartMapper mapper = new CartMapperImpl();   // dùng mapper thật để bắt lỗi thiếu field
        service = new CartServiceImpl(
                cartRepository, cartItemRepository, productClient, currentUserProvider, inventoryClient, mapper
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
    }

    @Test
    void returnsEmptyCartWhenUserHasNoCartYet() {
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.empty());

        CartResponse response = service.getMyCart();

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cartResponseCarriesIdAndTimestampsFromEntity() {
        Cart cart = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));

        CartResponse response = service.getMyCart();

        // Bug đã sửa: mapper trước đây bỏ sót id/userId/createdAt/updatedAt.
        assertThat(response.getId()).isEqualTo("cart-1");
        assertThat(response.getUserId()).isEqualTo("user-1");
    }

    @Test
    void addsNewItemWhenVariantNotYetInCart() {
        Cart cart = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(inventoryClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 3));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(3).build());

        assertThat(cart.getItems()).hasSize(1);
        assertThat(response.getTotalQuantity()).isEqualTo(3);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("60.00");
    }

    @Test
    void addingSameVariantTwiceAccumulatesQuantityAndChecksNewTotal() {
        Cart cart = cart();
        CartItem existing = cartItem(cart, "variant-1", 2, "20.00");
        cart.getItems().add(existing);

        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.of(existing));
        when(inventoryClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 5));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addToCart(AddToCartRequest.builder().variantId("variant-1").quantity(3).build());

        assertThat(existing.getQuantity()).isEqualTo(5);   // 2 cũ + 3 mới, không tạo dòng thứ hai
        assertThat(cart.getItems()).hasSize(1);
        // Gọi 2 lần với cùng nội dung: 1 lần để guard tồn kho, 1 lần nữa trong enrichAvailability khi
        // dựng response — cả hai đều dùng đúng tổng số lượng mới (5), không phải số lượng cũ.
        verify(inventoryClient, org.mockito.Mockito.times(2))
                .checkStock(eq(List.of(new StockCheckItem("variant-1", 5))));
    }

    @Test
    void rejectsAddToCartWhenStockIsInsufficient() {
        Cart cart = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(productClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(inventoryClient.checkStock(anyList())).thenReturn(notEnoughStock("variant-1", 3, 1));

        AppException exception = assertThrows(AppException.class, () -> service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(3).build()));

        assertThat(exception.getErrorCode()).isEqualTo(CartErrorCode.STOCK_INSUFFICIENT);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void firstTimeBuyerGetsANewCartCreatedOnDemand() {
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.empty());
        // save() đóng 2 vai trong 1 lần addToCart: (1) tạo cart mới — id còn null, Hibernate sẽ sinh id
        // lúc insert thật; (2) lưu lại cart sau khi thêm item — id đã có từ bước (1).
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart c = invocation.getArgument(0);
            if (c.getId() == null) {
                c.setId("cart-1");
            }
            return c;
        });
        when(productClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(inventoryClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 1));

        CartResponse response = service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(1).build());

        assertThat(response.getId()).isEqualTo("cart-1");
        verify(cartRepository, org.mockito.Mockito.times(2)).save(any(Cart.class));
    }

    /**
     * {@code cart.user_id} là unique — hai request đầu tiên của cùng 1 user có thể đua nhau tạo cart.
     * Bên thua cuộc phải đọc lại thay vì để lộ {@link DataIntegrityViolationException} ra ngoài.
     */
    @Test
    void concurrentCartCreationForTheSameUserFallsBackToReadingTheWinnerRow() {
        Cart winner = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE))
                .thenReturn(Optional.empty())      // lần 1: chưa có cart -> đi tạo mới
                .thenReturn(Optional.of(winner));  // lần 2 (sau khi save() lần đầu đụng unique key): đọc lại
        when(cartRepository.save(any(Cart.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))   // lần tạo cart mới
                .thenAnswer(invocation -> invocation.getArgument(0));              // lần lưu item vào winner
        when(productClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(inventoryClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 1));

        CartResponse response = service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(1).build());

        assertThat(response.getId()).isEqualTo("cart-1");
    }

    @Test
    void updatingSomeoneElsesCartItemIsRejected() {
        Cart cart = cart();
        cart.setUserId("another-user");
        CartItem item = cartItem(cart, "variant-1", 1, "20.00");
        when(cartItemRepository.findByIdWithCart("item-1")).thenReturn(Optional.of(item));

        assertThrows(AppException.class, () ->
                service.updateCartItem("item-1", UpdateCartRequest.builder().quantity(2).build()));

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void clearingCartRemovesAllItemsAndReturnsZeroTotals() {
        Cart cart = cart();
        cart.getItems().add(cartItem(cart, "variant-1", 2, "20.00"));
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = service.clearCart();

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalQuantity()).isZero();
        assertThat(response.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ----- helpers -----

    private Cart cart() {
        Cart cart = Cart.builder()
                .userId("user-1")
                .status(CartStatus.ACTIVE)
                .items(new ArrayList<>())
                .build();
        cart.setId("cart-1");
        return cart;
    }

    private CartItem cartItem(Cart cart, String variantId, int quantity, String unitPrice) {
        CartItem item = CartItem.builder()
                .cart(cart)
                .variantId(variantId)
                .productId("product-1")
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
        item.setId("item-1");
        return item;
    }

    private ProductVariantDto variant(String variantId, String price) {
        return ProductVariantDto.builder()
                .variantId(variantId)
                .productId("product-1")
                .productName("Basic Tee")
                .price(new BigDecimal(price))
                .build();
    }

    private StockCheckResult enoughStock(String variantId, int availableQty) {
        return StockCheckResult.builder()
                .allAvailable(true)
                .items(List.of(StockCheckItemResult.builder()
                        .variantId(variantId)
                        .available(true)
                        .availableQty(availableQty)
                        .requestedQty(availableQty)
                        .build()))
                .build();
    }

    private StockCheckResult notEnoughStock(String variantId, int requestedQty, int availableQty) {
        return StockCheckResult.builder()
                .allAvailable(false)
                .items(List.of(StockCheckItemResult.builder()
                        .variantId(variantId)
                        .available(false)
                        .availableQty(availableQty)
                        .requestedQty(requestedQty)
                        .build()))
                .build();
    }
}

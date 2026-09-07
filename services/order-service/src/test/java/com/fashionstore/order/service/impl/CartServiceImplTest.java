package com.fashionstore.order.service.impl;

import com.fashionstore.order.client.CatalogClient;
import com.fashionstore.order.dto.AddToCartRequest;
import com.fashionstore.order.dto.CartResponse;
import com.fashionstore.order.dto.UpdateCartRequest;
import com.fashionstore.order.dto.StockCheckItem;
import com.fashionstore.order.dto.StockCheckItemResult;
import com.fashionstore.order.dto.StockCheckResult;
import com.fashionstore.order.dto.ProductVariantDto;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.mapper.CartMapper;
import com.fashionstore.order.mapper.CartMapperImpl;
import com.fashionstore.order.entity.Cart;
import com.fashionstore.order.entity.CartItem;
import com.fashionstore.order.entity.enumeration.CartStatus;
import com.fashionstore.order.repository.CartItemRepository;
import com.fashionstore.order.repository.CartRepository;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
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
    private CatalogClient catalogClient;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        CartMapper mapper = new CartMapperImpl();   // dùng mapper thật để bắt lỗi thiếu field
        service = new CartServiceImpl(
                cartRepository, cartItemRepository, catalogClient, currentUserProvider, mapper
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
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 3));
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
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.of(existing));
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 5));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addToCart(AddToCartRequest.builder().variantId("variant-1").quantity(3).build());

        assertThat(existing.getQuantity()).isEqualTo(5);   // 2 cũ + 3 mới, không tạo dòng thứ hai
        assertThat(cart.getItems()).hasSize(1);
        // Gọi 2 lần với cùng nội dung: 1 lần để guard tồn kho, 1 lần nữa trong enrichAvailability khi
        // dựng response — cả hai đều dùng đúng tổng số lượng mới (5), không phải số lượng cũ.
        verify(catalogClient, org.mockito.Mockito.times(2))
                .checkStock(eq(List.of(new StockCheckItem("variant-1", 5))));
    }

    @Test
    void rejectsAddToCartWhenStockIsInsufficient() {
        Cart cart = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(catalogClient.checkStock(anyList())).thenReturn(notEnoughStock("variant-1", 3, 1));

        AppException exception = assertThrows(AppException.class, () -> service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(3).build()));

        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.STOCK_INSUFFICIENT);
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
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 1));

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
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 1));

        CartResponse response = service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(1).build());

        assertThat(response.getId()).isEqualTo("cart-1");
    }

    @Test
    void reportsMissingCartItemWithTheServiceOwnErrorCode() {
        when(cartItemRepository.findByIdWithCart("item-1")).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                service.updateCartItem("item-1", UpdateCartRequest.builder().quantity(2).build()));

        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.CART_ITEM_NOT_FOUND);
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

    /**
     * checkout_item.product_name là NOT NULL nhưng trước đây cart không lưu tên — tên chỉ được enrich sống
     * lúc đọc giỏ. Variant biến mất khỏi catalog là checkout ném NOT NULL violation. Nay snapshot lúc ghi.
     */
    @Test
    void addToCartSnapshotsProductNameSizeAndColor() {
        Cart cart = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 3));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addToCart(AddToCartRequest.builder().variantId("variant-1").quantity(1).build());

        CartItem saved = cart.getItems().get(0);
        assertThat(saved.getProductName()).isEqualTo("Basic Tee");
        assertThat(saved.getSize()).isEqualTo("M");
        assertThat(saved.getColor()).isEqualTo("Den");
        assertThat(saved.getProductId()).isEqualTo("product-1");
    }

    @Test
    void addToCartSnapshotsSalePriceWhenTheVariantIsDiscounted() {
        Cart cart = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00", "15.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 2));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(2).build());

        assertThat(cart.getItems().get(0).getUnitPrice()).isEqualByComparingTo("15.00");
        assertThat(response.getTotalPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void rejectsAddToCartWhenTheVariantIsNoLongerOnSale() {
        Cart cart = cart();
        ProductVariantDto inactive = variant("variant-1", "20.00");
        inactive.setActive(false);
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariant("variant-1")).thenReturn(inactive);

        AppException exception = assertThrows(AppException.class, () -> service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(1).build()));

        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.PRODUCT_VARIANT_INACTIVE);
        verify(cartRepository, never()).save(any());
    }

    /** Fallback của inventory nói "không đủ hàng" cho mọi variant — đừng dịch nó thành hết hàng. */
    @Test
    void reportsUpstreamFailureInsteadOfFakeStockShortage() {
        Cart cart = cart();
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariant("variant-1")).thenReturn(variant("variant-1", "20.00"));
        when(cartItemRepository.findByCartIdAndVariantId("cart-1", "variant-1")).thenReturn(Optional.empty());
        when(catalogClient.checkStock(anyList()))
                .thenReturn(StockCheckResult.unavailable(List.of(new StockCheckItem("variant-1", 1))));

        AppException exception = assertThrows(AppException.class, () -> service.addToCart(
                AddToCartRequest.builder().variantId("variant-1").quantity(1).build()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateCartItemRefreshesTheSnapshot() {
        Cart cart = cart();
        CartItem item = cartItem(cart, "variant-1", 1, "20.00");
        cart.getItems().add(item);
        when(cartItemRepository.findByIdWithCart("item-1")).thenReturn(Optional.of(item));
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 5));
        when(catalogClient.getVariant("variant-1")).thenReturn(
                ProductVariantDto.builder()
                        .variantId("variant-1")
                        .productId("product-1")
                        .productName("Basic Tee v2")
                        .size("L")
                        .color("Trang")
                        .price(new BigDecimal("30.00"))
                        .active(true)
                        .build());

        service.updateCartItem("item-1", UpdateCartRequest.builder().quantity(2).build());

        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getProductName()).isEqualTo("Basic Tee v2");
        assertThat(item.getSize()).isEqualTo("L");
        assertThat(item.getColor()).isEqualTo("Trang");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("30.00");
    }

    /** Catalog chết thì giỏ vẫn phải đọc được: tên lấy từ snapshot, còn hàng hay không thì "không biết". */
    @Test
    void keepsSnapshotAndUnknownAvailabilityWhenCatalogIsDown() {
        Cart cart = cart();
        cart.getItems().add(cartItem(cart, "variant-1", 2, "20.00"));
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariantsBatch(anyList()))
                .thenReturn(List.of(ProductVariantDto.unavailable("variant-1")));
        when(catalogClient.checkStock(anyList()))
                .thenReturn(StockCheckResult.unavailable(List.of(new StockCheckItem("variant-1", 2))));

        CartResponse response = service.getMyCart();

        assertThat(response.getItems().get(0).getProductName()).isEqualTo("Basic Tee");
        assertThat(response.getItems().get(0).getAvailable()).isNull();
    }

    /**
     * Checkout là bản chụp giá để thu tiền, nên trước khi mở nó phải xác nhận lại với catalog. Giá đổi
     * từ lúc bỏ vào giỏ thì lấy giá mới — khách thấy giá đúng trong checkout trước khi bấm đặt hàng.
     */
    @Test
    void revalidateRefreshesNameAndPriceFromCatalog() {
        Cart cart = cart();
        CartItem item = cartItem(cart, "variant-1", 2, "20.00");
        cart.getItems().add(item);
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariantsBatch(anyList())).thenReturn(List.of(
                ProductVariantDto.builder()
                        .variantId("variant-1")
                        .productId("product-1")
                        .productName("Basic Tee v2")
                        .size("L")
                        .color("Trang")
                        .price(new BigDecimal("30.00"))
                        .salePrice(new BigDecimal("25.00"))
                        .active(true)
                        .build()));
        when(catalogClient.checkStock(anyList())).thenReturn(enoughStock("variant-1", 2));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart revalidated = service.revalidateActiveCart();

        assertThat(revalidated.getItems().get(0).getUnitPrice()).isEqualByComparingTo("25.00");
        assertThat(revalidated.getItems().get(0).getProductName()).isEqualTo("Basic Tee v2");
        assertThat(revalidated.getItems().get(0).getSize()).isEqualTo("L");
        verify(cartRepository).save(cart);
    }

    @Test
    void revalidateRejectsAnEmptyCart() {
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart()));

        AppException exception = assertThrows(AppException.class, () -> service.revalidateActiveCart());

        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.CART_EMPTY);
    }

    @Test
    void revalidateRejectsAVariantThatDisappearedFromCatalog() {
        Cart cart = cart();
        cart.getItems().add(cartItem(cart, "variant-1", 1, "20.00"));
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariantsBatch(anyList())).thenReturn(List.of());

        AppException exception = assertThrows(AppException.class, () -> service.revalidateActiveCart());

        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        verify(cartRepository, never()).save(any());
    }

    /** Catalog chết thì không mở checkout với giá chưa xác nhận — 502, không phải "sản phẩm không tồn tại". */
    @Test
    void revalidateReportsUpstreamFailureWhenCatalogCannotConfirmPrices() {
        Cart cart = cart();
        cart.getItems().add(cartItem(cart, "variant-1", 1, "20.00"));
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariantsBatch(anyList()))
                .thenReturn(List.of(ProductVariantDto.unavailable("variant-1")));

        AppException exception = assertThrows(AppException.class, () -> service.revalidateActiveCart());

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UPSTREAM_SERVICE_ERROR);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void revalidateRejectsAVariantThatIsNoLongerOnSale() {
        Cart cart = cart();
        cart.getItems().add(cartItem(cart, "variant-1", 1, "20.00"));
        ProductVariantDto inactive = variant("variant-1", "20.00");
        inactive.setActive(false);
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariantsBatch(anyList())).thenReturn(List.of(inactive));

        AppException exception = assertThrows(AppException.class, () -> service.revalidateActiveCart());

        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.PRODUCT_VARIANT_INACTIVE);
    }

    /** Hết hàng phải chặn ngay ở checkout, đừng để tới bước saga reserve mới sinh ra một đơn CANCELLED. */
    @Test
    void revalidateRejectsWhenStockRanOutSinceTheItemWasAddedToTheCart() {
        Cart cart = cart();
        cart.getItems().add(cartItem(cart, "variant-1", 3, "20.00"));
        when(cartRepository.findByUserIdAndStatus("user-1", CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(catalogClient.getVariantsBatch(anyList())).thenReturn(List.of(variant("variant-1", "20.00")));
        when(catalogClient.checkStock(anyList())).thenReturn(notEnoughStock("variant-1", 3, 1));

        AppException exception = assertThrows(AppException.class, () -> service.revalidateActiveCart());

        assertThat(exception.getErrorCode()).isEqualTo(OrderErrorCode.STOCK_INSUFFICIENT);
        verify(cartRepository, never()).save(any());
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
                .productName("Basic Tee")
                .size("M")
                .color("Den")
                .quantity(quantity)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
        item.setId("item-1");
        return item;
    }

    private ProductVariantDto variant(String variantId, String price) {
        return variant(variantId, price, null);
    }

    private ProductVariantDto variant(String variantId, String price, String salePrice) {
        return ProductVariantDto.builder()
                .variantId(variantId)
                .productId("product-1")
                .productName("Basic Tee")
                .size("M")
                .color("Den")
                .sku("TEE-M-BLACK")
                .price(new BigDecimal(price))
                .salePrice(salePrice == null ? null : new BigDecimal(salePrice))
                .active(true)
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

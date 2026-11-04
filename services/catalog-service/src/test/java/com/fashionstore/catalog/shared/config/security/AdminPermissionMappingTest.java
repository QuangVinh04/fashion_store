package com.fashionstore.catalog.shared.config.security;

import static com.fashionstore.common.security.PermissionNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.catalog.media.controller.EvidenceUploadController;
import com.fashionstore.catalog.product.service.impl.ProductServiceImpl;
import com.fashionstore.catalog.review.controller.AdminReviewController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** Bảng quyền method-level của catalog-service (phần còn lại phân quyền theo path trong SecurityConfig). */
class AdminPermissionMappingTest {

    @Test
    void adminReviews() {
        assertThat(AdminReviewController.class.getAnnotation(PreAuthorize.class)).isNull();
        assertPermissions(
                AdminReviewController.class,
                Map.of(
                        REVIEW_READ, List.of("list"),
                        REVIEW_MANAGE, List.of("updateStatus", "reply", "deleteReply", "deleteReview")));
    }

    @Test
    void evidenceAndBackofficeProduct() {
        // staff có order:manage (không có refund:approve) cũng phải upload được bằng chứng thanh toán/COD
        Method payments = Arrays.stream(EvidenceUploadController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("payments"))
                .findFirst()
                .orElseThrow();
        assertThat(payments.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyAuthority('" + REFUND_APPROVE + "', '" + ORDER_MANAGE + "')");
        assertPermissions(ProductServiceImpl.class, Map.of(PRODUCT_READ, List.of("getProductByIdForBackoffice")));
    }

    private static void assertPermissions(Class<?> type, Map<String, List<String>> permissionToMethods) {
        permissionToMethods.forEach((permission, names) -> names.forEach(name -> {
            List<Method> methods = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.getName().equals(name))
                    .toList();
            assertThat(methods).as("%s#%s", type.getSimpleName(), name).isNotEmpty();
            methods.forEach(method -> assertThat(method.getAnnotation(PreAuthorize.class))
                    .as("%s#%s", type.getSimpleName(), name)
                    .isNotNull()
                    .extracting(PreAuthorize::value)
                    .isEqualTo("hasAuthority('" + permission + "')"));
        }));
    }
}

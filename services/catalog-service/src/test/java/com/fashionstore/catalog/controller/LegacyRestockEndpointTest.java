package com.fashionstore.catalog.controller;
import com.fashionstore.catalog.service.InventoryService;
import com.fashionstore.common.exception.AppException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class LegacyRestockEndpointTest {
    @Test void bulkReturnRestockEndpointRequiresIndependentManualStockOperation() {
        var stock=mock(InventoryService.class);var controller=new InventoryController(stock);
        assertThatThrownBy(() -> controller.restock("order")).isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException)error).getErrorCode().getCode()).isEqualTo(4058));
        verifyNoInteractions(stock);
    }
}

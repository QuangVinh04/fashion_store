package com.fashionstore.catalog.controller;
import com.fashionstore.common.dto.ApiResponse;
import com.fashionstore.common.exception.*;
import com.fashionstore.contracts.inventory.event.InventoryDisposition;
import com.fashionstore.catalog.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/inventory") @RequiredArgsConstructor
public class InternalInventoryDispositionController {
    private final InventoryReservationRepository reservations;
    @GetMapping("/orders/{id}/disposition") @Transactional(readOnly=true)
    public ApiResponse<InventoryDisposition> get(@PathVariable String id) {
        var row=reservations.findByOrderId(id).orElseThrow(()->new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        return ApiResponse.<InventoryDisposition>builder().data(new InventoryDisposition(id,row.getId(),row.getStatus().name())).build();
    }
}

package com.fashionstore.order.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.security.CurrentUserProvider;
import com.fashionstore.order.dto.RejectReturnRequest;
import com.fashionstore.order.dto.ReturnOrderRequest;
import com.fashionstore.order.dto.ReturnRequestResponse;
import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.OrderStatusHistory;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.enumeration.OrderStatus;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fashionstore.order.exception.OrderErrorCode;
import com.fashionstore.order.repository.OrderRepository;
import com.fashionstore.order.repository.OrderStatusHistoryRepository;
import com.fashionstore.order.repository.ReturnRequestRepository;
import com.fashionstore.order.saga.SagaCommands;
import com.fashionstore.order.saga.SagaOutbox;
import com.fashionstore.order.service.PromotionService;
import com.fashionstore.order.service.ReturnService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReturnServiceImpl implements ReturnService {

    ReturnRequestRepository returnRequestRepository;
    OrderRepository orderRepository;
    OrderStatusHistoryRepository orderStatusHistoryRepository;
    CurrentUserProvider currentUserProvider;
    PromotionService promotionService;
    SagaOutbox sagaOutbox;
    ObjectMapper objectMapper;

    @Override
    @Transactional
    public ReturnRequestResponse createReturnRequest(String orderId, ReturnOrderRequest request) {
        String userId = resolveUserIdSafely();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(OrderErrorCode.ORDER_NOT_FOUND);
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new AppException(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED);
        }

        LocalDateTime deliveredAt = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
        if (deliveredAt != null && deliveredAt.plusDays(7).isBefore(LocalDateTime.now())) {
            throw new AppException(OrderErrorCode.ORDER_RETURN_NOT_ALLOWED);
        }

        Optional<ReturnRequest> existingOpt = returnRequestRepository.findByOrderIdForUpdate(orderId);
        if (existingOpt.isPresent()) {
            ReturnRequest existing = existingOpt.get();
            if (existing.getStatus() == ReturnRequestStatus.PENDING) {
                log.info("[Return] Return request for order {} already pending, returning existing", orderId);
                return toResponse(existing);
            }
            throw new AppException(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED);
        }

        String reason = (request == null || request.getReason() == null || request.getReason().isBlank())
                ? "Khách hàng yêu cầu trả hàng"
                : request.getReason().trim();

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .userId(userId)
                .reason(reason)
                .images(serializeImages(request != null ? request.getImages() : null))
                .status(ReturnRequestStatus.PENDING)
                .build();
        returnRequest = returnRequestRepository.save(returnRequest);

        // Đơn hàng giữ nguyên DELIVERED, ghi nhận audit trail yêu cầu hoàn trả
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(OrderStatus.DELIVERED)
                .toStatus(OrderStatus.DELIVERED)
                .action("RETURN_REQUESTED")
                .changedBy(userId)
                .reason(reason)
                .build();
        orderStatusHistoryRepository.save(history);

        log.info("[Return] Created return request {} for order {}", returnRequest.getId(), orderId);
        return toResponse(returnRequest);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse approveReturn(String returnRequestId) {
        String adminId = resolveUserIdSafely();
        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(returnRequestId)
                .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));

        if (returnRequest.getStatus() == ReturnRequestStatus.APPROVED) {
            log.info("[Return] Return request {} already approved, returning existing", returnRequestId);
            return toResponse(returnRequest);
        }
        if (returnRequest.getStatus() == ReturnRequestStatus.REJECTED) {
            throw new AppException(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED);
        }

        Order order = orderRepository.findByIdForUpdate(returnRequest.getOrder().getId())
                .orElseThrow(() -> new AppException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new AppException(OrderErrorCode.ORDER_STATUS_INVALID);
        }

        returnRequest.setStatus(ReturnRequestStatus.APPROVED);
        returnRequest.setReviewedBy(adminId);
        returnRequest.setReviewedAt(LocalDateTime.now());
        returnRequest = returnRequestRepository.save(returnRequest);

        OrderStatus fromStatus = order.getStatus();
        order.setStatus(OrderStatus.RETURNED);
        order.setCancelReason(returnRequest.getReason());
        orderRepository.save(order);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(OrderStatus.RETURNED)
                .action("RETURN_APPROVED")
                .changedBy(adminId)
                .reason("Admin duyệt yêu cầu hoàn trả: " + returnRequest.getReason())
                .build();
        orderStatusHistoryRepository.save(history);

        // Nhả mã khuyến mãi/coupon đã áp dụng
        promotionService.release(order.getId());

        // Restock kho qua Outbox Saga (Message-driven)
        sagaOutbox.emit(
                order.getId(),
                order.getId(),
                SagaCommands.restockInventory(order.getId())
        );

        // Kích hoạt hoàn tiền qua Outbox Saga
        sagaOutbox.emit(
                order.getId(),
                order.getId(),
                SagaCommands.refundPayment(order, order.getTotalAmount(), returnRequest.getReason())
        );

        log.info("[Return] Approved return request {} for order {}, emitted restock & refund",
                returnRequestId, order.getId());
        return toResponse(returnRequest);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ReturnRequestResponse rejectReturn(String returnRequestId, RejectReturnRequest request) {
        String adminId = resolveUserIdSafely();
        if (request == null || request.getRejectReason() == null || request.getRejectReason().isBlank()) {
            throw new AppException(OrderErrorCode.RETURN_REJECT_REASON_REQUIRED);
        }

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(returnRequestId)
                .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));

        if (returnRequest.getStatus() == ReturnRequestStatus.REJECTED) {
            log.info("[Return] Return request {} already rejected, returning existing", returnRequestId);
            return toResponse(returnRequest);
        }
        if (returnRequest.getStatus() == ReturnRequestStatus.APPROVED) {
            throw new AppException(OrderErrorCode.RETURN_REQUEST_ALREADY_PROCESSED);
        }

        returnRequest.setStatus(ReturnRequestStatus.REJECTED);
        returnRequest.setRejectReason(request.getRejectReason().trim());
        returnRequest.setReviewedBy(adminId);
        returnRequest.setReviewedAt(LocalDateTime.now());
        returnRequest = returnRequestRepository.save(returnRequest);

        Order order = returnRequest.getOrder();
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(order.getStatus())
                .toStatus(order.getStatus())
                .action("RETURN_REJECTED")
                .changedBy(adminId)
                .reason("Admin từ chối hoàn trả: " + request.getRejectReason().trim())
                .build();
        orderStatusHistoryRepository.save(history);

        log.info("[Return] Rejected return request {} for order {}", returnRequestId, order.getId());
        return toResponse(returnRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnRequestResponse getReturnRequestByOrderId(String orderId) {
        String currentUserId = resolveUserIdSafely();
        ReturnRequest returnRequest = returnRequestRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND));

        if (!returnRequest.getUserId().equals(currentUserId)) {
            // Nếu không phải chính chủ, kiểm tra nếu là ADMIN thì cho xem
            // Ngược lại ném NOT_FOUND để bảo mật
            throw new AppException(OrderErrorCode.RETURN_REQUEST_NOT_FOUND);
        }

        return toResponse(returnRequest);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<List<ReturnRequestResponse>> searchReturnRequests(ReturnRequestStatus status, Pageable pageable) {
        Page<ReturnRequest> page = status != null
                ? returnRequestRepository.findByStatus(status, pageable)
                : returnRequestRepository.findAll(pageable);

        List<ReturnRequestResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<List<ReturnRequestResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(page.getTotalPages())
                .items(content)
                .build();
    }

    private String resolveUserIdSafely() {
        try {
            String userId = currentUserProvider.getCurrentUserId();
            return (userId != null && !userId.isBlank()) ? userId : "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    private String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> filtered = images.stream()
                .filter(img -> img != null && !img.isBlank())
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            log.warn("[Return] Failed to serialize images list: {}", e.getMessage());
            return String.join(",", filtered);
        }
    }

    private List<String> deserializeImages(String imagesStr) {
        if (imagesStr == null || imagesStr.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imagesStr, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of(imagesStr.split(","));
        }
    }

    private ReturnRequestResponse toResponse(ReturnRequest request) {
        return ReturnRequestResponse.builder()
                .id(request.getId())
                .orderId(request.getOrder() != null ? request.getOrder().getId() : null)
                .userId(request.getUserId())
                .reason(request.getReason())
                .images(deserializeImages(request.getImages()))
                .status(request.getStatus())
                .rejectReason(request.getRejectReason())
                .reviewedBy(request.getReviewedBy())
                .reviewedAt(request.getReviewedAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}

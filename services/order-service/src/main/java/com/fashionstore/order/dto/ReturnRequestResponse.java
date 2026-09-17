package com.fashionstore.order.dto;

import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnRequestResponse {

    String id;
    String orderId;
    String userId;
    String reason;
    List<String> images;
    ReturnRequestStatus status;
    String rejectReason;
    String reviewedBy;
    LocalDateTime reviewedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

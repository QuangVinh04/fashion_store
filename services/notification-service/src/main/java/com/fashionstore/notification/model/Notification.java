package com.fashionstore.notification.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "in_app_notification")
@Getter
@Setter
public class Notification extends BaseEntity {
    @Column(nullable = false) private String userId;
    @Column(nullable = false, length = 64) private String type;
    @Column(nullable = false) private String title;
    @Column(nullable = false, columnDefinition = "text") private String message;
    @Column(nullable = false) private String orderId;
    private Instant readAt;
}

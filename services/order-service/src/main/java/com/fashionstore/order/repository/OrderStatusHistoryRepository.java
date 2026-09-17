package com.fashionstore.order.repository;

import com.fashionstore.order.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, String> {

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtDescIdDesc(String orderId);
}

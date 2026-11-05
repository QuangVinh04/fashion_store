package com.fashionstore.order.order.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenue(LocalDate date, BigDecimal revenue, long orderCount) {
}

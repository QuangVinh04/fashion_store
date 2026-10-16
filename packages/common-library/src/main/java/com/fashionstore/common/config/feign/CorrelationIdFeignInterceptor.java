package com.fashionstore.common.config.feign;

import com.fashionstore.common.web.CorrelationIdFilter;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import org.slf4j.MDC;

/**
 * Chuyển correlation ID của request đang xử lý sang lời gọi Feign nội bộ, để log của order, identity,
 * catalog… nối được thành một chuỗi. Chỉ gắn cho client dùng {@link FeignGlobalConfig} (client nội bộ),
 * nên ID không rò sang GHN hay VNPay. Thread không có ID (listener, scheduler) thì không gắn gì và bên
 * nhận tự sinh ID mới.
 */
public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return;
        }
        if (template.headers().containsKey(CorrelationIdFilter.HEADER)) {
            return; // caller đã đặt tường minh
        }
        template.header(CorrelationIdFilter.HEADER, correlationId);
    }
}

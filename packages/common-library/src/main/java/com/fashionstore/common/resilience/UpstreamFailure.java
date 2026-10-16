package com.fashionstore.common.resilience;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.function.Predicate;

/**
 * Phân biệt "downstream hỏng" với "downstream trả lời nghiệp vụ" cho Resilience4j. Khai báo
 * {@code record-failure-predicate} (circuit breaker) và {@code retry-exception-predicate} (retry) trỏ vào
 * lớp này: 5xx, mất kết nối, timeout, body hỏng thì tính là lỗi; 4xx và {@link AppException} thì không —
 * khách nhập sai địa chỉ không được phép mở circuit hay kích hoạt gọi lại.
 */
public class UpstreamFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable error) {
        if (error instanceof AppException || error instanceof CallNotPermittedException) {
            return false;
        }
        if (error instanceof FeignException feign) {
            return feign.status() < 0 || feign.status() >= 500;
        }
        return true;
    }

    /**
     * Dùng trong fallback: sự cố hạ tầng (kể cả circuit đang mở) thành 502, còn câu trả lời nghiệp vụ
     * (4xx, {@link AppException}) được trả nguyên để người gọi xử lý như trước.
     */
    public static RuntimeException translate(Throwable error) {
        if (error instanceof AppException app) {
            return app;
        }
        if (error instanceof FeignException feign && !(feign.status() < 0 || feign.status() >= 500)) {
            return feign;
        }
        return new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR, error);
    }
}

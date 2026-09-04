package com.fashionstore.order.messaging;

/**
 * Một message saga muốn gửi đi. Handler chỉ khai báo "gửi cái gì"; việc bọc envelope, gắn
 * correlationId và ghi outbox do {@link SagaOutbox} lo.
 *
 * @param eventType giá trị trong EventTypes, dùng luôn làm routing key
 */
public record SagaCommand(String eventType, Object payload) {

    public static SagaCommand of(String eventType, Object payload) {
        return new SagaCommand(eventType, payload);
    }
}

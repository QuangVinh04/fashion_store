package com.fashionstore.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.payment.common.exception.ErrorCode;
import com.fashionstore.payment.config.payment.VnPayProperties;
import com.fashionstore.payment.dto.PaymentCallbackResult;
import com.fashionstore.payment.dto.PaymentInitiationResult;
import com.fashionstore.payment.dto.PaymentRefundResult;
import com.fashionstore.payment.entity.Payment;
import com.fashionstore.payment.entity.PaymentRefund;
import com.fashionstore.payment.entity.PaymentStatus;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VnPayPaymentGateway implements CallbackPaymentGateway, RefundablePaymentGateway {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VnPayProperties properties;
    private final VnPayFeignClient vnPayFeignClient;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public PaymentInitiationResult initiate(Payment payment, String clientIp) {
        LocalDateTime now = LocalDateTime.now(VN_TIME_ZONE);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", properties.getCommand());
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", toVnPayAmount(payment.getAmount()));
        params.put("vnp_CurrCode", properties.getCurrency());
        params.put("vnp_TxnRef", payment.getMerchantReference());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderId());
        params.put("vnp_OrderType", properties.getOrderType());
        params.put("vnp_Locale", properties.getLocale());
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        params.put("vnp_CreateDate", now.format(DATE_FORMATTER));
        params.put("vnp_ExpireDate", now.plusMinutes(properties.getExpirationMinutes()).format(DATE_FORMATTER));

        String query = toQueryString(params);
        return PaymentInitiationResult.builder()
                .paymentUrl(properties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + hmacSha512(query))
                .merchantReference(payment.getMerchantReference())
                .providerAmount(payment.getAmount())
                .providerCurrency(properties.getCurrency())
                .providerTransactionDate(now.format(DATE_FORMATTER))
                .build();
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> payload) {
        String secureHash = payload.get("vnp_SecureHash");
        Map<String, String> signedParams = payload.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vnp_"))
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));

        boolean signatureValid = StringUtils.hasText(secureHash)
                && MessageDigest.isEqual(
                hmacSha512(toQueryString(signedParams)).getBytes(StandardCharsets.UTF_8),
                secureHash.getBytes(StandardCharsets.UTF_8));
        boolean successful = "00".equals(payload.get("vnp_ResponseCode"))
                && "00".equals(payload.get("vnp_TransactionStatus"));

        return PaymentCallbackResult.builder()
                .merchantReference(payload.get("vnp_TxnRef"))
                .providerTransactionId(payload.get("vnp_TransactionNo"))
                .amount(fromVnPayAmount(payload.get("vnp_Amount")))
                .currency(properties.getCurrency())
                .status(successful ? PaymentStatus.COMPLETED : PaymentStatus.FAILED)
                .failureReason(successful ? null : "VNPay response code: " + payload.get("vnp_ResponseCode"))
                .signatureValid(signatureValid)
                .build();
    }

    @Override
    public PaymentRefundResult refund(Payment payment, PaymentRefund refund) {
        if (!StringUtils.hasText(payment.getTransactionId())
                || !StringUtils.hasText(payment.getProviderTransactionDate())) {
            throw new AppException(ErrorCode.PAYMENT_STATUS_INVALID);
        }

        String createDate = LocalDateTime.now(VN_TIME_ZONE).format(DATE_FORMATTER);
        String requestId = vnPayRequestId(refund.getIdempotencyKey());
        String transactionType = refund.getAmount().compareTo(payment.getAmount()) == 0 ? "02" : "03";
        String orderInfo = "Hoan tien don hang " + payment.getOrderId();
        String amount = toVnPayAmount(refund.getAmount());
        String hashData = String.join("|",
                requestId,
                properties.getVersion(),
                "refund",
                properties.getTmnCode(),
                transactionType,
                payment.getMerchantReference(),
                amount,
                payment.getTransactionId(),
                payment.getProviderTransactionDate(),
                properties.getCreateBy(),
                createDate,
                properties.getServerIp(),
                orderInfo);

        Map<String, String> request = new LinkedHashMap<>();
        request.put("vnp_RequestId", requestId);
        request.put("vnp_Version", properties.getVersion());
        request.put("vnp_Command", "refund");
        request.put("vnp_TmnCode", properties.getTmnCode());
        request.put("vnp_TransactionType", transactionType);
        request.put("vnp_TxnRef", payment.getMerchantReference());
        request.put("vnp_Amount", amount);
        request.put("vnp_TransactionNo", payment.getTransactionId());
        request.put("vnp_TransactionDate", payment.getProviderTransactionDate());
        request.put("vnp_CreateBy", properties.getCreateBy());
        request.put("vnp_CreateDate", createDate);
        request.put("vnp_IpAddr", properties.getServerIp());
        request.put("vnp_OrderInfo", orderInfo);
        request.put("vnp_SecureHash", hmacSha512(hashData));

        try {
            JsonNode response = vnPayFeignClient.refund(request);
            String responseCode = response.path("vnp_ResponseCode").asText();
            String transactionStatus = response.path("vnp_TransactionStatus").asText();
            String providerRefundId = response.path("vnp_TransactionNo").asText(null);
            if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
                return PaymentRefundResult.completed(providerRefundId);
            }
            if ("00".equals(responseCode)
                    && ("05".equals(transactionStatus) || "06".equals(transactionStatus))) {
                return PaymentRefundResult.pending(providerRefundId);
            }
            return PaymentRefundResult.failed(providerRefundId,
                    "VNPay refund response/status: " + responseCode + "/" + transactionStatus);
        } catch (FeignException exception) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, exception);
        }
    }

    private String toVnPayAmount(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private BigDecimal fromVnPayAmount(String amount) {
        if (!StringUtils.hasText(amount)) {
            return null;
        }
        try {
            return new BigDecimal(amount).movePointLeft(2);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return toHex(hmac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot generate VNPay secure hash", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private String vnPayRequestId(String idempotencyKey) {
        String normalized = idempotencyKey.replace("-", "");
        return normalized.substring(0, Math.min(normalized.length(), 32));
    }
}

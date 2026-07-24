package com.fashionstore.product.modules.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.common.exception.ErrorCode;
import com.fashionstore.product.modules.payment.dto.PaymentCallbackResult;
import com.fashionstore.product.modules.payment.dto.PaymentInitiationResult;
import com.fashionstore.product.modules.payment.entity.Payment;
import com.fashionstore.common.payment.PaymentProvider;
import com.fashionstore.product.modules.payment.entity.PaymentStatus;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class PaypalPaymentGateway implements CapturablePaymentGateway {
    private static final int PAYPAL_AMOUNT_SCALE = 2;

    private final PaypalFeignClient paypalFeignClient;
    private final String clientId;
    private final String clientSecret;
    private final String currency;
    private final BigDecimal vndPerCurrencyUnit;
    private final String returnUrl;
    private final String cancelUrl;

    public PaypalPaymentGateway(
            PaypalFeignClient paypalFeignClient,
            @Value("${payment.paypal.client-id}") String clientId,
            @Value("${payment.paypal.client-secret}") String clientSecret,
            @Value("${payment.paypal.currency:USD}") String currency,
            @Value("${payment.paypal.vnd-per-currency-unit}") BigDecimal vndPerCurrencyUnit,
            @Value("${payment.paypal.return-url}") String returnUrl,
            @Value("${payment.paypal.cancel-url}") String cancelUrl) {
        this.paypalFeignClient = paypalFeignClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.currency = currency;
        this.vndPerCurrencyUnit = vndPerCurrencyUnit;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYPAL;
    }

    @Override
    public PaymentInitiationResult initiate(Payment payment, String clientIp) {
        validateConfiguration();
        String accessToken = getAccessToken();
        JsonNode response = createOrder(
                accessToken,
                payment.getMerchantReference(),
                Map.of(
                        "intent", "CAPTURE",
                        "purchase_units", List.of(Map.of(
                                "reference_id", payment.getMerchantReference(),
                                "custom_id", payment.getMerchantReference(),
                                "amount", Map.of(
                                        "currency_code", currency,
                                        "value", toPaypalAmount(payment.getAmount()).toPlainString()))),
                        "payment_source", Map.of(
                                "paypal", Map.of(
                                        "experience_context", Map.of(
                                                "return_url", returnUrl,
                                                "cancel_url", cancelUrl,
                                                "user_action", "PAY_NOW")))));

        String paypalOrderId = requiredText(response, "id");
        String approvalUrl = response.path("links").findParents("rel").stream()
                .filter(link -> "approve".equals(link.path("rel").asText()))
                .map(link -> link.path("href").asText())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR));
        BigDecimal providerAmount = toPaypalAmount(payment.getAmount());

        return PaymentInitiationResult.builder()
                .paymentUrl(approvalUrl)
                .merchantReference(payment.getMerchantReference())
                .providerTransactionId(paypalOrderId)
                .providerAmount(providerAmount)
                .providerCurrency(currency)
                .build();
    }

    @Override
    public PaymentCallbackResult capture(Payment payment) {
        validateConfiguration();
        if (!StringUtils.hasText(payment.getTransactionId())) {
            throw new AppException(ErrorCode.PAYMENT_STATUS_INVALID);
        }

        JsonNode response = captureOrder(
                payment.getTransactionId(),
                getAccessToken(),
                payment.getMerchantReference() + "-capture",
                Map.of());
        JsonNode capture = response.path("purchase_units")
                .path(0)
                .path("payments")
                .path("captures")
                .path(0);
        String captureStatus = requiredText(capture, "status");

        return PaymentCallbackResult.builder()
                .merchantReference(payment.getMerchantReference())
                .providerTransactionId(requiredText(capture, "id"))
                .status(toPaymentStatus(captureStatus))
                .failureReason("COMPLETED".equals(captureStatus) ? null : "PayPal capture status: " + captureStatus)
                .amount(new BigDecimal(requiredText(capture.path("amount"), "value")))
                .currency(requiredText(capture.path("amount"), "currency_code"))
                .signatureValid(true)
                .build();
    }

    private String getAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        try {
            JsonNode response = paypalFeignClient.getAccessToken(basicAuth(), form);
            return requiredText(response, "access_token");
        } catch (FeignException exception) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, exception);
        }
    }

    private JsonNode createOrder(String accessToken, String requestId, Object body) {
        try {
            return paypalFeignClient.createOrder(bearerAuth(accessToken), requestId, body);
        } catch (FeignException exception) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, exception);
        }
    }

    private JsonNode captureOrder(String orderId, String accessToken, String requestId, Object body) {
        try {
            return paypalFeignClient.captureOrder(orderId, bearerAuth(accessToken), requestId, body);
        } catch (FeignException exception) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR, exception);
        }
    }

    private String basicAuth() {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String bearerAuth(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String requiredText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || !StringUtils.hasText(node.path(field).asText())) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
        return node.path(field).asText();
    }

    private PaymentStatus toPaymentStatus(String captureStatus) {
        return switch (captureStatus) {
            case "COMPLETED" -> PaymentStatus.COMPLETED;
            case "PENDING" -> PaymentStatus.PENDING;
            default -> PaymentStatus.FAILED;
        };
    }

    private BigDecimal toPaypalAmount(BigDecimal amountInVnd) {
        return amountInVnd.divide(vndPerCurrencyUnit, PAYPAL_AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(clientId)
                || !StringUtils.hasText(clientSecret)
                || !StringUtils.hasText(currency)
                || vndPerCurrencyUnit == null
                || vndPerCurrencyUnit.compareTo(BigDecimal.ZERO) <= 0
                || !StringUtils.hasText(returnUrl)
                || !StringUtils.hasText(cancelUrl)) {
            throw new AppException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }
}

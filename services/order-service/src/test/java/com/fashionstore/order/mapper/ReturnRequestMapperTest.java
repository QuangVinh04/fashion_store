package com.fashionstore.order.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fashionstore.order.entity.Order;
import com.fashionstore.order.entity.ReturnRequest;
import com.fashionstore.order.entity.enumeration.ReturnRequestKind;
import com.fashionstore.order.entity.enumeration.ReturnRequestStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

class ReturnRequestMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "REQUESTED, false, , false, NOT_STARTED",
        "RECEIVED, true, , false, PROCESSING",
        "RECEIVED, true, TIMEOUT, false, REVIEW_REQUIRED",
        "RECEIVED, true, REJECTED, false, REVIEW_REQUIRED",
        "REFUNDED, true, TIMEOUT, true, COMPLETED",
        "REFUNDED, true, , false, REVIEW_REQUIRED",
        "RECEIVED, false, , true, REVIEW_REQUIRED",
        "REFUND_PENDING, false, , false, REVIEW_REQUIRED",
        "LEGACY_REVIEW_REQUIRED, false, , false, REVIEW_REQUIRED",
        "CANCELLED, false, , false, NOT_STARTED",
        "REJECTED, false, , false, NOT_STARTED"
    })
    void refundStateSeparatesMoneyFromReturnProgress(
            ReturnRequestStatus status, boolean started, String failure,
            boolean receipt, String expected) {
        ReturnRequest request = ReturnRequest.builder().status(status)
                .refundStartedAt(started ? Instant.parse("2026-10-18T01:00:00Z") : null)
                .refundFailureReason(failure)
                .refundLedgerEntryId(receipt ? "ledger-1" : null).build();

        var json = objectMapper.valueToTree(ReturnRequestMapper.toResponse(request, objectMapper, null));

        assertThat(json.path("refundStatus").asText()).isEqualTo(expected);
        assertThat(json.path("status").asText()).isEqualTo(status.name());
    }

    @Test
    void pendingReturnShowsEstimateAndWalletWithoutClaimingMoneyWasRefunded() {
        Order order = Order.builder().totalAmount(new BigDecimal("125000.00"))
                .currency("VND").build();
        ReturnRequest request = ReturnRequest.builder().order(order)
                .status(ReturnRequestStatus.REQUESTED).build();

        var json = objectMapper.valueToTree(ReturnRequestMapper.toResponse(request, objectMapper, null));

        assertThat(json.path("expectedRefundAmount").asText()).isEqualTo("125000.00");
        assertThat(json.path("currency").asText()).isEqualTo("VND");
        assertThat(json.path("refundDestination").asText()).isEqualTo("FASHION_STORE_WALLET");
        assertThat(json.path("actualRefundAmount").isNull()).isTrue();
        assertThat(json.path("refundedAt").isNull()).isTrue();
    }

    @Test
    void confirmedRefundExposesExactAmountAndConfirmationTimestamp() {
        Instant confirmed = Instant.parse("2026-10-18T02:00:00Z");
        ReturnRequest request = ReturnRequest.builder()
                .order(Order.builder().totalAmount(new BigDecimal("125000.00")).currency("VND").build())
                .status(ReturnRequestStatus.REFUNDED)
                .approvedRefundAmount(new BigDecimal("125000.00"))
                .refundLedgerEntryId("ledger-1").closedAt(confirmed).build();

        var json = objectMapper.valueToTree(ReturnRequestMapper.toResponse(request, objectMapper, null));

        assertThat(json.path("actualRefundAmount").asText()).isEqualTo("125000.00");
        assertThat(json.path("refundedAt").asText()).isEqualTo("2026-10-18T02:00:00Z");
    }

    @Test
    void closingOrStartingReturnDoesNotClaimACompletedRefund() {
        for (ReturnRequestStatus status : List.of(ReturnRequestStatus.RECEIVED,
                ReturnRequestStatus.CANCELLED, ReturnRequestStatus.REFUNDED)) {
            ReturnRequest request = ReturnRequest.builder().status(status)
                    .approvedRefundAmount(new BigDecimal("125000.00"))
                    .refundStartedAt(Instant.parse("2026-10-18T01:00:00Z"))
                    .closedAt(Instant.parse("2026-10-18T02:00:00Z")).build();

            var json = objectMapper.valueToTree(ReturnRequestMapper.toResponse(request, objectMapper, null));

            assertThat(json.path("actualRefundAmount").isNull()).isTrue();
            assertThat(json.path("refundedAt").isNull()).isTrue();
        }
    }

    @Test
    void missingOrderDoesNotInventCurrencyOrEstimate() {
        var json = objectMapper.valueToTree(ReturnRequestMapper.toResponse(
                ReturnRequest.builder().status(ReturnRequestStatus.REQUESTED).build(), objectMapper, null));

        assertThat(json.path("expectedRefundAmount").isNull()).isTrue();
        assertThat(json.path("currency").isNull()).isTrue();
    }

    @Test
    void preservesReturnMoneyQualityEvidenceAndDeadlineInResponse() {
        Order order = Order.builder().orderCode("ORD-1").build();
        order.setId("order-1");
        Instant deadline = Instant.parse("2026-10-19T01:00:00Z");
        ReturnRequest request =
                ReturnRequest.builder()
                        .order(order)
                        .userId("user-1")
                        .kind(ReturnRequestKind.CARRIER_RETURN)
                        .status(ReturnRequestStatus.RECEIVED)
                        .reason("Damaged parcel")
                        .qcPassed(true)
                        .qcNote("Checked")
                        .qcEvidenceIds("[\"qc-1\"]")
                        .verificationBy("admin-1")
                        .verificationEvidenceIds("[\"proof-1\"]")
                        .images("[\"image-1\"]")
                        .approvedRefundAmount(new BigDecimal("100000.00"))
                        .refundLedgerEntryId("ledger-1")
                        .refundStartedAt(deadline.minusSeconds(60))
                        .build();
        request.setId("return-1");

        var response = ReturnRequestMapper.toResponse(request, objectMapper, deadline);

        assertThat(response.getId()).isEqualTo("return-1");
        assertThat(response.getOrderId()).isEqualTo("order-1");
        assertThat(response.getOrderCode()).isEqualTo("ORD-1");
        assertThat(response.getKind()).isEqualTo(ReturnRequestKind.CARRIER_RETURN);
        assertThat(response.getStatus()).isEqualTo(ReturnRequestStatus.RECEIVED);
        assertThat(response.getApprovedRefundAmount()).isEqualTo("100000.00");
        assertThat(response.getRefundLedgerEntryId()).isEqualTo("ledger-1");
        assertThat(response.getRefundStartedAt()).isEqualTo(deadline.minusSeconds(60));
        assertThat(response.getQcPassed()).isTrue();
        assertThat(response.getQcNote()).isEqualTo("Checked");
        assertThat(response.getQcEvidenceIds()).containsExactly("qc-1");
        assertThat(response.getVerificationBy()).isEqualTo("admin-1");
        assertThat(response.getVerificationEvidenceIds()).containsExactly("proof-1");
        assertThat(response.getImages()).containsExactly("image-1");
        assertThat(response.getSendBackBy()).isEqualTo(deadline);
    }

    @Test
    void keepsLegacyCommaSeparatedEvidenceAndEmptyValuesReadable() {
        assertThat(ReturnRequestMapper.deserializeImages(objectMapper, "image-1,image-2"))
                .containsExactly("image-1", "image-2");
        assertThat(ReturnRequestMapper.deserializeImages(objectMapper, null)).isEmpty();
        assertThat(ReturnRequestMapper.deserializeImages(objectMapper, " ")).isEmpty();
    }

    @Test
    void serializationFiltersBlankImagesAndKeepsTheirOrder() {
        var serialized =
                ReturnRequestMapper.serializeImages(
                        objectMapper, Arrays.asList("image-2", null, " ", "image-1"));
        assertThat(ReturnRequestMapper.deserializeImages(objectMapper, serialized))
                .containsExactly("image-2", "image-1");
        assertThat(ReturnRequestMapper.serializeImages(objectMapper, List.of())).isNull();
    }
}

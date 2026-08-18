package com.rikkei.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.LocalDate;
import java.util.List;

public class CustomerVoucherDto {

    public record VoucherItem(
        String voucherCode,
        String description,
        double discountPercent,
        LocalDate expiryDate
    ) {}

    public record VoucherQueryRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Mã định danh khách hàng cần tra cứu (ví dụ: KH888 hoặc số điện thoại)")
        String customerId
    ) {}

    public record VoucherQueryResponse(
        boolean isSuccess,
        String customerId,
        List<VoucherItem> availableVouchers,
        String message
    ) {
        public static VoucherQueryResponse success(String customerId, List<VoucherItem> vouchers) {
            return new VoucherQueryResponse(true, customerId, vouchers, "Tra cứu danh sách voucher thành công.");
        }
        public static VoucherQueryResponse failure(String msg) {
            return new VoucherQueryResponse(false, null, List.of(), msg);
        }
    }
}

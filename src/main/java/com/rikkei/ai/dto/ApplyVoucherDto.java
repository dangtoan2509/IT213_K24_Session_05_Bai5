package com.rikkei.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ApplyVoucherDto {

    public record ApplyVoucherRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Mã hóa đơn đặt phòng cần áp dụng mã giảm giá (ví dụ: HD999)")
        String invoiceId,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Mã giảm giá được chọn để áp dụng (ví dụ: VIP20)")
        String voucherCode
    ) {}

    public record ApplyVoucherResponse(
        boolean isSuccess,
        String invoiceId,
        String appliedVoucher,
        Double originalAmount,
        Double discountAmount,
        Double finalAmount,
        String message
    ) {
        public static ApplyVoucherResponse success(String invoiceId, String voucher, double orig, double disc, double fin, String msg) {
            return new ApplyVoucherResponse(true, invoiceId, voucher, orig, disc, fin, msg);
        }
        public static ApplyVoucherResponse failure(String msg) {
            return new ApplyVoucherResponse(false, null, null, 0.0, 0.0, 0.0, msg);
        }
    }
}

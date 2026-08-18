package com.rikkei.ai.service;

import com.rikkei.ai.dto.ApplyVoucherDto.ApplyVoucherRequest;
import com.rikkei.ai.dto.ApplyVoucherDto.ApplyVoucherResponse;
import com.rikkei.ai.dto.CustomerVoucherDto.VoucherItem;
import com.rikkei.ai.dto.CustomerVoucherDto.VoucherQueryRequest;
import com.rikkei.ai.dto.CustomerVoucherDto.VoucherQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CrmVoucherService {

    private static final Logger log = LoggerFactory.getLogger(CrmVoucherService.class);

    private static final Map<String, List<VoucherItem>> CRM_VOUCHERS_DB = new ConcurrentHashMap<>();
    private static final Map<String, InvoiceMock> INVOICE_DB = new ConcurrentHashMap<>();

    public record InvoiceMock(String invoiceId, double amount, boolean isPaid, String appliedVoucher) {}

    static {
        CRM_VOUCHERS_DB.put("KH888", List.of(
            new VoucherItem("VIP20", "Giảm 20% tổng hóa đơn cho thành viên VIP", 20.0, LocalDate.now().plusMonths(1)),
            new VoucherItem("WELCOME10", "Giảm 10% chào mừng khách hàng mới", 10.0, LocalDate.now().plusMonths(2))
        ));

        INVOICE_DB.put("HD999", new InvoiceMock("HD999", 1200000.0, false, null));
        INVOICE_DB.put("HD888", new InvoiceMock("HD888", 2000000.0, true, null));
    }

    @Tool(description = "Tra cứu toàn bộ danh sách mã giảm giá (voucher) còn hạn sử dụng của khách hàng dựa trên customerId (mã khách hàng hoặc số điện thoại).")
    public VoucherQueryResponse getCustomerVouchers(VoucherQueryRequest request) {
        log.info("Tool 1 [getCustomerVouchers] - Customer ID: {}", request.customerId());

        if (request == null || request.customerId() == null || request.customerId().isBlank()) {
            return VoucherQueryResponse.failure("Mã định danh khách hàng không được để trống.");
        }

        String customerId = request.customerId().trim().toUpperCase();
        List<VoucherItem> vouchers = CRM_VOUCHERS_DB.get(customerId);

        if (vouchers == null || vouchers.isEmpty()) {
            return VoucherQueryResponse.failure("Khách hàng " + customerId + " hiện không có mã giảm giá nào còn hiệu lực.");
        }

        return VoucherQueryResponse.success(customerId, vouchers);
    }

    @Tool(description = "Áp dụng một mã giảm giá (voucherCode) cụ thể vào hóa đơn đặt phòng (invoiceId) và cập nhật số tiền thanh toán thực tế vào hệ thống.")
    public ApplyVoucherResponse applyVoucherToInvoice(ApplyVoucherRequest request) {
        log.info("Tool 2 [applyVoucherToInvoice] - Hóa đơn: {}, Mã: {}", request.invoiceId(), request.voucherCode());

        if (request == null || request.invoiceId() == null || request.voucherCode() == null) {
            return ApplyVoucherResponse.failure("Thiếu thông tin hóa đơn hoặc mã giảm giá.");
        }

        String invoiceId = request.invoiceId().trim().toUpperCase();
        String voucherCode = request.voucherCode().trim().toUpperCase();

        InvoiceMock invoice = INVOICE_DB.get(invoiceId);
        if (invoice == null) {
            return ApplyVoucherResponse.failure("Không tìm thấy thông tin hóa đơn: " + invoiceId);
        }

        if (invoice.isPaid()) {
            return ApplyVoucherResponse.failure("Hóa đơn " + invoiceId + " đã được thanh toán trước đó, không thể áp dụng thêm mã giảm giá.");
        }

        double discountPercent = 0.0;
        if ("VIP20".equalsIgnoreCase(voucherCode)) {
            discountPercent = 20.0;
        } else if ("WELCOME10".equalsIgnoreCase(voucherCode)) {
            discountPercent = 10.0;
        } else {
            return ApplyVoucherResponse.failure("Mã giảm giá " + voucherCode + " không hợp lệ hoặc đã hết hạn.");
        }

        double origAmount = invoice.amount();
        double discountAmount = (origAmount * discountPercent) / 100.0;
        double finalAmount = origAmount - discountAmount;

        INVOICE_DB.put(invoiceId, new InvoiceMock(invoiceId, finalAmount, false, voucherCode));

        return ApplyVoucherResponse.success(
            invoiceId,
            voucherCode,
            origAmount,
            discountAmount,
            finalAmount,
            String.format("Áp dụng thành công mã %s (giảm %.0f%%) cho hóa đơn %s. Tiền giảm: %,.0f VNĐ, Tổng tiền mới: %,.0f VNĐ.",
                    voucherCode, discountPercent, invoiceId, discountAmount, finalAmount)
        );
    }
}

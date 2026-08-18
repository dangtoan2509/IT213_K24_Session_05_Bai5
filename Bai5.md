- Bài 5: Sáng tạo nâng cao - Thiết kế Trợ lý ảo tra cứu CRM & Áp dụng Voucher tự động

- Mô tả bối cảnh & Phân tích giải pháp chịu lỗi:
  - Bối cảnh nghiệp vụ:
    - Khách hàng gửi yêu cầu: "Áp dụng giúp tôi mã giảm giá tốt nhất của tôi vào đơn đặt phòng mã HD999 nhé."
    - Quy trình tự động hóa đa bước (Multi-step Agentic Workflow):
      - Bước 1 - Xác minh danh tính: AI kiểm tra trong ChatMemory xem khách hàng đã cung cấp mã định danh (customerId hoặc số điện thoại) chưa. Nếu chưa có, AI chủ động yêu cầu khách hàng cung cấp.
      - Bước 2 - Tra cứu CRM: Khi đã có Customer ID (ví dụ KH888), AI tự động gọi Tool getCustomerVouchers(customerId) để lấy danh sách mã giảm giá còn hạn.
      - Bước 3 - Suy luận & Chọn voucher tốt nhất: AI tự động so sánh mức giảm của các voucher (ví dụ VIP20 giảm 20% vs WELCOME10 giảm 10%) để chọn ra mã có ưu đãi cao nhất.
      - Bước 4 - Cập nhật hóa đơn: AI tự động gọi tiếp Tool applyVoucherToInvoice(invoiceId, voucherCode) để áp dụng vào hệ thống và cập nhật database.
      - Bước 5 - Tổng hợp câu trả lời: AI tổng hợp toàn bộ kết quả để trả lời tự nhiên cho khách hàng.
  - Phân tích giải pháp chịu lỗi:
    - Khi hóa đơn đã thanh toán trước đó hoặc không tồn tại: Tool trả về ApplyVoucherResponse.failure với thông điệp rõ ràng.
    - Khi mã voucher không hợp lệ hoặc đã hết hạn: Tool trả về kết quả failure.
    - Khi khách hàng không có voucher nào khả dụng: Tool trả về danh sách rỗng, AI thông báo lịch sự cho khách hàng.
    - Nguyên tắc thiết kế an toàn: Tất cả các phương thức Tool tuyệt đối không ném Exception làm sập ứng dụng. Khi gặp lỗi nghiệp vụ, Tool trả về cờ isSuccess = false kèm message để AI đọc và giải thích nhẹ nhàng cho khách hàng.

- Sơ đồ luồng xử lý dữ liệu (ASCII Flow Diagram):
```text
+----------------------------------------------------------------------------------------------------+
|                                    QUY TRÌNH XỬ LÝ AGENTIC CRM & VOUCHER                           |
+----------------------------------------------------------------------------------------------------+

 [Khách hàng]
      │
      │ 1. "Áp dụng giúp tôi mã giảm giá tốt nhất vào hóa đơn HD999"
      ▼
 [REST Controller / ChatClient]
      │
      │ 2. Đọc lịch sử hội thoại (ChatMemory)
      ▼
 [Kiểm tra Customer ID?] ──(Chưa có)──► [Phản hồi: "Vui lòng cung cấp SĐT hoặc Mã KH"] ──► [Khách hàng]
      │
      │ (Đã có: KH888)
      ▼
 [AI sinh Tool Call 1] ────────────────────────────────────────────────────────┐
      │                                                                         │
      ▼                                                                         │
 [Tool 1: getCustomerVouchers(KH888)]                                           │ (Vòng lặp ReAct)
      │                                                                         │
      ├── Tra cứu CRM Database                                                  │
      └── Trả về: [VIP20 (20%), WELCOME10 (10%)]                                │
      ▼                                                                         │
 [AI Reasoning: So sánh 20% > 10% -> Chọn VIP20]                               │
      │                                                                         │
      ▼                                                                         │
 [AI sinh Tool Call 2] ────────────────────────────────────────────────────────┤
      │                                                                         │
      ▼                                                                         │
 [Tool 2: applyVoucherToInvoice(HD999, VIP20)]                                  │
      │                                                                         │
      ├── Kiểm tra trạng thái hóa đơn & cập nhật Database                       │
      └── Trả về: {isSuccess: true, discountAmount: 240000, finalPrice: 960000} │
      ▼                                                                         │
 [AI Tổng hợp câu trả lời tự nhiên] ◄───────────────────────────────────────────┘
      │
      │ 3. "Đã áp dụng thành công mã VIP20 (giảm 20%) cho hóa đơn HD999.
      │     Tổng tiền thanh toán sau giảm: 960.000 VNĐ."
      ▼
 [Khách hàng]
```

- Mã nguồn Java triển khai (DTOs, Service Tools, REST Controller):
  - DTO Tool 1: CustomerVoucherDto.java
```java
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
```

  - DTO Tool 2: ApplyVoucherDto.java
```java
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
```

  - Service thực thi 2 Tools nghiệp vụ: CrmVoucherService.java
```java
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
```

  - REST Controller: CrmChatController.java
```java
package com.rikkei.ai.controller;

import com.rikkei.ai.dto.ChatResponseDto;
import com.rikkei.ai.service.CrmVoucherService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/crm-chat")
public class CrmChatController {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            Bạn là Trợ lý ảo CSKH cao cấp của R-Hotels (CRM Support Agent).
            Hôm nay là ngày: {current_date}.

            === QUY TRÌNH XỬ LÝ KHI KHÁCH HÀNG YÊU CẦU ÁP DỤNG MÃ GIẢM GIÁ / VOUCHER ===
            1. BƯỚC 1: XÁC MINH DANH TÍNH KHÁCH HÀNG
               - Hãy tra cứu trong lịch sử trò chuyện xem khách hàng đã cung cấp thông tin định danh (Customer ID hoặc Số điện thoại) chưa.
               - NẾU CHƯA CÓ: Bạn PHẢI dừng lại và hỏi khách hàng cung cấp Mã khách hàng (hoặc SĐT).
               - NẾU ĐÃ CÓ: Lấy Customer ID đó để thực hiện Bước 2.
            2. BƯỚC 2: TRA CỨU CRM
               - Tự động gọi công cụ 'getCustomerVouchers' để lấy danh sách mã giảm giá còn hạn.
            3. BƯỚC 3: LỰA CHỌN MÃ TỐT NHẤT & CẬP NHẬT
               - So sánh phần trăm giảm giá của các voucher trả về, chọn ra voucher có mức giảm cao nhất.
               - Tự động gọi công cụ 'applyVoucherToInvoice' để cập nhật hóa đơn.
            4. BƯỚC 4: PHẢN HỒI
               - Thông báo rõ ràng mã voucher đã chọn, số tiền được giảm và tổng số tiền sau khi giảm cho khách hàng.
            """;

    public CrmChatController(ChatClient.Builder builder,
                             CrmVoucherService crmVoucherService,
                             ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultTools(crmVoucherService)
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    @GetMapping("/chat")
    public ChatResponseDto chat(
            @RequestParam(required = false) String conversationId,
            @RequestParam String message) {

        String activeConversationId = (conversationId == null || conversationId.trim().isEmpty())
                ? UUID.randomUUID().toString()
                : conversationId.trim();

        String reply = this.chatClient.prompt()
                .system(sp -> sp.text(SYSTEM_PROMPT).param("current_date", LocalDate.now().toString()))
                .user(message)
                .advisors(advisor -> advisor.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, activeConversationId))
                .call()
                .content();

        return new ChatResponseDto(activeConversationId, reply);
    }
}
```

- Phân tích kỹ thuật chuyên sâu về luồng gọi tool liên tiếp:
  - Vòng lặp ReAct trong Spring AI:
    - Lượt suy luận 1: LLM nhận Prompt và danh sách Tools, quyết định gọi Tool 1 là getCustomerVouchers(customerId: "KH888").
    - Spring AI thực thi Tool 1: Spring AI gọi hàm Java và trả về danh sách JSON [VIP20 (20%), WELCOME10 (10%)].
    - Lượt suy luận 2: Spring AI gửi output của Tool 1 lại cho LLM. LLM suy luận so sánh 20% > 10%, chọn VIP20 và sinh tiếp Tool Call thứ hai: applyVoucherToInvoice(invoiceId: "HD999", voucherCode: "VIP20").
    - Spring AI thực thi Tool 2: Database được cập nhật và trả về JSON kết quả thành công.
    - Lượt suy luận 3: LLM nhận kết quả của Tool 2 và tổng hợp thành câu trả lời tự nhiên cuối cùng gửi cho khách hàng.
  - Ý nghĩa kiến trúc:
    - Tách biệt hoàn toàn tầng Business Logic (các hàm Java thuần túy làm nhiệm vụ chuyên biệt) khỏi tầng Điều phối quy trình (Orchestration do LLM tự động suy luận và xâu chuỗi).

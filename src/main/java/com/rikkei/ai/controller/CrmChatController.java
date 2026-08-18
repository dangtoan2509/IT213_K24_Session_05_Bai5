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

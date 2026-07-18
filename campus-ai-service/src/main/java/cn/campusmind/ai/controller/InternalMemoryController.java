package cn.campusmind.ai.controller;

import cn.campusmind.ai.application.ConversationMemory;
import cn.campusmind.common.web.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai/users")
public class InternalMemoryController {

    private final ConversationMemory conversationMemory;

    public InternalMemoryController(ConversationMemory conversationMemory) {
        this.conversationMemory = conversationMemory;
    }

    @DeleteMapping("/{userId}/chat-memory")
    public ApiResponse<Void> deleteChatMemory(@PathVariable Long userId) {
        conversationMemory.forgetUser(userId);
        return ApiResponse.ok(null);
    }
}

package com.rikkei.ai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseChatMemoryConfig {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public ChatMemory jdbcChatMemory(JdbcTemplate jdbcTemplate) {
        return new JdbcChatMemory(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory inMemoryChatMemoryFallback() {
        return new InMemoryChatMemory();
    }
}

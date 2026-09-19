package com.chat.server.controller;

import com.chat.server.config.JwtUtil;
import com.chat.server.entity.Message;
import com.chat.server.entity.User;
import com.chat.server.service.ChatService;
import com.chat.server.service.MessageService;
import com.chat.server.service.MessageStatusService;
import com.chat.server.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Регрессионные тесты на маршрутизацию {@link MessageController}.
 * <p>
 * Раньше {@code GET /api/messages/{chatUuid}} ("список сообщений чата") и
 * {@code GET /api/messages/by-uuid/{messageUuid}} ("сообщение по UUID") конфликтовали между собой:
 * сегмент {@code by-uuid} подставлялся в {@code {chatUuid}}. Этот тест гарантирует, что оба маппинга
 * ведут на свои методы и что литеральный сегмент {@code by-uuid} имеет приоритет над path-переменной.
 */
@WebMvcTest(MessageController.class)
@AutoConfigureMockMvc(addFilters = false)
class MessageControllerMappingTest {

    private final UUID chatUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID messageUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    @MockitoBean
    private MessageStatusService messageStatusService;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private org.springframework.test.web.servlet.request.RequestPostProcessor authPrincipal() {
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "7", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        return request -> {
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    private void stubCommon() {
        when(chatService.getChatIdByUuid(chatUuid)).thenReturn(5L);

        User sender = User.builder()
                .userId(7L)
                .userUuid(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .username("alice")
                .firstName("Alice")
                .build();
        when(userService.getUsersByIds(any())).thenReturn(List.of(sender));
        when(userService.getUserById(7L)).thenReturn(sender);
    }

    private Message message(UUID uuid) {
        Message message = Message.builder()
                .messageId(1L)
                .messageUuid(uuid)
                .chatId(5L)
                .senderId(7L)
                .messageText("hello")
                .messageType(Message.MessageType.TEXT)
                .build();
        message.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        message.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return message;
    }

    @Test
    void getMessages_mapsToChatHistoryEndpoint() throws Exception {
        stubCommon();
        when(messageService.getChatMessages(eq(5L), eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(message(messageUuid)), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/messages/{chatUuid}", chatUuid).with(authPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.content[0].messageUuid").value(messageUuid.toString()));

        verify(messageService).getChatMessages(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq(7L), any());
        verify(messageService, never()).getMessageByUuid(any());
    }

    @Test
    void getMessageByUuid_mapsToByUuidEndpoint() throws Exception {
        stubCommon();
        when(messageService.getMessageByUuid(messageUuid)).thenReturn(message(messageUuid));

        mockMvc.perform(get("/api/messages/by-uuid/{messageUuid}", messageUuid).with(authPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.messageUuid").value(messageUuid.toString()))
                .andExpect(jsonPath("$.text").value("hello"))
                .andExpect(jsonPath("$.senderId").value(7));

        verify(messageService).getMessageByUuid(messageUuid);
        verify(messageService, never()).getChatMessages(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq(7L), any());
        verify(chatService).validateUserAccessToChat(5L, 7L);
    }

    @Test
    void getMessagesBefore_literalSegmentTakesPrecedenceOverPathVariable() throws Exception {
        stubCommon();
        UUID beforeMessageUuid = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(messageService.getMessagesBeforeMessage(5L, 7L, beforeMessageUuid, 50))
                .thenReturn(List.of(message(beforeMessageUuid)));

        mockMvc.perform(get("/api/messages/{chatUuid}/before/{messageUuid}", chatUuid, beforeMessageUuid).with(authPrincipal())
                        )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].messageUuid").value(beforeMessageUuid.toString()));

        verify(messageService).getMessagesBeforeMessage(5L, 7L, beforeMessageUuid, 50);
        verify(messageService, never()).getMessageByUuid(any());
    }
}
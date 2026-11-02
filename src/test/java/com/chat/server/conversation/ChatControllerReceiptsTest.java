package com.chat.server.conversation;

import com.chat.server.identity.JwtUtil;
import com.chat.server.identity.OidcProperties;
import com.chat.server.identity.OidcUserProvisioner;
import com.chat.server.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerReceiptsTest {

    private final UUID chatUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ChatAvatarService chatAvatarService;

    @MockitoBean
    private MessageStatusService messageStatusService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private OidcUserProvisioner oidcUserProvisioner;

    @MockitoBean
    private OidcProperties oidcProperties;

    private org.springframework.test.web.servlet.request.RequestPostProcessor authPrincipal() {
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "7", null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        return request -> {
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    @Test
    void receiptsReturnParticipantWatermarksAndNames() throws Exception {
        when(chatService.getChatIdByUuid(chatUuid)).thenReturn(5L);
        when(messageStatusService.getChatReceipts(5L, 7L)).thenReturn(List.of(
                ChatReceiptDto.builder()
                        .userUuid(userUuid)
                        .username("anna")
                        .fullName("Anna")
                        .avatarUrl("https://cdn/a.jpg")
                        .joinedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                        .lastDeliveredMessageId(12L)
                        .lastDeliveredAt(LocalDateTime.of(2026, 1, 1, 10, 1))
                        .lastReadMessageId(11L)
                        .lastReadAt(LocalDateTime.of(2026, 1, 1, 10, 2))
                        .build()
        ));

        mockMvc.perform(get("/api/chats/{chatUuid}/receipts", chatUuid).with(authPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userUuid").value(userUuid.toString()))
                .andExpect(jsonPath("$[0].username").value("anna"))
                .andExpect(jsonPath("$[0].fullName").value("Anna"))
                .andExpect(jsonPath("$[0].lastDeliveredMessageId").value(12))
                .andExpect(jsonPath("$[0].lastReadMessageId").value(11));
    }
}

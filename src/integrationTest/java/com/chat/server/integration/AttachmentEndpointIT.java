package com.chat.server.integration;

import com.chat.server.conversation.AttachmentService;
import com.chat.server.conversation.ChatService;
import com.chat.server.conversation.Message;
import com.chat.server.conversation.MessageService;
import com.chat.server.identity.User;
import com.chat.server.identity.UserService;
import com.chat.server.notification.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-сценарии вложений: загрузка, скачивание целиком и по Range, превью,
 * списки по сообщению/чату/медиа и удаление.
 */
@AutoConfigureMockMvc
class AttachmentEndpointIT extends AbstractIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService users;
    @Autowired
    private ChatService chats;
    @Autowired
    private MessageService messages;
    @Autowired
    private AttachmentService attachments;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    private final ObjectMapper json = new ObjectMapper();

    private User owner;
    private User peer;
    private UUID chatUuid;

    @BeforeEach
    void setUp() {
        owner = users.createUser("att_owner");
        peer = users.createUser("att_peer");
        chatUuid = chats.createPrivateChat(owner.getUserId(), peer.getUserUuid()).getChatUuid();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png", PNG);
    }

    private String upload() throws Exception {
        String body = mvc.perform(multipart("/api/attachments/upload")
                        .file(image())
                        .param("chatUuid", chatUuid.toString())
                        .param("width", "10")
                        .param("height", "20")
                        .with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("photo.png"))
                .andExpect(jsonPath("$.type").value("IMAGE"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("attachmentUuid").asText();
    }

    @Test
    void uploadDownloadRangePreviewListAndDelete() throws Exception {
        String uuid = upload();

        mvc.perform(get("/api/attachments/{attachmentUuid}", uuid).with(auth(peer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));

        mvc.perform(get("/api/attachments/{attachmentUuid}", uuid)
                        .header("Range", "bytes=0-3")
                        .with(auth(owner)))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-3/8"))
                .andExpect(content().bytes(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));

        mvc.perform(get("/api/attachments/{attachmentUuid}", uuid)
                        .header("Range", "not-a-range")
                        .with(auth(owner)))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(get("/api/attachments/{attachmentUuid}", uuid)
                        .header("Range", "bytes=0-1,3-4")
                        .with(auth(owner)))
                .andExpect(status().isRequestedRangeNotSatisfiable());

        mvc.perform(get("/api/attachments/{attachmentUuid}/preview", uuid).with(auth(owner)))
                .andExpect(status().isOk());

        Message message = messages.sendMessage(
                chats.getChatIdByUuid(chatUuid), owner.getUserId(), "with file",
                Message.MessageType.TEXT, null, null);
        attachments.attachToMessage(UUID.fromString(uuid), message.getMessageUuid());

        mvc.perform(get("/api/attachments/message/{messageUuid}", message.getMessageUuid())
                        .with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/attachments/chat/{chatUuid}", chatUuid).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/attachments/chat/{chatUuid}/media", chatUuid).with(auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/api/attachments/{attachmentUuid}", uuid).with(auth(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attachments/{attachmentUuid}", uuid).with(auth(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void strangersCannotUploadOrDownload() throws Exception {
        String uuid = upload();
        User stranger = users.createUser("att_stranger");

        mvc.perform(get("/api/attachments/{attachmentUuid}", uuid).with(auth(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/attachments/upload")
                        .file(image())
                        .param("chatUuid", chatUuid.toString())
                        .with(auth(stranger)))
                .andExpect(status().isForbidden());
    }
}

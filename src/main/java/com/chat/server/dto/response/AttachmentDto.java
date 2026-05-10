package com.chat.server.dto.response;

import com.chat.server.entity.Attachment;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AttachmentDto {

    private UUID attachmentUuid;
    private String fileName;
    private String fileUrl;
    private String thumbnailUrl;
    private Long fileSize;
    private String mimeType;
    private String type;
    private Integer width;
    private Integer height;
    private Integer duration;

    public static AttachmentDto fromEntity(Attachment attachment) {
        return AttachmentDto.builder()
                .attachmentUuid(attachment.getAttachmentUuid())
                .fileName(attachment.getFileName())
                .fileUrl(attachment.getFileUrl())
                .thumbnailUrl(attachment.getThumbnailUrl())
                .fileSize(attachment.getFileSize())
                .mimeType(attachment.getMimeType())
                .type(attachment.getType().name())
                .width(attachment.getWidth())
                .height(attachment.getHeight())
                .duration(attachment.getDuration())
                .build();
    }
}
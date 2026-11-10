package com.chat.server.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ErrorTelemetryControllerTest {

    private final ErrorEventRepository repository = mock(ErrorEventRepository.class);
    private final ErrorTelemetryController controller = new ErrorTelemetryController(repository);

    private ErrorReportRequest.ErrorEventDto dto(String eventId) {
        ErrorReportRequest.ErrorEventDto dto = new ErrorReportRequest.ErrorEventDto();
        dto.setEventId(eventId);
        dto.setTime("2026-11-10T10:00:00Z");
        dto.setLevel("ERROR");
        dto.setTag("test");
        dto.setMessage("boom");
        dto.setStack("stack");
        dto.setAppVersion("1.7.30");
        dto.setVersionCode(64);
        dto.setBuildType("release");
        dto.setDevice("Infinix Note 40");
        dto.setOs("Android 16");
        return dto;
    }

    @Test
    void savesNewEventsAndSkipsDuplicates() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.existsByEventId(first)).thenReturn(false);
        when(repository.existsByEventId(second)).thenReturn(true);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("13");

        ErrorReportRequest request = new ErrorReportRequest();
        request.setEvents(List.of(dto(first.toString()), dto(second.toString())));
        request.setOverflowed(true);

        var response = controller.upload(request, auth);

        assertEquals(1, response.getBody().get("accepted"));
        var captor = org.mockito.ArgumentCaptor.forClass(ErrorEvent.class);
        verify(repository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().isBufferOverflowed());
    }

    @Test
    void invalidEventIdIsIgnored() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("13");
        ErrorReportRequest request = new ErrorReportRequest();
        request.setEvents(List.of(dto("not-a-uuid")));

        var response = controller.upload(request, auth);

        assertEquals(0, response.getBody().get("accepted"));
        verify(repository, never()).save(any());
    }
}

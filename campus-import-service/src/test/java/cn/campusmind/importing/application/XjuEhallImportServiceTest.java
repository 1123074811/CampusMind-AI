package cn.campusmind.importing.application;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.config.XjuEhallProperties;
import cn.campusmind.importing.controller.XjuEhallImportRequest;
import cn.campusmind.importing.controller.XjuEhallImportResponse;
import cn.campusmind.importing.domain.ImportTask;
import cn.campusmind.importing.feign.UserPrivacyFeignClient;
import cn.campusmind.importing.infrastructure.mapper.ImportTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XjuEhallImportServiceTest {

    @Test
    void expandsWeeklyCourseAndImportsPrivateEvents() {
        ImportService importService = mock(ImportService.class);
        ImportTaskMapper taskMapper = mock(ImportTaskMapper.class);
        EventServiceClient events = mock(EventServiceClient.class);
        UserPrivacyFeignClient privacy = mock(UserPrivacyFeignClient.class);
        XjuEhallProperties properties = new XjuEhallProperties(
                true,
                "https://ehall.xju.edu.cn/new/index.html",
                List.of("authserver.xju.edu.cn", "ehall.xju.edu.cn"),
                List.of("jw.xju.edu.cn"),
                1,
                "2026-07-18-v1",
                2097152,
                500,
                800,
                15);
        doAnswer(invocation -> {
            ImportTask task = invocation.getArgument(0);
            task.setId(99L);
            return 1;
        }).when(taskMapper).insert(any(ImportTask.class));
        when(events.createEvent(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(privacy.privacy()).thenReturn(ApiResponse.ok(new UserPrivacyFeignClient.PrivacyStatus(
                "2026-07-18-v1",
                365,
                List.of(new UserPrivacyFeignClient.Consent(
                        1L,
                        "ACADEMIC_DATA_IMPORT",
                        "2026-07-18-v1",
                        true,
                        "APP",
                        List.of("TIMETABLE"),
                        OffsetDateTime.now().toString())))));

        XjuEhallImportService service = new XjuEhallImportService(
                properties, importService, taskMapper, events, privacy, new ObjectMapper().findAndRegisterModules());
        XjuEhallImportRequest request = new XjuEhallImportRequest(
                1,
                "XJU_EHALL",
                "2026-07-18-v1",
                OffsetDateTime.now(),
                List.of("jw.xju.edu.cn"),
                List.of("TIMETABLE"),
                new XjuEhallImportRequest.Semester(
                        "2026-2027-1",
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2027, 1, 10),
                        "Asia/Shanghai"),
                List.of(new XjuEhallImportRequest.Item(
                        "course-1",
                        "COURSE",
                        "软件工程",
                        "SE1001",
                        "软件工程",
                        "张老师",
                        null,
                        null,
                        null,
                        "A101",
                        null,
                        new XjuEhallImportRequest.Schedule(
                                List.of(1, 2), 1, 1, 2,
                                LocalTime.of(10, 0), LocalTime.of(11, 40))
                )));

        XjuEhallImportResponse response = service.importData(
                new CurrentUser(1L, "alice", "STUDENT"), request);

        assertEquals("SUCCESS", response.status());
        assertEquals(2, response.summary().success());
        assertEquals(2, response.summary().total());
        verify(events, times(2)).createEvent(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }
}

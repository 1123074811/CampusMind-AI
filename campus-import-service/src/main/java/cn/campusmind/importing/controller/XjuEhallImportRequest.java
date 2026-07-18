package cn.campusmind.importing.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record XjuEhallImportRequest(
        @Min(1) int schemaVersion,
        @NotBlank @Pattern(regexp = "XJU_EHALL") String provider,
        @NotBlank @Size(max = 32) String consentVersion,
        @NotNull OffsetDateTime collectedAt,
        @NotEmpty @Size(max = 8) List<@Pattern(regexp = "[A-Za-z0-9.-]{1,253}") String> originHosts,
        @NotEmpty @Size(max = 3) List<@Pattern(regexp = "TIMETABLE|EXAM|HOMEWORK") String> scopes,
        @NotNull @Valid Semester semester,
        @NotEmpty @Valid List<Item> items
) {
    public record Semester(
            @NotBlank @Size(max = 64) String code,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Pattern(regexp = "Asia/Shanghai") String timezone
    ) { }

    public record Item(
            @Size(max = 128) String providerItemId,
            @NotBlank @Pattern(regexp = "COURSE|HOMEWORK|EXAM|COURSE_CHANGE") String type,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 128) String courseCode,
            @Size(max = 255) String courseName,
            @Size(max = 128) String teacherName,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            OffsetDateTime deadline,
            @Size(max = 255) String location,
            @Size(max = 4000) String description,
            @Valid Schedule schedule
    ) { }

    public record Schedule(
            @NotEmpty @Size(max = 30) List<@Min(1) @Max(30) Integer> weekNumbers,
            @Min(1) @Max(7) int weekday,
            @Min(1) @Max(20) int startSection,
            @Min(1) @Max(20) int endSection,
            @NotNull LocalTime startClock,
            @NotNull LocalTime endClock
    ) { }
}

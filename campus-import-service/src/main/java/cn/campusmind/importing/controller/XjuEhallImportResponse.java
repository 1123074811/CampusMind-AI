package cn.campusmind.importing.controller;

import java.util.List;
import java.util.Map;

public record XjuEhallImportResponse(
        Long taskId,
        String status,
        String message,
        Summary summary
) {
    public record Summary(
            int total,
            int success,
            int skipped,
            int failed,
            Map<String, Integer> byType,
            List<FailureReason> failureReasons
    ) { }

    public record FailureReason(int index, String code, String message) { }
}

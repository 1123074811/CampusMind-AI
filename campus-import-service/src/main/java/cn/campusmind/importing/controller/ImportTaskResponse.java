package cn.campusmind.importing.controller;

public record ImportTaskResponse(
        Long taskId,
        String status,
        String message
) {
}

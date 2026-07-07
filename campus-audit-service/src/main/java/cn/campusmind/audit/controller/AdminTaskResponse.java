package cn.campusmind.audit.controller;

public record AdminTaskResponse(
        Long id,
        String name,
        String status,
        String owner,
        String time,
        String note
) {
}

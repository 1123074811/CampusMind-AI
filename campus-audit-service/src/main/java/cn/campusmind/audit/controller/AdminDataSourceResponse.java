package cn.campusmind.audit.controller;

public record AdminDataSourceResponse(
        Long id,
        String name,
        String channel,
        String status,
        String lastSync,
        int successRate,
        long pending
) {
}

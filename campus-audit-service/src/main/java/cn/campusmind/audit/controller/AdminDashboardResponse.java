package cn.campusmind.audit.controller;

import java.util.List;

public record AdminDashboardResponse(
        MetricsResponse metrics,
        List<AdminEventResponse> events,
        long eventTotal,
        long eventPage,
        long eventPageSize,
        List<AdminDataSourceResponse> dataSources,
        List<AdminTaskResponse> tasks
) {
}

package cn.campusmind.audit.controller;

import java.util.List;

public record AdminDashboardResponse(
        MetricsResponse metrics,
        List<AdminEventResponse> events,
        List<AdminDataSourceResponse> dataSources,
        List<AdminTaskResponse> tasks
) {
}

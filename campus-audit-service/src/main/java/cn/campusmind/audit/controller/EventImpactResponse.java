package cn.campusmind.audit.controller;

public record EventImpactResponse(
        Long eventId,
        long pendingReminders,
        long dueReminders,
        long activeDeliveries,
        long affectedUsers
) { }

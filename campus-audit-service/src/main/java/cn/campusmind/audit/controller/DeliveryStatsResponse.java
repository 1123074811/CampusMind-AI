package cn.campusmind.audit.controller;

public record DeliveryStatsResponse(
        long total,
        long pending,
        long sending,
        long sent,
        long retry,
        long failed,
        long withdrawn
) { }

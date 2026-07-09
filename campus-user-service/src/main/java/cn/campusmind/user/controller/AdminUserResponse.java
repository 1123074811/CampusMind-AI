package cn.campusmind.user.controller;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String username,
        String phone,
        String role,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

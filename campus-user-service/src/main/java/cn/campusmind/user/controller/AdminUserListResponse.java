package cn.campusmind.user.controller;

import java.util.List;

public record AdminUserListResponse(
        List<AdminUserResponse> items,
        long total
) {
}

package cn.campusmind.user.controller;

public record UserMeResponse(
        Long id,
        String username,
        String phone,
        String role,
        Integer status,
        UserProfileResponse profile
) {
}

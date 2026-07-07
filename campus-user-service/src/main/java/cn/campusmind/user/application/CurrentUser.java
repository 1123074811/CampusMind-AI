package cn.campusmind.user.application;

public record CurrentUser(
        Long userId,
        String username,
        String role
) {
}

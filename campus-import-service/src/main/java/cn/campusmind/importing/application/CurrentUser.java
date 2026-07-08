package cn.campusmind.importing.application;

public record CurrentUser(
        Long userId,
        String username,
        String role
) {
}

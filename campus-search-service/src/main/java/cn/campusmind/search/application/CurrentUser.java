package cn.campusmind.search.application;

public record CurrentUser(
        Long userId,
        String username,
        String role
) {
}

package cn.campusmind.user.controller;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        String college,
        String major,
        String grade,
        String className,
        List<String> interestTags,
        List<String> courseCodes,
        LocalDateTime updatedAt
) {
}

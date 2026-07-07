package cn.campusmind.user.controller;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProfileRequest(
        @Size(max = 128)
        String college,

        @Size(max = 128)
        String major,

        @Size(max = 32)
        String grade,

        @Size(max = 128)
        String className,

        @Size(max = 20)
        List<@Size(max = 32) String> interestTags,

        @Size(max = 50)
        List<@Size(max = 64) String> courseCodes
) {
}

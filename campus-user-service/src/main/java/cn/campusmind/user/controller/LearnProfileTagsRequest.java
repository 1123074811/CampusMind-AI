package cn.campusmind.user.controller;

import jakarta.validation.constraints.Size;

import java.util.List;

public record LearnProfileTagsRequest(
        @Size(max = 8)
        List<@Size(max = 32) String> tags
) {
}

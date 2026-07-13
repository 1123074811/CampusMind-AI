package cn.campusmind.user.controller;

import java.util.List;

public record ProfileTagsResponse(
        List<String> tags,
        double sensitivity
) {
}

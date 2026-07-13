package cn.campusmind.user.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProfileTagsRequest(
        @Size(max = 20)
        List<@Size(max = 32) String> tags,

        @Min(0) @Max(1)
        double sensitivity
) {
}

package cn.campusmind.audit.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchReviewRequest(
        @NotEmpty List<Long> ids,
        @NotBlank @Size(max = 32) String status,
        @Size(max = 500) String comment
) { }

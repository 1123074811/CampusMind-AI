package cn.campusmind.feed.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInformationItemRequest(
        @NotBlank @Size(max = 512) String title,
        @NotBlank String detailContent,
        @NotBlank @Size(max = 128) String sourceName,
        @Size(max = 1024) String sourceUrl,
        @Size(max = 1024) String itemUrl,
        @NotBlank @Size(max = 64) String contentHash,
        @Size(max = 64) String sourceType
) {
}

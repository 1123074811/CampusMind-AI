package cn.campusmind.audit.controller;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchDeleteRequest(
        @NotEmpty
        List<Long> ids
) {
}

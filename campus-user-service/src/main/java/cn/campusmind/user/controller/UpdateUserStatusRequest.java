package cn.campusmind.user.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateUserStatusRequest(
        @Min(0)
        @Max(1)
        Integer status
) {
}

package cn.campusmind.auth.controller;

import jakarta.validation.constraints.NotBlank;

public record SsoExchangeRequest(@NotBlank String code) {
}

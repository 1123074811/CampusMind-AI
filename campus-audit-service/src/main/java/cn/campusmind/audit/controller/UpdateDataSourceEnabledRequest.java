package cn.campusmind.audit.controller;

import jakarta.validation.constraints.NotNull;

public record UpdateDataSourceEnabledRequest(@NotNull Boolean enabled) {
}

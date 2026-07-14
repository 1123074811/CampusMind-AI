package cn.campusmind.audit.controller;

import jakarta.validation.constraints.Min;

public record RollbackDataSourceRequest(@Min(1) int versionNo) { }

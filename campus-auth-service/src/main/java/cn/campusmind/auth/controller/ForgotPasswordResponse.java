package cn.campusmind.auth.controller;

public record ForgotPasswordResponse(boolean accepted, String developmentResetToken) { }

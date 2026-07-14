package cn.campusmind.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.auth.cookie")
public record SessionCookieProperties(boolean secure) { }

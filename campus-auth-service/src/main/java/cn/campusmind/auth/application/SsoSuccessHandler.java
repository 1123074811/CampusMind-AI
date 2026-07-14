package cn.campusmind.auth.application;

import cn.campusmind.auth.config.SsoProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Profile("sso")
public class SsoSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService authService;
    private final SsoCodeService ssoCodeService;
    private final SsoProperties properties;

    public SsoSuccessHandler(AuthService authService, SsoCodeService ssoCodeService, SsoProperties properties) {
        this.authService = authService;
        this.ssoCodeService = ssoCodeService;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OidcUser user = (OidcUser) authentication.getPrincipal();
        String username = firstNonBlank(user.getPreferredUsername(), user.getEmail(), user.getSubject());
        String code = ssoCodeService.create(authService.loginSso(username));
        response.sendRedirect(UriComponentsBuilder.fromUriString(properties.callbackUrl())
                .queryParam("code", code).build(true).toUriString());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        throw new IllegalArgumentException("OIDC identity has no usable username");
    }
}

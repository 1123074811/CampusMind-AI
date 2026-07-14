package cn.campusmind.auth.application;

import cn.campusmind.auth.config.AuthProperties;
import cn.campusmind.auth.domain.UserAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtTokenService {

    private final AuthProperties properties;
    private final SecretKey signingKey;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenIssue issueAccessToken(UserAccount user, String sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtlMinutes(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .id(sessionId)
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new TokenIssue(token, expiresAt);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record TokenIssue(String token, Instant expiresAt) {
    }
}

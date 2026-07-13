package cn.campusmind.audit.application;

import cn.campusmind.common.config.JwtAuthProperties;
import cn.campusmind.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class AuditTokenService {

    private final JwtAuthProperties properties;

    public AuditTokenService(JwtAuthProperties properties) {
        this.properties = properties;
    }

    public Long requireAdmin(String authorization) {
        CurrentOperator operator = requireOperatorOrAdmin(authorization);
        if (!"ADMIN".equals(operator.role())) {
            throw forbidden("仅管理员可执行此操作");
        }
        return operator.userId();
    }

    public CurrentOperator requireOperatorOrAdmin(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw forbidden("缺少后台访问令牌");
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).build()
                    .parseSignedClaims(authorization.substring("Bearer ".length()).trim()).getPayload();
            String role = claims.get("role", String.class);
            if (!"ADMIN".equals(role) && !"OPERATOR".equals(role)) {
                throw forbidden("仅管理员或运营可访问后台");
            }
            return new CurrentOperator(Long.valueOf(claims.getSubject()), role);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw forbidden("访问令牌无效或已过期");
        }
    }

    public record CurrentOperator(Long userId, String role) {
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}

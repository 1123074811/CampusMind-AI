package cn.campusmind.audit.application;

import cn.campusmind.audit.config.AuditAuthProperties;
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

    private final AuditAuthProperties properties;

    public AuditTokenService(AuditAuthProperties properties) {
        this.properties = properties;
    }

    public Long requireAdmin(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw forbidden("缺少管理员访问令牌");
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).build()
                    .parseSignedClaims(authorization.substring("Bearer ".length()).trim()).getPayload();
            if (!"ADMIN".equals(claims.get("role", String.class))) {
                throw forbidden("仅管理员可修改智能体配置");
            }
            return Long.valueOf(claims.getSubject());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw forbidden("访问令牌无效或已过期");
        }
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}

package cn.campusmind.gateway.security;

/**
 * 网关鉴权失败时抛出，由 {@link GatewayWebExceptionHandler} 统一捕获
 * 并转换为 401 ApiResponse JSON 响应。
 */
public class GatewayAuthenticationException extends RuntimeException {

    public GatewayAuthenticationException(String message) {
        super(message);
    }
}

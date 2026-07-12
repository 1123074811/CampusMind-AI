package cn.campusmind.gateway.security;

public class GatewayAccessDeniedException extends RuntimeException {
    public GatewayAccessDeniedException(String message) {
        super(message);
    }
}

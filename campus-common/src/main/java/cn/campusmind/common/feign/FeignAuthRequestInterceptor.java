package cn.campusmind.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求头传递拦截器：在服务间调用时自动透传用户身份信息。
 *
 * <p>从当前 Servlet 请求上下文中提取以下请求头并注入 Feign 请求：
 * <ul>
 *   <li>{@code Authorization} — JWT 令牌，用于下游服务鉴权</li>
 *   <li>{@code X-User-Id} — 解析后的用户 ID</li>
 *   <li>{@code X-User-Role} — 解析后的用户角色</li>
 *   <li>{@code X-Request-Id} — 链路追踪请求 ID（可选）</li>
 * </ul>
 *
 * <p>当调用不在 HTTP 请求上下文中（如定时任务、异步线程）时，不会注入任何请求头，
 * 调用方需自行设置所需请求头。
 *
 * <p>使用方式：在各服务的 {@code @Configuration} 类中声明为 Bean，或直接在
 * {@code @EnableFeignClients} 的 defaultConfiguration 中引用。
 */
public class FeignAuthRequestInterceptor implements RequestInterceptor {

    private static final String[] PROPAGATED_HEADERS = {
            "Authorization",
            "X-User-Id",
            "X-User-Role",
            "X-Request-Id"
    };

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // 非请求上下文（如定时任务），跳过请求头透传
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        for (String header : PROPAGATED_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !template.headers().containsKey(header)) {
                template.header(header, value);
            }
        }
    }
}

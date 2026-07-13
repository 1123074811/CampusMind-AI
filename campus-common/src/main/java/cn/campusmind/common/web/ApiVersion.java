package cn.campusmind.common.web;

/**
 * API 版本策略常量。
 *
 * <p>当前所有公开 API 统一使用 {@code /api/v1/} 前缀。
 * 当需要发布不兼容的新版本时，新增 {@code /api/v2/} 前缀，
 * 旧版本保留至少 2 个发布周期后标记 @Deprecated，
 * 并在网关 application.yml 中添加对应路由规则。
 *
 * <p>版本演进流程：
 * <ol>
 *   <li>新增 v2 端点，同时保留 v1 端点</li>
 *   <li>在 v1 端点的响应头添加 {@code Deprecated: true}</li>
 *   <li>经过至少 2 个发布周期后移除 v1 端点</li>
 * </ol>
 */
public final class ApiVersion {

    /** 当前稳定版本前缀 */
    public static final String V1 = "/api/v1";

    /** 管理后台端点前缀（需 ADMIN/OPERATOR 角色） */
    public static final String ADMIN = "/api/admin";

    private ApiVersion() {
    }
}

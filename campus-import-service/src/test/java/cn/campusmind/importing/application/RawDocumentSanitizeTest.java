package cn.campusmind.importing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RawDocument 字段白名单脱敏测试。
 */
class RawDocumentSanitizeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RawDocumentService service = new RawDocumentService(null, null, objectMapper);

    @Test
    void shouldRemoveSensitiveFieldsFromJson() {
        String json = """
                {
                  "title": "讲座通知",
                  "name": "张三",
                  "phone": "13800138000",
                  "mobile": "13900139000",
                  "studentId": "20210001",
                  "avatar": "https://example.com/avatar.png",
                  "content": "讲座内容详情"
                }
                """;

        String sanitized = service.sanitizeRawJson(json, "USER_TEXT");

        assertFalse(sanitized.contains("张三"), "姓名应被脱敏");
        assertFalse(sanitized.contains("13800138000"), "手机号应被脱敏");
        assertFalse(sanitized.contains("13900139000"), "备用手机号应被脱敏");
        assertFalse(sanitized.contains("20210001"), "学号应被脱敏");
        assertFalse(sanitized.contains("avatar"), "头像应被脱敏");
        assertTrue(sanitized.contains("讲座通知"), "标题应保留");
        assertTrue(sanitized.contains("讲座内容详情"), "内容应保留");
    }

    @Test
    void shouldRemoveNestedSensitiveFields() {
        String json = """
                {
                  "title": "活动通知",
                  "organizer": {
                    "name": "李四",
                    "phone": "13700137000",
                    "department": "计算机学院"
                  },
                  "content": "活动详情"
                }
                """;

        String sanitized = service.sanitizeRawJson(json, "USER_TEXT");

        assertFalse(sanitized.contains("李四"), "嵌套姓名应被脱敏");
        assertFalse(sanitized.contains("13700137000"), "嵌套手机号应被脱敏");
        assertTrue(sanitized.contains("计算机学院"), "非敏感嵌套字段应保留");
        assertTrue(sanitized.contains("活动详情"), "内容应保留");
    }

    @Test
    void shouldRemoveAllSensitiveFieldVariants() {
        String json = """
                {
                  "student_id": "20210001",
                  "device_id": "abc123",
                  "token": "jwt-token-value",
                  "cookie": "session-cookie",
                  "password": "secret123"
                }
                """;

        String sanitized = service.sanitizeRawJson(json, "RAIN_CLASSROOM");

        assertFalse(sanitized.contains("20210001"), "student_id 应被脱敏");
        assertFalse(sanitized.contains("abc123"), "device_id 应被脱敏");
        assertFalse(sanitized.contains("jwt-token-value"), "token 应被脱敏");
        assertFalse(sanitized.contains("session-cookie"), "cookie 应被脱敏");
        assertFalse(sanitized.contains("secret123"), "password 应被脱敏");
    }

    @Test
    void shouldReturnPlainTextAsIs() {
        String text = "这是一段普通文本，包含手机号13800138000和姓名张三";

        String sanitized = service.sanitizeRawJson(text, "USER_TEXT");

        assertEquals(text, sanitized, "非 JSON 文本应原样返回");
    }

    @Test
    void shouldHandleEmptyAndNullInput() {
        assertEquals(null, service.sanitizeRawJson(null, "USER_TEXT"));
        assertEquals("", service.sanitizeRawJson("", "USER_TEXT"));
        assertEquals("  ", service.sanitizeRawJson("  ", "USER_TEXT"));
    }

    @Test
    void shouldSanitizeNestedObjectsInJsonArray() {
        String jsonArray = """
                [{"name": "test", "value": 1, "headers": {"Authorization": "Bearer secret"}}]
                """;

        String sanitized = service.sanitizeRawJson(jsonArray, "USER_TEXT");

        assertFalse(sanitized.contains("test"), "数组中的姓名应被脱敏");
        assertFalse(sanitized.contains("Bearer secret"), "大小写不同的授权字段应被脱敏");
        assertTrue(sanitized.contains("\"value\":1"), "非敏感数组字段应保留");
    }

    @Test
    void shouldRejectMalformedSensitiveJson() {
        assertThrows(IllegalArgumentException.class,
                () -> service.sanitizeRawJson("{\"token\":", "RAIN_CLASSROOM"));
    }
}

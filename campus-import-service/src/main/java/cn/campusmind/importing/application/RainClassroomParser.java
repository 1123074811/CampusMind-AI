package cn.campusmind.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 解析 App WebView 或旧版浏览器扩展生成的雨课堂 JSON。 */
@Component
public class RainClassroomParser {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Set<String> DATA_TYPES = Set.of("COURSE", "HOMEWORK", "NOTICE", "EXAM");
    private final ObjectMapper objectMapper;

    public RainClassroomParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawRainItem> parseJson(String rawJson) throws Exception {
        return parseJson(rawJson, null);
    }

    public List<RawRainItem> parseJson(String rawJson, String defaultDataType) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        if (root == null || (!root.isObject() && !root.isArray())) {
            throw new IllegalArgumentException("雨课堂JSON根节点必须是对象或数组");
        }
        validateEnvelope(root);
        JsonNode list = locateList(root);
        if (list == null || !list.isArray()) {
            throw new IllegalArgumentException("未识别的雨课堂JSON结构");
        }

        List<RawRainItem> items = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            JsonNode node = list.get(index);
            if (!node.isObject()) {
                throw new IllegalArgumentException("第" + (index + 1) + "条数据必须是对象");
            }
            validateCanonicalTypes(node);
            String dataType = normalizeDataType(firstText(node, "dataType", "data_type"), defaultDataType);
            String courseName = firstText(node,
                    "course.name", "course.courseName", "course.course_name", "course.title",
                    "classroom.courseName", "classroom.course_name", "courseName", "course_name");
            String semester = firstText(node, "semester", "term", "semesterName", "semester_name");
            String rawTitle = firstText(node, "title", "homeworkTitle", "homework_title",
                    "noticeTitle", "notice_title", "examTitle", "exam_title", "name", "className", "class_name");
            String title = "COURSE".equals(dataType) && courseName != null ? courseName
                    : rawTitle != null ? rawTitle : courseName;
            RawRainItem item = new RawRainItem(
                    firstText(node, "providerItemId", "provider_item_id", "classroom_id", "classroomId",
                            "homework_id", "homeworkId", "assignment_id", "assignmentId", "notice_id",
                            "noticeId", "exam_id", "examId", "activity_id", "activityId", "id", "course.id"),
                    dataType,
                    courseName,
                    semester,
                    title,
                    firstText(node, "content", "description", "detail", "text", "summary", "message", "body"),
                    firstText(node, "deadline", "endTime", "end_time", "dueTime", "due_time", "endDate", "end_date"),
                    firstText(node, "publishedAt", "published_at", "publishTime", "publish_time",
                            "releasedAt", "released_at", "releaseTime", "release_time",
                            "createdAt", "created_at", "createTime", "create_time",
                            "startTime", "start_time", "beginTime", "begin_time", "openTime", "open_time"),
                    firstText(node, "teacherName", "teacher_name", "teacher.name", "teacher.username",
                            "teachers.0.name", "teachers.0.username", "course.teacher.name"),
                    firstText(node, "sourceUrl", "source_url")
            );
            if (item.toPlainText().isBlank()) {
                throw new IllegalArgumentException("第" + (index + 1) + "条数据没有可导入内容");
            }
            items.add(item);
        }
        return items;
    }

    private void validateEnvelope(JsonNode root) {
        if (!root.isObject()) return;
        JsonNode version = root.get("schemaVersion");
        if (version != null && (!version.isIntegralNumber() || version.intValue() != SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalArgumentException("不支持的schemaVersion，仅支持版本" + SUPPORTED_SCHEMA_VERSION);
        }
        JsonNode provider = root.get("provider");
        if (provider != null && (!provider.isTextual() || !"RAIN_CLASSROOM".equals(provider.textValue()))) {
            throw new IllegalArgumentException("provider必须为RAIN_CLASSROOM");
        }
    }

    private void validateCanonicalTypes(JsonNode node) {
        for (String field : List.of("courseName", "semester", "title", "content", "deadline", "publishedAt", "teacherName", "sourceUrl", "dataType")) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.isTextual()) {
                throw new IllegalArgumentException("字段" + field + "必须是字符串");
            }
        }
    }

    private JsonNode locateList(JsonNode root) {
        if (root.isArray()) return root;
        if (root.path("items").isArray()) return root.path("items");
        if (root.path("data").isArray()) return root.path("data");
        if (root.path("data").path("list").isArray()) return root.path("data").path("list");
        if (root.path("list").isArray()) return root.path("list");
        return null;
    }

    private String normalizeDataType(String itemType, String defaultType) {
        String value = itemType != null ? itemType : defaultType;
        if (value == null || value.isBlank()) return "NOTICE";
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!DATA_TYPES.contains(value)) {
            throw new IllegalArgumentException("dataType仅支持COURSE、HOMEWORK、NOTICE、EXAM");
        }
        return value;
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = atPath(node, path);
            if (value != null && value.isValueNode() && !value.isNull()) {
                String text = value.asText().replaceAll("\\s+", " ").trim();
                if (!text.isBlank() && !"[object Object]".equals(text)) return text;
            }
        }
        return null;
    }

    private JsonNode atPath(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) return null;
            current = current.isArray() ? current.path(Integer.parseInt(part)) : current.path(part);
        }
        return current;
    }
}

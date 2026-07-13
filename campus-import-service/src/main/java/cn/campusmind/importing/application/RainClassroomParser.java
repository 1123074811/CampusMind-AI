package cn.campusmind.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 雨课堂 JSON 解析器：用户手动粘贴课程/作业/通知 JSON，本系统只解析用户提交文本，不代用户请求雨课堂。
 * 支持结构：{"data":{"list":[...]}} / {"data":[...]} / 顶层 [...]。
 */
@Component
public class RainClassroomParser {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public RainClassroomParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawRainItem> parseJson(String rawJson) throws Exception {
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
            RawRainItem item = new RawRainItem(
                    textOrNull(node, "courseName"),
                    textOrNull(node, "title"),
                    textOrNull(node, "content"),
                    textOrNull(node, "deadline"),
                    textOrNull(node, "teacherName")
            );
            if (item.toPlainText().isBlank()) {
                throw new IllegalArgumentException("第" + (index + 1) + "条数据没有可导入内容");
            }
            items.add(item);
        }
        return items;
    }

    private void validateEnvelope(JsonNode root) {
        if (!root.isObject()) {
            return; // 兼容用户直接导出的旧版数组结构
        }
        JsonNode version = root.get("schemaVersion");
        if (version != null && (!version.isIntegralNumber() || version.intValue() != SUPPORTED_SCHEMA_VERSION)) {
            throw new IllegalArgumentException("不支持的schemaVersion，仅支持版本" + SUPPORTED_SCHEMA_VERSION);
        }
        JsonNode provider = root.get("provider");
        if (provider != null && (!provider.isTextual() || !"RAIN_CLASSROOM".equals(provider.textValue()))) {
            throw new IllegalArgumentException("provider必须为RAIN_CLASSROOM");
        }
    }

    private JsonNode locateList(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        JsonNode data = root.path("data");
        if (data.isArray()) {
            return data;
        }
        JsonNode list = data.path("list");
        if (list.isArray()) {
            return list;
        }
        JsonNode topLevelList = root.path("list");
        if (topLevelList.isArray()) {
            return topLevelList;
        }
        JsonNode exportedItems = root.path("items");
        if (exportedItems.isArray()) {
            return exportedItems;
        }
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        if (!child.isTextual()) {
            throw new IllegalArgumentException("字段" + field + "必须是字符串");
        }
        String text = child.textValue();
        return text == null || text.isBlank() ? null : text;
    }
}

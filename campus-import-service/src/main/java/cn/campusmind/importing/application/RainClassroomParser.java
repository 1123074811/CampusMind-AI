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

    private final ObjectMapper objectMapper;

    public RainClassroomParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawRainItem> parseJson(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);

        JsonNode list = locateList(root);
        if (list == null || !list.isArray()) {
            throw new IllegalArgumentException("未识别的雨课堂JSON结构");
        }

        List<RawRainItem> items = new ArrayList<>();
        for (JsonNode node : list) {
            items.add(new RawRainItem(
                    textOrNull(node, "courseName"),
                    textOrNull(node, "title"),
                    textOrNull(node, "content"),
                    textOrNull(node, "deadline"),
                    textOrNull(node, "teacherName")
            ));
        }
        return items;
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
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text == null || text.isBlank() ? null : text;
    }
}

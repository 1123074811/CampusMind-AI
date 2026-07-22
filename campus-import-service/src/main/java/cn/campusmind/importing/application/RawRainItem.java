package cn.campusmind.importing.application;

/** 雨课堂本地采集后允许上传的最小事件字段。 */
public record RawRainItem(
        String providerItemId,
        String dataType,
        String courseName,
        String semester,
        String title,
        String content,
        String deadline,
        String publishedAt,
        String teacherName,
        String sourceUrl
) {
    public String toPlainText() {
        StringBuilder text = new StringBuilder();
        append(text, "类型", dataType);
        append(text, "课程", courseName);
        append(text, "学期", semester);
        append(text, "标题", title);
        append(text, "内容", content);
        append(text, "截止时间", deadline);
        append(text, "发布时间", publishedAt);
        append(text, "教师", teacherName);
        return text.toString();
    }

    private static void append(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append(label).append('：').append(value).append('\n');
        }
    }
}

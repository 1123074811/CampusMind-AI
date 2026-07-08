package cn.campusmind.importing.application;

/**
 * 雨课堂原始条目，由 RainClassroomParser 从用户粘贴的 JSON 解析得到。
 */
public record RawRainItem(
        String courseName,
        String title,
        String content,
        String deadline,
        String teacherName
) {
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        if (courseName != null && !courseName.isBlank()) {
            sb.append("课程：").append(courseName).append("\n");
        }
        if (title != null && !title.isBlank()) {
            sb.append("标题：").append(title).append("\n");
        }
        if (content != null && !content.isBlank()) {
            sb.append("内容：").append(content).append("\n");
        }
        if (deadline != null && !deadline.isBlank()) {
            sb.append("截止时间：").append(deadline).append("\n");
        }
        if (teacherName != null && !teacherName.isBlank()) {
            sb.append("教师：").append(teacherName).append("\n");
        }
        return sb.toString();
    }
}

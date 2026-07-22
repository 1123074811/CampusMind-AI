(() => {
  const scalar = (value) => {
    if (value === undefined || value === null || typeof value === "object") return null;
    const text = String(value).replace(/\s+/g, " ").trim();
    return text && text !== "[object Object]" ? text : null;
  };

  const atPath = (item, path) => path.split(".").reduce((value, key) => {
    if (Array.isArray(value)) return value[Number(key) || 0];
    return value?.[key];
  }, item);

  const first = (item, paths) => {
    for (const path of paths) {
      const value = scalar(atPath(item, path));
      if (value) return value;
    }
    return null;
  };

  const locateLists = (root) => {
    const found = [];
    const visit = (value, depth) => {
      if (depth > 5 || value === null || typeof value !== "object") return;
      if (Array.isArray(value)) {
        if (value.some((item) => item && typeof item === "object" && !Array.isArray(item))) found.push(value);
        return;
      }
      for (const child of Object.values(value)) visit(child, depth + 1);
    };
    visit(root, 0);
    return found;
  };

  const locateList = (root) => locateLists(root)[0] || [];

  const classifyUrl = (url) => {
    let path = "";
    try { path = new URL(url).pathname.toLowerCase(); } catch (_) { return null; }
    if (/(exam|exercise|quiz|test)/.test(path)) return "EXAM";
    if (/(homework|assignment)/.test(path)) return "HOMEWORK";
    if (/(notice|announcement)/.test(path)) return "NOTICE";
    if (/(course|class)/.test(path)) return "COURSE";
    return null;
  };

  const inferDataType = (item, fallback, url) => {
    const explicit = first(item, ["dataType", "data_type", "eventType", "event_type"]);
    if (explicit && ["COURSE", "HOMEWORK", "NOTICE", "EXAM"].includes(explicit.toUpperCase())) {
      return explicit.toUpperCase();
    }
    const hint = [
      first(item, ["typeName", "type_name", "category", "activityType", "activity_type", "kind"]),
      first(item, ["title", "name", "homeworkTitle", "homework_title", "noticeTitle", "notice_title"]),
      url
    ].filter(Boolean).join(" ").toLowerCase();
    if (/(考试|测验|试卷|exam|quiz|test)/.test(hint)) return "EXAM";
    if (/(作业|homework|assignment)/.test(hint)) return "HOMEWORK";
    if (/(公告|通知|notice|announcement)/.test(hint)) return "NOTICE";
    return fallback || (/(课程|课堂|course|class)/.test(hint) ? "COURSE" : null);
  };

  const sanitizeUrl = (url) => {
    try {
      const parsed = new URL(url);
      return parsed.origin + parsed.pathname;
    } catch (_) {
      return null;
    }
  };

  const normalizeItem = (item, fallbackType, sourceUrl) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return null;
    const dataType = inferDataType(item, fallbackType, sourceUrl);
    if (!dataType) return null;
    const courseName = first(item, [
      "course.name", "course.courseName", "course.course_name", "course.title",
      "classroom.courseName", "classroom.course_name", "courseName", "course_name"
    ]);
    const rawTitle = first(item, [
      "title", "homeworkTitle", "homework_title", "noticeTitle", "notice_title",
      "examTitle", "exam_title", "activityTitle", "activity_title", "name", "className", "class_name"
    ]);
    const title = dataType === "COURSE" ? (courseName || rawTitle) : (rawTitle || courseName);
    const content = first(item, [
      "content", "description", "detail", "text", "summary", "message", "body",
      "announcement.content", "notice.content"
    ]);
    if (!title && !content && !courseName) return null;
    return {
      providerItemId: first(item, [
        "classroom_id", "classroomId", "homework_id", "homeworkId", "assignment_id",
        "assignmentId", "notice_id", "noticeId", "exam_id", "examId", "activity_id",
        "activityId", "id", "course.id"
      ]),
      dataType,
      courseName,
      title,
      content,
      deadline: first(item, [
        "deadline", "endTime", "end_time", "dueTime", "due_time", "endDate", "end_date",
        "closeTime", "close_time"
      ]),
      teacherName: first(item, [
        "teacherName", "teacher_name", "teacher.name", "teacher.username",
        "teachers.0.name", "teachers.0.username", "course.teacher.name"
      ]),
      sourceUrl: sanitizeUrl(sourceUrl)
    };
  };

  const normalizePayload = (payload, url) => {
    const fallbackType = classifyUrl(url);
    const seen = new Set();
    const items = [];
    for (const list of locateLists(payload)) {
      for (const raw of list) {
        const item = normalizeItem(raw, fallbackType, url);
        if (!item) continue;
        const key = [item.dataType, item.providerItemId || "", item.courseName || "", item.title || "", item.deadline || ""].join("|");
        if (!seen.has(key)) {
          seen.add(key);
          items.push(item);
        }
      }
    }
    return items;
  };

  const linesOf = (text) => String(text || "").split(/\r?\n/)
    .map((line) => line.replace(/\s+/g, " ").trim()).filter(Boolean);

  const isCourseCode = (line) => /^\d{4}[春秋]-/.test(line) || /^\d{5,}-\d{2,}$/.test(line);
  const ignoredCourseLine = (line) => /^(我的课|我教的课|搜索课程|加入班级|课程班级|学习日志|学习内容|未完成|讨论区|公告|分组|错题集|成绩单)$/.test(line);

  const courseFromNode = (node, pageUrl) => {
    let card = node;
    for (let i = 0; i < 5 && card?.parentElement; i++) {
      const lines = linesOf(card.innerText);
      if (lines.length >= 3 && lines.length <= 12) break;
      card = card.parentElement;
    }
    const lines = linesOf(card?.innerText);
    const heading = [...(card?.querySelectorAll?.('h1,h2,h3,h4,[class*="title" i]') || [])]
      .map((item) => linesOf(item.innerText)[0]).find((line) => line && !isCourseCode(line) && !ignoredCourseLine(line));
    const title = heading || lines.find((line) => line.length <= 120 && !isCourseCode(line) && !ignoredCourseLine(line));
    if (!title) return null;
    let href = node.href || pageUrl;
    try { href = new URL(href, pageUrl).href; } catch (_) {}
    let providerItemId = null;
    try {
      const parsed = new URL(href);
      providerItemId = parsed.searchParams.get("classroom_id") || parsed.pathname.match(/studentLog\/(\d+)/i)?.[1] || null;
    } catch (_) {}
    return {
      providerItemId, dataType: "COURSE", courseName: title, title,
      content: null, deadline: null, teacherName: null, sourceUrl: sanitizeUrl(href)
    };
  };

  const extractCourseCards = (document, sourceUrl) => {
    const nodes = [...document.querySelectorAll(
      'a[href*="studentLog"], a[href*="classroom_id"], [class*="course-card" i], [class*="course_item" i], [class*="course-item" i]'
    )];
    const seen = new Set();
    return nodes.map((node) => courseFromNode(node, sourceUrl)).filter((item) => {
      if (!item) return false;
      const key = item.providerItemId || item.title;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  };

  const currentCourseName = (document) => [...document.querySelectorAll("h1,h2,h3")]
    .map((node) => linesOf(node.innerText)[0])
    .find((line) => line && line.length <= 120 && !isCourseCode(line) && !ignoredCourseLine(line)) || null;

  const extractLearningLog = (document, sourceUrl) => {
    if (!/studentLog/i.test(sourceUrl)) return [];
    const courseName = currentCourseName(document);
    const candidates = [...document.querySelectorAll("article,li,div")].map((node) => ({ node, text: node.innerText || "" }))
      .filter(({ node, text }) => node.children.length <= 12 && text.length >= 8 && text.length <= 800 && /(截止时间|考试|测验|作业|公告|通知)/.test(text))
      .sort((a, b) => a.text.length - b.text.length);
    const seen = new Set();
    const items = [];
    for (const { node, text } of candidates) {
      const lines = linesOf(text);
      const labelIndex = lines.findIndex((line) => /^(考试|测验|试卷|作业|公告|通知)$/.test(line));
      const title = (labelIndex >= 0 ? lines.slice(labelIndex + 1) : lines)
        .find((line) => line.length <= 160 && !/^(满分|共\d|截止时间|得分|\d{1,2}:\d{2})/.test(line));
      if (!title) continue;
      const dataType = inferDataType({ title, typeName: labelIndex >= 0 ? lines[labelIndex] : null }, null, sourceUrl);
      if (!dataType) continue;
      const deadline = text.match(/截止时间[：:]?\s*([^\n|]+)/)?.[1]?.trim() || null;
      const providerItemId = scalar(node.dataset?.id || node.getAttribute?.("data-id") || node.id);
      const key = [dataType, providerItemId || "", courseName || "", title, deadline || ""].join("|");
      if (seen.has(key)) continue;
      seen.add(key);
      items.push({
        providerItemId, dataType, courseName, title,
        content: lines.join("\n"), deadline, teacherName: null, sourceUrl: sanitizeUrl(sourceUrl)
      });
      if (items.length >= 200) break;
    }
    return items;
  };

  const extractPage = (document, sourceUrl) => [
    ...extractCourseCards(document, sourceUrl),
    ...extractLearningLog(document, sourceUrl)
  ];

  const api = {
    locateList, locateLists, classifyUrl, inferDataType, normalizeItem, normalizePayload,
    extractCourseCards, extractLearningLog, extractPage
  };
  globalThis.CampusMindRainNormalizer = api;
  if (typeof module !== "undefined") module.exports = api;
})();

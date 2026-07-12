(() => {
  const FIRST = (item, keys) => {
    for (const key of keys) {
      const value = item?.[key];
      if (value !== undefined && value !== null && String(value).trim()) return String(value).trim();
    }
    return null;
  };

  const locateList = (root) => {
    if (Array.isArray(root)) return root;
    if (Array.isArray(root?.items)) return root.items;
    if (Array.isArray(root?.data)) return root.data;
    if (Array.isArray(root?.data?.list)) return root.data.list;
    if (Array.isArray(root?.list)) return root.list;
    return [];
  };

  const classifyUrl = (url) => {
    const path = new URL(url).pathname.toLowerCase();
    if (/(homework|assignment)/.test(path)) return "HOMEWORK";
    if (/(notice|announcement)/.test(path)) return "NOTICE";
    if (/(course|class)/.test(path)) return "COURSE";
    return null;
  };

  const sanitizeUrl = (url) => {
    const parsed = new URL(url);
    return parsed.origin + parsed.pathname;
  };

  const normalizeItem = (item, dataType, sourceUrl) => ({
    providerItemId: FIRST(item, ["id", "homeworkId", "assignmentId", "noticeId"]),
    dataType,
    courseName: FIRST(item, ["courseName", "course_name", "className", "class_name"]),
    title: FIRST(item, ["title", "name", "homeworkTitle", "noticeTitle"]),
    content: FIRST(item, ["content", "description", "detail", "text"]),
    deadline: FIRST(item, ["deadline", "endTime", "end_time", "dueTime", "due_time"]),
    teacherName: FIRST(item, ["teacherName", "teacher_name", "teacher"]),
    sourceUrl
  });

  const normalizePayload = (payload, url) => {
    const dataType = classifyUrl(url);
    if (!dataType) return [];
    const sourceUrl = sanitizeUrl(url);
    return locateList(payload)
      .map((item) => normalizeItem(item, dataType, sourceUrl))
      .filter((item) => item.title || item.content || item.courseName);
  };

  const normalizeCourseText = (text, sourceUrl) => {
    const title = String(text || "").split(/\r?\n/)
      .map((line) => line.replace(/\s+/g, " ").trim())
      .find((line) => line && line.length <= 120 && !/^(我的课|我教的课|搜索课程|加入班级)$/.test(line));
    return title ? {
      providerItemId: null, dataType: "COURSE", courseName: title, title,
      content: null, deadline: null, teacherName: null, sourceUrl: sanitizeUrl(sourceUrl)
    } : null;
  };

  const extractCourseCards = (document, sourceUrl) => [...document.querySelectorAll(
    'a[href*="course"], a[href*="class"], [class*="course-card" i], [class*="course_item" i], [class*="course-item" i]'
  )].map((node) => normalizeCourseText(node.innerText, sourceUrl)).filter(Boolean);

  const api = { locateList, classifyUrl, normalizeItem, normalizePayload, normalizeCourseText, extractCourseCards };
  globalThis.CampusMindRainNormalizer = api;
  if (typeof module !== "undefined") module.exports = api;
})();

(() => {
  if (window.__campusMindRainBridgeInstalled) return;
  window.__campusMindRainBridgeInstalled = true;

  const scalar = (value) => {
    if (value === undefined || value === null || typeof value === "object") return null;
    const text = String(value).replace(/\s+/g, " ").trim();
    return text && text !== "[object Object]" ? text.slice(0, 2000) : null;
  };
  const at = (item, path) => path.split(".").reduce((value, key) => {
    if (Array.isArray(value)) return value[Number(key) || 0];
    return value?.[key];
  }, item);
  const first = (item, paths) => {
    for (const path of paths) {
      const value = scalar(at(item, path));
      if (value) return value;
    }
    return null;
  };
  const firstTimestamp = (item, paths) => {
    for (const path of paths) {
      const value = at(item, path);
      if (value === undefined || value === null || typeof value === "object") continue;
      if (typeof value === "number" || /^\d{10,13}$/.test(String(value))) {
        const number = Number(value);
        const date = new Date(number < 1e12 ? number * 1000 : number);
        if (!Number.isNaN(date.getTime())) return date.toISOString();
      }
      const text = scalar(value);
      if (text) return text;
    }
    return null;
  };
  const semesterFrom = (...values) => {
    const text = values.filter(Boolean).join(" ");
    const explicit = text.match(/20\d{2}[春秋]/)?.[0];
    if (explicit) return explicit;
    const academic = text.match(/(20\d{2})[-/](20\d{2})[-/]([12])/);
    if (!academic) return null;
    return academic[3] === "1" ? `${academic[1]}秋` : `${academic[2]}春`;
  };
  const lists = (root) => {
    const found = [];
    const visit = (value, depth) => {
      if (depth > 5 || value === null || typeof value !== "object") return;
      if (Array.isArray(value)) {
        if (value.some((item) => item && typeof item === "object" && !Array.isArray(item))) found.push(value);
        for (const child of value) visit(child, depth + 1);
        return;
      }
      for (const child of Object.values(value)) visit(child, depth + 1);
    };
    visit(root, 0);
    return found;
  };
  const safeUrl = (url) => {
    try { const parsed = new URL(url); return parsed.origin + parsed.pathname; } catch (_) { return null; }
  };
  const urlType = (url) => {
    let path = "";
    try { path = new URL(url).pathname.toLowerCase(); } catch (_) { return null; }
    if (/(exam|exercise|quiz|test)/.test(path)) return "EXAM";
    if (/(homework|assignment)/.test(path)) return "HOMEWORK";
    if (/(notice|announcement)/.test(path)) return "NOTICE";
    if (/(course|class)/.test(path)) return "COURSE";
    return null;
  };
  const dataType = (item, fallback, url) => {
    const explicit = first(item, ["dataType", "data_type", "eventType", "event_type"]);
    if (explicit && ["COURSE", "HOMEWORK", "NOTICE", "EXAM"].includes(explicit.toUpperCase())) return explicit.toUpperCase();
    const hint = [first(item, ["typeName", "type_name", "category", "activityType", "activity_type", "kind"]),
      first(item, ["title", "name", "homeworkTitle", "homework_title", "noticeTitle", "notice_title"]), url]
      .filter(Boolean).join(" ").toLowerCase();
    if (/(考试|测验|试卷|exam|quiz|test)/.test(hint)) return "EXAM";
    if (/(作业|习题|练习|实验|报告|论文|预习|复习|任务|homework|assignment)/.test(hint)) return "HOMEWORK";
    if (/(公告|通知|notice|announcement)/.test(hint)) return "NOTICE";
    return fallback || (/(课程|课堂|course|class)/.test(hint) ? "COURSE" : null);
  };
  const normalize = (item, fallback, url) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return null;
    const type = dataType(item, fallback, url);
    if (!type) return null;
    const courseName = first(item, ["course.name", "course.courseName", "course.course_name", "course.title",
      "classroom.courseName", "classroom.course_name", "courseName", "course_name"]);
    const semester = semesterFrom(first(item, ["semester", "term", "semesterName", "semester_name",
      "classroomName", "classroom_name", "className", "class_name", "courseCode", "course_code"]));
    const rawTitle = first(item, ["title", "homeworkTitle", "homework_title", "noticeTitle", "notice_title",
      "examTitle", "exam_title", "activityTitle", "activity_title", "name", "className", "class_name"]);
    const classroomId = first(item, ["classroom_id", "classroomId"]);
    // 课程接口包含教师、标签、统计等多层数组；只有同时具备课堂 ID 和课程名的
    // 对象才是课程，不能因 URL 含 course 就把所有子对象都当成课程。
    if (type === "COURSE" && (!classroomId || !courseName)) return null;
    const title = type === "COURSE" ? (courseName || rawTitle) : (rawTitle || courseName);
    const content = first(item, ["content", "description", "detail", "text", "summary", "message", "body",
      "announcement.content", "notice.content"]);
    if (!title && !content && !courseName) return null;
    return {
      providerItemId: first(item, ["classroom_id", "classroomId", "homework_id", "homeworkId", "assignment_id",
        "assignmentId", "notice_id", "noticeId", "exam_id", "examId", "activity_id", "activityId", "id", "course.id"]),
      dataType: type,
      courseName,
      semester,
      title,
      content,
      deadline: first(item, ["deadline", "endTime", "end_time", "dueTime", "due_time", "endDate", "end_date", "closeTime", "close_time"]),
      publishedAt: firstTimestamp(item, ["publishedAt", "published_at", "publishTime", "publish_time",
        "releasedAt", "released_at", "releaseTime", "release_time", "createdAt", "created_at", "createTime", "create_time",
        "startTime", "start_time", "beginTime", "begin_time", "openTime", "open_time"]),
      teacherName: first(item, ["teacherName", "teacher_name", "teacher.name", "teacher.username", "teachers.0.name", "teachers.0.username"]),
      sourceUrl: safeUrl(url)
    };
  };
  const discoveredCourses = new Map();
  const rememberCourse = (url, name, semester) => {
    try {
      const parsed = new URL(url, location.href);
      if (/studentLog/i.test(parsed.pathname)) {
        const previous = discoveredCourses.get(parsed.href) || {};
        discoveredCourses.set(parsed.href, {
          courseName: name || previous.courseName || null,
          semester: semester || previous.semester || null
        });
      }
    } catch (_) {}
  };
  const rememberCourseUrl = (raw, item) => {
    if (item?.dataType !== "COURSE" || !item.providerItemId) return;
    const explicit = first(raw, ["studentLogUrl", "student_log_url", "courseUrl", "course_url", "url"]);
    if (explicit) {
      try {
        const parsed = new URL(explicit, location.href);
        rememberCourse(parsed.href, item.courseName || item.title, item.semester);
      } catch (_) {}
    }
    const url = new URL(`/v2/web/studentLog/${encodeURIComponent(item.providerItemId)}`, location.origin);
    const universityId = first(raw, ["university_id", "universityId", "university.id", "school_id", "schoolId"]);
    const platformId = first(raw, ["platform_id", "platformId"]);
    if (universityId) url.searchParams.set("university_id", universityId);
    if (platformId) url.searchParams.set("platform_id", platformId);
    url.searchParams.set("classroom_id", item.providerItemId);
    rememberCourse(url.href, item.courseName || item.title, item.semester);
  };
  const contentDigest = (text) => {
    if (!text) return "";
    let h = 0;
    const s = String(text);
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    return h.toString(16);
  };
  const normalizePayload = (payload, url) => {
    const fallback = urlType(url);
    const output = [];
    const seen = new Set();
    for (const list of lists(payload)) {
      for (const raw of list) {
        const item = normalize(raw, fallback, url);
        if (!item) continue;
        rememberCourseUrl(raw, item);
        // XHR 返回的结构化数据中同名不同内容的条目很常见（如多条"作业"），
        // 仅靠 title 去重会把不同内容的条目误并。纳入内容摘要确保只去重真正重复的条目。
        const key = [item.dataType, item.providerItemId || "", item.courseName || "", item.semester || "", item.title || "", item.deadline || "", item.publishedAt || "", contentDigest(item.content)].join("|");
        if (!seen.has(key)) { seen.add(key); output.push(item); }
      }
    }
    return output;
  };
  const lines = (text) => String(text || "").split(/\r?\n/).map((line) => line.replace(/\s+/g, " ").trim()).filter(Boolean);
  const codeLine = (line) => /^\d{4}[春秋]-/.test(line) || /^\d{5,}-\d{2,}$/.test(line);
  const ignored = (line) => /^(我的课|我教的课|搜索课程|加入班级|课程班级|学习日志|学习内容|未完成|讨论区|公告|分组|错题集|成绩单)$/.test(line);
  const courseCards = () => {
    if (/studentLog/i.test(location.pathname)) return [];
    const output = [];
    const seen = new Set();
    const nodes = [...document.querySelectorAll('a[href*="studentLog"],a[href*="classroom_id"]')];
    for (const node of nodes) {
      let card = node;
      for (let i = 0; i < 5 && card?.parentElement; i++) {
        const cardLines = lines(card.innerText);
        if (cardLines.length >= 3 && cardLines.length <= 12) break;
        card = card.parentElement;
      }
      const cardLines = lines(card?.innerText);
      const semester = semesterFrom(...cardLines);
      const heading = [...(card?.querySelectorAll?.('h1,h2,h3,h4,[class*="title" i]') || [])]
        .map((item) => lines(item.innerText)[0]).find((line) => line && !codeLine(line) && !ignored(line));
      const title = heading || cardLines.find((line) => line.length <= 120 && !codeLine(line) && !ignored(line));
      if (!title) continue;
      let href = node.href || location.href;
      try { href = new URL(href, location.href).href; } catch (_) {}
      let id = null;
      try { const parsed = new URL(href); id = parsed.searchParams.get("classroom_id") || parsed.pathname.match(/studentLog\/(\d+)/i)?.[1] || null; } catch (_) {}
      if (!id && !/studentLog|classroom_id/i.test(href)) continue;
      rememberCourse(href, title, semester);
      const key = id || title;
      if (seen.has(key)) continue;
      seen.add(key);
      output.push({providerItemId:id,dataType:"COURSE",courseName:title,semester,title,content:null,deadline:null,publishedAt:null,teacherName:null,sourceUrl:safeUrl(href)});
    }
    return output;
  };
  const learningLog = () => {
    if (!/studentLog/i.test(location.href)) return [];
    const candidates = [...document.querySelectorAll("article,li,div,section")].map((node) => ({node,text:node.innerText || ""}))
      .filter(({node,text}) => node.children.length <= 24
        && text.length >= 8 && text.length <= 1600
        && /(考试|测验|试卷|作业|公告|通知|签到|打卡|实验|报告|论文|预习|复习|习题|任务)/.test(text)
        && /(截止时间|20\d{2}[年\-/.]\d{1,2}|(?:^|\n)\d{1,2}:\d{2})/.test(text)
        && !/(学习日志[\s\S]*学习内容[\s\S]*未完成|全部日志[\s\S]*线上学习[\s\S]*试卷[\s\S]*公告|返回\s*雨课堂公告|正在加载，请稍后|老师还没有发布教学活动)/.test(text))
      .sort((a,b) => a.text.length - b.text.length);
    // 仅保留最内层完整活动卡，避免同一条消息被父级 div/section 重复采集。
    const cards = candidates.filter(({node}) => !candidates.some(
      (other) => other.node !== node && node.contains(other.node)));
    const output = [];
    const seen = new Set();
    for (const {node,text} of cards) {
      const itemLines = lines(text);
      // 标签可能独占一行，也可能与标题同行（如"作业 第一章练习"）。
      const labelIndex = itemLines.findIndex((line) => /^(考试|测验|试卷|作业|公告|通知)$/.test(line));
      const title = (labelIndex >= 0 ? itemLines.slice(labelIndex + 1) : itemLines)
        .find((line) => line.length <= 160 && !/^(满分|共\d|截止时间|得分|\d{1,2}:\d{2})/.test(line));
      // 找不到明确标题时，用类型标签或首行作为标题，不再直接丢弃条目。
      const fallbackTitle = itemLines.find((line) => line.length <= 160
        && !/^(满分|共\d|截止时间|得分|\d{1,2}:\d{2}|20\d{2}[年\-/.])/.test(line));
      const effectiveTitle = title || fallbackTitle;
      if (!effectiveTitle || /^(公告|通知|分组|错题集|学习内容|全部日志)$/.test(effectiveTitle)) continue;
      // 同行内嵌标签（"作业 xxx"）也参与类型推断。
      const inlineLabel = itemLines.map((line) => line.match(/^(考试|测验|试卷|作业|公告|通知)/)?.[1]).find(Boolean);
      const type = dataType({title:text,typeName:(labelIndex >= 0 ? itemLines[labelIndex] : null) || inlineLabel}, "NOTICE", location.href);
      if (!type) continue;
      const deadline = text.match(/截止时间[：:]?\s*([^\n|]+)/)?.[1]?.trim() || null;
      const publishedAt = text.match(/(?:发布时间|发布于|创建时间)[：:]?\s*(20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}日?(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)/)?.[1]?.trim() || null;
      const id = scalar(node.dataset?.logId || node.dataset?.activityId
        || node.dataset?.homeworkId || node.dataset?.examId || node.dataset?.id);
      // 内容参与去重，避免同课程下同模板消息被误并。
      const digest = (() => { let h = 0; const s = itemLines.join("\n"); for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0; return h.toString(16); })();
      const key = [type,id || "",effectiveTitle,deadline || "",publishedAt || "",digest].join("|");
      if (seen.has(key)) continue;
      seen.add(key);
      // 课程与学期由 App 按当前 studentLog URL 注入，避免异步响应串到下一门课。
      output.push({providerItemId:id,dataType:type,courseName:null,semester:null,title:effectiveTitle,content:itemLines.join("\n"),deadline,publishedAt,teacherName:null,sourceUrl:safeUrl(location.href)});
      if (output.length >= 500) break;
    }
    return output;
  };

  const pending = [];
  const send = (items) => {
    if (!items?.length) return;
    const payload = {origin: location.origin, pageUrl: location.href, items: items.slice(0, 500)};
    if (window.flutter_inappwebview?.callHandler) {
      window.flutter_inappwebview.callHandler("rainItems", payload);
    } else {
      pending.push(payload);
    }
  };
  const scan = () => send([...courseCards(), ...learningLog()]);
  const scrollForMore = () => {
    scan();
    const roots = [document.scrollingElement, ...document.querySelectorAll('main,[class*="scroll" i],div')]
      .filter((node) => node && node.scrollHeight > node.clientHeight + 80)
      .sort((a, b) => (b.scrollHeight - b.clientHeight) - (a.scrollHeight - a.clientHeight));
    const root = roots[0];
    if (!root) return '1|0';
    const before = root.scrollTop;
    root.scrollTop = Math.min(root.scrollTop + Math.max(root.clientHeight * 0.8, 500), root.scrollHeight);
    const bottom = root.scrollTop >= root.scrollHeight - root.clientHeight - 8 || root.scrollTop === before;
    return `${bottom ? 1 : 0}|${root.scrollHeight}`;
  };
  window.addEventListener("flutterInAppWebViewPlatformReady", () => {
    while (pending.length) window.flutter_inappwebview.callHandler("rainItems", pending.shift());
    scan();
  });
  const originalFetch = window.fetch;
  window.fetch = async (...args) => {
    const response = await originalFetch(...args);
    const url = response.url || String(args[0]);
    if ((response.headers.get("content-type") || "").includes("application/json")) {
      response.clone().json().then((payload) => send(normalizePayload(payload, url))).catch(() => {});
    }
    return response;
  };
  const originalOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(method, url, ...rest) {
    this.__campusMindUrl = new URL(url, location.href).href;
    return originalOpen.call(this, method, url, ...rest);
  };
  const originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function(...args) {
    this.addEventListener("load", () => {
      if (!(this.getResponseHeader("content-type") || "").includes("application/json") || typeof this.responseText !== "string") return;
      try { send(normalizePayload(JSON.parse(this.responseText), this.responseURL || this.__campusMindUrl)); } catch (_) {}
    }, {once:true});
    return originalSend.apply(this, args);
  };
  let scanTimer;
  const startDomCapture = () => {
    if (!document.documentElement) return;
    new MutationObserver(() => {
      clearTimeout(scanTimer);
      scanTimer = setTimeout(scan, 800);
    }).observe(document.documentElement, {childList:true,subtree:true});
    scan();
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", startDomCapture, {once:true}); else startDomCapture();
  window.CampusMindRainBridge = {
    scan,
    scrollForMore,
    courses: () => [...discoveredCourses].map(([url, course]) => ({url, ...course})),
    courseUrls: () => [...discoveredCourses.keys()]
  };
})();

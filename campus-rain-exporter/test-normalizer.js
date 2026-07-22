const assert = require("node:assert/strict");
const { classifyUrl, normalizePayload } = require("./normalizer.js");

assert.equal(classifyUrl("https://www.yuketang.cn/api/v1/homework/list?token=hidden"), "HOMEWORK");
assert.equal(classifyUrl("https://www.yuketang.cn/api/v1/profile"), null);

assert.deepEqual(normalizePayload({ data: { list: [{
  classroom_id: 29436288,
  name: "2026春-129000024-001",
  course: { name: "感知与人机交互" },
  teacher: { name: "徐扬" }
}] } }, "https://www.yuketang.cn/v2/api/web/courses/list?private=yes"), [{
  providerItemId: "29436288", dataType: "COURSE", courseName: "感知与人机交互", title: "感知与人机交互",
  content: null, deadline: null, teacherName: "徐扬", sourceUrl: "https://www.yuketang.cn/v2/api/web/courses/list"
}]);

assert.deepEqual(normalizePayload({ data: { list: [{
  id: 42,
  type_name: "考试",
  title: "感知与人机交互大作业",
  course: { name: "感知与人机交互" },
  deadline: "2026-07-05 23:59"
}] } }, "https://www.yuketang.cn/v2/api/web/log/list"), [{
  providerItemId: "42", dataType: "EXAM", courseName: "感知与人机交互", title: "感知与人机交互大作业",
  content: null, deadline: "2026-07-05 23:59", teacherName: null, sourceUrl: "https://www.yuketang.cn/v2/api/web/log/list"
}]);

assert.equal(normalizePayload({ data: { list: [{ name: "学生", avatar: "x" }] } },
  "https://www.yuketang.cn/v2/api/web/profile").length, 0);

console.log("normalizer ok");

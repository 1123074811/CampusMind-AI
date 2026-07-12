const assert = require("node:assert/strict");
const { classifyUrl, normalizePayload, normalizeCourseText } = require("./normalizer.js");

assert.equal(classifyUrl("https://www.yuketang.cn/api/v1/homework/list?token=hidden"), "HOMEWORK");
assert.equal(classifyUrl("https://www.yuketang.cn/api/v1/profile"), null);
assert.deepEqual(normalizePayload({ data: { list: [{ id: 1, courseName: "软件工程", title: "作业一", deadline: "2026-07-18" }] } }, "https://www.yuketang.cn/api/homework/list?private=yes"), [{
  providerItemId: "1", dataType: "HOMEWORK", courseName: "软件工程", title: "作业一", content: null,
  deadline: "2026-07-18", teacherName: null, sourceUrl: "https://www.yuketang.cn/api/homework/list"
}]);
assert.deepEqual(normalizeCourseText("软件工程专业实训\n马萍\n2026春-260237-099", "https://www.yuketang.cn/v2/web/course"), {
  providerItemId: null, dataType: "COURSE", courseName: "软件工程专业实训", title: "软件工程专业实训",
  content: null, deadline: null, teacherName: null, sourceUrl: "https://www.yuketang.cn/v2/web/course"
});
console.log("normalizer ok");

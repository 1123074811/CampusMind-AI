const byId = (id) => document.getElementById(id);
const count = byId("count");
const status = byId("status");

const activeTab = async () => (await chrome.tabs.query({ active: true, currentWindow: true }))[0];
const send = (message) => new Promise((resolve, reject) => {
  chrome.runtime.sendMessage(message, (response) => {
    if (chrome.runtime.lastError) reject(new Error(chrome.runtime.lastError.message));
    else resolve(response);
  });
});

const refresh = async () => {
  const tab = await activeTab();
  const { items = [] } = await send({ type: "rain:get", tabId: tab.id });
  count.textContent = `已捕获 ${items.length} 条`;
  return { tab, items };
};

const toggle = async (enabled) => {
  const tab = await activeTab();
  if (!tab.url?.includes("yuketang.cn")) throw new Error("请先打开雨课堂官方页面");
  if (enabled) await send({ type: "rain:clear", tabId: tab.id });
  await send({ type: "rain:set-active", tabId: tab.id, enabled });
  if (enabled) {
    status.textContent = "正在自动刷新并同步当前页课程数据。";
    await chrome.tabs.reload(tab.id);
  } else {
    await chrome.tabs.sendMessage(tab.id, { type: "rain:toggle", enabled }).catch(() => {});
    status.textContent = "自动同步已停止。";
  }
  await refresh();
};

byId("start").addEventListener("click", () => toggle(true).catch((error) => status.textContent = error.message));
byId("stop").addEventListener("click", () => toggle(false).catch((error) => status.textContent = error.message));
byId("copy").addEventListener("click", async () => {
  const { items } = await refresh();
  if (!items.length) return status.textContent = "尚未捕获到可导入条目。";
  const output = JSON.stringify({ schemaVersion: 1, provider: "RAIN_CLASSROOM", exportedAt: new Date().toISOString(), items }, null, 2);
  await navigator.clipboard.writeText(output);
  status.textContent = "已复制。打开 CampusMind 的雨课堂 JSON 导入页并粘贴。";
});

refresh().catch(() => status.textContent = "请先打开雨课堂官方页面。");

const keyFor = (tabId) => `rain-export:${tabId}`;
const activeKeyFor = (tabId) => `rain-active:${tabId}`;

const getItems = async (tabId) => (await chrome.storage.session.get(keyFor(tabId)))[keyFor(tabId)] || [];

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const tabId = message.tabId ?? sender.tab?.id;
  if (tabId == null) return;

  if (message.type === "rain:clear") {
    chrome.storage.session.set({ [keyFor(tabId)]: [] }).then(() => sendResponse({ items: [] }));
    return true;
  }
  if (message.type === "rain:set-active") {
    chrome.storage.session.set({ [activeKeyFor(tabId)]: Boolean(message.enabled) })
      .then(() => sendResponse({ enabled: Boolean(message.enabled) }));
    return true;
  }
  if (message.type === "rain:is-active") {
    chrome.storage.session.get(activeKeyFor(tabId)).then((data) => sendResponse({ enabled: Boolean(data[activeKeyFor(tabId)]) }));
    return true;
  }
  if (message.type === "rain:get") {
    getItems(tabId).then((items) => sendResponse({ items }));
    return true;
  }
  if (message.type === "rain:append") {
    getItems(tabId).then((current) => {
      const seen = new Set(current.map((item) => `${item.dataType}|${item.providerItemId || ""}|${item.title || ""}|${item.deadline || ""}`));
      const next = [...current];
      for (const item of message.items || []) {
        const key = `${item.dataType}|${item.providerItemId || ""}|${item.title || ""}|${item.deadline || ""}`;
        if (!seen.has(key) && next.length < 500) {
          seen.add(key);
          next.push(item);
        }
      }
      chrome.storage.session.set({ [keyFor(tabId)]: next }).then(() => sendResponse({ items: next }));
    });
    return true;
  }
});

chrome.tabs.onRemoved.addListener((tabId) => chrome.storage.session.remove([keyFor(tabId), activeKeyFor(tabId)]));

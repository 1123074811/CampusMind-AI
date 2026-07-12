const inject = (file, done) => {
  const script = document.createElement("script");
  script.src = chrome.runtime.getURL(file);
  script.onload = () => {
    script.remove();
    done?.();
  };
  (document.head || document.documentElement).appendChild(script);
};

const postControl = (enabled) => window.postMessage({
  source: "CampusMindRainExporter", type: enabled ? "start" : "stop"
}, location.origin);

inject("normalizer.js", () => inject("page-hook.js", () => {
  chrome.runtime.sendMessage({ type: "rain:is-active" }, (response) => {
    if (response?.enabled) postControl(true);
  });
}));

window.addEventListener("message", (event) => {
  if (event.source !== window || event.origin !== location.origin) return;
  if (event.data?.source !== "CampusMindRainExporter" || event.data.type !== "items") return;
  chrome.runtime.sendMessage({ type: "rain:append", items: event.data.items });
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type !== "rain:toggle") return;
  postControl(message.enabled);
  if (message.enabled) window.postMessage({ source: "CampusMindRainExporter", type: "scan" }, location.origin);
  sendResponse({ ok: true });
});

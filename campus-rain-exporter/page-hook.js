(() => {
  if (window.__campusMindRainHookInstalled) return;
  window.__campusMindRainHookInstalled = true;

  let enabled = false;
  const normalizer = window.CampusMindRainNormalizer;
  if (!normalizer) return;

  const report = (payload, url) => {
    if (!enabled) return;
    const items = normalizer.normalizePayload(payload, url);
    if (!items.length) return;
    window.postMessage({ source: "CampusMindRainExporter", type: "items", items }, location.origin);
  };

  const scanCourseCards = () => {
    if (!enabled) return;
    const items = normalizer.extractCourseCards(document, location.href);
    if (items.length) window.postMessage({ source: "CampusMindRainExporter", type: "items", items }, location.origin);
  };

  const originalFetch = window.fetch;
  window.fetch = async (...args) => {
    const response = await originalFetch(...args);
    const url = response.url || String(args[0]);
    const contentType = response.headers.get("content-type") || "";
    if (enabled && contentType.includes("application/json")) {
      response.clone().json().then((payload) => report(payload, url)).catch(() => {});
    }
    return response;
  };

  const originalOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    this.__campusMindUrl = new URL(url, location.href).href;
    return originalOpen.call(this, method, url, ...rest);
  };
  const originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function (...args) {
    this.addEventListener("load", () => {
      const contentType = this.getResponseHeader("content-type") || "";
      if (!enabled || !contentType.includes("application/json") || typeof this.responseText !== "string") return;
      try {
        report(JSON.parse(this.responseText), this.responseURL || this.__campusMindUrl);
      } catch (_) {}
    }, { once: true });
    return originalSend.apply(this, args);
  };

  window.addEventListener("message", (event) => {
    if (event.source !== window || event.origin !== location.origin) return;
    if (event.data?.source !== "CampusMindRainExporter") return;
    if (event.data.type === "start") {
      enabled = true;
      if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", scanCourseCards, { once: true });
      else scanCourseCards();
    }
    if (event.data.type === "scan") scanCourseCards();
    if (event.data.type === "stop") enabled = false;
  });
})();

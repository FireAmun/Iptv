// Fills in the latest release details. Without this script the button still links to
// GitHub's stable "latest release" download address.
(function () {
  "use strict";
  var box = document.getElementById("download");
  if (!box || !window.fetch) return;
  var config;
  try { config = JSON.parse(box.getAttribute("data-release")); } catch (error) { return; }
  var button = box.querySelector('[data-role="button"]');
  var meta = box.querySelector('[data-role="meta"]');
  var checksum = box.querySelector('[data-role="checksum"]');

  function unavailable() {
    button.setAttribute("aria-disabled", "true");
    button.removeAttribute("href");
    meta.textContent = config.none;
  }

  fetch("https://api.github.com/repos/" + config.repo + "/releases/latest", {
    headers: { Accept: "application/vnd.github+json" },
    credentials: "omit",
    referrerPolicy: "no-referrer"
  }).then(function (response) {
    if (response.status === 404) return null;
    if (!response.ok) throw new Error("GitHub " + response.status);
    return response.json();
  }).then(function (release) {
    if (!release) { unavailable(); return; }
    var asset = (release.assets || []).filter(function (item) { return item.name === config.asset; })[0];
    if (!asset) {
      // The newest release has no APK attached: send people to the full release list instead.
      if (/^https:\/\/github\.com\//.test(config.releases)) button.href = config.releases;
      return;
    }
    if (/^https:\/\/github\.com\//.test(asset.browser_download_url)) button.href = asset.browser_download_url;
    var version = String(release.tag_name || "").replace(/^v/, "");
    var size = new Intl.NumberFormat(config.lang, { maximumFractionDigits: 1 }).format(asset.size / 1048576);
    var date = new Intl.DateTimeFormat(config.lang, { dateStyle: "medium" }).format(new Date(release.published_at));
    meta.textContent = config.template.replace("{version}", version).replace("{size}", size).replace("{date}", date);
    var digest = typeof asset.digest === "string" ? asset.digest.replace(/^sha256:/, "") : "";
    if (/^[0-9a-f]{64}$/.test(digest)) {
      checksum.querySelector("code").textContent = digest;
      checksum.hidden = false;
    }
  }).catch(function () {
    // Offline or rate limited: keep the static link and text.
  });
})();

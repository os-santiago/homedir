// Shared DOM/string utilities for Homedir page scripts.
// Loaded before any other deferred script (layout <head>) so it is safe to
// consume from any page script regardless of document order.
(function () {
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, m => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "\"": "&quot;",
      "'": "&#039;"
    }[m]));
  }
  function escapeAttr(s) {
    return escapeHtml(s);
  }
  window.HomeDirUtils = { escapeHtml, escapeAttr };
})();

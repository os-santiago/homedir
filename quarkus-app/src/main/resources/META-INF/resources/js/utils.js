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
  function formatDate(raw) {
    if (!raw) {
      return '';
    }
    try {
      var d = new Date(raw);
      if (isNaN(d.getTime())) {
        return '';
      }
      var locale = document.documentElement.lang || navigator.language || undefined;
      return d.toLocaleDateString(locale, {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
      });
    } catch (e) {
      return '';
    }
  }
  function formatDateTime(raw) {
    if (!raw) {
      return '';
    }
    try {
      var d = new Date(raw);
      if (isNaN(d.getTime())) {
        return '';
      }
      return d.toLocaleString();
    } catch (e) {
      return '';
    }
  }
  window.HomeDirUtils = { escapeHtml, escapeAttr, formatDate, formatDateTime };
})();

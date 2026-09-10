// Shared DOM/string utilities for Homedir page scripts.
// Loaded before any other deferred script (layout <head>) so it is safe to
// consume from any page script regardless of document order.
(function () {
  function escapeHtml(s) {
    if (s === null || s === undefined) {
      return '';
    }
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
  // Formats a date using a named preset or custom Intl options.
  //   formatDate(raw)              -> 'date' preset (year, month short, day numeric)
  //   formatDate(raw, 'datetime')  -> month short, day 2-digit, hour/minute 2-digit
  //   formatDate(raw, 'short')     -> month short, day 2-digit
  //   formatDate(raw, { weekday: 'long' }) -> custom Intl.DateTimeFormat options
  // Returns '' for falsy/invalid input.
  var FORMAT_PRESETS = {
    date:     { year: 'numeric', month: 'short', day: 'numeric' },
    datetime: { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' },
    short:    { month: 'short', day: '2-digit' }
  };
  function formatDate(raw, opts) {
    if (!raw) {
      return '';
    }
    try {
      var d = new Date(raw);
      if (isNaN(d.getTime())) {
        return '';
      }
      var locale = document.documentElement.lang || navigator.language || undefined;
      var formatOpts = typeof opts === 'string' ? FORMAT_PRESETS[opts] || FORMAT_PRESETS.date : (opts || FORMAT_PRESETS.date);
      var hasTime = formatOpts.hour || formatOpts.minute;
      return hasTime ? d.toLocaleString(locale, formatOpts) : d.toLocaleDateString(locale, formatOpts);
    } catch (e) {
      return '';
    }
  }
  window.HomeDirUtils = { escapeHtml, escapeAttr, formatDate, formatDateTime: formatDate };
})();

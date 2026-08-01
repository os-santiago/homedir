(function () {
  var btn = document.getElementById('verifyAchievements');
  if (!btn) {
    return;
  }
  btn.addEventListener('click', function () {
    var url = btn.getAttribute('data-verify-url');
    if (!url) {
      return;
    }
    btn.disabled = true;
    var originalText = btn.textContent;
    btn.textContent = '...';
    fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      cache: 'no-store'
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('HTTP ' + res.status);
        }
        return res.json();
      })
      .then(function (data) {
        if (data.newlyCompleted && data.newlyCompleted.length > 0) {
          btn.textContent = '✓ ' + data.newlyCompleted.length + ' new!';
        } else {
          btn.textContent = '✓ Verified';
        }
        setTimeout(function () { window.location.reload(); }, 1500);
      })
      .catch(function (err) {
        console.error('achievement verify failed', err);
        btn.textContent = '✗ Error';
        btn.disabled = false;
        setTimeout(function () { btn.textContent = originalText; }, 2000);
      });
  });
})();

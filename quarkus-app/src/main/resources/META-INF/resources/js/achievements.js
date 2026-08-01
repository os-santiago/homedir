(function () {
  "use strict";

  const claimButtons = document.querySelectorAll(".achievement-claim-btn");
  if (claimButtons.length === 0) {
    return;
  }

  claimButtons.forEach(function (btn) {
    btn.addEventListener("click", async function () {
      if (btn.dataset.claimed === "true" || btn.classList.contains("is-loading")) {
        return;
      }
      const key = btn.dataset.achievementKey;
      if (!key) {
        return;
      }
      btn.classList.add("is-loading");
      btn.disabled = true;
      try {
        const response = await fetch("/api/achievements/claim/" + encodeURIComponent(key), {
          headers: { Accept: "application/json" },
          credentials: "same-origin"
        });
        if (!response.ok) {
          throw new Error("HTTP " + response.status);
        }
        const data = await response.json().catch(function () { return {}; });
        if (data.awarded) {
          btn.dataset.claimed = "true";
          btn.textContent = "XP Claimed!";
          btn.classList.add("claimed");
        } else {
          btn.textContent = data.message || "Already claimed";
          btn.classList.add("claimed");
        }
      } catch (error) {
        btn.textContent = "Error - try again";
        btn.disabled = false;
      } finally {
        btn.classList.remove("is-loading");
      }
    });
  });
})();

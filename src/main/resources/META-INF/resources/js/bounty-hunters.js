document.addEventListener('DOMContentLoaded', () => {
  const leaderboardTable = document.getElementById('bounty-leaderboard-body');
  if (!leaderboardTable) return;

  fetch('/api/bounty-hunters/leaderboard?limit=50')
    .then(res => res.json())
    .then(hunters => {
      leaderboardTable.innerHTML = hunters.map((h, i) => {
        const userId = h.userId || '';
        const rawLevel = h.level;
        const rawPoints = h.totalPoints;

        // Resolve level text and class, handling potential objects or nested properties
        const levelText = typeof rawLevel === 'object' && rawLevel ? (rawLevel.displayName || rawLevel.name || '') : (rawLevel || '');
        const levelClass = typeof rawLevel === 'object' && rawLevel ? (rawLevel.rewardFrameId || rawLevel.name || '') : (rawLevel || '');

        // Resolve points, handling potential objects or nested properties
        const pointsText = typeof rawPoints === 'object' && rawPoints ? (rawPoints.value || rawPoints.amount || 0) : (rawPoints || 0);

        return `
          <tr class="hd-table-row">
            <td class="hd-rank">#${i + 1}</td>
            <td class="hd-user"><a href="/bounty-hunters/${HomeDirUtils.escapeAttr(userId)}">${HomeDirUtils.escapeHtml(userId)}</a></td>
            <td class="hd-points">${HomeDirUtils.escapeHtml(pointsText)} pts</td>
            <td class="hd-level"><span class="hd-badge hd-badge-${HomeDirUtils.escapeAttr(String(levelClass).toLowerCase())}">${HomeDirUtils.escapeHtml(levelText)}</span></td>
          </tr>
        `;
      }).join('');
    })
    .catch(err => console.error('Failed to load bounty leaderboard:', err));
});

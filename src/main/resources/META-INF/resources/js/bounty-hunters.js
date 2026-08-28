/**
 * Bounty Hunters Leaderboard with XSS Sanitization
 */

function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

document.addEventListener('DOMContentLoaded', () => {
  const leaderboardTable = document.getElementById('bounty-leaderboard-body');
  if (!leaderboardTable) return;

  fetch('/api/bounty-hunters/leaderboard?limit=50')
    .then(res => res.json())
    .then(hunters => {
      leaderboardTable.innerHTML = hunters.map((h, i) => {
        const userIdEscaped = escapeHtml(h.userId);
        const pointsEscaped = escapeHtml(h.totalPoints);
        const levelEscaped = escapeHtml(h.level);
        const levelClass = escapeHtml((h.level || '').toLowerCase());
        const safeHref = encodeURIComponent(h.userId || '');
        return `
        <tr class="hd-table-row">
          <td class="hd-rank">#${i + 1}</td>
          <td class="hd-user"><a href="/bounty-hunters/${safeHref}">${userIdEscaped}</a></td>
          <td class="hd-points">${pointsEscaped} pts</td>
          <td class="hd-level"><span class="hd-badge hd-badge-${levelClass}">${levelEscaped}</span></td>
        </tr>
      `;
      }).join('');
    })
    .catch(err => console.error('Failed to load bounty leaderboard:', err));
});

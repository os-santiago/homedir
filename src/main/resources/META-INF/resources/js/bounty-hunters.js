document.addEventListener('DOMContentLoaded', () => {
  const leaderboardTable = document.getElementById('bounty-leaderboard-body');
  if (!leaderboardTable) return;

  fetch('/api/bounty-hunters/leaderboard?limit=50')
    .then(res => res.json())
    .then(hunters => {
      leaderboardTable.innerHTML = hunters.map((h, i) => `
        <tr class="hd-table-row">
          <td class="hd-rank">#${i + 1}</td>
          <td class="hd-user"><a href="/bounty-hunters/${h.userId}">${h.userId}</a></td>
          <td class="hd-points">${h.totalPoints} pts</td>
          <td class="hd-level"><span class="hd-badge hd-badge-${(h.level || '').toLowerCase()}">${h.level}</span></td>
        </tr>
      `).join('');
    })
    .catch(err => console.error('Failed to load bounty leaderboard:', err));
});

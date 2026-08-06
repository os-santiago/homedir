describe('Bounty Hunters Leaderboard UI Component', () => {
  let container;

  beforeEach(() => {
    container = document.createElement('tbody');
    container.id = 'bounty-leaderboard-body';
    document.body.appendChild(container);
  });

  afterEach(() => {
    document.body.innerHTML = '';
    jest.restoreAllMocks();
  });

  it('should render leaderboard rows matching API schema', async () => {
    const mockHunters = [
      { userId: 'user-1', totalPoints: 100, level: 'Novice Bounty Hunter' },
      { userId: 'user-2', totalPoints: 250, level: 'Professional Bounty Hunter' }
    ];

    global.fetch = jest.fn().mockResolvedValue({
      json: jest.fn().mockResolvedValue(mockHunters)
    });

    require('../META-INF/resources/js/bounty-hunters.js');
    document.dispatchEvent(new Event('DOMContentLoaded'));

    await new Promise(resolve => setTimeout(resolve, 50));

    const rows = container.querySelectorAll('tr');
    expect(rows.length).toBe(2);
    expect(rows[0].querySelector('.hd-user').textContent).toBe('user-1');
    expect(rows[0].querySelector('.hd-points').textContent).toBe('100 pts');
    expect(rows[0].querySelector('.hd-level').textContent).toBe('Novice Bounty Hunter');
    expect(rows[1].querySelector('.hd-level').textContent).toBe('Professional Bounty Hunter');
  });
});

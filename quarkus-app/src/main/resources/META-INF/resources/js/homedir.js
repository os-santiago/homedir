let currentUser = null;
let allUsers = [];

// Equipment/Collectibles System
const COLLECTIBLES = [
  { id: 'laptop', icon: '💻', name: 'DEVELOPER LAPTOP', rarity: 'common', description: '+5 Coding Power' },
  { id: 'keyboard', icon: '⌨️', name: 'MECHANICAL KEYBOARD', rarity: 'rare', description: '+10 Typing Speed' },
  { id: 'coffee', icon: '☕', name: 'ENERGY COFFEE', rarity: 'common', description: '+3 Focus' },
  { id: 'badge', icon: '🏅', name: 'CONTRIBUTOR BADGE', rarity: 'epic', description: '+15 Reputation' },
  { id: 'trophy', icon: '🏆', name: 'CHAMPION TROPHY', rarity: 'legendary', description: '+25 Achievement' },
  { id: 'book', icon: '📚', name: 'CODE MANUAL', rarity: 'rare', description: '+8 Knowledge' },
  { id: 'rocket', icon: '🚀', name: 'LAUNCH ROCKET', rarity: 'epic', description: '+20 Deployment' },
  { id: 'gem', icon: '💎', name: 'RARE GEM', rarity: 'legendary', description: '+30 Value' },
  { id: 'shield', icon: '🛡️', name: 'SECURITY SHIELD', rarity: 'epic', description: '+18 Protection' },
  { id: 'wand', icon: '🪄', name: 'DEBUG WAND', rarity: 'rare', description: '+12 Bug Fix' },
  { id: 'crown', icon: '👑', name: 'LEADER CROWN', rarity: 'legendary', description: '+35 Leadership' },
  { id: 'glasses', icon: '👓', name: 'CODE GLASSES', rarity: 'common', description: '+4 Vision' }
];

function generateEquipment(level, experience) {
  const equipment = [];
  const itemCount = Math.min(6, Math.floor(level / 2) + Math.floor(experience / 200));
  const shuffled = [...COLLECTIBLES].sort(() => Math.random() - 0.5);

  for (let i = 0; i < itemCount; i++) {
    equipment.push(shuffled[i].id);
  }

  return equipment;
}

function updateEquipmentDisplay(equipment = []) {
  const slots = document.querySelectorAll('.equipment-slot');
  if (!slots.length) return;

  let filledCount = 0;

  slots.forEach((slot, index) => {
    if (equipment[index]) {
      const collectible = COLLECTIBLES.find((c) => c.id === equipment[index]);
      if (collectible) {
        slot.classList.remove('empty');
        slot.classList.add('filled');
        slot.innerHTML = `
              ${collectible.icon}
              <div class="equipment-rarity rarity-${collectible.rarity}"></div>
              <div class="equipment-tooltip">
                ${collectible.name}<br>
                <span style="color: #FFD700;">${collectible.description}</span>
              </div>
            `;
        filledCount++;
      }
    } else {
      slot.classList.add('empty');
      slot.classList.remove('filled');
      slot.innerHTML = `
            <div class="equipment-tooltip">EMPTY SLOT</div>
          `;
    }
  });

  const equipmentCount = document.querySelector('.equipment-count');
  if (equipmentCount) {
    equipmentCount.textContent = `${filledCount} / 6 ITEMS`;
  }
}

const defaultConfig = {
  platform_name: 'HomeDir',
  tagline: 'by OpenSourceSantiago',
  community_title: 'Community',
  community_description: 'Modern toolbox for community building and opensource technology innovation',
  events_title: 'Events',
  events_description: 'Scale your teams with collaborative meetups and tech conferences',
  projects_title: 'Projects',
  projects_description: 'Innovation hub for your opensource technology projects',
  background_start: '#667eea',
  background_end: '#764ba2',
  card_background: '#ffffff',
  text_color: '#ffffff',
  accent_color: '#FFD700'
};

let config = defaultConfig;

function onConfigChange(newConfig) {
  config = newConfig || config;

  const platformName = config.platform_name || defaultConfig.platform_name;
  const tagline = config.tagline || defaultConfig.tagline;

  const navPlatformName = document.getElementById('navPlatformName');
  const navTagline = document.getElementById('navTagline');
  const mainTitle = document.getElementById('mainTitle');
  const mainTagline = document.getElementById('mainTagline');

  if (navPlatformName) navPlatformName.textContent = platformName;
  if (navTagline) navTagline.textContent = tagline;
  if (mainTitle) mainTitle.textContent = platformName;
  if (mainTagline) {
    mainTagline.textContent = `${tagline} - The community platform to scale your teams and projects`;
  }

  document.body.style.background = `linear-gradient(135deg, ${config.background_start || defaultConfig.background_start} 0%, ${config.background_end || defaultConfig.background_end} 100%)`;
  document.body.style.color = config.text_color || defaultConfig.text_color;
}

function showToast(message) {
  const toast = document.getElementById('toast');
  if (!toast) return;

  toast.textContent = message;
  toast.classList.add('show');
  setTimeout(() => {
    toast.classList.remove('show');
  }, 3000);
}

function openLoginModal() {
  const modal = document.getElementById('loginModal');
  if (modal) {
    modal.classList.add('show');
  }
}

function closeLoginModal() {
  const modal = document.getElementById('loginModal');
  if (modal) {
    modal.classList.remove('show');
  }
}

function updateNavigation() {
  const loginBtn = document.getElementById('openLoginBtn');
  const userMenu = document.getElementById('userMenuNav');
  const avatarEl = document.getElementById('navAvatar');
  const nameEl = document.getElementById('navUserName');

  if (!loginBtn || !userMenu) {
    return;
  }

  if (currentUser) {
    const emoji = currentUser.display_name ? currentUser.display_name.charAt(0) : '👤';
    loginBtn.style.display = 'none';
    userMenu.classList.add('active');
    if (avatarEl) avatarEl.textContent = emoji;
    if (nameEl) nameEl.textContent = currentUser.display_name || 'Player';
  } else {
    loginBtn.style.display = 'block';
    userMenu.classList.remove('active');
  }
}

function updateCharacterSheet() {
  const characterName = document.getElementById('characterName');
  const characterClass = document.getElementById('characterClass');
  const characterLevel = document.getElementById('characterLevel');
  const noviceWarning = document.getElementById('noviceWarning');
  const hpValue = document.getElementById('hpValue');
  const hpBar = document.getElementById('hpBar');
  const spValue = document.getElementById('spValue');
  const spBar = document.getElementById('spBar');
  const xpValue = document.getElementById('xpValue');
  const xpBar = document.getElementById('xpBar');
  const contributionsStat = document.getElementById('contributionsStat');
  const questsStat = document.getElementById('questsStat');
  const eventsStat = document.getElementById('eventsStat');
  const projectsStat = document.getElementById('projectsStat');
  const connectionsStat = document.getElementById('connectionsStat');
  const experienceStat = document.getElementById('experienceStat');
  const loginCharacterBtn = document.getElementById('loginCharacterBtn');
  const logoutCharacterBtn = document.getElementById('logoutCharacterBtn');

  if (!characterName || !characterClass || !characterLevel) return;

  if (currentUser) {
    if (noviceWarning) noviceWarning.style.display = 'none';

    characterName.textContent = currentUser.display_name.toUpperCase();
    characterClass.textContent = 'DEVELOPER';
    characterLevel.textContent = `LEVEL ${currentUser.level}`;

    const maxHP = 100;
    const maxSP = 100;
    const maxXPForLevel = currentUser.level * 100;

    const currentHP = Math.min(100, 10 + currentUser.level * 10 + currentUser.contributions);
    const currentSP = Math.min(100, 5 + currentUser.level * 8 + currentUser.quests_completed * 5);
    const currentXP = currentUser.experience % maxXPForLevel;

    if (hpValue) hpValue.textContent = `${currentHP} / ${maxHP}`;
    if (hpBar) hpBar.style.width = `${currentHP}%`;

    if (spValue) spValue.textContent = `${currentSP} / ${maxSP}`;
    if (spBar) spBar.style.width = `${currentSP}%`;

    if (xpValue) xpValue.textContent = `${currentXP} / ${maxXPForLevel}`;
    if (xpBar) xpBar.style.width = `${(currentXP / maxXPForLevel) * 100}%`;

    if (contributionsStat) contributionsStat.textContent = currentUser.contributions;
    if (questsStat) questsStat.textContent = currentUser.quests_completed;
    if (eventsStat) eventsStat.textContent = currentUser.events_attended;
    if (projectsStat) projectsStat.textContent = currentUser.projects_hosted;
    if (connectionsStat) connectionsStat.textContent = currentUser.connections;
    if (experienceStat) experienceStat.textContent = currentUser.experience;

    const equipment = generateEquipment(currentUser.level, currentUser.experience);
    updateEquipmentDisplay(equipment);

    if (loginCharacterBtn) loginCharacterBtn.style.display = 'none';
    if (logoutCharacterBtn) logoutCharacterBtn.style.display = 'block';
  } else {
    if (noviceWarning) noviceWarning.style.display = 'block';

    characterName.textContent = 'NOVICE GUEST';
    characterClass.textContent = 'VISITOR';
    characterLevel.textContent = 'LEVEL 1';

    if (hpValue) hpValue.textContent = '10 / 100';
    if (hpBar) hpBar.style.width = '10%';

    if (spValue) spValue.textContent = '5 / 100';
    if (spBar) spBar.style.width = '5%';

    if (xpValue) xpValue.textContent = '0 / 100';
    if (xpBar) xpBar.style.width = '0%';

    if (contributionsStat) contributionsStat.textContent = '0';
    if (questsStat) questsStat.textContent = '0';
    if (eventsStat) eventsStat.textContent = '0';
    if (projectsStat) projectsStat.textContent = '0';
    if (connectionsStat) connectionsStat.textContent = '0';
    if (experienceStat) experienceStat.textContent = '0';

    updateEquipmentDisplay([]);

    if (loginCharacterBtn) loginCharacterBtn.style.display = 'block';
    if (logoutCharacterBtn) logoutCharacterBtn.style.display = 'none';
  }
}

// TODO: Reemplazar este login demo por integración real con backend/OIDC de Homedir
async function handleLogin(provider) {
  const btn = provider === 'google' ? document.getElementById('googleLogin') : document.getElementById('githubLogin');
  if (!btn) return;

  btn.classList.add('loading');
  btn.disabled = true;

  const demoNames = ['Alex Chen', 'Maria Garcia', 'Jamal Johnson', 'Sofia Rodriguez', 'Kai Nakamura'];
  const randomName = demoNames[Math.floor(Math.random() * demoNames.length)];
  const userId = `user_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

  const demoLevel = Math.floor(Math.random() * 5) + 1;
  const demoExperience = Math.floor(Math.random() * 500);

  const newUser = {
    user_id: userId,
    display_name: randomName,
    avatar_url: '',
    level: demoLevel,
    experience: demoExperience,
    contributions: Math.floor(Math.random() * 30),
    quests_completed: Math.floor(Math.random() * 10),
    events_attended: Math.floor(Math.random() * 5),
    projects_hosted: Math.floor(Math.random() * 3),
    connections: Math.floor(Math.random() * 20),
    login_provider: provider,
    created_at: new Date().toISOString()
  };

  // DEMO: guardamos en memoria nada más
  allUsers.push(newUser);
  currentUser = newUser;

  closeLoginModal();
  updateNavigation();
  updateCharacterSheet();
  showToast(`Welcome, ${randomName}! 🎉 Your character has been created!`);

  btn.classList.remove('loading');
  btn.disabled = false;
}

function handleLogout() {
  currentUser = null;
  updateNavigation();
  updateCharacterSheet();
  showToast('Logged out successfully - Character data saved!');
}

const TECH_AVATARS = ['👨‍💻', '👩‍💻', '🧑‍💻', '👨‍🎨', '👩‍🎨', '🧑‍🎨', '👨‍🔬', '👩‍🔬', '🧑‍🔬', '🦸‍♂️', '🦸‍♀️', '🧙‍♂️', '🧙‍♀️', '🤖', '👾', '🚀'];

const TECH_ROLES = [
  { emoji: '⚛️', name: 'FRONTEND' },
  { emoji: '⚙️', name: 'BACKEND' },
  { emoji: '🎨', name: 'DESIGNER' },
  { emoji: '📊', name: 'DATA SCI' },
  { emoji: '🏗️', name: 'PLATFORM' },
  { emoji: '🔐', name: 'DEVOPS' },
  { emoji: '📱', name: 'MOBILE' },
  { emoji: '🧪', name: 'QA' },
  { emoji: '📈', name: 'PRODUCT' }
];

const STATUS_TYPES = ['online', 'busy', 'away'];

function generateVillageInhabitants() {
  const inhabitantsLayer = document.getElementById('inhabitantsLayer');
  if (!inhabitantsLayer) return;

  inhabitantsLayer.innerHTML = '';

  const members = allUsers.length > 0 ? allUsers : generateDemoInhabitants();

  members.forEach((user, index) => {
    const member = document.createElement('div');
    member.className = 'team-member';

    const left = 12 + Math.random() * 76;
    const bottom = 18 + Math.random() * 35;

    member.style.left = `${left}%`;
    member.style.bottom = `${bottom}%`;
    member.style.animationDelay = `${Math.random() * 4}s`;

    const avatar = user.display_name
      ? TECH_AVATARS[user.display_name.charCodeAt(0) % TECH_AVATARS.length]
      : TECH_AVATARS[index % TECH_AVATARS.length];
    const role = TECH_ROLES[index % TECH_ROLES.length];
    const status = STATUS_TYPES[Math.floor(Math.random() * STATUS_TYPES.length)];

    member.innerHTML = `
          <div class="member-level">${user.level || 1}</div>
          <div class="member-avatar">
            ${avatar}
            <div class="member-status status-${status}"></div>
          </div>
          <div class="member-badge">${user.display_name || 'Guest'}</div>
          <div class="member-role">${role.emoji}</div>
        `;

    inhabitantsLayer.appendChild(member);
  });

  updateCommunityStats(members);
}

function generateDemoInhabitants() {
  const demoMembers = [
    { name: 'Alex Chen', role: 'Senior Frontend Dev' },
    { name: 'Maria Garcia', role: 'Backend Architect' },
    { name: 'Jamal Johnson', role: 'UX Designer' },
    { name: 'Sofia Rodriguez', role: 'Data Scientist' },
    { name: 'Kai Nakamura', role: 'DevOps Engineer' },
    { name: 'Emma Wilson', role: 'Full Stack Dev' },
    { name: 'Lucas Silva', role: 'Mobile Developer' },
    { name: 'Aisha Patel', role: 'UI Designer' },
    { name: 'Oliver Kim', role: 'QA Engineer' },
    { name: 'Isabella Brown', role: 'Product Manager' },
    { name: 'Noah Zhang', role: 'Tech Lead' },
    { name: 'Mia Ivanov', role: 'Frontend Dev' },
    { name: 'Ethan Okafor', role: 'ML Engineer' },
    { name: 'Ava Müller', role: 'Brand Designer' },
    { name: "Liam O'Brien", role: 'Security Expert' }
  ];

  return demoMembers.map((member, index) => ({
    user_id: `demo_${index}`,
    display_name: member.name,
    level: Math.floor(Math.random() * 10) + 1,
    experience: Math.floor(Math.random() * 1200),
    contributions: Math.floor(Math.random() * 80),
    quests_completed: Math.floor(Math.random() * 30),
    projects_hosted: Math.floor(Math.random() * 8),
    connections: Math.floor(Math.random() * 20)
  }));
}

function updateCommunityStats(inhabitants) {
  const totalMembers = inhabitants.length;
  const totalXP = inhabitants.reduce((sum, user) => sum + (user.experience || 0), 0);
  const totalQuests = inhabitants.reduce((sum, user) => sum + (user.quests_completed || 0), 0);
  const totalProjects = inhabitants.reduce((sum, user) => sum + (user.projects_hosted || 0), 0);

  const totalMembersEl = document.getElementById('totalMembers');
  const totalXPEl = document.getElementById('totalXP');
  const totalQuestsEl = document.getElementById('totalQuests');
  const totalProjectsEl = document.getElementById('totalProjects');

  if (totalMembersEl) totalMembersEl.textContent = totalMembers;
  if (totalXPEl) totalXPEl.textContent = totalXP.toLocaleString();
  if (totalQuestsEl) totalQuestsEl.textContent = totalQuests;
  if (totalProjectsEl) totalProjectsEl.textContent = totalProjects;
}

async function fetchCurrentUserProfile() {
  try {
    const response = await fetch('/api/me', {
      headers: {
        Accept: 'application/json'
      }
    });

    if (!response.ok) {
      console.error('Failed to fetch current user profile', response.status);
      return;
    }

    const profile = await response.json();
    if (!profile) {
      return;
    }

    if (!profile.authenticated) {
      // Usuario anónimo: mantenemos el modo NOVICE GUEST
      currentUser = null;
      updateNavigation();
      updateCharacterSheet();
      return;
    }

    // TODO: cuando Homedir tenga un perfil de usuario completo,
    // alinear estos campos con el modelo real.
    currentUser = {
      user_id: profile.userId || profile.displayName || 'me',
      display_name: profile.displayName || 'Player',
      avatar_url: profile.avatarUrl || '',
      level: profile.level ?? 1,
      experience: profile.experience ?? 0,
      contributions: profile.contributions ?? 0,
      quests_completed: profile.questsCompleted ?? 0,
      events_attended: profile.eventsAttended ?? 0,
      projects_hosted: profile.projectsHosted ?? 0,
      connections: profile.connections ?? 0
    };

    const existingIndex = allUsers.findIndex((u) => u.user_id === currentUser.user_id);
    if (existingIndex === -1) {
      allUsers.push(currentUser);
    } else {
      allUsers[existingIndex] = currentUser;
    }

    updateNavigation();
    updateCharacterSheet();
  } catch (e) {
    console.error('Error loading current user profile', e);
  }
}

// Fuente de verdad para las estadísticas mostradas en la landing.
// En el futuro debe alinearse con los datos reales de Homedir.
async function fetchLandingStats() {
  try {
    const response = await fetch('/api/landing/stats');
    if (!response.ok) {
      console.error('Failed to fetch landing stats', response.status);
      return;
    }

    const stats = await response.json();

    const membersEl = document.getElementById('totalMembers');
    const xpEl = document.getElementById('totalXP');
    const questsEl = document.getElementById('totalQuests');
    const projectsEl = document.getElementById('totalProjects');

    if (membersEl) membersEl.textContent = stats.totalMembers ?? 0;
    if (xpEl) xpEl.textContent = (stats.totalXP ?? 0).toLocaleString();
    if (questsEl) questsEl.textContent = stats.totalQuests ?? 0;
    if (projectsEl) projectsEl.textContent = stats.totalProjects ?? 0;
  } catch (e) {
    console.error('Error loading landing stats', e);
  }
}

function showCommunityView() {
  const publicContent = document.getElementById('publicContent') || document.querySelector('.public-content');
  const communityView = document.getElementById('communityView');

  if (publicContent) publicContent.style.display = 'none';
  if (communityView) communityView.style.display = 'block';

  generateVillageInhabitants();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function hideCommunityView() {
  const publicContent = document.getElementById('publicContent') || document.querySelector('.public-content');
  const communityView = document.getElementById('communityView');

  if (publicContent) publicContent.style.display = 'block';
  if (communityView) communityView.style.display = 'none';

  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function updateProfileDisplay() {
  const communityView = document.getElementById('communityView');
  if (communityView && communityView.style.display === 'block') {
    generateVillageInhabitants();
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const openLoginBtn = document.getElementById('openLoginBtn');
  if (openLoginBtn) openLoginBtn.addEventListener('click', openLoginModal);

  const closeLoginBtn = document.getElementById('closeLoginBtn');
  if (closeLoginBtn) closeLoginBtn.addEventListener('click', closeLoginModal);

  const googleLogin = document.getElementById('googleLogin');
  if (googleLogin) googleLogin.addEventListener('click', () => handleLogin('google'));

  const githubLogin = document.getElementById('githubLogin');
  if (githubLogin) githubLogin.addEventListener('click', () => handleLogin('github'));

  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) logoutBtn.addEventListener('click', handleLogout);

  const loginCharacterBtn = document.getElementById('loginCharacterBtn');
  if (loginCharacterBtn) loginCharacterBtn.addEventListener('click', openLoginModal);

  const logoutCharacterBtn = document.getElementById('logoutCharacterBtn');
  if (logoutCharacterBtn) logoutCharacterBtn.addEventListener('click', handleLogout);

  const communityCard = document.getElementById('communityCard');
  if (communityCard) communityCard.addEventListener('click', showCommunityView);

  const backButton = document.getElementById('backButton');
  if (backButton) backButton.addEventListener('click', hideCommunityView);

  const loginModal = document.getElementById('loginModal');
  if (loginModal) {
    loginModal.addEventListener('click', (e) => {
      if (e.target.id === 'loginModal') {
        closeLoginModal();
      }
    });
  }

  updateCharacterSheet();
  onConfigChange(defaultConfig);
  fetchCurrentUserProfile();
  fetchLandingStats && fetchLandingStats();
});

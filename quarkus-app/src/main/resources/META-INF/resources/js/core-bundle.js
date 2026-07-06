var currentUser = window.currentUser || null;
var allUsers = window.allUsers || [];

// Equipment/Collectibles System
var COLLECTIBLES = window.COLLECTIBLES || [
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
window.currentUser = currentUser;
window.allUsers = allUsers;
window.COLLECTIBLES = COLLECTIBLES;

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

  // document.body.style.background = `linear-gradient(135deg, ${config.background_start || defaultConfig.background_start} 0%, ${config.background_end || defaultConfig.background_end} 100%)`;
  // document.body.style.color = config.text_color || defaultConfig.text_color;
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

// Toggle user menu dropdown
function toggleUserMenu(event) {
  if (event) event.stopPropagation();
  const userMenu = document.getElementById('userMenuNav');
  if (!userMenu) return;
  userMenu.classList.toggle('active');
  const dropdown = document.querySelector('.header-dropdown-menu');
  if (dropdown) dropdown.classList.toggle('show');
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
  // updateCharacterSheet();
  updateCommunityStats(allUsers);
  updateProfileDisplay();
  showToast(`Welcome, ${randomName}! 🎉 Your character has been created!`);

  btn.classList.remove('loading');
  btn.disabled = false;
}

function handleLogout() {
  currentUser = null;
  updateNavigation();
  // updateCharacterSheet();
  updateCommunityStats(allUsers);
  updateProfileDisplay();
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
    // updateCharacterSheet();
    updateCommunityStats(allUsers);
    updateProfileDisplay();
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
  const communityCard = document.getElementById('communityCard');
  const eventsCard = document.getElementById('eventsCard');
  const projectsCard = document.getElementById('projectsCard');

  if (communityCard) {
    communityCard.addEventListener('click', (event) => {
      if (event.target.closest('a') || event.target.closest('button')) {
        return;
      }
      event.preventDefault();
      showCommunityView();
      // TODO: si en el futuro existe una sección /community completa,
      // evaluar si esta card debe navegar ahí en vez de usar solo showCommunityView().
    });
  }

  if (eventsCard) {
    eventsCard.addEventListener('click', (event) => {
      if (event.target.closest('a') || event.target.closest('button')) {
        return;
      }
      event.preventDefault();
      window.location.href = '/eventos';
    });
  }

  if (projectsCard) {
    projectsCard.addEventListener('click', (event) => {
      if (event.target.closest('a') || event.target.closest('button')) {
        return;
      }
      event.preventDefault();
      window.location.href = '/proyectos';
    });
  }

  const openCommunityButton = document.getElementById('openCommunity');
  if (openCommunityButton) {
    openCommunityButton.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      showCommunityView();
    });
  }

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
  document.querySelectorAll('[data-user-menu-toggle]').forEach((button) => {
    button.addEventListener('click', toggleUserMenu);
  });
  // onConfigChange(defaultConfig);
  // Close user menu when clicking outside
  document.addEventListener('click', (e) => {
    // If the click is on the user menu button, ignore (handled by toggleUserMenu)
    if (e.target.closest('.user-menu-btn')) return;
    const userMenu = document.getElementById('userMenuNav');
    if (userMenu) {
      userMenu.classList.remove('active');
      const dropdown = document.querySelector('.header-dropdown-menu');
      if (dropdown) dropdown.classList.remove('show');
    }
  });
});
(function () {
    const origFetch = window.fetch;
    const resolveRequestUrl = (input) => {
        try {
            if (typeof input === 'string') {
                return new URL(input, window.location.origin);
            }
            if (input && typeof input.url === 'string') {
                return new URL(input.url, window.location.origin);
            }
        } catch (error) {
            return null;
        }
        return null;
    };
    const isSameOriginRequest = (url) => Boolean(url) && url.origin === window.location.origin;
    const isCriticalApi = (url) =>
        isSameOriginRequest(url) && String(url.pathname || '').startsWith('/api/private/');
    const shouldAttachBearer = (url) => isCriticalApi(url);
    window.fetch = async function (input, init = {}) {
        const requestUrl = resolveRequestUrl(input);
        if (isSameOriginRequest(requestUrl) && !init.credentials) {
            init.credentials = 'include';
        }
        init.headers = new Headers(init.headers || {});
        if (shouldAttachBearer(requestUrl) && !init.headers.has('Authorization')) {
            const token = sessionStorage.getItem('token') || localStorage.getItem('token');
            if (token) { init.headers.set('Authorization', `Bearer ${token}`); }
        }
        const res = await origFetch(input, init);
        const url = requestUrl ? requestUrl.pathname : (typeof input === 'string' ? input : (input && input.url) || '');
        if (res.status === 401) {
            if (isCriticalApi(requestUrl) && res.headers.get('X-Session-Expired') === 'true') {
                try { sessionStorage.clear(); localStorage.clear(); } catch (e) { }
                if (location.pathname !== '/') { location.assign('/?session=expired'); }
            } else {
                console.debug('non-critical 401', url);
            }
        }
        return res;
    };
})();

/**
 * Mapa de selectores usados por la capa de UI.
 * Cada entrada apunta a un atributo `data-*` presente en el HTML, lo que
 * desacopla la lógica de JavaScript de los ids o clases concretos.
 * Modifique estos valores si cambia la estructura del marcado para mantener
 * el contrato entre HTML y JavaScript.
 */
const SELECTORS = {
    /** Botón para desplegar el menú principal en móvil */
    menuToggle: '[data-menu-toggle]',
    /** Contenedor de enlaces de navegación principal */
    primaryNav: '[data-primary-navigation]',
    /** Contenedor del menú del usuario */
    userMenu: '[data-user-menu]',
    /** Botón que abre/cierra el menú del usuario */
    userMenuBtn: '[data-user-menu-btn]',
    /** Botón para vista secuencial de agenda */
    agendaSeqBtn: '[data-agenda-seq-btn]',
    /** Botón para vista en cuadrícula de agenda */
    agendaGridBtn: '[data-agenda-grid-btn]',
    /** Contenedores de la vista secuencial de agenda */
    agendaSeqViews: '[data-agenda-seq]',
    /** Contenedores de la vista en cuadrícula de agenda */
    agendaGridViews: '[data-agenda-grid]',
    /** Banner superior con efecto parallax */
    banner: '[data-banner]',
    /** Elemento indicador de carga */
    loading: '[data-loading]',
    /** Área de notificaciones */
    notification: '[data-notification]',
    /** Enlaces de navegación */
    navLinks: '[data-nav-link]',
    /** Indicador cuando no hay eventos */
    noEvents: '[data-no-events]',
    /** Formularios generales de la página */
    forms: 'form',
    /** Formulario selector de idioma en el header */
    localeSwitcherForm: '[data-locale-switcher-form]',
    /** Select de idioma del header */
    localeSwitcherSelect: '[data-locale-select]',
    /** Hidden redirect del selector de idioma */
    localeSwitcherRedirect: '[data-locale-redirect]'
};

// Funciones de ayuda para seleccionar elementos utilizando los selectores centralizados.
const $ = (key) => document.querySelector(SELECTORS[key]);
const $$ = (key) => document.querySelectorAll(SELECTORS[key]);
const isUltraLiteMode = () => document.body && document.body.classList.contains('ultra-lite-mode');
const APP_I18N = (() => {
    const body = document.body;
    const dataset = body && body.dataset ? body.dataset : {};
    const read = (key, fallback) => {
        const value = dataset[key];
        if (typeof value === 'string' && value.trim()) {
            return value;
        }
        return fallback;
    };
    return {
        loadErrorPrefix: read('i18nAppLoadErrorPrefix', 'Could not load'),
        loadingContent: read('i18nAppLoadingContent', 'content'),
        loadingData: read('i18nAppLoadingData', 'data'),
        loadingPage: read('i18nAppLoadingPage', 'page'),
        sessionExpired: read('i18nAppSessionExpired', 'Session expired'),
        noEvents: read('i18nAppNoEvents', 'No events available')
    };
})();

function currentRelativeUrl() {
    const path = window.location.pathname || '/';
    const query = window.location.search || '';
    const hash = window.location.hash || '';
    return `${path}${query}${hash}`;
}

function normalizeSupportedLocale(rawLocale) {
    if (typeof rawLocale !== 'string') return null;
    const trimmed = rawLocale.trim().toLowerCase();
    if (!trimmed) return null;
    const short = trimmed.includes('-') ? trimmed.split('-')[0] : trimmed;
    if (short === 'es' || short === 'en') return short;
    return null;
}

function resolveBrowserPreferredLocale() {
    const langs = Array.isArray(navigator.languages) ? navigator.languages : [];
    const fallbackList = [navigator.language, navigator.userLanguage, navigator.browserLanguage]
        .filter(Boolean);
    const candidates = [...langs, ...fallbackList];
    for (const candidate of candidates) {
        const normalized = normalizeSupportedLocale(candidate);
        if (normalized) return normalized;
    }
    return 'es';
}

function setLocaleCookie(locale) {
    const safeLocale = normalizeSupportedLocale(locale) || 'es';
    const oneYearInSeconds = 60 * 60 * 24 * 365;
    document.cookie = `QP_LOCALE=${safeLocale}; Path=/; Max-Age=${oneYearInSeconds}; SameSite=Lax`;
}

function buildLoginCallbackUrl(redirectTarget) {
    const fallback = '/';
    const rawTarget = typeof redirectTarget === 'string' ? redirectTarget.trim() : '';
    const target = rawTarget || currentRelativeUrl() || fallback;
    return `/private/login-callback?redirect=${encodeURIComponent(target)}`;
}

function setupLoginReturnLinks() {
    const links = document.querySelectorAll('a[data-login-return-current="true"]');
    links.forEach((link) => {
        const preferred = (link.getAttribute('data-login-redirect') || '').trim();
        link.setAttribute('href', buildLoginCallbackUrl(preferred || currentRelativeUrl()));
    });
}

function setupLocaleSwitcher() {
    const form = $('localeSwitcherForm');
    if (!form) return;
    const select = form.querySelector(SELECTORS.localeSwitcherSelect);
    const redirectInput = form.querySelector(SELECTORS.localeSwitcherRedirect);
    if (!select) return;

    const isAuthenticated = String(form.dataset.authenticated || '').toLowerCase() === 'true';
    const applyRedirectTarget = () => {
        if (redirectInput) {
            redirectInput.value = currentRelativeUrl();
        }
    };

    applyRedirectTarget();

    if (!isAuthenticated && !normalizeSupportedLocale(select.value)) {
        select.value = resolveBrowserPreferredLocale();
    }

    form.addEventListener('submit', (event) => {
        applyRedirectTarget();
        const chosenLocale = normalizeSupportedLocale(select.value)
            || (isAuthenticated ? 'es' : resolveBrowserPreferredLocale());
        setLocaleCookie(chosenLocale);

        if (!isAuthenticated) {
            event.preventDefault();
            window.location.assign(currentRelativeUrl());
            return;
        }

        select.value = chosenLocale;
    });

    select.addEventListener('change', () => {
        const chosenLocale = normalizeSupportedLocale(select.value)
            || (isAuthenticated ? 'es' : resolveBrowserPreferredLocale());
        select.value = chosenLocale;
        setLocaleCookie(chosenLocale);

        if (!isAuthenticated) {
            window.location.assign(currentRelativeUrl());
            return;
        }
        if (typeof form.requestSubmit === 'function') {
            form.requestSubmit();
        } else {
            form.submit();
        }
    });
}

function adjustLayout() {
    const toggle = $('menuToggle');
    const links = $('primaryNav');
    if (window.innerWidth < 600) {
        document.body.classList.add('mobile');
        if (links && !links.classList.contains('active')) {
            links.setAttribute('hidden', '');
            if (toggle) toggle.setAttribute('aria-expanded', 'false');
        }
    } else {
        document.body.classList.remove('mobile');
        if (links) {
            links.classList.remove('active');
            links.removeAttribute('hidden');
        }
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }
}

function setupMenu() {
    const toggle = $('menuToggle');
    const links = $('primaryNav');
    if (toggle && links) {
        toggle.addEventListener('click', () => {
            const expanded = toggle.getAttribute('aria-expanded') === 'true';
            toggle.setAttribute('aria-expanded', String(!expanded));
            links.classList.toggle('active');
            if (expanded) {
                links.setAttribute('hidden', '');
            } else {
                links.removeAttribute('hidden');
            }
        });
    }
}

function setupUserMenu() {
    const menu = $('userMenu');
    const btn = $('userMenuBtn');
    if (menu && btn) {
        btn.addEventListener('click', () => {
            const expanded = btn.getAttribute('aria-expanded') === 'true';
            btn.setAttribute('aria-expanded', String(!expanded));
            menu.classList.toggle('open');
            const dropdown = menu.querySelector('.dropdown');
            if (dropdown) {
                if (expanded) {
                    dropdown.setAttribute('hidden', '');
                } else {
                    dropdown.removeAttribute('hidden');
                }
            }
        });
        document.addEventListener('click', (e) => {
            if (!menu.contains(e.target)) {
                btn.setAttribute('aria-expanded', 'false');
                menu.classList.remove('open');
                const dropdown = menu.querySelector('.dropdown');
                if (dropdown) dropdown.setAttribute('hidden', '');
            }
        });
    }
}

function setupUserMenuActiveState() {
    // Seleccionar el contenedor user-menu-toggle
    const userMenuToggle = document.querySelector('.user-menu-toggle');
    const userProfileLink = document.querySelector('.user-profile-link');

    if (!userMenuToggle) return;

    // Restaurar el estado activo si está guardado en sessionStorage
    const isActive = sessionStorage.getItem('userMenuActive');
    if (isActive === 'true') {
        userMenuToggle.classList.add('active');
    }

    // Agregar evento click al enlace del perfil y a todo el toggle
    if (userProfileLink) {
        userProfileLink.addEventListener('click', () => {
            userMenuToggle.classList.add('active');
            sessionStorage.setItem('userMenuActive', 'true');
        });
    }

    // Detectar clics en cualquier parte del user-menu-toggle
    userMenuToggle.addEventListener('click', () => {
        userMenuToggle.classList.add('active');
        sessionStorage.setItem('userMenuActive', 'true');
    });

    // Limpiar el estado activo cuando se navega a otra pestaña
    const navLinks = document.querySelectorAll('.retro-nav-link');
    navLinks.forEach(link => {
        link.addEventListener('click', () => {
            sessionStorage.removeItem('userMenuActive');
        });
    });
}

function setupAgendaToggle() {
    const seqBtn = $('agendaSeqBtn');
    const gridBtn = $('agendaGridBtn');
    const seqViews = $$('agendaSeqViews');
    const gridViews = $$('agendaGridViews');
    if (seqBtn && gridBtn && seqViews.length && gridViews.length) {
        seqBtn.addEventListener('click', () => {
            seqBtn.classList.add('active');
            gridBtn.classList.remove('active');
            seqViews.forEach(v => v.classList.remove('hidden'));
            gridViews.forEach(v => v.classList.add('hidden'));
        });
        gridBtn.addEventListener('click', () => {
            gridBtn.classList.add('active');
            seqBtn.classList.remove('active');
            gridViews.forEach(v => v.classList.remove('hidden'));
            seqViews.forEach(v => v.classList.add('hidden'));
        });
    }
}

function setupViewFullAgenda() {
    const btn = document.getElementById('view-full-agenda');
    if (btn) {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            const panel = document.querySelector('#agenda');
            if (panel) {
                panel.classList.remove('collapsed');
                if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
                    panel.scrollIntoView();
                } else {
                    panel.scrollIntoView({ behavior: 'smooth' });
                }
            }
        });
    }
}

function bannerParallax() {
    if (isUltraLiteMode()) {
        return;
    }
    const banner = $('banner');
    if (banner) {
        banner.style.backgroundPositionX = (window.scrollY * 0.3) + 'px';
    }
}

let loadingTimeout;
let loadingTarget = APP_I18N.loadingContent;

function showLoading(target = APP_I18N.loadingContent, enableTimeout = true) {
    loadingTarget = target;
    document.body.classList.remove('loaded');
    const loader = $('loading');
    if (loader) loader.classList.remove('hidden');
    clearTimeout(loadingTimeout);
    if (enableTimeout) {
        loadingTimeout = setTimeout(() => {
            hideLoading();
            showNotification('error', `${APP_I18N.loadErrorPrefix} ${loadingTarget}`);
        }, 5000);
    }
}

function hideLoading() {
    const loader = $('loading');
    if (loader) loader.classList.add('hidden');
    document.body.classList.add('loaded');
    clearTimeout(loadingTimeout);
}

function showNotification(type, message) {
    const note = $('notification');
    if (!note) return;
    note.innerHTML = message;
    note.className = 'notification ' + type;
    note.classList.add('show');
    setTimeout(() => {
        note.classList.remove('show');
        setTimeout(() => {
            note.className = 'notification hidden';
            note.innerHTML = '';
        }, 300);
    }, 3000);
}

function handleForms() {
    $$('forms').forEach(form => {
        form.addEventListener('submit', e => {
            if (form.classList.contains('needs-confirm') && !confirm('¿Estás seguro?')) {
                e.preventDefault();
                return;
            }
            sessionStorage.setItem('scrollPos', String(window.scrollY));
            sessionStorage.setItem('scrollPath', window.location.pathname);
            showLoading(APP_I18N.loadingData);
            const btn = e.submitter || form.querySelector('button[type="submit"]');
            if (btn) {
                setTimeout(() => btn.disabled = true, 0);
            }
        });
    });
}

function highlightNav() {
    const links = $$('navLinks');
    links.forEach(link => {
        if (link.getAttribute('href') === window.location.pathname) {
            link.classList.add('active');
        }
    });
}

function handleNotificationsFromUrl() {
    const params = new URLSearchParams(window.location.search);
    if (params.has('success')) {
        showNotification('success', params.get('success'));
    }
    if (params.has('error')) {
        showNotification('error', params.get('error'));
    }
    if (params.get('session') === 'expired') {
        showNotification('error', APP_I18N.sessionExpired);
    }
    if (params.has('msg')) {
        const msg = params.get('msg');
        const type = msg.startsWith('✅') ? 'success' : 'error';
        showNotification(type, msg);
    }
}

function restoreScroll() {
    const path = sessionStorage.getItem('scrollPath');
    const pos = sessionStorage.getItem('scrollPos');
    if (path === window.location.pathname && pos !== null) {
        window.scrollTo(0, parseInt(pos, 10));
    }
    sessionStorage.removeItem('scrollPath');
    sessionStorage.removeItem('scrollPos');
}

function onDomContentLoaded() {
    setupLocaleSwitcher();
    setupLoginReturnLinks();
    setupMenu();
    setupUserMenu();
    setupUserMenuActiveState();
    setupAgendaToggle();
    setupViewFullAgenda();
    adjustLayout();
    if (!isUltraLiteMode()) {
        bannerParallax();
    }
    handleForms();
    highlightNav();
    handleNotificationsFromUrl();
    restoreScroll();
    hideLoading();
    if ($('noEvents')) {
        hideLoading();
        showNotification('info', APP_I18N.noEvents);
    }
}

let domContentLoadedHandler;
let resizeHandler;
let scrollHandler;
let beforeUnloadHandler;
let unloadHandler;

function initListeners() {
    domContentLoadedHandler = onDomContentLoaded;
    window.addEventListener('DOMContentLoaded', domContentLoadedHandler);

    resizeHandler = adjustLayout;
    window.addEventListener('resize', resizeHandler);

    if (!isUltraLiteMode()) {
        scrollHandler = bannerParallax;
        window.addEventListener('scroll', scrollHandler);

        beforeUnloadHandler = () => showLoading(APP_I18N.loadingPage, false);
        window.addEventListener('beforeunload', beforeUnloadHandler);

        unloadHandler = () => removeListeners();
        window.addEventListener('unload', unloadHandler);
    }
}

function removeListeners() {
    if (domContentLoadedHandler) {
        window.removeEventListener('DOMContentLoaded', domContentLoadedHandler);
        domContentLoadedHandler = null;
    }
    if (resizeHandler) {
        window.removeEventListener('resize', resizeHandler);
        resizeHandler = null;
    }
    if (scrollHandler) {
        window.removeEventListener('scroll', scrollHandler);
        scrollHandler = null;
    }
    if (beforeUnloadHandler) {
        window.removeEventListener('beforeunload', beforeUnloadHandler);
        beforeUnloadHandler = null;
    }
    if (unloadHandler) {
        window.removeEventListener('unload', unloadHandler);
        unloadHandler = null;
    }
}

if (typeof window !== 'undefined') {
    window.buildLoginCallbackUrl = buildLoginCallbackUrl;
    window.initListeners = initListeners;
    window.removeListeners = removeListeners;
    initListeners();
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { initListeners, removeListeners };
}
// window.__EF_NOTIFICATIONS_MODE__ = 'global' | 'user'; default: 'global'
(function () {
  const MODE = window.__EF_NOTIFICATIONS_MODE__ || 'global';
  const LS = window.localStorage;
  const LS_UNREAD_KEY = 'ef_global_unread_count'; // derivado del estado local
  function getUnreadCountGlobal() {
    const raw = LS.getItem(LS_UNREAD_KEY);
    return raw ? parseInt(raw, 10) : 0;
  }
  async function getUnreadCountUser() {
    const res = await fetch('/api/notifications?filter=unread&limit=1', { cache: 'no-store' });
    if (!res.ok) throw new Error('unread fetch failed: ' + res.status);
    const data = await res.json();
    return data.unreadCount ?? 0;
  }
  window.EFNotificationsAdapter = {
    async getUnreadCount() {
      return MODE === 'user' ? getUnreadCountUser() : getUnreadCountGlobal();
    }
  };
})();
(function () {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  function uid() { return (self.crypto && crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)); }
  class ToastQueueManager {
    constructor(container) {
      this.container = container;
      this.closeAllBtn = document.getElementById('ef-toast-close-all');
      this.maxVisible = parseInt(container.dataset.maxVisible || '3', 10);
      this.autoDismissMs = parseInt(container.dataset.autoDismissMs || '6000', 10);
      this.rateLimitWindow = 30000;
      this.rateLimitMax = 6;
      this.visible = [];
      this.queue = [];
      this.timestamps = [];
      this.closeAllBtn.addEventListener('click', () => this.closeAll());
      this.reduceMotion = reduceMotion;
    }
    enqueue(vm) {
      const now = Date.now();
      this.timestamps = this.timestamps.filter(t => now - t < this.rateLimitWindow);
      if (this.timestamps.length >= this.rateLimitMax) { this.metric('rate_limited'); return; }
      if (this.visible.some(t => t.id === vm.id) || this.queue.some(t => t.id === vm.id)) { this.metric('suppressed'); return; }
      this.timestamps.push(now);
      if (this.visible.length < this.maxVisible) { this.show(vm); } else { this.queue.push(vm); }
      this.updateCloseAll();
    }
    show(vm) {
      vm.node = this.render(vm);
      this.visible.push(vm);
      this.metric('shown');
      requestAnimationFrame(() => this.container.appendChild(vm.node));
      vm.timer = this.startTimer(vm);
    }
    startTimer(vm) {
      let remaining = this.autoDismissMs;
      let start = Date.now();
      const tick = () => this.close(vm.id);
      let timer = setTimeout(tick, remaining);
      const pause = () => { clearTimeout(timer); remaining -= Date.now() - start; };
      const resume = () => { start = Date.now(); timer = setTimeout(tick, remaining); };
      vm.node.addEventListener('mouseenter', pause);
      vm.node.addEventListener('mouseleave', resume);
      vm.node.addEventListener('focusin', pause);
      vm.node.addEventListener('focusout', resume);
      return timer;
    }
    render(vm) {
      const toast = document.createElement('div');
      toast.className = 'ef-toast';
      toast.setAttribute('data-id', vm.id);
      toast.setAttribute('tabindex', '0');
      toast.setAttribute('role', vm.urgent ? 'alert' : 'status');
      toast.addEventListener('keydown', e => { if (e.key === 'Escape') { this.close(vm.id); } });
      const title = document.createElement('div');
      title.className = 'ef-toast__title';
      title.textContent = vm.title;
      toast.appendChild(title);
      const msg = document.createElement('div');
      msg.className = 'ef-toast__message';
      msg.textContent = vm.message;
      toast.appendChild(msg);
      const actions = document.createElement('div');
      actions.className = 'ef-toast__actions';
      if (vm.centerUrl) {
        const detail = document.createElement('a');
        detail.href = vm.centerUrl;
        detail.textContent = 'Ver detalle';
        actions.appendChild(detail);
      }
      if (vm.url) {
        const link = document.createElement('a');
        link.href = vm.url;
        link.textContent = 'Ver charla';
        actions.appendChild(link);
      }
      const close = document.createElement('button');
      close.type = 'button';
      close.textContent = '×';
      close.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); this.close(vm.id); });
      actions.appendChild(close);
      toast.appendChild(actions);
      return toast;
    }
    close(id) {
      const idx = this.visible.findIndex(t => t.id === id);
      if (idx === -1) return;
      const vm = this.visible.splice(idx, 1)[0];
      clearTimeout(vm.timer);
      if (this.reduceMotion) { vm.node.remove(); }
      else {
        vm.node.classList.add('hide');
        requestAnimationFrame(() => vm.node.remove());
      }
      this.metric('dismissed');
      if (this.queue.length > 0) { this.show(this.queue.shift()); }
      this.updateCloseAll();
    }
    closeAll() {
      this.visible.slice().forEach(t => this.close(t.id));
      this.queue = [];
      this.metric('closed_all');
      this.updateCloseAll();
    }
    updateCloseAll() {
      const total = this.visible.length + this.queue.length;
      const hide = total === 0;
      this.closeAllBtn.hidden = hide;
      this.closeAllBtn.classList.toggle('hidden', hide);
    }
    metric(name) {
      if (window.__metrics && window.__metrics.count) { window.__metrics.count('ui.notifications.' + name); }
    }
  }
  const container = document.getElementById('ef-toast-container');
  const manager = container ? new ToastQueueManager(container) : null;
  function updateCloseAllVisibility() { if (manager) manager.updateCloseAll(); }
  window.updateCloseAllVisibility = updateCloseAllVisibility;
  updateCloseAllVisibility();
  const badge = document.getElementById('notif-badge');
  const UNREAD_KEY = 'ef_global_unread_count';
  let unread = 0;

  function renderBadge() {
    if (!badge) return;
    if (unread > 0) {
      badge.textContent = unread > 9 ? '9+' : String(unread);
      badge.classList.remove('badge-hidden');
    } else {
      badge.textContent = '0';
      badge.classList.add('badge-hidden');
    }
  }

  function updateUnreadFromLocal() {
    if (!badge) return;
    try {
      const raw = localStorage.getItem(UNREAD_KEY);
      unread = raw ? parseInt(raw, 10) : 0;
    } catch (_) { unread = 0; }
    renderBadge();
  }
  window.updateUnreadFromLocal = updateUnreadFromLocal;
  if (badge) {
    window.addEventListener('DOMContentLoaded', updateUnreadFromLocal);
    document.addEventListener('ef:notifs:changed', updateUnreadFromLocal);
  }

  window.HomeDirNotifications = {
    accept(dto) {
      if (window.updateUnreadFromLocal) window.updateUnreadFromLocal();
      if (!manager) return;
      const id = dto.id || uid();
      const vm = {
        id: id,
        title: dto.title || dto.type,
        message: dto.message || '',
        urgent: dto.urgent === true,
        url: (dto.talkId && dto.category !== 'break') ? ('/talks/' + dto.talkId) : null,
        centerUrl: dto.id ? ('/notifications/center#' + dto.id) : '/notifications/center'
      };
      manager.enqueue(vm);
    },
    closeAll() { manager && manager.closeAll(); }
  };
  window.__notifyDev__ = (partial = {}) => {
    const demo = Object.assign({ type: 'UPCOMING', title: 'Demo', message: 'Example', talkId: 'demo' }, partial);
    window.HomeDirNotifications.accept(demo);
  };
})();
(function () {
  const storeKey = 'ef_global_lastCursor';
  const inboxKey = 'ef_global_notifs';
  const unreadKey = 'ef_global_unread_count';
  function connect(delay) {
    const retry = delay || 1000;
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(proto + '://' + window.location.host + '/ws/global-notifications');
    ws.onopen = () => {
      const cursor = Number(localStorage.getItem(storeKey)) || 0;
      ws.send(JSON.stringify({ t: 'hello', cursor: cursor, cap: ['toast', 'center'] }));
    };
    ws.onmessage = (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.t === 'notif') {
        if (msg.test) {
          const optin =
            localStorage.getItem('ef_notif_test_optin') === 'true' ||
            window.location.search.includes('test=1');
          if (!optin) return;
        }
        const last = Number(localStorage.getItem(storeKey)) || 0;
        if (msg.createdAt && msg.createdAt > last) {
          localStorage.setItem(storeKey, String(msg.createdAt));
        }
        try {
          const arr = JSON.parse(localStorage.getItem(inboxKey) || '[]');
          arr.unshift(msg);
          localStorage.setItem(inboxKey, JSON.stringify(arr.slice(0, 1000)));
          const unread = parseInt(localStorage.getItem(unreadKey) || '0', 10) + 1;
          localStorage.setItem(unreadKey, String(unread));
        } catch (e) {}
        if (window.HomeDirNotifications && window.HomeDirNotifications.accept) {
          window.HomeDirNotifications.accept(msg);
        }
        if (window.__EF_GLOBAL_NOTIF_ACCEPT__) {
          window.__EF_GLOBAL_NOTIF_ACCEPT__(msg);
        }
        if (window.updateUnreadFromLocal) {
          window.updateUnreadFromLocal();
        }
      }
    };
    ws.onclose = () => {
      const next = Math.min(retry * 2, 30000);
      setTimeout(() => connect(next), retry + Math.random() * 1000);
    };
    ws.onerror = () => ws.close();
  }
  if ('WebSocket' in window) {
    connect(1000);
  } else {
    console.info('WebSocket not supported');
  }
})();

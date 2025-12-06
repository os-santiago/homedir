
var currentUser = null;
var allUsers = [];

// Equipment/Collectibles System
const COLLECTIBLES = [
  { id: 'laptop', icon: '💻', name: 'DEVELOPER LAPTOP', rarity: 'common', description: '+5 Coding Power' },
  { id: 'keyboard', icon: '��️', name: 'MECHANICAL KEYBOARD', rarity: 'rare', description: '+10 Typing Speed' },
  { id: 'coffee', icon: '☕', name: 'ENERGY COFFEE', rarity: 'common', description: '+3 Focus' },
  { id: 'badge', icon: '🏅', name: 'CONTRIBUTOR BADGE', rarity: 'epic', description: '+15 Reputation' },
  { id: 'trophy', icon: '🏆', name: 'CHAMPION TROPHY', rarity: 'legendary', description: '+25 Achievement' },
  { id: 'book', icon: '📚', name: 'CODE MANUAL', rarity: 'rare', description: '+8 Knowledge' },
  { id: 'rocket', icon: '��', name: 'LAUNCH ROCKET', rarity: 'epic', description: '+20 Deployment' },
  { id: 'gem', icon: '💎', name: 'RARE GEM', rarity: 'legendary', description: '+30 Value' },
  { id: 'shield', icon: '🛡️', name: 'SECURITY SHIELD', rarity: 'epic', description: '+18 Protection' },
  { id: 'wand', icon: '🪄', name: 'DEBUG WAND', rarity: 'rare', description: '+12 Bug Fix' },
  { id: 'crown', icon: '👑', name: 'LEADER CROWN', rarity: 'legendary', description: '+35 Leadership' },
  { id: 'glasses', icon: '👓', name: 'CODE GLASSES', rarity: 'common', description: '+4 Vision' }
];

// Function to get random collectibles for a user
function generateEquipment(level, experience) {
  const equipment = [];
  const itemCount = Math.min(6, Math.floor(level / 2) + Math.floor(experience / 200));

  const shuffled = [...COLLECTIBLES].sort(() => Math.random() - 0.5);

  for (let i = 0; i < itemCount; i++) {
    equipment.push(shuffled[i].id);
  }

  return equipment;
}

// Function to update equipment display
function updateEquipmentDisplay(equipment = []) {
  const slots = document.querySelectorAll('.equipment-slot');
  let filledCount = 0;

  slots.forEach((slot, index) => {
    if (equipment[index]) {
      const collectible = COLLECTIBLES.find(c => c.id === equipment[index]);
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

  document.querySelector('.equipment-count').textContent = `${filledCount} / 6 ITEMS`;
}

const defaultConfig = {
  platform_name: "HomeDir",
  tagline: "by OpenSourceSantiago",
  community_title: "Community",
  community_description: "Modern toolbox for community building and opensource technology innovation",
  events_title: "Events",
  events_description: "Scale your teams with collaborative meetups and tech conferences",
  projects_title: "Projects",
  projects_description: "Innovation hub for your opensource technology projects",
  background_start: "#667eea",
  background_end: "#764ba2",
  card_background: "#ffffff",
  text_color: "#ffffff",
  accent_color: "#FFD700"
};

let config = defaultConfig;

// Data SDK Handler
const dataHandler = {
  onDataChanged(data) {
    allUsers = data;

    if (currentUser) {
      const updatedUser = data.find(u => u.user_id === currentUser.user_id);
      if (updatedUser) {
        currentUser = updatedUser;
        updateProfileDisplay();
      }
    }
  }
};

// Initialize Data SDK
async function initDataSdk() {
  const result = await window.dataSdk.init(dataHandler);
  if (!result.isOk) {
    showToast('Failed to initialize data storage');
  }
}

// Element SDK Functions
async function onConfigChange(newConfig) {
  config = newConfig;

  document.getElementById('navPlatformName').textContent = config.platform_name || defaultConfig.platform_name;
  document.getElementById('navTagline').textContent = config.tagline || defaultConfig.tagline;
  document.getElementById('mainTitle').textContent = config.platform_name || defaultConfig.platform_name;
  document.getElementById('mainTagline').textContent = (config.tagline || defaultConfig.tagline) + ' - The community platform to scale your teams and projects';
  document.getElementById('communityTitle').textContent = config.community_title || defaultConfig.community_title;
  document.getElementById('communityDescription').textContent = config.community_description || defaultConfig.community_description;
  document.getElementById('eventsTitle').textContent = config.events_title || defaultConfig.events_title;
  document.getElementById('eventsDescription').textContent = config.events_description || defaultConfig.events_description;
  document.getElementById('projectsTitle').textContent = config.projects_title || defaultConfig.projects_title;
  document.getElementById('projectsDescription').textContent = config.projects_description || defaultConfig.projects_description;

  document.body.style.background = `linear-gradient(135deg, ${config.background_start || defaultConfig.background_start} 0%, ${config.background_end || defaultConfig.background_end} 100%)`;
  document.body.style.color = config.text_color || defaultConfig.text_color;
}

function mapToCapabilities(config) {
  return {
    recolorables: [
      {
        get: () => config.background_start || defaultConfig.background_start,
        set: (value) => {
          config.background_start = value;
          if (window.elementSdk) {
            window.elementSdk.setConfig({ background_start: value });
          }
        }
      },
      {
        get: () => config.background_end || defaultConfig.background_end,
        set: (value) => {
          config.background_end = value;
          if (window.elementSdk) {
            window.elementSdk.setConfig({ background_end: value });
          }
        }
      },
      {
        get: () => config.card_background || defaultConfig.card_background,
        set: (value) => {
          config.card_background = value;
          if (window.elementSdk) {
            window.elementSdk.setConfig({ card_background: value });
          }
        }
      },
      {
        get: () => config.text_color || defaultConfig.text_color,
        set: (value) => {
          config.text_color = value;
          if (window.elementSdk) {
            window.elementSdk.setConfig({ text_color: value });
          }
        }
      },
      {
        get: () => config.accent_color || defaultConfig.accent_color,
        set: (value) => {
          config.accent_color = value;
          if (window.elementSdk) {
            window.elementSdk.setConfig({ accent_color: value });
          }
        }
      }
    ],
    borderables: [],
    fontEditable: undefined,
    fontSizeable: undefined
  };
}

function mapToEditPanelValues(config) {
  return new Map([
    ["platform_name", config.platform_name || defaultConfig.platform_name],
    ["tagline", config.tagline || defaultConfig.tagline],
    ["community_title", config.community_title || defaultConfig.community_title],
    ["community_description", config.community_description || defaultConfig.community_description],
    ["events_title", config.events_title || defaultConfig.events_title],
    ["events_description", config.events_description || defaultConfig.events_description],
    ["projects_title", config.projects_title || defaultConfig.projects_title],
    ["projects_description", config.projects_description || defaultConfig.projects_description]
  ]);
}

// Toast Notification
function showToast(message) {
  const toast = document.getElementById('toast');
  toast.textContent = message;
  toast.classList.add('show');
  setTimeout(() => {
    toast.classList.remove('show');
  }, 3000);
}

// UI Control Functions
function openLoginModal() {
  document.getElementById('loginModal').classList.add('show');
}

function closeLoginModal() {
  document.getElementById('loginModal').classList.remove('show');
}

function updateNavigation() {
  if (currentUser) {
    const emoji = currentUser.display_name.charAt(0);
    document.getElementById('openLoginBtn').style.display = 'none';
    document.getElementById('userMenuNav').classList.add('active');
    document.getElementById('navAvatar').textContent = emoji;
    document.getElementById('navUserName').textContent = currentUser.display_name;
  } else {
    document.getElementById('openLoginBtn').style.display = 'block';
    document.getElementById('userMenuNav').classList.remove('active');
  }
}

function updateCharacterSheet() {
  if (currentUser) {
    // Hide novice warning
    document.getElementById('noviceWarning').style.display = 'none';

    // Update character info
    document.getElementById('characterName').textContent = currentUser.display_name.toUpperCase();
    document.getElementById('characterClass').textContent = 'DEVELOPER';
    document.getElementById('characterLevel').textContent = `LEVEL ${currentUser.level}`;

    // Calculate vitals (HP and SP based on level and activity)
    const maxHP = 100;
    const maxSP = 100;
    const maxXPForLevel = currentUser.level * 100;

    const currentHP = Math.min(100, 10 + (currentUser.level * 10) + currentUser.contributions);
    const currentSP = Math.min(100, 5 + (currentUser.level * 8) + (currentUser.quests_completed * 5));
    const currentXP = currentUser.experience % maxXPForLevel;

    // Update HP
    document.getElementById('hpValue').textContent = `${currentHP} / ${maxHP}`;
    document.getElementById('hpBar').style.width = `${currentHP}%`;

    // Update SP
    document.getElementById('spValue').textContent = `${currentSP} / ${maxSP}`;
    document.getElementById('spBar').style.width = `${currentSP}%`;

    // Update XP
    document.getElementById('xpValue').textContent = `${currentXP} / ${maxXPForLevel}`;
    document.getElementById('xpBar').style.width = `${(currentXP / maxXPForLevel) * 100}%`;

    // Update stats
    document.getElementById('contributionsStat').textContent = currentUser.contributions;
    document.getElementById('questsStat').textContent = currentUser.quests_completed;
    document.getElementById('eventsStat').textContent = currentUser.events_attended;
    document.getElementById('projectsStat').textContent = currentUser.projects_hosted;
    document.getElementById('connectionsStat').textContent = currentUser.connections;
    document.getElementById('experienceStat').textContent = currentUser.experience;

    // Update equipment (generate based on level and experience)
    const equipment = generateEquipment(currentUser.level, currentUser.experience);
    updateEquipmentDisplay(equipment);

    // Show logout button, hide login button
    document.getElementById('loginCharacterBtn').style.display = 'none';
    document.getElementById('logoutCharacterBtn').style.display = 'block';
  } else {
    // Show novice warning
    document.getElementById('noviceWarning').style.display = 'block';

    // Reset to novice state
    document.getElementById('characterName').textContent = 'NOVICE GUEST';
    document.getElementById('characterClass').textContent = 'VISITOR';
    document.getElementById('characterLevel').textContent = 'LEVEL 1';

    // Reset vitals to minimum
    document.getElementById('hpValue').textContent = '10 / 100';
    document.getElementById('hpBar').style.width = '10%';

    document.getElementById('spValue').textContent = '5 / 100';
    document.getElementById('spBar').style.width = '5%';

    document.getElementById('xpValue').textContent = '0 / 100';
    document.getElementById('xpBar').style.width = '0%';

    // Reset all stats to 0
    document.getElementById('contributionsStat').textContent = '0';
    document.getElementById('questsStat').textContent = '0';
    document.getElementById('eventsStat').textContent = '0';
    document.getElementById('projectsStat').textContent = '0';
    document.getElementById('connectionsStat').textContent = '0';
    document.getElementById('experienceStat').textContent = '0';

    // Clear equipment
    updateEquipmentDisplay([]);

    // Show login button, hide logout button
    document.getElementById('loginCharacterBtn').style.display = 'block';
    document.getElementById('logoutCharacterBtn').style.display = 'none';
  }
}

// Login Functions
async function handleLogin(provider) {
  const btn = provider === 'google' ? document.getElementById('googleLogin') : document.getElementById('githubLogin');
  btn.classList.add('loading');
  btn.disabled = true;

  // Simulate login with demo user
  const demoNames = ['Alex Chen', 'Maria Garcia', 'Jamal Johnson', 'Sofia Rodriguez', 'Kai Nakamura'];
  const randomName = demoNames[Math.floor(Math.random() * demoNames.length)];
  const userId = `user_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

  // Generate demo data with some progress
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

  const result = await window.dataSdk.create(newUser);

  if (result.isOk) {
    currentUser = newUser;
    closeLoginModal();
    updateNavigation();
    updateCharacterSheet();
    showToast(`Welcome, ${randomName}! 🎉 Your character has been created!`);
  } else {
    showToast('Login failed. Please try again.');
  }

  btn.classList.remove('loading');
  btn.disabled = false;
}

function handleLogout() {
  currentUser = null;
  updateNavigation();
  updateCharacterSheet();
  showToast('Logged out successfully - Character data saved!');
}

// Tech Campus Functions
const TECH_AVATARS = ['👨����💻', '👩‍💻', '🧑‍💻', '👨‍🎨', '👩‍🎨', '🧑‍🎨', '👨‍🔬', '👩‍🔬', '🧑‍🔬', '🦸‍♂️', '🦸‍♀️', '🧙‍♂️', '🧙‍♀️', '🤖', '👾', '🚀'];

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
  inhabitantsLayer.innerHTML = '';

  // Use all users from the database
  const members = allUsers.length > 0 ? allUsers : generateDemoInhabitants();

  members.forEach((user, index) => {
    const member = document.createElement('div');
    member.className = 'team-member';

    // Random position in the tech campus ground
    const left = 12 + (Math.random() * 76);
    const bottom = 18 + (Math.random() * 35);

    member.style.left = `${left}%`;
    member.style.bottom = `${bottom}%`;
    member.style.animationDelay = `${Math.random() * 4}s`;

    const avatar = user.display_name ? TECH_AVATARS[user.display_name.charCodeAt(0) % TECH_AVATARS.length] : TECH_AVATARS[index % TECH_AVATARS.length];
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
    { name: 'Liam O\'Brien', role: 'Security Expert' }
  ];

  return demoMembers.map((member, index) => ({
    user_id: `demo_${index}`,
    display_name: member.name,
    level: Math.floor(Math.random() * 10) + 1,
    experience: Math.floor(Math.random() * 1200),
    contributions: Math.floor(Math.random() * 80),
    quests_completed: Math.floor(Math.random() * 30),
    projects_hosted: Math.floor(Math.random() * 8)
  }));
}

function updateCommunityStats(inhabitants) {
  const totalMembers = inhabitants.length;
  const totalXP = inhabitants.reduce((sum, user) => sum + (user.experience || 0), 0);
  const totalQuests = inhabitants.reduce((sum, user) => sum + (user.quests_completed || 0), 0);
  const totalProjects = inhabitants.reduce((sum, user) => sum + (user.projects_hosted || 0), 0);

  document.getElementById('totalMembers').textContent = totalMembers;
  document.getElementById('totalXP').textContent = totalXP.toLocaleString();
  document.getElementById('totalQuests').textContent = totalQuests;
  document.getElementById('totalProjects').textContent = totalProjects;
}

function showCommunityView() {
  document.getElementById('publicContent').style.display = 'none';
  document.getElementById('communityView').style.display = 'block';
  generateVillageInhabitants();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function hideCommunityView() {
  document.getElementById('publicContent').style.display = 'block';
  document.getElementById('communityView').style.display = 'none';
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function updateProfileDisplay() {
  // If community view is open, refresh inhabitants
  if (document.getElementById('communityView').style.display === 'block') {
    generateVillageInhabitants();
  }
}

// Event Listeners
document.getElementById('openLoginBtn').addEventListener('click', openLoginModal);
document.getElementById('closeLoginBtn').addEventListener('click', closeLoginModal);
document.getElementById('googleLogin').addEventListener('click', () => handleLogin('google'));
document.getElementById('githubLogin').addEventListener('click', () => handleLogin('github'));
document.getElementById('logoutBtn').addEventListener('click', handleLogout);
document.getElementById('loginCharacterBtn').addEventListener('click', openLoginModal);
document.getElementById('logoutCharacterBtn').addEventListener('click', handleLogout);
document.getElementById('communityCard').addEventListener('click', showCommunityView);
document.getElementById('backButton').addEventListener('click', hideCommunityView);

// Close modal when clicking outside
document.getElementById('loginModal').addEventListener('click', (e) => {
  if (e.target.id === 'loginModal') {
    closeLoginModal();
  }
});

// Initialize SDKs
if (window.elementSdk) {
  window.elementSdk.init({
    defaultConfig,
    onConfigChange,
    mapToCapabilities,
    mapToEditPanelValues
  });
}

if (window.dataSdk) {
  initDataSdk();
}

// Initialize character sheet on page load
updateCharacterSheet();


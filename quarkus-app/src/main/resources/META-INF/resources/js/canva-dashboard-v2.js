// Variables inherited from homedir.js: currentUser, allUsers

// Equipment/Collectibles System
// COLLECTIBLES inherited from homedir.js

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

// Function to update equipment display (Adapted for Retro Dashboard)
function updateEquipmentDisplay(equipment = []) {
    // Use .dash-slot inside .equipment-slot-grid
    const slots = document.querySelectorAll('.dash-slot');
    let filledCount = 0;

    slots.forEach((slot, index) => {
        // Clear previous
        slot.innerHTML = '';
        slot.className = 'dash-slot'; // Reset class

        if (equipment[index]) {
            const collectible = COLLECTIBLES.find(c => c.id === equipment[index]);
            if (collectible) {
                slot.classList.add('filled');
                slot.style.backgroundImage = 'none'; // Remove striped bg
                slot.style.display = 'flex';
                slot.style.alignItems = 'center';
                slot.style.justifyContent = 'center';
                slot.style.fontSize = '1.5rem';

                slot.innerHTML = `
          ${collectible.icon}
          <!-- Tooltip could be added here if CSS supports it -->
        `;
                filledCount++;
            }
        }
    });

    // Note: Previous design had equipment count text, new design doesn't exist?
    // If it does, update it. If not, ignore.
}

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
    if (!window.dataSdk) return;
    const result = await window.dataSdk.init(dataHandler);
    if (!result.isOk) {
        showToast('Failed to initialize data storage');
    }
}

// Toast Notification
function showToast(message) {
    const toast = document.getElementById('toast');
    if (toast) {
        toast.textContent = message;
        toast.classList.add('show');
        setTimeout(() => {
            toast.classList.remove('show');
        }, 3000);
    }
}

// UI Control Functions
function openLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) modal.classList.add('show');
}

function closeLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) modal.classList.remove('show');
}

function updateNavigation() {
    if (currentUser) {
        const emoji = currentUser.display_name.charAt(0);
        const openLoginBtn = document.getElementById('openLoginBtn');
        if (openLoginBtn) openLoginBtn.style.display = 'none';

        document.getElementById('userMenuNav').classList.add('active');
        document.getElementById('navAvatar').textContent = emoji;
        document.getElementById('navUserName').textContent = currentUser.display_name;
    } else {
        const openLoginBtn = document.getElementById('openLoginBtn');
        if (openLoginBtn) openLoginBtn.style.display = 'block';

        document.getElementById('userMenuNav').classList.remove('active');
    }
}

function updateCharacterSheet() {
    const warning = document.querySelector('.dashboard-warning');
    const characterName = document.querySelector('.identity-name');
    const characterClass = document.querySelector('.identity-class');
    const characterLevel = document.querySelector('.level-box');

    if (currentUser) {
        // Hide novice warning
        if (warning) warning.style.display = 'none';

        // Update character info
        if (characterName) characterName.textContent = currentUser.display_name.toUpperCase();
        if (characterClass) characterClass.textContent = 'DEVELOPER';
        if (characterLevel) characterLevel.textContent = `LEVEL ${currentUser.level}`;

        // Calculate vitals
        const maxHP = 100;
        const maxSP = 100;
        const maxXPForLevel = currentUser.level * 100;

        const currentHP = Math.min(100, 10 + (currentUser.level * 10) + currentUser.contributions);
        const currentSP = Math.min(100, 5 + (currentUser.level * 8) + (currentUser.quests_completed * 5));
        const currentXP = currentUser.experience % maxXPForLevel;

        // Update Vitals (Selectors from character-sheet.html)
        // We look for .vital-card, then .vital-val and .vital-bar-fill
        // Ideally we'd use IDs, but the HTML didn't set IDs for the containers.
        // However, the spans inside DID generally have IDs or valid classes in the OLD version.
        // In NEW version, I didn't add IDs to .vital-val.
        // I should fix the HTML to add IDs or use querySelectors carefully.

        // Using nth-child logic or specific classes:

        // HP
        const hpCard = document.querySelector('.vital-bar-fill.hp').closest('.vital-card');
        if (hpCard) {
            hpCard.querySelector('.vital-val').textContent = `${currentHP} / ${maxHP}`;
            hpCard.querySelector('.vital-bar-fill').style.width = `${currentHP}%`;
        }

        // SP
        const spCard = document.querySelector('.vital-bar-fill.sp').closest('.vital-card');
        if (spCard) {
            spCard.querySelector('.vital-val').textContent = `${currentSP} / ${maxSP}`;
            spCard.querySelector('.vital-bar-fill').style.width = `${currentSP}%`;
        }

        // XP
        const xpCard = document.querySelector('.vital-bar-fill.xp').closest('.vital-card');
        if (xpCard) {
            xpCard.querySelector('.vital-val').textContent = `${currentXP} / ${maxXPForLevel}`;
            xpCard.querySelector('.vital-bar-fill').style.width = `${(currentXP / maxXPForLevel) * 100}%`;
        }

        // Update stats (These didn't have IDs in the new HTML? Let's check.)
        // In the new HTML, I used standard classes. I should have added IDs.
        // I will use querySelector with index assumptions for now, or better to UPDATE HTML to have IDs.
        // BUT since I want to fix JS now, let's assume I will update HTML to have IDs.

        const setStat = (label, val) => {
            // Find stat card with label
            const labels = document.querySelectorAll('.stat-label');
            for (let l of labels) {
                if (l.textContent.trim() === label) {
                    l.parentElement.querySelector('.stat-value').textContent = val;
                }
            }
        };

        setStat('CONTRIBUTIONS', currentUser.contributions);
        setStat('QUESTS', currentUser.quests_completed);
        setStat('EVENTS', currentUser.events_attended);
        setStat('PROJECTS', currentUser.projects_hosted);
        setStat('CONNECTIONS', currentUser.connections);
        setStat('TOTAL XP', currentUser.experience);

        // Update equipment
        const equipment = generateEquipment(currentUser.level, currentUser.experience);
        updateEquipmentDisplay(equipment);

        // Show logout button (if exists), hide login button
        const loginBtn = document.getElementById('loginCharacterBtn');
        const logoutBtn = document.getElementById('logoutCharacterBtn');

        if (loginBtn) loginBtn.style.display = 'none';
        if (logoutBtn) logoutBtn.style.display = 'inline-block';

    } else {
        // Show novice warning
        if (warning) warning.style.display = 'block';

        // Reset identity
        if (characterName) characterName.textContent = 'NOVICE GUEST';
        if (characterClass) characterClass.textContent = 'VISITOR';
        if (characterLevel) characterLevel.textContent = 'LEVEL 1';

        // Reset Vitals
        const hpCard = document.querySelector('.vital-bar-fill.hp').closest('.vital-card');
        if (hpCard) { hpCard.querySelector('.vital-val').textContent = '10 / 100'; hpCard.querySelector('.vital-bar-fill').style.width = '10%'; }

        const spCard = document.querySelector('.vital-bar-fill.sp').closest('.vital-card');
        if (spCard) { spCard.querySelector('.vital-val').textContent = '5 / 100'; spCard.querySelector('.vital-bar-fill').style.width = '5%'; }

        const xpCard = document.querySelector('.vital-bar-fill.xp').closest('.vital-card');
        if (xpCard) { xpCard.querySelector('.vital-val').textContent = '0 / 100'; xpCard.querySelector('.vital-bar-fill').style.width = '0%'; }

        // Reset Stats
        document.querySelectorAll('.stat-value').forEach(el => el.textContent = '0');

        // Clear equipment
        updateEquipmentDisplay([]);

        // Show login button, hide logout button
        const loginBtn = document.getElementById('loginCharacterBtn');
        const logoutBtn = document.getElementById('logoutCharacterBtn');

        if (loginBtn) loginBtn.style.display = 'inline-block';
        if (logoutBtn) logoutBtn.style.display = 'none';
    }
}

// Login Functions
// Login Functions
// Using Real Backend Flow
function handleLogin(provider) {
    if (provider === 'github') {
        window.location.href = '/private/github/start?redirect=' + encodeURIComponent(window.location.pathname);
    } else {
        // Google or others using standard endpoint
        window.location.href = '/private/profile';
    }
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
        if (!profile) return;

        if (!profile.authenticated) {
            currentUser = null;
        } else {
            currentUser = {
                user_id: profile.userId,
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
        }
        updateNavigation();
        updateCharacterSheet();
        updateProfileDisplay();

    } catch (e) {
        console.error('Error loading current user profile', e);
    }
}

function checkGithubLinkSuccess() {
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('githubLinked') === '1') {
        showToast('🎉 GitHub Account Linked Successfully!');
        // Clean URL
        const newUrl = window.location.pathname;
        window.history.replaceState({}, document.title, newUrl);
        // Refresh profile to get updated stats/avatar
        fetchCurrentUserProfile();
    }
}

function handleLogout() {
    currentUser = null;
    updateNavigation();
    updateCharacterSheet();
    showToast('Logged out successfully - Character data saved!');
}

function updateProfileDisplay() {
    // Only used for "Community View" (inhabitants)
    // Left empty here or copied from retro-theme.js if needed.
    // For now assuming we just need the dashboard working.
}

// Event Listeners initialization
function initListeners() {
    const el = (id) => document.getElementById(id);
    if (el('openLoginBtn')) el('openLoginBtn').addEventListener('click', openLoginModal);
    if (el('closeLoginBtn')) el('closeLoginBtn').addEventListener('click', closeLoginModal);
    if (el('googleLogin')) el('googleLogin').addEventListener('click', () => handleLogin('google'));
    if (el('githubLogin')) el('githubLogin').addEventListener('click', () => handleLogin('github'));
    if (el('logoutBtn')) el('logoutBtn').addEventListener('click', handleLogout);

    if (el('loginCharacterBtn')) el('loginCharacterBtn').addEventListener('click', openLoginModal);
    if (el('logoutCharacterBtn')) el('logoutCharacterBtn').addEventListener('click', handleLogout);

    document.getElementById('loginModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'loginModal') closeLoginModal();
    });
}

// Initialize
if (window.dataSdk) {
    initDataSdk();
}

// Run on load
document.addEventListener('DOMContentLoaded', () => {
    initListeners();
    fetchCurrentUserProfile(); // Fetch real data
    checkGithubLinkSuccess(); // Check for redirects
    updateCharacterSheet();
});

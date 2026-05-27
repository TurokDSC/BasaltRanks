/* ============================================================
   BasaltRanks Web Editor - JavaScript
   Vanilla JS, no frameworks, no dependencies
   ============================================================ */

// -----------------------------------------------
// 5x7 Pixel Font (matches BadgeRenderer.kt exactly)
// -----------------------------------------------
const FONT = {
    'A': ['01110','10001','10001','11111','10001','10001','10001'],
    'B': ['11110','10001','10001','11110','10001','10001','11110'],
    'C': ['01111','10000','10000','10000','10000','10000','01111'],
    'D': ['11110','10001','10001','10001','10001','10001','11110'],
    'E': ['11111','10000','10000','11110','10000','10000','11111'],
    'F': ['11111','10000','10000','11110','10000','10000','10000'],
    'G': ['01111','10000','10000','10111','10001','10001','01110'],
    'H': ['10001','10001','10001','11111','10001','10001','10001'],
    'I': ['11111','00100','00100','00100','00100','00100','11111'],
    'J': ['00111','00001','00001','00001','00001','10001','01110'],
    'K': ['10001','10010','10100','11000','10100','10010','10001'],
    'L': ['10000','10000','10000','10000','10000','10000','11111'],
    'M': ['10001','11011','10101','10001','10001','10001','10001'],
    'N': ['10001','11001','10101','10011','10001','10001','10001'],
    'O': ['01110','10001','10001','10001','10001','10001','01110'],
    'P': ['11110','10001','10001','11110','10000','10000','10000'],
    'Q': ['01110','10001','10001','10001','10101','10010','01101'],
    'R': ['11110','10001','10001','11110','10100','10010','10001'],
    'S': ['01111','10000','10000','01110','00001','00001','11110'],
    'T': ['11111','00100','00100','00100','00100','00100','00100'],
    'U': ['10001','10001','10001','10001','10001','10001','01110'],
    'V': ['10001','10001','10001','10001','01010','01010','00100'],
    'W': ['10001','10001','10001','10101','10101','11011','10001'],
    'X': ['10001','10001','01010','00100','01010','10001','10001'],
    'Y': ['10001','10001','01010','00100','00100','00100','00100'],
    'Z': ['11111','00001','00010','00100','01000','10000','11111'],
    '0': ['01110','10011','10101','10101','10101','11001','01110'],
    '1': ['00100','01100','00100','00100','00100','00100','11111'],
    '2': ['01110','10001','00001','00110','01000','10000','11111'],
    '3': ['01110','10001','00001','00110','00001','10001','01110'],
    '4': ['00010','00110','01010','10010','11111','00010','00010'],
    '5': ['11111','10000','11110','00001','00001','10001','01110'],
    '6': ['01110','10000','10000','11110','10001','10001','01110'],
    '7': ['11111','00001','00010','00100','01000','01000','01000'],
    '8': ['01110','10001','10001','01110','10001','10001','01110'],
    '9': ['01110','10001','10001','01111','00001','00001','01110'],
    ' ': ['00000','00000','00000','00000','00000','00000','00000'],
    '+': ['00000','00100','00100','11111','00100','00100','00000'],
    '-': ['00000','00000','00000','11111','00000','00000','00000'],
    '!': ['00100','00100','00100','00100','00100','00000','00100'],
    '?': ['01110','10001','00001','00110','00100','00000','00100']
};

const CHAR_WIDTH = 5;
const CHAR_HEIGHT = 7;
const CHAR_SPACING = 1;
const PADDING_X = 2;
const PADDING_Y = 2;
const BORDER_WIDTH = 1;
const SHADOW_OFFSET = 1;
const PREVIEW_SCALE = 4;

// Valid badge styles
const BADGE_STYLES = ['flat', '3d', 'glass', 'metallic', 'neon'];
const ANIMATION_TYPES = ['none', 'rainbow', 'pulse', 'glow', 'gradient_shift'];

// -----------------------------------------------
// State
// -----------------------------------------------
let currentView = 'badges';
let badges = [];
let templates = [];
let editingBadgeId = null; // null = creating new, string = editing existing
let confirmCallback = null;
let currentStyle = 'flat'; // current style selected in editor

// -----------------------------------------------
// Initialization
// -----------------------------------------------
document.addEventListener('DOMContentLoaded', function() {
    const saved = localStorage.getItem('basaltranks_token');
    if (saved) {
        checkConnection(saved).then(ok => {
            if (ok) {
                showApp();
            } else {
                showAuthModal();
            }
        });
    } else {
        showAuthModal();
    }

    // Enter key on auth input
    document.getElementById('authPassword').addEventListener('keydown', function(e) {
        if (e.key === 'Enter') submitAuth();
    });

    // Enable/disable gradient and border inputs
    document.getElementById('enableGradient').addEventListener('change', function() {
        document.getElementById('gradientColor').disabled = !this.checked;
        document.getElementById('gradientColorHex').disabled = !this.checked;
    });

    document.getElementById('enableBorder').addEventListener('change', function() {
        document.getElementById('borderColor').disabled = !this.checked;
        document.getElementById('borderColorHex').disabled = !this.checked;
    });

    // Initialize style selector buttons
    initStyleSelector();
});

// -----------------------------------------------
// Auth
// -----------------------------------------------
function showAuthModal() {
    document.getElementById('authModal').style.display = 'flex';
    document.getElementById('app').style.display = 'none';
    document.getElementById('authPassword').focus();
}

function submitAuth() {
    const pw = document.getElementById('authPassword').value;
    if (!pw) return;

    checkConnection(pw).then(ok => {
        if (ok) {
            localStorage.setItem('basaltranks_token', pw);
            document.getElementById('authError').style.display = 'none';
            showApp();
        } else {
            document.getElementById('authError').textContent = 'Invalid password';
            document.getElementById('authError').style.display = 'block';
        }
    });
}

function logout() {
    localStorage.removeItem('basaltranks_token');
    document.getElementById('authPassword').value = '';
    showAuthModal();
}

async function checkConnection(token) {
    try {
        const resp = await fetch('/api/badges', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        return resp.ok;
    } catch (e) {
        return false;
    }
}

function showApp() {
    document.getElementById('authModal').style.display = 'none';
    document.getElementById('app').style.display = 'block';
    loadBadges();
    loadTemplatesData();
}

// -----------------------------------------------
// API Helper
// -----------------------------------------------
function getToken() {
    return localStorage.getItem('basaltranks_token') || '';
}

async function apiCall(method, path, body) {
    const opts = {
        method: method,
        headers: {
            'Authorization': 'Bearer ' + getToken(),
            'Content-Type': 'application/json'
        }
    };
    if (body) {
        opts.body = JSON.stringify(body);
    }

    const resp = await fetch('/api' + path, opts);

    if (resp.status === 401) {
        showToast('Session expired. Please login again.', 'error');
        logout();
        throw new Error('Unauthorized');
    }

    const text = await resp.text();
    let data = null;
    try { data = JSON.parse(text); } catch (e) { data = text; }

    if (!resp.ok) {
        const msg = data && data.error ? data.error : 'Request failed';
        throw new Error(msg);
    }

    return data;
}

// -----------------------------------------------
// View Switching
// -----------------------------------------------
function switchView(view) {
    currentView = view;

    // Update nav buttons
    document.querySelectorAll('.nav-btn[data-view]').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.view === view);
    });

    // Show/hide views
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    const target = document.getElementById('view-' + view);
    if (target) target.classList.add('active');

    // Load data for the view
    if (view === 'badges') loadBadges();
    if (view === 'templates') renderTemplates();
    if (view === 'players') loadPlayers();
    if (view === 'luckperms') loadLuckPermsView();
}

// -----------------------------------------------
// Badge List
// -----------------------------------------------
async function loadBadges() {
    try {
        badges = await apiCall('GET', '/badges');
        renderBadgeList();
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            showToast('Failed to load badges: ' + e.message, 'error');
        }
    }
}

function renderBadgeList() {
    const grid = document.getElementById('badgeGrid');
    const empty = document.getElementById('badgeEmpty');

    if (!badges || badges.length === 0) {
        grid.innerHTML = '';
        empty.style.display = 'block';
        return;
    }

    empty.style.display = 'none';
    grid.innerHTML = badges.map(badge => {
        const canvasId = 'badge-card-' + badge.id;
        const styleLabel = (badge.style || 'flat').toUpperCase();
        return `
            <div class="badge-card">
                <div class="badge-card-preview">
                    <canvas id="${canvasId}" style="image-rendering:pixelated;"></canvas>
                </div>
                <div class="badge-card-info">
                    <div class="badge-card-name">${escapeHtml(badge.id)}</div>
                    <div class="badge-card-details">
                        <span class="badge-card-text">"${escapeHtml(badge.displayText)}"</span>
                        <span class="color-swatch" style="background:${badge.bgColor};" title="Background"></span>
                        <span class="color-swatch" style="background:${badge.textColor};" title="Text"></span>
                        ${badge.gradientColor ? `<span class="color-swatch" style="background:${badge.gradientColor};" title="Gradient"></span>` : ''}
                        ${badge.borderColor ? `<span class="color-swatch" style="background:${badge.borderColor};" title="Border"></span>` : ''}
                        <span class="badge-card-text">P:${badge.priority}</span>
                        <span class="badge-style-tag">${styleLabel}</span>
                        ${badge.animated ? `<span class="badge-style-tag" style="background:#FF6B00;">${(badge.animationType || 'none').toUpperCase()}</span>` : ''}
                    </div>
                </div>
                <div class="badge-card-actions">
                    <button class="btn btn-sm btn-ghost" onclick="editBadge('${escapeHtml(badge.id)}')">Edit</button>
                    <button class="btn btn-sm btn-ghost" onclick="showAssignModal('${escapeHtml(badge.id)}')">Assign</button>
                    <button class="btn-icon danger" onclick="confirmDeleteBadge('${escapeHtml(badge.id)}')">&#10005;</button>
                </div>
            </div>
        `;
    }).join('');

    // Render canvases after DOM update
    requestAnimationFrame(() => {
        badges.forEach(badge => {
            const canvas = document.getElementById('badge-card-' + badge.id);
            if (canvas) renderBadgeOnCanvas(canvas, badge, 3);
        });
    });
}

// -----------------------------------------------
// Template Browser
// -----------------------------------------------
async function loadTemplatesData() {
    try {
        templates = await apiCall('GET', '/templates');
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            showToast('Failed to load templates: ' + e.message, 'error');
        }
    }
}

function renderTemplates() {
    const container = document.getElementById('templateContainer');

    if (!templates || templates.length === 0) {
        container.innerHTML = '<div class="empty-state"><p>No templates available.</p></div>';
        return;
    }

    // Group by category
    const groups = {};
    templates.forEach(t => {
        if (!groups[t.category]) groups[t.category] = [];
        groups[t.category].push(t);
    });

    let html = '';
    for (const [category, items] of Object.entries(groups)) {
        html += `
            <div class="template-category">
                <h2>${escapeHtml(category)}</h2>
                <div class="template-grid">
                    ${items.map(t => {
                        const canvasId = 'tmpl-' + t.id;
                        const styleLabel = (t.style || 'flat').toUpperCase();
                        return `
                            <div class="template-card" onclick="createFromTemplate('${escapeHtml(t.id)}')">
                                <div class="template-card-preview">
                                    <canvas id="${canvasId}"></canvas>
                                </div>
                                <div class="template-card-name">${escapeHtml(t.id)}</div>
                                <div class="template-card-style">${styleLabel}</div>
                            </div>
                        `;
                    }).join('')}
                </div>
            </div>
        `;
    }

    container.innerHTML = html;

    // Render template previews
    requestAnimationFrame(() => {
        templates.forEach(t => {
            const canvas = document.getElementById('tmpl-' + t.id);
            if (canvas) {
                renderBadgeOnCanvas(canvas, {
                    displayText: t.displayText,
                    bgColor: t.bgColor,
                    textColor: t.textColor,
                    gradientColor: t.gradientColor,
                    borderColor: t.borderColor,
                    borderRadius: t.borderRadius,
                    style: t.style || 'flat'
                }, 3);
            }
        });
    });
}

async function createFromTemplate(templateId) {
    try {
        const badge = await apiCall('POST', '/badges/from-template', { templateId: templateId });
        showToast('Badge "' + badge.id + '" created from template', 'success');
        await loadBadges();
        editBadge(badge.id);
    } catch (e) {
        showToast(e.message, 'error');
    }
}

// -----------------------------------------------
// Style Selector
// -----------------------------------------------
function initStyleSelector() {
    const container = document.getElementById('styleSelector');
    if (!container) return;

    container.innerHTML = BADGE_STYLES.map(style => {
        const label = style === '3d' ? '3D' : style.charAt(0).toUpperCase() + style.slice(1);
        return `<button type="button" class="style-btn${style === 'flat' ? ' active' : ''}" data-style="${style}" onclick="selectStyle('${style}')">${label}</button>`;
    }).join('');
}

function selectStyle(style) {
    currentStyle = style;

    // Update button states
    document.querySelectorAll('.style-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.style === style);
    });

    updatePreview();
}

// -----------------------------------------------
// Badge Editor
// -----------------------------------------------
function openEditor(badge) {
    if (badge) {
        // Editing existing badge
        editingBadgeId = badge.id;
        document.getElementById('editorTitle').textContent = 'Edit Badge: ' + badge.id;
        document.getElementById('badgeId').value = badge.id;
        document.getElementById('badgeId').disabled = true;
        document.getElementById('badgeText').value = badge.displayText;
        document.getElementById('bgColor').value = badge.bgColor;
        document.getElementById('bgColorHex').value = badge.bgColor;
        document.getElementById('textColor').value = badge.textColor;
        document.getElementById('textColorHex').value = badge.textColor;

        const hasGradient = !!badge.gradientColor;
        document.getElementById('enableGradient').checked = hasGradient;
        document.getElementById('gradientColor').disabled = !hasGradient;
        document.getElementById('gradientColorHex').disabled = !hasGradient;
        if (hasGradient) {
            document.getElementById('gradientColor').value = badge.gradientColor;
            document.getElementById('gradientColorHex').value = badge.gradientColor;
        }

        const hasBorder = !!badge.borderColor;
        document.getElementById('enableBorder').checked = hasBorder;
        document.getElementById('borderColor').disabled = !hasBorder;
        document.getElementById('borderColorHex').disabled = !hasBorder;
        if (hasBorder) {
            document.getElementById('borderColor').value = badge.borderColor;
            document.getElementById('borderColorHex').value = badge.borderColor;
        }

        document.getElementById('borderRadius').value = badge.borderRadius;
        document.getElementById('radiusValue').textContent = badge.borderRadius;
        document.getElementById('priority').value = badge.priority;

        // Set style from badge data
        currentStyle = badge.style || 'flat';

        const animType = badge.animationType || 'none';
        document.getElementById('animationType').value = animType;
        document.getElementById('animationSpeed').value = badge.animationSpeed || 10;
        document.getElementById('speedValue').textContent = badge.animationSpeed || 10;
        document.getElementById('animationFrames').value = badge.animationFrames || 6;
        document.getElementById('framesValue').textContent = badge.animationFrames || 6;
        onAnimationTypeChange();
    } else {
        // Creating new badge
        editingBadgeId = null;
        document.getElementById('editorTitle').textContent = 'Create Badge';
        document.getElementById('badgeId').value = '';
        document.getElementById('badgeId').disabled = false;
        document.getElementById('badgeText').value = '';
        document.getElementById('bgColor').value = '#808080';
        document.getElementById('bgColorHex').value = '#808080';
        document.getElementById('textColor').value = '#FFFFFF';
        document.getElementById('textColorHex').value = '#FFFFFF';
        document.getElementById('enableGradient').checked = false;
        document.getElementById('gradientColor').disabled = true;
        document.getElementById('gradientColorHex').disabled = true;
        document.getElementById('gradientColor').value = '#4B0082';
        document.getElementById('gradientColorHex').value = '#4B0082';
        document.getElementById('enableBorder').checked = false;
        document.getElementById('borderColor').disabled = true;
        document.getElementById('borderColorHex').disabled = true;
        document.getElementById('borderColor').value = '#FFD700';
        document.getElementById('borderColorHex').value = '#FFD700';
        document.getElementById('borderRadius').value = 0;
        document.getElementById('radiusValue').textContent = '0';
        document.getElementById('priority').value = 50;

        // Default style
        currentStyle = 'flat';

        document.getElementById('animationType').value = 'none';
        document.getElementById('animationSpeed').value = 10;
        document.getElementById('speedValue').textContent = '10';
        document.getElementById('animationFrames').value = 6;
        document.getElementById('framesValue').textContent = '6';
        onAnimationTypeChange();
    }

    // Switch to editor view
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.getElementById('view-editor').classList.add('active');

    // Deselect nav buttons
    document.querySelectorAll('.nav-btn[data-view]').forEach(btn => btn.classList.remove('active'));

    // Apply style selection (updates buttons, currentStyle, and preview)
    selectStyle(currentStyle);
}

function editBadge(badgeId) {
    const badge = badges.find(b => b.id === badgeId);
    if (badge) openEditor(badge);
}

function closeEditor() {
    switchView('badges');
}

async function saveBadge() {
    const id = document.getElementById('badgeId').value.trim().toLowerCase().replace(/\s+/g, '-');
    const displayText = document.getElementById('badgeText').value.trim();
    const bgColor = document.getElementById('bgColor').value;
    const textColor = document.getElementById('textColor').value;
    const hasGradient = document.getElementById('enableGradient').checked;
    const gradientColor = hasGradient ? document.getElementById('gradientColor').value : null;
    const hasBorder = document.getElementById('enableBorder').checked;
    const borderColor = hasBorder ? document.getElementById('borderColor').value : null;
    const borderRadius = parseInt(document.getElementById('borderRadius').value) || 0;
    const priority = parseInt(document.getElementById('priority').value) || 50;

    if (!id) { showToast('Badge ID is required', 'error'); return; }
    if (!displayText) { showToast('Display text is required', 'error'); return; }

    const animationType = document.getElementById('animationType').value;
    const isAnimated = animationType !== 'none';
    const animationSpeed = parseInt(document.getElementById('animationSpeed').value) || 10;
    const animationFrames = parseInt(document.getElementById('animationFrames').value) || 6;

    try {
        if (editingBadgeId) {
            // Update existing
            const body = {
                displayText: displayText.toUpperCase(),
                bgColor: bgColor,
                textColor: textColor,
                borderRadius: borderRadius,
                priority: priority,
                clearGradient: !hasGradient,
                clearBorder: !hasBorder,
                style: currentStyle,
                animated: isAnimated,
                animationType: animationType,
                animationSpeed: animationSpeed,
                animationFrames: animationFrames
            };
            if (hasGradient) body.gradientColor = gradientColor;
            if (hasBorder) body.borderColor = borderColor;

            await apiCall('PUT', '/badges/' + editingBadgeId, body);
            showToast('Badge "' + editingBadgeId + '" updated', 'success');
        } else {
            // Create new
            const body = {
                id: id,
                displayText: displayText.toUpperCase(),
                bgColor: bgColor,
                textColor: textColor,
                borderRadius: borderRadius,
                priority: priority,
                style: currentStyle,
                animated: isAnimated,
                animationType: animationType,
                animationSpeed: animationSpeed,
                animationFrames: animationFrames
            };
            if (hasGradient) body.gradientColor = gradientColor;
            if (hasBorder) body.borderColor = borderColor;

            await apiCall('POST', '/badges', body);
            showToast('Badge "' + id + '" created', 'success');
        }

        await loadBadges();
        switchView('badges');
    } catch (e) {
        showToast(e.message, 'error');
    }
}

function confirmDeleteBadge(badgeId) {
    showConfirm(
        'Delete Badge',
        'Are you sure you want to delete "' + badgeId + '"? This will also remove all player assignments.',
        async function() {
            try {
                await apiCall('DELETE', '/badges/' + badgeId);
                showToast('Badge "' + badgeId + '" deleted', 'success');
                await loadBadges();
            } catch (e) {
                showToast(e.message, 'error');
            }
        }
    );
}

// -----------------------------------------------
// Player Management
// -----------------------------------------------
async function loadPlayers() {
    try {
        const players = await apiCall('GET', '/players');
        renderPlayerList(players);
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            showToast('Failed to load players: ' + e.message, 'error');
        }
    }
}

function renderPlayerList(players) {
    const list = document.getElementById('playerList');
    const empty = document.getElementById('playerEmpty');

    if (!players || players.length === 0) {
        list.innerHTML = '';
        empty.style.display = 'block';
        return;
    }

    empty.style.display = 'none';

    const badgeOptions = badges.map(b =>
        `<option value="${escapeHtml(b.id)}">${escapeHtml(b.id)} (${escapeHtml(b.displayText)})</option>`
    ).join('');

    list.innerHTML = players.map(player => {
        const prefixInfo = player.badge
            ? `<strong>${escapeHtml(player.badge)}</strong> (${escapeHtml(player.badgeName || '')})`
            : '<em>None</em>';
        const suffixInfo = player.suffixBadge
            ? `<strong>${escapeHtml(player.suffixBadge)}</strong> (${escapeHtml(player.suffixBadgeName || '')})`
            : '<em>None</em>';

        return `
            <div class="player-row">
                <div class="player-avatar">${escapeHtml(player.name.charAt(0).toUpperCase())}</div>
                <div class="player-info">
                    <div class="player-name">${escapeHtml(player.name)}</div>
                    <div class="player-badge-info">
                        Prefix: ${prefixInfo} &nbsp;|&nbsp; Suffix: ${suffixInfo}
                    </div>
                </div>
                <div class="player-actions" style="display:flex;flex-direction:column;gap:6px;">
                    <div style="display:flex;align-items:center;gap:6px;">
                        <select id="assign-prefix-${escapeHtml(player.name)}" style="flex:1;">
                            <option value="">-- Badge --</option>
                            ${badgeOptions}
                        </select>
                        <button class="btn btn-sm btn-success" onclick="assignBadgeSlot('${escapeHtml(player.name)}','prefix')">Prefix</button>
                        ${player.badge ? `<button class="btn btn-sm btn-ghost" onclick="removeBadgeSlot('${escapeHtml(player.name)}','prefix')">&#10005;</button>` : ''}
                    </div>
                    <div style="display:flex;align-items:center;gap:6px;">
                        <select id="assign-suffix-${escapeHtml(player.name)}" style="flex:1;">
                            <option value="">-- Badge --</option>
                            ${badgeOptions}
                        </select>
                        <button class="btn btn-sm btn-success" onclick="assignBadgeSlot('${escapeHtml(player.name)}','suffix')">Suffix</button>
                        ${player.suffixBadge ? `<button class="btn btn-sm btn-ghost" onclick="removeBadgeSlot('${escapeHtml(player.name)}','suffix')">&#10005;</button>` : ''}
                    </div>
                </div>
            </div>
        `;
    }).join('');

    // Pre-select current badges
    players.forEach(player => {
        if (player.badge) {
            const sel = document.getElementById('assign-prefix-' + player.name);
            if (sel) sel.value = player.badge;
        }
        if (player.suffixBadge) {
            const sel = document.getElementById('assign-suffix-' + player.name);
            if (sel) sel.value = player.suffixBadge;
        }
    });
}

async function assignBadgeSlot(playerName, slot) {
    const select = document.getElementById('assign-' + slot + '-' + playerName);
    if (!select || !select.value) {
        showToast('Select a badge first', 'error');
        return;
    }

    try {
        await apiCall('POST', '/assign', { player: playerName, badgeId: select.value, slot: slot });
        showToast(slot.charAt(0).toUpperCase() + slot.slice(1) + ' badge assigned to ' + playerName, 'success');
        await loadPlayers();
    } catch (e) {
        showToast(e.message, 'error');
    }
}

async function removeBadgeSlot(playerName, slot) {
    try {
        await apiCall('POST', '/remove', { player: playerName, slot: slot });
        showToast(slot.charAt(0).toUpperCase() + slot.slice(1) + ' badge removed from ' + playerName, 'success');
        await loadPlayers();
    } catch (e) {
        showToast(e.message, 'error');
    }
}

function showAssignModal(badgeId) {
    switchView('players');
    setTimeout(() => {
        document.querySelectorAll('[id^="assign-prefix-"]').forEach(select => {
            select.value = badgeId;
        });
    }, 500);
}

// -----------------------------------------------
// Regenerate Resource Pack
// -----------------------------------------------
async function regeneratePack() {
    try {
        const result = await apiCall('POST', '/regenerate');
        showToast(result.message || 'Resource pack regenerated', 'success');
    } catch (e) {
        showToast(e.message, 'error');
    }
}

// -----------------------------------------------
// Color Utilities
// -----------------------------------------------
function parseColor(hex) {
    if (!hex || hex.length < 7) return { r: 128, g: 128, b: 128, a: 255 };
    const clean = hex.replace('#', '');
    return {
        r: parseInt(clean.substring(0, 2), 16) || 0,
        g: parseInt(clean.substring(2, 4), 16) || 0,
        b: parseInt(clean.substring(4, 6), 16) || 0,
        a: 255
    };
}

function brightenColor(c, factor) {
    return {
        r: Math.min(255, Math.round(c.r * factor)),
        g: Math.min(255, Math.round(c.g * factor)),
        b: Math.min(255, Math.round(c.b * factor)),
        a: c.a
    };
}

function darkenColor(c, factor) {
    return {
        r: Math.max(0, Math.round(c.r * factor)),
        g: Math.max(0, Math.round(c.g * factor)),
        b: Math.max(0, Math.round(c.b * factor)),
        a: c.a
    };
}

function calculateTextWidth(text) {
    if (!text || text.length === 0) return 0;
    return (text.length * CHAR_WIDTH) + ((text.length - 1) * CHAR_SPACING);
}

// -----------------------------------------------
// Canvas Rendering (client-side badge preview)
// -----------------------------------------------
function renderBadgeOnCanvas(canvas, badge, scale) {
    const text = (badge.displayText || 'BADGE').toUpperCase();
    const style = (badge.style || 'flat').toLowerCase();
    const hasBorder = !!badge.borderColor;

    const textWidth = calculateTextWidth(text);
    const textHeight = CHAR_HEIGHT;

    const contentWidth = textWidth + (PADDING_X * 2);
    const contentHeight = textHeight + (PADDING_Y * 2);

    const totalWidth = hasBorder ? contentWidth + (BORDER_WIDTH * 2) : contentWidth;
    const totalHeight = hasBorder ? contentHeight + (BORDER_WIDTH * 2) : contentHeight;

    canvas.width = totalWidth * scale;
    canvas.height = totalHeight * scale;
    canvas.style.width = (totalWidth * scale) + 'px';
    canvas.style.height = (totalHeight * scale) + 'px';

    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;

    // Create a pixel buffer to match server rendering exactly
    const pixels = new ImageData(totalWidth, totalHeight);
    const data = pixels.data;

    // Helper to set pixel
    function setPixel(x, y, r, g, b, a) {
        if (x < 0 || x >= totalWidth || y < 0 || y >= totalHeight) return;
        const idx = (y * totalWidth + x) * 4;
        if (a < 255) {
            // Alpha blend
            const existR = data[idx];
            const existG = data[idx + 1];
            const existB = data[idx + 2];
            const existA = data[idx + 3];
            const alpha = a / 255;
            data[idx] = Math.round(r * alpha + existR * (1 - alpha));
            data[idx + 1] = Math.round(g * alpha + existG * (1 - alpha));
            data[idx + 2] = Math.round(b * alpha + existB * (1 - alpha));
            data[idx + 3] = Math.max(existA, a);
        } else {
            data[idx] = r;
            data[idx + 1] = g;
            data[idx + 2] = b;
            data[idx + 3] = 255;
        }
    }

    function getPixel(x, y) {
        if (x < 0 || x >= totalWidth || y < 0 || y >= totalHeight) return { r: 0, g: 0, b: 0, a: 0 };
        const idx = (y * totalWidth + x) * 4;
        return { r: data[idx], g: data[idx+1], b: data[idx+2], a: data[idx+3] };
    }

    function clearPixel(x, y) {
        if (x < 0 || x >= totalWidth || y < 0 || y >= totalHeight) return;
        const idx = (y * totalWidth + x) * 4;
        data[idx] = 0;
        data[idx + 1] = 0;
        data[idx + 2] = 0;
        data[idx + 3] = 0;
    }

    function drawPixelText(txt, sx, sy, r, g, b, a) {
        let cursorX = sx;
        for (let i = 0; i < txt.length; i++) {
            const ch = txt[i];
            const glyph = FONT[ch] || FONT['?'];
            if (!glyph) { cursorX += CHAR_WIDTH + CHAR_SPACING; continue; }
            for (let row = 0; row < glyph.length; row++) {
                const rowStr = glyph[row];
                for (let col = 0; col < rowStr.length; col++) {
                    if (rowStr[col] === '1') {
                        setPixel(cursorX + col, sy + row, r, g, b, a);
                    }
                }
            }
            cursorX += CHAR_WIDTH + CHAR_SPACING;
        }
    }

    // Fill area coords
    const fillX = hasBorder ? BORDER_WIDTH : 0;
    const fillY = hasBorder ? BORDER_WIDTH : 0;
    const fillW = contentWidth;
    const fillH = contentHeight;

    const bgColor = parseColor(badge.bgColor);
    const tc = parseColor(badge.textColor);
    const textStartX = fillX + PADDING_X;
    const textStartY = fillY + PADDING_Y;

    // Delegate to style-specific renderer
    switch (style) {
        case '3d':
            render3DCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
                fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text);
            break;
        case 'glass':
            renderGlassCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
                fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text);
            break;
        case 'metallic':
            renderMetallicCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
                fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text);
            break;
        case 'neon':
            renderNeonCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
                fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text);
            break;
        default:
            renderFlatCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
                fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text);
            break;
    }

    // Apply border radius (shared across all styles)
    const radius = badge.borderRadius || 0;
    if (radius > 0) {
        const corners = generateCornerMask(radius);
        // Top-left
        corners.forEach(([dy, dx]) => clearPixel(dx, dy));
        // Top-right
        corners.forEach(([dy, dx]) => clearPixel(totalWidth - 1 - dx, dy));
        // Bottom-left
        corners.forEach(([dy, dx]) => clearPixel(dx, totalHeight - 1 - dy));
        // Bottom-right
        corners.forEach(([dy, dx]) => clearPixel(totalWidth - 1 - dx, totalHeight - 1 - dy));
    }

    // Scale and draw to canvas
    const offscreen = document.createElement('canvas');
    offscreen.width = totalWidth;
    offscreen.height = totalHeight;
    const offCtx = offscreen.getContext('2d');
    offCtx.putImageData(pixels, 0, 0);

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(offscreen, 0, 0, totalWidth, totalHeight, 0, 0, totalWidth * scale, totalHeight * scale);

    return { width: totalWidth, height: totalHeight };
}

// -----------------------------------------------
// FLAT style renderer (default)
// -----------------------------------------------
function renderFlatCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
    fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text) {

    // Draw border
    if (hasBorder) {
        const bc = parseColor(badge.borderColor);
        for (let y = 0; y < totalHeight; y++) {
            for (let x = 0; x < totalWidth; x++) {
                setPixel(x, y, bc.r, bc.g, bc.b, 255);
            }
        }
    }

    // Fill background
    if (badge.gradientColor) {
        const gradEnd = parseColor(badge.gradientColor);
        for (let y = fillY; y < fillY + fillH; y++) {
            for (let x = fillX; x < fillX + fillW; x++) {
                const t = (x - fillX) / Math.max(fillW - 1, 1);
                const r = Math.round(bgColor.r + (gradEnd.r - bgColor.r) * t);
                const g = Math.round(bgColor.g + (gradEnd.g - bgColor.g) * t);
                const b = Math.round(bgColor.b + (gradEnd.b - bgColor.b) * t);
                setPixel(x, y, r, g, b, 255);
            }
        }
    } else {
        for (let y = fillY; y < fillY + fillH; y++) {
            for (let x = fillX; x < fillX + fillW; x++) {
                setPixel(x, y, bgColor.r, bgColor.g, bgColor.b, 255);
            }
        }
    }

    // Shadow + text
    drawPixelText(text, textStartX + SHADOW_OFFSET, textStartY + SHADOW_OFFSET, 0, 0, 0, 80);
    drawPixelText(text, textStartX, textStartY, tc.r, tc.g, tc.b, 255);
}

// -----------------------------------------------
// 3D style renderer — raised button look
// -----------------------------------------------
function render3DCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
    fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text) {

    // Draw border
    if (hasBorder) {
        const bc = parseColor(badge.borderColor);
        for (let y = 0; y < totalHeight; y++) {
            for (let x = 0; x < totalWidth; x++) {
                setPixel(x, y, bc.r, bc.g, bc.b, 255);
            }
        }
    }

    // Fill background (gradient or solid)
    if (badge.gradientColor) {
        const gradEnd = parseColor(badge.gradientColor);
        for (let y = fillY; y < fillY + fillH; y++) {
            for (let x = fillX; x < fillX + fillW; x++) {
                const t = (x - fillX) / Math.max(fillW - 1, 1);
                const r = Math.round(bgColor.r + (gradEnd.r - bgColor.r) * t);
                const g = Math.round(bgColor.g + (gradEnd.g - bgColor.g) * t);
                const b = Math.round(bgColor.b + (gradEnd.b - bgColor.b) * t);
                setPixel(x, y, r, g, b, 255);
            }
        }
    } else {
        for (let y = fillY; y < fillY + fillH; y++) {
            for (let x = fillX; x < fillX + fillW; x++) {
                setPixel(x, y, bgColor.r, bgColor.g, bgColor.b, 255);
            }
        }
    }

    // 3D effect: darker bottom strip (2px)
    const darkBottom = darkenColor(bgColor, 0.55);
    const edgeH = Math.min(2, fillH);
    for (let y = fillY + fillH - edgeH; y < fillY + fillH; y++) {
        for (let x = fillX; x < fillX + fillW; x++) {
            setPixel(x, y, darkBottom.r, darkBottom.g, darkBottom.b, 255);
        }
    }

    // 3D effect: top highlight line (1px)
    const brightTop = brightenColor(bgColor, 1.4);
    for (let x = fillX; x < fillX + fillW; x++) {
        setPixel(x, fillY, brightTop.r, brightTop.g, brightTop.b, 255);
    }

    // Stronger shadow (2px down, darker)
    drawPixelText(text, textStartX + SHADOW_OFFSET, textStartY + 2, 0, 0, 0, 130);
    // Main text
    drawPixelText(text, textStartX, textStartY, tc.r, tc.g, tc.b, 255);
}

// -----------------------------------------------
// GLASS style renderer — frosted glass / iOS button look
// -----------------------------------------------
function renderGlassCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
    fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text) {

    // Subtle light border (white at 20% opacity) or specified border
    if (hasBorder) {
        const bc = parseColor(badge.borderColor);
        for (let y = 0; y < totalHeight; y++) {
            for (let x = 0; x < totalWidth; x++) {
                setPixel(x, y, bc.r, bc.g, bc.b, 255);
            }
        }
    } else {
        for (let y = 0; y < totalHeight; y++) {
            for (let x = 0; x < totalWidth; x++) {
                setPixel(x, y, 255, 255, 255, 50);
            }
        }
    }

    // Glass always gets a border (either specified or subtle white)
    const gFillX = BORDER_WIDTH;
    const gFillY = BORDER_WIDTH;
    const gFillW = hasBorder ? fillW : totalWidth - 2;
    const gFillH = hasBorder ? fillH : totalHeight - 2;
    const gTextX = gFillX + PADDING_X;
    const gTextY = gFillY + PADDING_Y;

    // Fill background
    if (badge.gradientColor) {
        const gradEnd = parseColor(badge.gradientColor);
        for (let y = gFillY; y < gFillY + gFillH; y++) {
            for (let x = gFillX; x < gFillX + gFillW; x++) {
                const t = (x - gFillX) / Math.max(gFillW - 1, 1);
                const r = Math.round(bgColor.r + (gradEnd.r - bgColor.r) * t);
                const g = Math.round(bgColor.g + (gradEnd.g - bgColor.g) * t);
                const b = Math.round(bgColor.b + (gradEnd.b - bgColor.b) * t);
                setPixel(x, y, r, g, b, 255);
            }
        }
    } else {
        for (let y = gFillY; y < gFillY + gFillH; y++) {
            for (let x = gFillX; x < gFillX + gFillW; x++) {
                setPixel(x, y, bgColor.r, bgColor.g, bgColor.b, 255);
            }
        }
    }

    // Glass gloss: white overlay on top 45% of content area
    const glossH = Math.max(1, Math.floor(gFillH * 0.45));
    for (let y = gFillY; y < gFillY + glossH; y++) {
        for (let x = gFillX; x < gFillX + gFillW; x++) {
            setPixel(x, y, 255, 255, 255, 64);
        }
    }

    // Very light shadow
    drawPixelText(text, gTextX + SHADOW_OFFSET, gTextY + SHADOW_OFFSET, 0, 0, 0, 40);
    // Main text
    drawPixelText(text, gTextX, gTextY, tc.r, tc.g, tc.b, 255);
}

// -----------------------------------------------
// METALLIC style renderer — chrome/metal with bevel
// -----------------------------------------------
function renderMetallicCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
    fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text) {

    // Bevel border: top/left bright, bottom/right dark
    if (hasBorder) {
        const borderBase = parseColor(badge.borderColor);
        const borderLight = brightenColor(borderBase, 1.5);
        const borderDark = darkenColor(borderBase, 0.5);

        // Fill all with dark base (bottom/right will remain)
        for (let y = 0; y < totalHeight; y++) {
            for (let x = 0; x < totalWidth; x++) {
                setPixel(x, y, borderDark.r, borderDark.g, borderDark.b, 255);
            }
        }
        // Top edge bright
        for (let x = 0; x < totalWidth; x++) {
            setPixel(x, 0, borderLight.r, borderLight.g, borderLight.b, 255);
        }
        // Left edge bright
        for (let y = 0; y < totalHeight; y++) {
            setPixel(0, y, borderLight.r, borderLight.g, borderLight.b, 255);
        }
    }

    // Metallic gradient: bright at top, dark at bottom
    const brightTop = brightenColor(bgColor, 1.4);
    const darkBottom = darkenColor(bgColor, 0.6);
    for (let y = fillY; y < fillY + fillH; y++) {
        const t = (y - fillY) / Math.max(fillH - 1, 1);
        const r = Math.round(brightTop.r + (darkBottom.r - brightTop.r) * t);
        const g = Math.round(brightTop.g + (darkBottom.g - brightTop.g) * t);
        const b = Math.round(brightTop.b + (darkBottom.b - brightTop.b) * t);
        for (let x = fillX; x < fillX + fillW; x++) {
            setPixel(x, y, r, g, b, 255);
        }
    }

    // Highlight reflection band at ~30% from top
    const highlight = brightenColor(bgColor, 1.8);
    const hlY = fillY + Math.floor(fillH * 0.3);
    if (hlY < fillY + fillH) {
        for (let x = fillX; x < fillX + fillW; x++) {
            setPixel(x, hlY, highlight.r, highlight.g, highlight.b, 180);
        }
    }

    // Text with dark outline: draw text in dark at 4 offsets, then main text on top
    const outline = darkenColor(tc, 0.25);
    drawPixelText(text, textStartX - 1, textStartY, outline.r, outline.g, outline.b, 255);
    drawPixelText(text, textStartX + 1, textStartY, outline.r, outline.g, outline.b, 255);
    drawPixelText(text, textStartX, textStartY - 1, outline.r, outline.g, outline.b, 255);
    drawPixelText(text, textStartX, textStartY + 1, outline.r, outline.g, outline.b, 255);

    // Bright inner fill
    const bright = brightenColor(tc, 1.3);
    drawPixelText(text, textStartX, textStartY, bright.r, bright.g, bright.b, 255);
}

// -----------------------------------------------
// NEON style renderer — glowing neon sign look
// -----------------------------------------------
function renderNeonCanvas(setPixel, clearPixel, drawPixelText, badge, bgColor, tc, hasBorder,
    fillX, fillY, fillW, fillH, totalWidth, totalHeight, textStartX, textStartY, text) {

    // Neon border: glow effect with double border
    if (hasBorder) {
        const borderColor = parseColor(badge.borderColor);
        for (let y = 0; y < totalHeight; y++) {
            for (let x = 0; x < totalWidth; x++) {
                setPixel(x, y, borderColor.r, borderColor.g, borderColor.b, 100);
            }
        }
    }

    // Very dark background (dark version of bgColor, each channel capped at 30)
    const darkBg = darkenColor(bgColor, 0.15);
    const dbR = Math.min(darkBg.r, 30);
    const dbG = Math.min(darkBg.g, 30);
    const dbB = Math.min(darkBg.b, 30);
    for (let y = fillY; y < fillY + fillH; y++) {
        for (let x = fillX; x < fillX + fillW; x++) {
            setPixel(x, y, dbR, dbG, dbB, 255);
        }
    }

    // Pass 1: outer glow — 8 surrounding offsets
    drawPixelText(text, textStartX - 1, textStartY - 1, bgColor.r, bgColor.g, bgColor.b, 60);
    drawPixelText(text, textStartX + 1, textStartY - 1, bgColor.r, bgColor.g, bgColor.b, 60);
    drawPixelText(text, textStartX - 1, textStartY + 1, bgColor.r, bgColor.g, bgColor.b, 60);
    drawPixelText(text, textStartX + 1, textStartY + 1, bgColor.r, bgColor.g, bgColor.b, 60);
    drawPixelText(text, textStartX - 1, textStartY, bgColor.r, bgColor.g, bgColor.b, 60);
    drawPixelText(text, textStartX + 1, textStartY, bgColor.r, bgColor.g, bgColor.b, 60);
    drawPixelText(text, textStartX, textStartY - 1, bgColor.r, bgColor.g, bgColor.b, 60);
    drawPixelText(text, textStartX, textStartY + 1, bgColor.r, bgColor.g, bgColor.b, 60);

    // Pass 2: body glow — brighter glow at center
    drawPixelText(text, textStartX, textStartY, bgColor.r, bgColor.g, bgColor.b, 120);

    // Pass 3: core — bright crisp text
    const brightCore = brightenColor(tc, 1.3);
    drawPixelText(text, textStartX, textStartY, brightCore.r, brightCore.g, brightCore.b, 255);
}

// -----------------------------------------------
// Corner Mask
// -----------------------------------------------
function generateCornerMask(radius) {
    switch (Math.min(Math.max(radius, 0), 3)) {
        case 1: return [[0, 0]];
        case 2: return [[0, 0], [0, 1], [1, 0]];
        case 3: return [[0, 0], [0, 1], [0, 2], [1, 0], [1, 1], [2, 0]];
        default: return [];
    }
}

function onAnimationTypeChange() {
    const type = document.getElementById('animationType').value;
    const isAnimated = type !== 'none';
    document.getElementById('animSpeedGroup').style.display = isAnimated ? '' : 'none';
    document.getElementById('animFramesGroup').style.display = isAnimated ? '' : 'none';
}

function updateSpeedLabel() {
    document.getElementById('speedValue').textContent = document.getElementById('animationSpeed').value;
}

function updateFramesLabel() {
    document.getElementById('framesValue').textContent = document.getElementById('animationFrames').value;
}

// -----------------------------------------------
// Live Preview
// -----------------------------------------------
function updatePreview() {
    const displayText = document.getElementById('badgeText').value || 'BADGE';
    const bgColor = document.getElementById('bgColor').value;
    const textColor = document.getElementById('textColor').value;
    const hasGradient = document.getElementById('enableGradient').checked;
    const gradientColor = hasGradient ? document.getElementById('gradientColor').value : null;
    const hasBorder = document.getElementById('enableBorder').checked;
    const borderColor = hasBorder ? document.getElementById('borderColor').value : null;
    const borderRadius = parseInt(document.getElementById('borderRadius').value) || 0;

    const badge = {
        displayText: displayText.toUpperCase(),
        bgColor: bgColor,
        textColor: textColor,
        gradientColor: gradientColor,
        borderColor: borderColor,
        borderRadius: borderRadius,
        style: currentStyle
    };

    // Main preview
    const canvas = document.getElementById('previewCanvas');
    const dims = renderBadgeOnCanvas(canvas, badge, PREVIEW_SCALE);
    document.getElementById('previewSize').textContent = 'Size: ' + dims.width + 'x' + dims.height + 'px';

    // Style label
    const styleInfo = document.getElementById('previewStyle');
    if (styleInfo) {
        styleInfo.textContent = 'Style: ' + currentStyle.toUpperCase();
    }

    // Chat preview (smaller scale)
    const chatCanvas = document.getElementById('chatBadgeCanvas');
    renderBadgeOnCanvas(chatCanvas, badge, 2);

    // Sync hex inputs
    document.getElementById('bgColorHex').value = bgColor;
    document.getElementById('textColorHex').value = textColor;
    if (hasGradient) {
        document.getElementById('gradientColorHex').value = document.getElementById('gradientColor').value;
    }
    if (hasBorder) {
        document.getElementById('borderColorHex').value = document.getElementById('borderColor').value;
    }
}

function syncColorFromHex(inputName) {
    const hex = document.getElementById(inputName + 'Hex').value;
    if (/^#[0-9a-fA-F]{6}$/.test(hex)) {
        document.getElementById(inputName).value = hex;
        updatePreview();
    }
}

function updateRadiusLabel() {
    document.getElementById('radiusValue').textContent = document.getElementById('borderRadius').value;
}

// -----------------------------------------------
// Toast Notifications
// -----------------------------------------------
function showToast(message, type) {
    type = type || 'info';
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('toast-out');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// -----------------------------------------------
// Confirm Dialog
// -----------------------------------------------
function showConfirm(title, message, callback) {
    document.getElementById('confirmTitle').textContent = title;
    document.getElementById('confirmMessage').textContent = message;
    document.getElementById('confirmModal').style.display = 'flex';
    confirmCallback = callback;
}

function closeConfirm(confirmed) {
    document.getElementById('confirmModal').style.display = 'none';
    if (confirmed && confirmCallback) {
        confirmCallback();
    }
    confirmCallback = null;
}

// -----------------------------------------------
// LuckPerms View
// -----------------------------------------------
let lpStatus = null;
let lpGroups = [];

async function loadLuckPermsView() {
    const content = document.getElementById('lpContent');
    const statusEl = document.getElementById('lpStatus');

    // Show loading state
    content.innerHTML = '<div class="empty-state"><p>Loading LuckPerms data...</p></div>';

    try {
        // Fetch status first
        lpStatus = await apiCall('GET', '/luckperms/status');
    } catch (e) {
        if (e.message === 'Unauthorized') return;
        lpStatus = { available: false, groups: [] };
    }

    // Update status indicator
    if (lpStatus.available) {
        statusEl.className = 'lp-status lp-status-connected';
        statusEl.textContent = 'LuckPerms Connected';
    } else {
        statusEl.className = 'lp-status lp-status-disconnected';
        statusEl.textContent = 'LuckPerms Not Installed';
    }

    if (!lpStatus.available) {
        content.innerHTML = `
            <div class="lp-unavailable">
                <div class="lp-unavailable-icon">&#128279;</div>
                <h2>LuckPerms Not Installed</h2>
                <p>LuckPerms is required for automatic group-based badge assignment.</p>
                <p>Install LuckPerms on your server and restart to enable this feature.</p>
                <a href="https://luckperms.net/" target="_blank" class="btn btn-primary" style="margin-top:16px;">Get LuckPerms</a>
            </div>
        `;
        return;
    }

    // Fetch group mappings
    try {
        lpGroups = await apiCall('GET', '/luckperms/groups');
    } catch (e) {
        if (e.message === 'Unauthorized') return;
        showToast('Failed to load LuckPerms groups: ' + e.message, 'error');
        lpGroups = [];
    }

    // Make sure badges are loaded for the dropdowns
    if (!badges || badges.length === 0) {
        try {
            badges = await apiCall('GET', '/badges');
        } catch (e) {
            // Badges will just be empty in dropdown
        }
    }

    renderLuckPermsView();
}

function renderLuckPermsView() {
    const content = document.getElementById('lpContent');

    if (!lpGroups || lpGroups.length === 0) {
        content.innerHTML = `
            <div class="empty-state">
                <p>No LuckPerms groups found.</p>
                <p>Create groups in LuckPerms first, then assign badges here.</p>
            </div>
        `;
        return;
    }

    const badgeOptions = badges.map(b =>
        `<option value="${escapeHtml(b.id)}">${escapeHtml(b.id)} (${escapeHtml(b.displayText)})</option>`
    ).join('');

    let tableRows = lpGroups.map((entry, index) => {
        const canvasId = 'lp-preview-' + index;
        return `
            <tr class="lp-row">
                <td class="lp-group-cell">
                    <span class="lp-group-name">${escapeHtml(entry.group)}</span>
                </td>
                <td class="lp-badge-cell">
                    <select id="lp-select-${index}" data-group="${escapeHtml(entry.group)}" onchange="updateLpPreview(${index})">
                        <option value="">None</option>
                        ${badgeOptions}
                    </select>
                </td>
                <td class="lp-preview-cell">
                    <div class="lp-preview-wrapper" id="lp-preview-wrap-${index}">
                        <canvas id="${canvasId}" style="image-rendering:pixelated;"></canvas>
                    </div>
                </td>
            </tr>
        `;
    }).join('');

    content.innerHTML = `
        <div class="lp-table-container">
            <table class="lp-table">
                <thead>
                    <tr>
                        <th>Group Name</th>
                        <th>Badge</th>
                        <th>Preview</th>
                    </tr>
                </thead>
                <tbody>
                    ${tableRows}
                </tbody>
            </table>
        </div>
        <div class="lp-actions">
            <button class="btn btn-primary" onclick="saveLuckPermsMappings()">Save Mappings</button>
            <button class="btn btn-ghost" onclick="loadLuckPermsView()">Refresh</button>
        </div>
    `;

    // Set current values and render previews
    requestAnimationFrame(() => {
        lpGroups.forEach((entry, index) => {
            const select = document.getElementById('lp-select-' + index);
            if (select && entry.badge) {
                select.value = entry.badge;
            }
            updateLpPreview(index);
        });
    });
}

function updateLpPreview(index) {
    const select = document.getElementById('lp-select-' + index);
    const canvas = document.getElementById('lp-preview-' + index);
    const wrapper = document.getElementById('lp-preview-wrap-' + index);
    if (!select || !canvas || !wrapper) return;

    const badgeId = select.value;
    if (!badgeId) {
        // No badge selected — clear canvas
        canvas.width = 0;
        canvas.height = 0;
        canvas.style.width = '0px';
        canvas.style.height = '0px';
        wrapper.classList.add('lp-preview-empty');
        return;
    }

    const badge = badges.find(b => b.id === badgeId);
    if (!badge) {
        canvas.width = 0;
        canvas.height = 0;
        wrapper.classList.add('lp-preview-empty');
        return;
    }

    wrapper.classList.remove('lp-preview-empty');
    renderBadgeOnCanvas(canvas, badge, 3);
}

async function saveLuckPermsMappings() {
    const mappings = {};
    const selects = document.querySelectorAll('[id^="lp-select-"]');

    selects.forEach(select => {
        const group = select.dataset.group;
        if (group) {
            mappings[group] = select.value || '';
        }
    });

    try {
        await apiCall('PUT', '/luckperms/groups', { mappings: mappings });
        showToast('LuckPerms mappings saved and players refreshed', 'success');
        // Reload to reflect any changes
        await loadLuckPermsView();
    } catch (e) {
        showToast(e.message, 'error');
    }
}

// -----------------------------------------------
// Utility
// -----------------------------------------------
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

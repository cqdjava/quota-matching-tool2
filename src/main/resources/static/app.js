const API_BASE = '/api/quota';
let currentEditItemId = null;
let currentItemQuotas = [];
let currentBasicEditItemId = null;
let selectedItemIds = new Set();
let selectedQuotaIds = new Set();
let selectedVersionIds = new Set();
let currentVersionId = null; // 当前选中的版本ID
let currentViewingVersionId = null; // 当前查看的版本明细ID
let selectedRowIndex = -1; // 用于跟踪用户选择的行索引

// ==================== 工具函数 ====================

/** Toast 通知 */
function showToast(message, type) {
    type = type || 'info';
    var container = document.getElementById('toastContainer');
    if (!container) { alert(message); return; }
    var toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(function() {
        toast.classList.add('removing');
        setTimeout(function() { toast.remove(); }, 250);
    }, 3000);
}

/** 自定义确认对话框（返回 Promise） */
function showConfirm(message, title) {
    title = title || '操作确认';
    return new Promise(function(resolve) {
        var overlay = document.createElement('div');
        overlay.className = 'confirm-overlay';
        // 图标映射
        var icons = { '删除': '⚠️', '清空': '⚠️', '确定': '💡', '退出': '👋' };
        var icon = '💡';
        for (var k in icons) { if (title.indexOf(k) >= 0) { icon = icons[k]; break; } }

        // 安全构建 DOM：用 createElement + textContent 替代 innerHTML 拼接，防止 XSS
        var dialog = document.createElement('div');
        dialog.className = 'confirm-dialog';
        var iconEl = document.createElement('div');
        iconEl.className = 'confirm-icon';
        iconEl.textContent = icon;
        var titleEl = document.createElement('div');
        titleEl.className = 'confirm-title';
        titleEl.textContent = title;
        var bodyEl = document.createElement('div');
        bodyEl.className = 'confirm-body';
        bodyEl.textContent = message;
        var actions = document.createElement('div');
        actions.className = 'confirm-actions';
        var cancelBtn = document.createElement('button');
        cancelBtn.className = 'btn-secondary confirm-cancel';
        cancelBtn.textContent = '取消';
        cancelBtn.onclick = function() {
            document.body.removeChild(overlay);
            resolve(false);
        };
        var okBtn = document.createElement('button');
        okBtn.className = 'btn-primary confirm-ok';
        okBtn.textContent = '确定';
        okBtn.onclick = function() {
            document.body.removeChild(overlay);
            resolve(true);
        };
        actions.appendChild(cancelBtn);
        actions.appendChild(okBtn);
        dialog.appendChild(iconEl);
        dialog.appendChild(titleEl);
        dialog.appendChild(bodyEl);
        dialog.appendChild(actions);
        overlay.appendChild(dialog);

        overlay.onclick = function(e) {
            if (e.target === overlay) { document.body.removeChild(overlay); resolve(false); }
        };
        document.body.appendChild(overlay);
    });
}

/** 防抖 */
function debounce(fn, delay) {
    var timer = null;
    return function() {
        var context = this, args = arguments;
        clearTimeout(timer);
        timer = setTimeout(function() { fn.apply(context, args); }, delay);
    };
}

/** 按钮加载状态 */
function setLoading(btn, loading) {
    if (!btn) return;
    if (loading) {
        btn._origHtml = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span>' + (btn.textContent || '处理中...');
        btn.classList.add('btn-loading');
    } else {
        btn.disabled = false;
        if (btn._origHtml) btn.innerHTML = btn._origHtml;
        btn.classList.remove('btn-loading');
    }
}

/** 侧边栏 hover 展开/收起（默认收起，hover 展开，移出延迟 300ms 收起） */
(function initSidebarHover() {
    var sidebar = document.getElementById('sidebar');
    if (!sidebar) return;
    var collapseTimer = null;
    var COLLAPSE_DELAY = 300;

    sidebar.addEventListener('mouseenter', function() {
        if (collapseTimer) {
            clearTimeout(collapseTimer);
            collapseTimer = null;
        }
        sidebar.classList.remove('collapsed');
    });

    sidebar.addEventListener('mouseleave', function() {
        collapseTimer = setTimeout(function() {
            sidebar.classList.add('collapsed');
        }, COLLAPSE_DELAY);
    });
})();

// 全局覆盖 alert → toast（自动识别消息类型）
// 注意：失败/错误检查优先于成功，避免"部分成功但部分失败"类消息被判为 success
var _origAlert = window.alert;
window.alert = function(msg) {
    var s = String(msg);
    var type = 'info';
    if (s.indexOf('失败') >= 0 || s.indexOf('错误') >= 0 || s.indexOf('出错') >= 0) {
        type = 'error';
    } else if (s.indexOf('成功') >= 0 || s.indexOf('完成') >= 0) {
        type = 'success';
    } else if (s.indexOf('导入') >= 0 && s.indexOf('条') >= 0) {
        type = 'success';
    } else if (s.indexOf('请') >= 0 || s.indexOf('不能为空') >= 0 || s.indexOf('无效') >= 0 ||
               s.indexOf('先选择') >= 0 || s.indexOf('无权限') >= 0 || s.indexOf('至少') >= 0 ||
               s.indexOf('必须是') >= 0 || s.indexOf('已添加') >= 0 || s.indexOf('已存在') >= 0) {
        type = 'warning';
    }
    showToast(s, type);
};

window.onload = function() {
    try {
        checkLoginStatus();

        // 确保默认显示 AI 助手页面
        switchNav('assistant');
        renderChatMessages(); // 初始化欢迎页+输入框

        // 搜索框防抖初始化
        var searchInput = document.getElementById('searchInput');
        if (searchInput) { searchInput.removeAttribute('onkeyup'); searchInput.addEventListener('input', debounce(filterItems, 200)); }
        var quotaMgmtSearch = document.getElementById('quotaManagementSearchInput');
        if (quotaMgmtSearch) { quotaMgmtSearch.removeAttribute('onkeyup'); quotaMgmtSearch.addEventListener('input', debounce(filterQuotas, 200)); }
        // 模态框中的搜索框保持 onkeyup 属性，在 openEditModal 中动态绑定防抖

        setTimeout(function() {
            // 初始化 Lucide 图标
            if (typeof lucide !== 'undefined') { lucide.createIcons(); }
            loadItems();
            loadVersions();
            loadVersionOptions();
            checkCurrentUserRole();
            loadUsers();
            loadDocumentTemplates();
            loadReplacementTemplates();
            checkAIStatus();
        }, 100);

        console.log('页面加载完成');
    } catch (error) {
        console.error('页面加载错误：', error);
        showToast('页面加载出错：' + error.message, 'error');
    }
};

// 检查登录状态
async function checkLoginStatus() {
    try {
        const response = await fetch('/api/auth/check');
        const result = await response.json();
        if (!result.loggedIn) {
            window.location.href = '/login.html';
            return;
        }
        // 显示用户信息
        const userInfo = document.getElementById('userInfo');
        if (userInfo) {
            userInfo.textContent = '欢迎，' + (result.username || '用户');
        }
    } catch (error) {
        console.error('检查登录状态失败:', error);
        window.location.href = '/login.html';
    }
}

// 退出登录
async function logout() {
    var ok = await showConfirm('确定要退出登录吗？', '退出登录');
    if (!ok) return;

    try {
        const response = await fetch('/api/auth/logout', {
            method: 'POST'
        });
        const result = await response.json();
        if (result.success) {
            window.location.href = '/login.html';
        }
    } catch (error) {
        console.error('退出登录失败:', error);
        showToast('退出登录失败：' + error.message, 'error');
    }
}

// 确保表格容器可以正常滚动（简化版——新版CSS已修复flex布局问题）
function ensureTableScrolling() {
    var containers = document.querySelectorAll('.table-container');
    for (var i = 0; i < containers.length; i++) {
        containers[i].style.overflowY = 'auto';
        containers[i].style.overflowX = 'auto';
    }
}

async function importQuotas() {
    const fileInput = document.getElementById('quotaFile');
    const statusSpan = document.getElementById('quotaStatus');
    
    if (!fileInput.files[0]) {
        statusSpan.textContent = '请选择文件';
        statusSpan.className = 'status-message status-error';
        return;
    }
    
    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    
    statusSpan.textContent = '导入中...';
    statusSpan.className = 'status-message';
    
    try {
        const response = await fetch(API_BASE + '/import-quotas', {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        
        if (result.success) {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-success';
            fileInput.value = '';
        } else {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-error';
        }
    } catch (error) {
        statusSpan.textContent = '导入失败：' + error.message;
        statusSpan.className = 'status-message status-error';
    }
}

async function importItems() {
    const fileInput = document.getElementById('itemFile');
    const statusSpan = document.getElementById('itemStatus');
    
    if (!fileInput.files[0]) {
        statusSpan.textContent = '请选择文件';
        statusSpan.className = 'status-message status-error';
        return;
    }
    
    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    
    statusSpan.textContent = '导入中...';
    statusSpan.className = 'status-message';
    
    try {
        const response = await fetch(API_BASE + '/import-items', {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        
        if (result.success) {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-success';
            fileInput.value = '';
            loadItems();
        } else {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-error';
        }
    } catch (error) {
        statusSpan.textContent = '导入失败：' + error.message;
        statusSpan.className = 'status-message status-error';
    }
}

async function matchQuotas() {
    const statusSpan = document.getElementById('matchStatus');
    const versionSelect = document.getElementById('versionSelect');
    const versionId = versionSelect.value;

    // 检查是否选择了版本
    if (!versionId) {
        statusSpan.textContent = '请先选择定额版本';
        statusSpan.className = 'status-message status-error';
        return;
    }

    statusSpan.textContent = '匹配中...';
    statusSpan.className = 'status-message';

    try {
        const url = API_BASE + '/match?versionId=' + versionId;
        const response = await fetch(url, {
            method: 'POST'
        });

        const result = await response.json();

        if (result.success) {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-success';
            loadItems();
            // AI 复核已由后端自动触发，前端开始轮询进度
            setTimeout(() => {
                aiWasRunning = true; // 假设后端已触发，避免错过状态
                if (aiPollTimer) clearInterval(aiPollTimer);
                aiPollTimer = setInterval(checkAIStatus, 2000);
            }, 300);
        } else {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-error';
        }
    } catch (error) {
        statusSpan.textContent = '匹配失败：' + error.message;
        statusSpan.className = 'status-message status-error';
    }
}

/** 启动 AI 复核（手动触发） */
async function startAiReview() {
    const aiStatus = document.getElementById('aiReviewStatus');
    const aiBtn = document.getElementById('aiReviewBtn');

    if (!aiBtn || aiBtn.disabled) return;

    const versionSelect = document.getElementById('versionSelect');
    const versionId = versionSelect ? versionSelect.value : '';

    setLoading(aiBtn, true);

    try {
        const url = '/api/ai/review?versionId=' + (versionId || '');
        const response = await fetch(url, { method: 'POST' });
        const result = await response.json();

        if (result.success) {
            aiStatus.textContent = '已提交，后台处理中...';
            aiStatus.style.color = 'var(--text-tertiary)';
            // 开始轮询进度
            aiWasRunning = true;
            if (aiPollTimer) clearInterval(aiPollTimer);
            aiPollTimer = setInterval(checkAIStatus, 2000);
            // AI 复核按钮在运行期间由 checkAIStatus 管理状态
        } else {
            aiStatus.textContent = result.message || 'AI复核失败';
            aiStatus.style.color = 'var(--danger)';
            setLoading(aiBtn, false);
        }
    } catch (error) {
        aiStatus.textContent = 'AI复核请求失败';
        aiStatus.style.color = 'var(--danger)';
        setLoading(aiBtn, false);
    }
}

/** 手动停止 AI 复核 */
async function cancelAiReview() {
    var ok = await showConfirm('确定要停止当前的 AI 复核吗？已完成的批次不会回退。', '停止AI复核');
    if (!ok) return;
    try {
        const response = await fetch('/api/ai/cancel', { method: 'POST' });
        const result = await response.json();
        const aiStatus = document.getElementById('aiReviewStatus');
        if (aiStatus) {
            aiStatus.textContent = result.message;
            aiStatus.style.color = 'var(--text-tertiary)';
        }
    } catch (error) {
        console.error('停止AI复核失败：', error);
    }
}

/** 接受 AI 建议 */
async function acceptAiSuggestion(itemId) {
    var ok = await showConfirm('确定要接受AI的建议吗？将更新此清单项的匹配定额。', '接受AI建议');
    if (!ok) return;
    try {
        const response = await fetch('/api/ai/accept/' + itemId, { method: 'POST' });
        const result = await response.json();
        if (result.success) {
            loadItems();
        } else {
            showToast(result.message, 'error');
        }
    } catch (error) {
        showToast('操作失败：' + error.message, 'error');
    }
}

/** 拒绝 AI 建议 */
async function rejectAiSuggestion(itemId) {
    var ok = await showConfirm('确定要拒绝AI的建议吗？将恢复原来的匹配结果。', '拒绝AI建议');
    if (!ok) return;
    try {
        const response = await fetch('/api/ai/reject/' + itemId, { method: 'POST' });
        const result = await response.json();
        if (result.success) {
            loadItems();
        } else {
            showToast(result.message, 'error');
        }
    } catch (error) {
        showToast('操作失败：' + error.message, 'error');
    }
}

/** AI 轮询定时器 */
let aiPollTimer = null;
/** 上次 running 状态（用于检测完成） */
let aiWasRunning = false;

/** 检查 AI 状态并更新按钮 */
async function checkAIStatus() {
    try {
        const response = await fetch('/api/ai/status');
        const result = await response.json();
        const aiBtn = document.getElementById('aiReviewBtn');
        const aiStatus = document.getElementById('aiReviewStatus');
        const progress = result.progress || {};
        const stopBtn = document.getElementById('aiStopBtn');

        if (result.running) {
            aiWasRunning = true;
            if (aiBtn) {
                aiBtn.disabled = true;
                aiBtn.textContent = '复核中...';
                aiBtn.title = 'AI复核正在运行中';
            }
            if (stopBtn) stopBtn.style.display = 'inline-block';
            if (aiStatus) {
                var pct = Number(progress.percent) || 0;
                var currentBatch = Number(progress.currentBatch) || 0;
                var totalBatches = Number(progress.totalBatches) || 0;
                var processedItems = Number(progress.processedItems) || 0;
                var totalItems = Number(progress.totalItems) || 0;
                // 安全构建 DOM：用 createElement + textContent 替代 innerHTML
                var wrapper = document.createElement('span');
                wrapper.className = 'ai-progress-wrapper';
                var bar = document.createElement('span');
                bar.className = 'ai-progress-bar';
                var fill = document.createElement('span');
                fill.className = 'ai-progress-fill';
                fill.style.width = pct + '%';
                bar.appendChild(fill);
                var text = document.createElement('span');
                text.className = 'ai-progress-text';
                text.textContent = pct + '% (批次 ' + currentBatch + '/' + totalBatches + ', ' + processedItems + '/' + totalItems + ' 条)';
                wrapper.appendChild(bar);
                wrapper.appendChild(text);
                aiStatus.textContent = '';
                aiStatus.appendChild(wrapper);
            }
            if (!aiPollTimer) {
                aiPollTimer = setInterval(checkAIStatus, 2000);
            }
        } else {
            if (aiBtn) {
                aiBtn.textContent = 'AI复核';
                if (result.enabled) {
                    aiBtn.disabled = false;
                    aiBtn.title = '对模糊区间和未匹配的清单项进行AI复核';
                } else {
                    aiBtn.disabled = true;
                    aiBtn.title = 'AI复核未配置API Key，请设置DEEPSEEK_API_KEY环境变量';
                }
            }
            if (stopBtn) stopBtn.style.display = 'none';
            if (aiWasRunning) {
                aiWasRunning = false;
                if (aiStatus) {
                    var doneWrapper = document.createElement('span');
                    doneWrapper.className = 'ai-progress-wrapper';
                    var doneBar = document.createElement('span');
                    doneBar.className = 'ai-progress-bar';
                    var doneFill = document.createElement('span');
                    doneFill.className = 'ai-progress-fill';
                    doneFill.style.width = '100%';
                    doneFill.style.background = '#52c41a';
                    doneBar.appendChild(doneFill);
                    var doneText = document.createElement('span');
                    doneText.className = 'ai-progress-text';
                    doneText.style.color = '#52c41a';
                    doneText.textContent = '复核完成，变更 ' + (Number(progress.changedItems) || 0) + ' 条';
                    doneWrapper.appendChild(doneBar);
                    doneWrapper.appendChild(doneText);
                    aiStatus.textContent = '';
                    aiStatus.appendChild(doneWrapper);
                }
                loadItems();
            } else if (aiStatus && result.tokenUsage && result.tokenUsage.totalCalls > 0) {
                aiStatus.textContent = 'Token: ' + (Number(result.tokenUsage.totalTokens) || 0);
                aiStatus.style.color = 'var(--text-tertiary)';
            }
            if (aiPollTimer) {
                clearInterval(aiPollTimer);
                aiPollTimer = null;
            }
        }
    } catch (e) {
        // API 不可用时清除定时器，停止无限轮询
        if (aiPollTimer) {
            clearInterval(aiPollTimer);
            aiPollTimer = null;
        }
        aiWasRunning = false;
        const aiBtn = document.getElementById('aiReviewBtn');
        const stopBtn = document.getElementById('aiStopBtn');
        if (aiBtn) { aiBtn.disabled = true; aiBtn.title = 'AI复核服务不可用'; }
        if (stopBtn) stopBtn.style.display = 'none';
    }
}

async function loadItems() {
    try {
        const response = await fetch(API_BASE + '/items');
        
        // 检查响应状态
        if (!response.ok) {
            console.error('加载清单数据失败，状态码:', response.status);
            // 即使API返回错误，也要确保表格显示空状态而不是空白
            renderItemsTable([]);
            return;
        }
        
        const items = await response.json();
        
        // 验证返回的数据格式
        if (!Array.isArray(items)) {
            console.error('返回的数据格式错误:', items);
            renderItemsTable([]);
            return;
        }
        
        // 对于多定额匹配的项目，加载其关联的定额列表
        const itemsWithQuotas = await Promise.all(items.map(async (item) => {
            if (item && item.matchStatus === 3) {
                try {
                    const quotasResponse = await fetch(API_BASE + `/items/${item.id}/quotas`);
                    if (quotasResponse.ok) {
                        item.quotas = await quotasResponse.json();
                    } else {
                        console.error(`加载项目 ${item.id} 的定额列表失败，状态码:`, quotasResponse.status);
                        item.quotas = [];
                    }
                } catch (error) {
                    console.error(`加载项目 ${item.id} 的定额列表失败：`, error);
                    item.quotas = [];
                }
            }
            return item;
        }));
        
        renderItemsTable(itemsWithQuotas);
        
        // 初始化可编辑单元格
        setTimeout(() => {
            initEditableCells();
        }, 100);
    } catch (error) {
        console.error('加载数据失败：', error);
        // 发生错误时也应确保显示空状态
        renderItemsTable([]);
    }
}

function renderItemsTable(items) {
    const tbody = document.getElementById('itemsTableBody');
    
    // 根据经验教训，在渲染前显式设置tbody的显示属性
    if (tbody) {
        tbody.style.display = '';
        tbody.style.visibility = 'visible';
    }
    
    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="15" class="empty-message">暂无数据，请先导入项目清单</td></tr>';
        selectedItemIds.clear();
        updateItemBatchActions();
        updateTotalAmount(0);
        return;
    }
    
    // 计算总金额
    let totalAmount = 0;
    
    tbody.innerHTML = items.map((item, index) => {
        const statusClass = item.matchStatus === 1 ? 'status-matched' :
                           item.matchStatus === 2 ? 'status-manual' :
                           item.matchStatus === 3 ? 'status-multi' :
                           item.matchStatus === 4 ? 'status-ai-suggest' : 'status-unmatched';
        const statusText = item.matchStatus === 1 ? '已匹配' :
                          item.matchStatus === 2 ? '手动修改' :
                          item.matchStatus === 3 ? '多定额匹配' :
                          item.matchStatus === 4 ? 'AI建议复核' : '未匹配';
        
        // 如果是多定额匹配，显示所有定额的详细信息
        let quotaDisplay = '';
        let quotaNameDisplay = '';
        let quotaFeatureDisplay = '';
        
        if (item.matchStatus === 3 && item.quotas && item.quotas.length > 0) {
            // 分别显示每个定额的编码、名称、特征值
            quotaDisplay = item.quotas.map(q => q.quotaCode || '无').join('<br>');
            quotaNameDisplay = item.quotas.map(q => q.quotaName || '无').join('<br>');
            quotaFeatureDisplay = item.quotas.map(q => q.quotaFeatureValue || '无').join('<br>');
        } else {
            quotaDisplay = item.matchedQuotaCode || '';
            quotaNameDisplay = item.matchedQuotaName || '';
            quotaFeatureDisplay = item.matchedQuotaFeatureValue || '';
        }
        
        // 累加总金额（只计算有匹配的项目）
        if (item.totalPrice != null) {
            const price = parseFloat(item.totalPrice) || 0;
            totalAmount += price;
        }
        
        const isSelected = selectedItemIds.has(item.id);
        const isAiSuggest = item.matchStatus === 4;
        const rowClass = isAiSuggest ? 'ai-suggest-row' : (selectedRowIndex === index ? 'selected-row' : '');

        // AI 建议信息
        let aiSuggestHtml = '';
        if (isAiSuggest && item.aiSuggestQuotaName) {
            const confidencePercent = item.aiSuggestConfidence ? Math.round(item.aiSuggestConfidence * 100) : 0;
            const confClass = confidencePercent >= 80 ? 'conf-high' : confidencePercent >= 60 ? 'conf-medium' : 'conf-low';
            aiSuggestHtml = `
                <div class="ai-suggest-info">
                    <span class="ai-label">🤖 AI建议：</span>
                    <span>${item.aiSuggestQuotaCode || ''} ${item.aiSuggestQuotaName || ''}</span>
                    <span class="confidence-badge ${confClass}">${confidencePercent}%</span>
                    ${item.aiSuggestReasoning ? `<span class="ai-reasoning" title="${item.aiSuggestReasoning}">${item.aiSuggestReasoning}</span>` : ''}
                </div>`;
        }

        return `
            <tr data-item-id="${item.id}" onclick="selectTableRow(this, ${index})" class="${rowClass}">
                <td style="text-align: center;">
                    <input type="checkbox" ${isSelected ? 'checked' : ''}
                           onchange="toggleItemSelection(${item.id}, this.checked)">
                </td>
                <td style="text-align: center; font-weight: bold;">${index + 1}</td>
                <td class="editable-cell" data-field="itemCode" data-item-id="${item.id}" title="双击编辑">${item.itemCode || ''}</td>
                <td class="editable-cell" data-field="itemName" data-item-id="${item.id}" title="双击编辑">${item.itemName || ''}</td>
                <td class="editable-cell" data-field="featureValue" data-item-id="${item.id}" title="双击编辑">${item.featureValue || ''}</td>
                <td class="editable-cell" data-field="unit" data-item-id="${item.id}" title="双击编辑">${item.unit || ''}</td>
                <td class="editable-cell" data-field="quantity" data-item-id="${item.id}" data-type="number" title="双击编辑">${item.quantity || 0}</td>
                <td class="editable-cell" data-field="remark" data-item-id="${item.id}" title="双击编辑">${item.remark || ''}</td>
                <td style="vertical-align: top;">${quotaDisplay}${aiSuggestHtml}</td>
                <td style="vertical-align: top;">${quotaNameDisplay}</td>
                <td style="vertical-align: top;">${quotaFeatureDisplay}</td>
                <td>${item.matchedUnitPrice || 0}</td>
                <td>${item.totalPrice || 0}</td>
                <td><span class="status-badge ${statusClass}">${statusText}</span></td>
                <td>
                    ${isAiSuggest ? `
                    <button class="action-btn accept-btn" onclick="event.stopPropagation(); acceptAiSuggestion(${item.id})" title="接受AI建议">✓接受</button>
                    <button class="action-btn reject-btn" onclick="event.stopPropagation(); rejectAiSuggestion(${item.id})" title="拒绝AI建议">✗拒绝</button>
                    ` : ''}
                    <button class="action-btn" onclick="openEditModal(${item.id})">
                        匹配定额
                    </button>
                    <button class="action-btn" onclick="openItemEditModal(${item.id})" style="background: linear-gradient(135deg, #4caf50 0%, #388e3c 100%);">编辑</button>
                </td>
            </tr>
        `;
    }).join('');
    
    // 更新总金额显示
    updateTotalAmount(totalAmount);
    
    // 初始化列宽调整功能、批量操作和滚动
    setTimeout(() => {
        initResizableColumns();
        updateItemBatchActions();
        ensureTableScrolling();
        
        // 根据经验教训，强制重排确保表格正确渲染
        if (tbody) {
            tbody.offsetHeight; // 触发重排
            tbody.style.transform = 'translateZ(0)'; // 启用硬件加速
            setTimeout(() => {
                tbody.style.transform = ''; // 移除临时样式
            }, 10);
        }
    }, 100);
}

// 更新总金额显示
function updateTotalAmount(total) {
    const totalAmountElement = document.getElementById('totalAmount');
    if (totalAmountElement) {
        totalAmountElement.textContent = total.toFixed(2);
    }
}

// 增加新行到表格（恢复为原始逻辑：在末尾增加一行）
async function addNewRowToTable() {
    try {
        // 创建一个新的空项目清单
        const newItem = {
            itemCode: '',
            itemName: '',
            featureValue: '',
            unit: '',
            quantity: 0,
            remark: ''
        };
        
        // 如果用户选择了行，使用插入API
        if (selectedRowIndex >= 0) {
            const requestData = {
                insertAfterIndex: selectedRowIndex,
                item: newItem
            };
            console.log('发送插入请求:', requestData);
            console.log('选中行索引:', selectedRowIndex);
            console.log('selectedRowIndex类型:', typeof selectedRowIndex);
            console.log('selectedRowIndex值:', selectedRowIndex);
            
            try {
                const response = await fetch(API_BASE + '/items/insert', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(requestData)
                });
                
                console.log('响应状态:', response.status);
                console.log('响应是否成功:', response.ok);
                
                if (!response.ok) {
                    const errorText = await response.text();
                    console.error('响应错误内容:', errorText);
                    throw new Error(`HTTP ${response.status}: ${errorText}`);
                }
                
                const result = await response.json();
                console.log('响应结果:', result);
                
                if (result.success) {
                    // 重新加载项目列表（现在按排序字段排列）
                    await loadItems();
                    
                    // 重置选中行索引
                    selectedRowIndex = -1;
                    
                    console.log('已在指定行下方成功插入新行');
                } else {
                    console.error('插入失败:', result.message);
                    alert('增加行失败：' + result.message);
                }
            } catch (error) {
                console.error('网络请求错误:', error);
                alert('增加行失败：' + error.message);
            }
        } else {
            // 如果没有选择行，则在末尾添加
            const response = await fetch(API_BASE + '/items', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(newItem)
            });
            
            const result = await response.json();
            
            if (result.success) {
                // 刷新列表
                await loadItems();
            } else {
                alert('增加行失败：' + result.message);
            }
        }
    } catch (error) {
        alert('增加行失败：' + error.message);
    }
}

// 选择表格行
function selectTableRow(rowElement, rowIndex) {
    // 清除之前的选择样式
    const allRows = document.querySelectorAll('#itemsTableBody tr');
    allRows.forEach(row => {
        row.classList.remove('selected-row');
    });
    
    // 添加选择样式到当前行
    rowElement.classList.add('selected-row');
    
    // 更新选中行索引
    selectedRowIndex = rowIndex;
    
    console.log('选中行:', rowIndex);
}

function filterItems() {
    const searchInput = document.getElementById('searchInput');
    const keyword = searchInput.value.toLowerCase();
    const rows = document.querySelectorAll('#itemsTableBody tr');
    
    rows.forEach(row => {
        const text = row.textContent.toLowerCase();
        row.style.display = text.includes(keyword) ? '' : 'none';
    });
}

// 双击编辑功能
function initEditableCells() {
    const editableCells = document.querySelectorAll('.editable-cell');
    editableCells.forEach(cell => {
        cell.addEventListener('dblclick', function() {
            startEditCell(this);
        });
    });
}

function startEditCell(cell) {
    const originalValue = cell.textContent.trim();
    const field = cell.getAttribute('data-field');
    const itemId = cell.getAttribute('data-item-id');
    const isNumber = cell.getAttribute('data-type') === 'number';
    
    // 创建输入框
    const input = document.createElement('input');
    input.type = isNumber ? 'number' : 'text';
    input.value = originalValue;
    input.className = 'cell-input';
    input.style.width = '100%';
    input.style.padding = '4px';
    input.style.border = '2px solid #2196F3';
    input.style.borderRadius = '3px';
    
    // 保存原始值
    const originalText = cell.textContent;
    
    // 替换单元格内容
    cell.textContent = '';
    cell.appendChild(input);
    input.focus();
    input.select();
    
    // 保存函数
    const saveEdit = async () => {
        const newValue = input.value.trim();
        
        // 如果值没有变化，直接恢复
        if (newValue === originalValue) {
            cell.textContent = originalText;
            return;
        }
        
        // 验证必填字段
        if (field === 'itemName' && !newValue) {
            alert('清单名称不能为空');
            input.focus();
            return;
        }
        
        // 如果是数量，验证是否为有效数字
        if (isNumber && newValue && isNaN(parseFloat(newValue))) {
            alert('请输入有效的数字');
            input.focus();
            return;
        }
        
        // 显示保存中
        cell.textContent = '保存中...';
        cell.style.color = '#999';
        
        try {
            // 构建更新数据
            const updateData = {};
            updateData[field] = isNumber && newValue ? parseFloat(newValue) : newValue;
            
            // 调用更新接口
            const response = await fetch(API_BASE + `/items/${itemId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(updateData)
            });
            
            const result = await response.json();
            
            if (result.success) {
                // 更新成功，刷新列表
                await loadItems();
            } else {
                alert('保存失败：' + result.message);
                cell.textContent = originalText;
            }
        } catch (error) {
            alert('保存失败：' + error.message);
            cell.textContent = originalText;
        } finally {
            cell.style.color = '';
        }
    };
    
    // 失去焦点时保存
    input.addEventListener('blur', saveEdit);
    
    // 按回车保存
    input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            input.blur();
        } else if (e.key === 'Escape') {
            e.preventDefault();
            cell.textContent = originalText;
        }
    });
}

async function openEditModal(itemId) {
    currentEditItemId = itemId;

    // 从当前表格行中获取清单名称，避免在 onclick 中直接传入过长字符串导致解析失败
    let itemName = '';
    const row = document.querySelector(`#itemsTableBody tr[data-item-id="${itemId}"]`);
    if (row) {
        const nameCell = row.querySelector('.editable-cell[data-field="itemName"]');
        if (nameCell) {
            itemName = nameCell.textContent.trim();
        }
    }

    document.getElementById('currentItemName').textContent = itemName;
    document.getElementById('editModal').style.display = 'block';
    document.getElementById('quotaList').innerHTML = '<p>请输入关键词搜索企业定额</p>';
    document.getElementById('quotaSearchInput').value = '';

    // 模态框搜索防抖（替换 HTML 中的 onkeyup 属性）
    var quotaSearch = document.getElementById('quotaSearchInput');
    if (quotaSearch) {
        quotaSearch.removeAttribute('onkeyup');
        quotaSearch.oninput = debounce(searchQuotas, 300);
    }

    document.getElementById('manualPrice').value = '';

    // 同步版本选择：使用匹配界面的版本下拉框值
    const versionSelect = document.getElementById('versionSelect');
    if (versionSelect && versionSelect.value) {
        currentVersionId = versionSelect.value;
    } else {
        currentVersionId = null;
    }

    // 加载已添加的定额
    await loadItemQuotas(itemId);
}

function closeEditModal() {
    document.getElementById('editModal').style.display = 'none';
    currentEditItemId = null;
    currentItemQuotas = [];
}

// 确定按钮，关闭编辑匹配定额模态框
function confirmEditModal() {
    closeEditModal();
    // 刷新列表以显示最新数据
    loadItems();
}

function openItemEditModal(itemId) {
    currentBasicEditItemId = itemId;
    const modal = document.getElementById('itemEditModal');
    const title = document.getElementById('itemEditModalTitle');
    const codeInput = document.getElementById('editItemCode');
    const nameInput = document.getElementById('editItemName');
    const featureInput = document.getElementById('editFeatureValue');
    const unitInput = document.getElementById('editUnit');
    const quantityInput = document.getElementById('editQuantity');
    
    if (itemId) {
        // 编辑模式，从当前表格数据中读取
        const itemsTableRows = document.querySelectorAll('#itemsTableBody tr');
        for (const row of itemsTableRows) {
            const editBtn = row.querySelector('button.action-btn');
            if (!editBtn) continue;
        }
        // 为简单起见，重新从接口获取单条数据（当前接口返回全部，这里直接复用已有数据列表会更复杂）
        // 用户体验上问题不大，因为我们编辑后会整体刷新列表
        // 在这里仅根据 itemId 设置标题和清空输入，具体值由用户重新输入或后续扩展单项查询接口
        title.textContent = '编辑清单';
    } else {
        title.textContent = '新增清单';
    }
    
    // 目前简化为每次打开都清空输入，由用户输入完整信息
    codeInput.value = '';
    nameInput.value = '';
    featureInput.value = '';
    unitInput.value = '';
    quantityInput.value = '';
    
    modal.style.display = 'block';
}

function closeItemEditModal() {
    document.getElementById('itemEditModal').style.display = 'none';
    currentBasicEditItemId = null;
}

// 切换清单选择
function toggleItemSelection(itemId, checked) {
    if (checked) {
        selectedItemIds.add(itemId);
    } else {
        selectedItemIds.delete(itemId);
    }
    updateItemBatchActions();
}

// 全选/取消全选清单
function toggleSelectAllItems() {
    const selectAll = document.getElementById('selectAllItems');
    if (!selectAll) return;
    
    const checked = selectAll.checked;
    const checkboxes = document.querySelectorAll('#itemsTableBody input[type="checkbox"]');
    
    checkboxes.forEach(cb => {
        cb.checked = checked;
        const match = cb.getAttribute('onchange').match(/\d+/);
        if (match) {
            const itemId = parseInt(match[0]);
            if (checked) {
                selectedItemIds.add(itemId);
            } else {
                selectedItemIds.delete(itemId);
            }
        }
    });
    
    updateItemBatchActions();
}

// 更新清单批量操作显示
function updateItemBatchActions() {
    const toolbarBtn = document.getElementById('toolbarBatchDeleteItemsBtn');
    if (toolbarBtn) {
        toolbarBtn.style.display = selectedItemIds.size > 0 ? 'inline-block' : 'none';
    }
}

// 批量删除清单
async function batchDeleteItems() {
    if (selectedItemIds.size === 0) {
        alert('请先选择要删除的清单');
        return;
    }
    
    if (!confirm(`确定要删除选中的 ${selectedItemIds.size} 条清单吗？`)) {
        return;
    }
    
    try {
        // 逐个删除选中的清单
        const deletePromises = Array.from(selectedItemIds).map(async (itemId) => {
            try {
                const response = await fetch(API_BASE + `/items/${itemId}`, { 
                    method: 'DELETE' 
                });
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                return await response.json();
            } catch (error) {
                console.error(`删除清单 ${itemId} 失败:`, error);
                return { success: false, message: error.message };
            }
        });
        
        const results = await Promise.all(deletePromises);
        const successCount = results.filter(r => r && r.success).length;
        const failCount = results.length - successCount;
        
        if (successCount > 0) {
            let message = `成功删除 ${successCount} 条清单`;
            if (failCount > 0) {
                message += `，${failCount} 条删除失败`;
            }
            alert(message);
            selectedItemIds.clear();
            updateItemBatchActions();
            loadItems();
        } else {
            const errorMessages = results
                .filter(r => r && !r.success)
                .map(r => r.message || '未知错误')
                .join('; ');
            alert('批量删除失败：' + (errorMessages || '请检查网络连接'));
        }
    } catch (error) {
        console.error('批量删除异常:', error);
        alert('批量删除失败：' + error.message);
    }
}

// 删除清单
async function deleteItem(itemId) {
    if (!confirm('确定要删除这条清单吗？')) {
        return;
    }
    
    try {
        const response = await fetch(API_BASE + `/items/${itemId}`, {
            method: 'DELETE'
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('删除成功！');
            loadItems();
        } else {
            alert('删除失败：' + result.message);
        }
    } catch (error) {
        alert('删除失败：' + error.message);
    }
}

async function saveItem() {
    const code = document.getElementById('editItemCode').value.trim();
    const name = document.getElementById('editItemName').value.trim();
    const feature = document.getElementById('editFeatureValue').value.trim();
    const unit = document.getElementById('editUnit').value.trim();
    const quantityStr = document.getElementById('editQuantity').value.trim();
    
    if (!name) {
        alert('清单名称不能为空');
        return;
    }
    
    let quantity = null;
    if (quantityStr) {
        const q = parseFloat(quantityStr);
        if (isNaN(q)) {
            alert('工程量必须是数字');
            return;
        }
        quantity = q;
    }
    
    const payload = {
        itemCode: code || null,
        itemName: name,
        featureValue: feature || null,
        unit: unit || null,
        quantity: quantity
    };
    
    try {
        let response;
        if (currentBasicEditItemId) {
            // 更新
            response = await fetch(API_BASE + `/items/${currentBasicEditItemId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        } else {
            // 新增
            response = await fetch(API_BASE + '/items', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
        }
        
        const result = await response.json();
        if (result.success) {
            alert(result.message || '保存成功');
            closeItemEditModal();
            loadItems();
        } else {
            alert(result.message || '保存失败');
        }
    } catch (error) {
        alert('保存失败：' + error.message);
    }
}

async function searchQuotas() {
    const keywordInput = document.getElementById('quotaSearchInput');
    if (!keywordInput) return;
    
    const keyword = keywordInput.value.trim();
    const quotaList = document.getElementById('quotaList');
    if (!quotaList) return;
    
    if (!keyword) {
        quotaList.innerHTML = '<p>请输入关键词搜索企业定额</p>';
        return;
    }
    
    quotaList.innerHTML = '<p>搜索中...</p>';
    
    try {
        // 使用匹配界面的版本下拉框值，与自动匹配保持一致
        const versionSelect = document.getElementById('versionSelect');
        const versionId = versionSelect ? versionSelect.value : null;
        
        let url = API_BASE + '/quotas/search?keyword=' + encodeURIComponent(keyword);
        if (versionId) {
            url += '&versionId=' + versionId;
        }
        const response = await fetch(url);
        const quotas = await response.json();
        
        if (quotas.length === 0) {
            quotaList.innerHTML = '<p>未找到匹配的企业定额</p>';
            return;
        }
        
        quotaList.innerHTML = quotas.map(quota => {
            const isAdded = currentItemQuotas.some(q => q.quotaId === quota.id);
            return `
            <div class="quota-item" onclick="selectQuota(${quota.id})" style="position: relative; ${isAdded ? 'opacity: 0.6; background: #e0e0e0;' : ''}">
                ${isAdded ? '<span style="position: absolute; top: 5px; right: 5px; background: #4CAF50; color: white; padding: 2px 8px; border-radius: 3px; font-size: 12px;">已添加</span>' : ''}
                <h4>${quota.quotaCode} - ${quota.quotaName}</h4>
                <p>特征值：${quota.featureValue || '无'}</p>
                <p>单价：${quota.unitPrice || 0} 元/${quota.unit || ''}</p>
            </div>
        `;
        }).join('');
    } catch (error) {
        quotaList.innerHTML = '<p>搜索失败：' + error.message + '</p>';
    }
}

async function selectQuota(quotaId) {
    if (!currentEditItemId) return;
    
    // 检查是否已添加
    const exists = currentItemQuotas.some(q => q.quotaId === quotaId);
    if (exists) {
        alert('该定额已添加！');
        return;
    }
    
    try {
        const response = await fetch(
            API_BASE + `/items/${currentEditItemId}/quotas/${quotaId}`,
            { method: 'POST' }
        );
        
        const result = await response.json();
        
        if (result.success) {
            // 重新加载已添加的定额列表
            await loadItemQuotas(currentEditItemId);
            loadItems();
        } else {
            alert('添加失败：' + result.message);
        }
    } catch (error) {
        alert('添加失败：' + error.message);
    }
}

async function loadItemQuotas(itemId) {
    try {
        const response = await fetch(API_BASE + `/items/${itemId}/quotas`);
        currentItemQuotas = await response.json();
        renderAddedQuotas();
    } catch (error) {
        console.error('加载定额列表失败：', error);
        currentItemQuotas = [];
        renderAddedQuotas();
    }
}

function renderAddedQuotas() {
    const container = document.getElementById('addedQuotasList');
    
    if (currentItemQuotas.length === 0) {
        container.innerHTML = '<p style="color: #999;">暂无已添加的定额</p>';
        return;
    }
    
    // 计算总价
    const totalPrice = currentItemQuotas.reduce((sum, q) => {
        return sum + (q.unitPrice ? parseFloat(q.unitPrice) : 0);
    }, 0);
    
    container.innerHTML = currentItemQuotas.map((quota, index) => `
        <div class="added-quota-item" style="border: 1px solid #ddd; padding: 10px; margin: 5px 0; border-radius: 4px; background: #f9f9f9;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start;">
                <div style="flex: 1;">
                    <div style="margin-bottom: 8px;">
                        <strong style="color: #667eea;">${index + 1}. 匹配定额编码：</strong>
                        <span>${quota.quotaCode || '无'}</span>
                    </div>
                    <div style="margin-bottom: 8px;">
                        <strong style="color: #667eea;">匹配定额名称：</strong>
                        <span>${quota.quotaName || '无'}</span>
                    </div>
                    <div style="margin-bottom: 8px;">
                        <strong style="color: #667eea;">定额项目特征：</strong>
                        <span>${quota.quotaFeatureValue || '无'}</span>
                    </div>
                    <div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid #e0e0e0;">
                        <strong style="color: #2196F3;">单价：${quota.unitPrice || 0} 元</strong>
                    </div>
                </div>
                <button onclick="removeQuota(${quota.id})" style="background: #f44336; color: white; border: none; padding: 5px 10px; border-radius: 4px; cursor: pointer; margin-left: 10px; align-self: flex-start;">
                    移除
                </button>
            </div>
        </div>
    `).join('') + `
        <div style="margin-top: 10px; padding: 10px; background: #e3f2fd; border-radius: 4px;">
            <strong>合计单价：${totalPrice.toFixed(2)} 元</strong>
        </div>
    `;
}

async function removeQuota(itemQuotaId) {
    if (!currentEditItemId) return;
    
    if (!confirm('确定要移除这个定额吗？')) {
        return;
    }
    
    try {
        const response = await fetch(
            API_BASE + `/items/${currentEditItemId}/quotas/${itemQuotaId}`,
            { method: 'DELETE' }
        );
        
        const result = await response.json();
        
        if (result.success) {
            await loadItemQuotas(currentEditItemId);
            loadItems();
        } else {
            alert('移除失败：' + result.message);
        }
    } catch (error) {
        alert('移除失败：' + error.message);
    }
}

async function clearAllQuotas() {
    if (!currentEditItemId) return;
    
    if (!confirm('确定要清空所有已添加的定额吗？')) {
        return;
    }
    
    try {
        const response = await fetch(
            API_BASE + `/items/${currentEditItemId}/quotas`,
            { method: 'DELETE' }
        );
        
        const result = await response.json();
        
        if (result.success) {
            await loadItemQuotas(currentEditItemId);
            loadItems();
        } else {
            alert('清空失败：' + result.message);
        }
    } catch (error) {
        alert('清空失败：' + error.message);
    }
}

async function updatePrice() {
    if (!currentEditItemId) return;
    
    const price = parseFloat(document.getElementById('manualPrice').value);
    
    if (isNaN(price) || price < 0) {
        alert('请输入有效的单价');
        return;
    }
    
    try {
        const response = await fetch(
            API_BASE + `/items/${currentEditItemId}/price?unitPrice=${price}`,
            { method: 'PUT' }
        );
        
        const result = await response.json();
        
        if (result.success) {
            alert('更新成功！');
            closeEditModal();
            loadItems();
        } else {
            alert('更新失败：' + result.message);
        }
    } catch (error) {
        alert('更新失败：' + error.message);
    }
}

async function exportData() {
    try {
        const response = await fetch(API_BASE + '/export');
        
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = '匹配结果.xlsx';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        } else {
            alert('导出失败');
        }
    } catch (error) {
        alert('导出失败：' + error.message);
    }
}

async function clearAll() {
    if (!confirm('确定要清空所有数据吗？此操作不可恢复！')) {
        return;
    }
    
    try {
        const response = await fetch(API_BASE + '/clear', {
            method: 'DELETE'
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('清空成功！');
            loadItems();
        } else {
            alert('清空失败：' + result.message);
        }
    } catch (error) {
        alert('清空失败：' + error.message);
    }
}

window.onclick = function(event) {
    const editModal = document.getElementById('editModal');
    const itemEditModal = document.getElementById('itemEditModal');
    const quotaEditModal = document.getElementById('quotaEditModal');
    
    // 检查点击目标是否在模态框内
    if (editModal && event.target === editModal) {
        closeEditModal();
    }
    if (itemEditModal && event.target === itemEditModal) {
        closeItemEditModal();
    }
    if (quotaEditModal && event.target === quotaEditModal) {
        closeQuotaEditModal();
    }
    
    // 防止点击事件影响表格显示
    // 检查点击是否在表格区域内，如果是，则保持表格可见
    const clickedInTable = event.target.closest('.table-container') ||
                         event.target.closest('#itemsTableBody') ||
                         event.target.closest('#quotasTableBody') ||
                         event.target.closest('#versionsTableBody') ||
                         event.target.closest('#usersTableBody');
    
    if (clickedInTable) {
        // 点击在表格区域，确保表格内容可见
        setTimeout(() => {
            // 检查并修复表格可见性
            const tableBodies = ['#itemsTableBody', '#quotasTableBody', '#versionsTableBody', '#usersTableBody'];
            tableBodies.forEach(selector => {
                const tbody = document.querySelector(selector);
                if (tbody) {
                    // 确保tbody显示正常
                    tbody.style.display = 'table-row-group';
                    tbody.style.visibility = 'visible';
                    
                    // 检查表格行是否可见
                    const rows = tbody.querySelectorAll('tr');
                    rows.forEach(row => {
                        row.style.display = 'table-row';
                        row.style.visibility = 'visible';
                    });
                }
            });
        }, 10);
    }
}

// ==================== AI 助手聊天 ====================
const CHAT_API = '/api/assistant';
var chatMessages = []; // 当前会话消息

/** 发送消息 */
async function sendMessage() {
    var input = document.getElementById('chatInput');
    var question = (input.value || '').trim();
    if (!question) return;

    // 添加用户消息
    appendChatMessage('user', question);
    input.value = '';
    input.focus();

    // 显示加载动画
    showChatLoading();

    try {
        var response = await fetch(CHAT_API + '/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question: question })
        });
        var result = await response.json();

        removeChatLoading();

        if (result.success) {
            appendChatMessage('assistant', result.answer, result.sources || []);
        } else {
            appendChatMessage('assistant', result.answer || ('抱歉，处理您的问题时出错了：' + (result.message || '未知错误')));
        }
    } catch (e) {
        removeChatLoading();
        appendChatMessage('assistant', '网络请求失败：' + e.message + '。请检查网络连接后重试。');
    }
}

/** 推荐问题快捷发送 */
function sendSuggestion(el) {
    var input = document.getElementById('chatInput');
    input.value = el.textContent.trim();
    sendMessage();
}

/** 添加消息到聊天界面 */
function appendChatMessage(role, content, sources) {
    chatMessages.push({ role: role, content: content, sources: sources, time: new Date() });
    renderChatMessages();
}

/** 渲染聊天消息 */
function renderChatMessages() {
    var container = document.getElementById('chatMessagesInner');
    if (!container) return;

    var hasMessages = chatMessages.length > 0;
    var html = '';

    if (!hasMessages) {
        // 空状态：欢迎语 + 输入框（居中显示）
        html +=
            '<div class="chat-welcome">' +
            '<div class="welcome-icon"><i data-lucide="bot"></i></div>' +
            '<h3>AI 定额助手</h3>' +
            '<p>我可以基于已上传的企业定额数据回答您的问题</p>' +
            '<div class="suggestion-chips">' +
            '<span onclick="sendSuggestion(this)">电缆敷设施工费是多少？</span>' +
            '<span onclick="sendSuggestion(this)">路灯安装的定额单价怎么套？</span>' +
            '<span onclick="sendSuggestion(this)">给排水管道敷设有哪些相关定额？</span>' +
            '</div></div>';
    } else {
        // 有消息：渲染消息列表
        for (var i = 0; i < chatMessages.length; i++) {
            var msg = chatMessages[i];
            var timeStr = msg.time ? formatChatTime(msg.time) : '';
            if (msg.role === 'user') {
                html += '<div class="chat-message user">' +
                    '<div class="message-bubble">' + escapeHtml(msg.content) + '</div>' +
                    '<div class="message-time">' + timeStr + '</div></div>';
            } else {
                html += '<div class="chat-message assistant">' +
                    '<div class="message-bubble">' + formatChatContent(msg.content) + '</div>';
                if (msg.sources && msg.sources.length > 0) {
                    html += '<div class="source-citations">' +
                        '<div class="source-title">📌 数据来源</div>';
                    for (var s = 0; s < msg.sources.length; s++) {
                        html += '<div class="source-item"><span class="source-dot">•</span>' +
                            escapeHtml(msg.sources[s].label || msg.sources[s]) + '</div>';
                    }
                    html += '</div>';
                }
                html += '<div class="message-time">' + timeStr + '</div></div>';
            }
        }
    }

    // 始终包含输入框
    html += buildInputAreaHtml();

    container.innerHTML = html;
    container.classList.toggle('has-messages', hasMessages);

    // 滚动到底部
    var scrollContainer = document.getElementById('chatMessages');
    if (scrollContainer) {
        scrollContainer.scrollTop = scrollContainer.scrollHeight;
    }

    // 重新初始化 Lucide 图标
    if (typeof lucide !== 'undefined') { lucide.createIcons(); }
}

/** 构建输入框 HTML */
function buildInputAreaHtml() {
    return '<div class="chat-input-area">' +
        '<div class="chat-input-inner">' +
        '<input type="text" id="chatInput" placeholder="输入您的问题，基于定额数据智能回答..."' +
        ' onkeydown="if(event.key===\'Enter\')sendMessage()">' +
        '<button onclick="sendMessage()" id="chatSendBtn" class="btn-primary" title="发送">' +
        '<i data-lucide="send"></i></button>' +
        '</div></div>';
}

/** 格式化聊天内容（处理换行和markdown基础语法） */
function formatChatContent(text) {
    if (!text) return '';
    // HTML 转义
    var escaped = escapeHtml(text);
    // 换行
    escaped = escaped.replace(/\n/g, '<br>');
    // 粗体 **text**
    escaped = escaped.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    return escaped;
}

/** 格式化时间 */
function formatChatTime(date) {
    var h = date.getHours();
    var m = date.getMinutes();
    return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m;
}

/** HTML 转义 */
function escapeHtml(text) {
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}

/** 显示加载动画 */
function showChatLoading() {
    var container = document.getElementById('chatMessagesInner');
    if (!container) return;
    var loading = document.createElement('div');
    loading.className = 'chat-loading';
    loading.id = 'chatLoading';
    loading.innerHTML =
        '<div class="typing-dots">' +
        '<span></span><span></span><span></span>' +
        '</div>';
    container.appendChild(loading);
    container.scrollTop = container.scrollHeight;
}

/** 移除加载动画 */
function removeChatLoading() {
    var loading = document.getElementById('chatLoading');
    if (loading) loading.remove();
}

// ==================== 导航切换 ====================
function switchNav(navName) {
    // 隐藏所有标签页
    document.querySelectorAll('.tab-content').forEach(function(tab) {
        tab.classList.remove('active');
    });

    // 更新侧边栏 nav-item 激活状态
    document.querySelectorAll('.sidebar .nav-item').forEach(function(item) {
        item.classList.remove('active');
    });
    var activeNav = document.querySelector('.sidebar .nav-item[data-nav="' + navName + '"]');
    if (activeNav) activeNav.classList.add('active');

    // 更新面包屑
    var breadcrumb = document.getElementById('breadcrumbCurrent');
    var labels = { assistant: 'AI助手', items: '定额匹配', versions: '定额管理', document: '文档生成', users: '用户管理' };

    if (navName === 'assistant') {
        document.getElementById('assistantTab').classList.add('active');
        if (breadcrumb) breadcrumb.textContent = labels.assistant;
    } else if (navName === 'items') {
        document.getElementById('itemsTab').classList.add('active');
        if (breadcrumb) breadcrumb.textContent = labels.items;
        loadItems();
    } else if (navName === 'versions') {
        document.getElementById('versionsTab').classList.add('active');
        var versionDetailTab = document.getElementById('versionDetailTab');
        if (versionDetailTab) versionDetailTab.classList.remove('active');
        if (breadcrumb) breadcrumb.textContent = labels.versions;
        loadVersions();
    } else if (navName === 'users') {
        var usersTab = document.getElementById('usersTab');
        if (usersTab) usersTab.classList.add('active');
        if (breadcrumb) breadcrumb.textContent = labels.users;
        loadUsers();
    } else if (navName === 'document') {
        document.getElementById('documentTab').classList.add('active');
        if (breadcrumb) breadcrumb.textContent = labels.document;
        loadDocumentTemplates();
        loadReplacementTemplates();
    }
}

// ==================== 定额管理模块 ====================
let currentEditQuotaId = null;
// selectedQuotaIds 已在文件开头声明，不需要重复声明

// 加载定额列表
async function loadQuotas() {
    try {
        let url = API_BASE + '/quotas';
        if (currentViewingVersionId) {
            url += '?versionId=' + currentViewingVersionId;
        }
        const response = await fetch(url);
        
        // 检查响应状态
        if (!response.ok) {
            console.error('加载定额数据失败，状态码:', response.status);
            // 即使API返回错误，也要确保表格显示空状态而不是空白
            renderQuotasTable([]);
            return;
        }
        
        const quotas = await response.json();
        
        // 验证返回的数据格式
        if (!Array.isArray(quotas)) {
            console.error('返回的定额数据格式错误:', quotas);
            renderQuotasTable([]);
            return;
        }
        
        renderQuotasTable(quotas);
    } catch (error) {
        console.error('加载定额数据失败：', error);
        // 发生错误时也应确保显示空状态
        renderQuotasTable([]);
    }
}

// 渲染定额表格
function renderQuotasTable(quotas) {
    const tbody = document.getElementById('quotasTableBody');
    
    // 根据经验教训，在渲染前显式设置tbody的显示属性
    if (tbody) {
        tbody.style.display = '';
        tbody.style.visibility = 'visible';
    }
    
    if (quotas.length === 0) {
        tbody.innerHTML = '<tr><td colspan="12" class="empty-message">暂无数据，请先导入或新增企业定额</td></tr>';
        selectedQuotaIds.clear();
        updateBatchActions();
        return;
    }
    
    tbody.innerHTML = quotas.map((quota, index) => {
        const isSelected = selectedQuotaIds.has(quota.id);
        return `
            <tr>
                <td style="text-align: center;">
                    <input type="checkbox" ${isSelected ? 'checked' : ''} 
                           onchange="toggleQuotaSelection(${quota.id}, this.checked)">
                </td>
                <td style="text-align: center; font-weight: bold;">${index + 1}</td>
                <td class="editable-quota-cell" data-field="quotaCode" data-quota-id="${quota.id}" title="双击编辑">${quota.quotaCode || ''}</td>
                <td class="editable-quota-cell" data-field="quotaName" data-quota-id="${quota.id}" title="双击编辑">${quota.quotaName || ''}</td>
                <td class="editable-quota-cell" data-field="featureValue" data-quota-id="${quota.id}" title="双击编辑">${quota.featureValue || ''}</td>
                <td class="editable-quota-cell" data-field="unit" data-quota-id="${quota.id}" title="双击编辑">${quota.unit || ''}</td>
                <td class="editable-quota-cell" data-field="unitPrice" data-quota-id="${quota.id}" data-type="number" title="双击编辑">${quota.unitPrice || 0}</td>
                <td class="editable-quota-cell" data-field="laborCost" data-quota-id="${quota.id}" data-type="number" title="双击编辑">${quota.laborCost || 0}</td>
                <td class="editable-quota-cell" data-field="materialCost" data-quota-id="${quota.id}" data-type="number" title="双击编辑">${quota.materialCost || 0}</td>
                <td class="editable-quota-cell" data-field="machineCost" data-quota-id="${quota.id}" data-type="number" title="双击编辑">${quota.machineCost || 0}</td>
                <td class="editable-quota-cell" data-field="remark" data-quota-id="${quota.id}" title="双击编辑">${quota.remark || ''}</td>
                <td>
                    <button class="action-btn" onclick="openQuotaEditModal(${quota.id})">编辑</button>
                </td>
            </tr>
        `;
    }).join('');
    
    // 初始化可编辑单元格、列宽调整和滚动条
    setTimeout(() => {
        initEditableQuotaCells();
        initResizableQuotaColumns();
        ensureTableScrolling();
        
        // 根据经验教训，强制重排确保表格正确渲染
        if (tbody) {
            tbody.offsetHeight; // 触发重排
            tbody.style.transform = 'translateZ(0)'; // 启用硬件加速
            setTimeout(() => {
                tbody.style.transform = ''; // 移除临时样式
            }, 10);
        }
    }, 100);
    
    // 显示/隐藏批量操作（页面未必存在 batchActions 容器，需判空避免抛错导致表格异常）
    updateBatchActions();
}

// 初始化可编辑定额单元格
function initEditableQuotaCells() {
    const editableCells = document.querySelectorAll('.editable-quota-cell');
    editableCells.forEach(cell => {
        cell.addEventListener('dblclick', function() {
            startEditQuotaCell(this);
        });
    });
}

function startEditQuotaCell(cell) {
    const originalValue = cell.textContent.trim();
    const field = cell.getAttribute('data-field');
    const quotaId = cell.getAttribute('data-quota-id');
    const isNumber = cell.getAttribute('data-type') === 'number';
    
    const input = document.createElement('input');
    input.type = isNumber ? 'number' : 'text';
    input.value = originalValue;
    input.className = 'cell-input';
    input.style.width = '100%';
    input.style.padding = '4px';
    input.style.border = '2px solid #1976d2';
    input.style.borderRadius = '3px';
    
    const originalText = cell.textContent;
    cell.textContent = '';
    cell.appendChild(input);
    input.focus();
    input.select();
    
    const saveEdit = async () => {
        const newValue = input.value.trim();
        
        if (newValue === originalValue) {
            cell.textContent = originalText;
            return;
        }
        
        if (field === 'quotaName' && !newValue) {
            alert('定额名称不能为空');
            input.focus();
            return;
        }
        
        if (isNumber && newValue && isNaN(parseFloat(newValue))) {
            alert('请输入有效的数字');
            input.focus();
            return;
        }
        
        cell.textContent = '保存中...';
        cell.style.color = '#999';
        
        try {
            const updateData = {};
            updateData[field] = isNumber && newValue ? parseFloat(newValue) : newValue;
            
            const response = await fetch(API_BASE + `/quotas/${quotaId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(updateData)
            });
            
            const result = await response.json();
            
            if (result.success) {
                await loadQuotas();
            } else {
                alert('保存失败：' + result.message);
                cell.textContent = originalText;
            }
        } catch (error) {
            alert('保存失败：' + error.message);
            cell.textContent = originalText;
        } finally {
            cell.style.color = '';
        }
    };
    
    input.addEventListener('blur', saveEdit);
    input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            input.blur();
        } else if (e.key === 'Escape') {
            e.preventDefault();
            cell.textContent = originalText;
        }
    });
}

// 切换定额选择
function toggleQuotaSelection(quotaId, checked) {
    if (checked) {
        selectedQuotaIds.add(quotaId);
    } else {
        selectedQuotaIds.delete(quotaId);
    }
    updateBatchActions();
}

// 全选/取消全选
function toggleSelectAllQuotas() {
    const selectAll = document.getElementById('selectAllQuotas').checked;
    const checkboxes = document.querySelectorAll('#quotasTableBody input[type="checkbox"]');
    
    checkboxes.forEach(cb => {
        cb.checked = selectAll;
        const quotaId = parseInt(cb.getAttribute('onchange').match(/\d+/)[0]);
        if (selectAll) {
            selectedQuotaIds.add(quotaId);
        } else {
            selectedQuotaIds.delete(quotaId);
        }
    });
    
    updateBatchActions();
}

// 更新批量操作显示
function updateBatchActions() {
    const hasSelection = selectedQuotaIds.size > 0;
    const batchActions = document.getElementById('batchActions');
    const toolbarBtn = document.getElementById('toolbarBatchDeleteBtn');
    
    if (batchActions) {
        batchActions.style.display = hasSelection ? 'block' : 'none';
    }
    if (toolbarBtn) {
        toolbarBtn.style.display = hasSelection ? 'inline-block' : 'none';
    }
}

// 打开定额编辑模态框
async function openQuotaEditModal(quotaId) {
    currentEditQuotaId = quotaId;
    const modal = document.getElementById('quotaEditModal');
    const title = document.getElementById('quotaEditModalTitle');
    
    title.textContent = quotaId ? '编辑定额' : '新增定额';
    
    if (quotaId) {
        try {
            const response = await fetch(API_BASE + `/quotas/${quotaId}`);
            const quota = await response.json();
            
            document.getElementById('editQuotaCode').value = quota.quotaCode || '';
            document.getElementById('editQuotaName').value = quota.quotaName || '';
            document.getElementById('editQuotaFeatureValue').value = quota.featureValue || '';
            document.getElementById('editQuotaUnit').value = quota.unit || '';
            document.getElementById('editQuotaUnitPrice').value = quota.unitPrice || '';
            document.getElementById('editQuotaLaborCost').value = quota.laborCost || '';
            document.getElementById('editQuotaMaterialCost').value = quota.materialCost || '';
            document.getElementById('editQuotaMachineCost').value = quota.machineCost || '';
            document.getElementById('editQuotaRemark').value = quota.remark || '';
        } catch (error) {
            alert('加载定额数据失败：' + error.message);
            return;
        }
    } else {
        // 清空表单
        document.getElementById('editQuotaCode').value = '';
        document.getElementById('editQuotaName').value = '';
        document.getElementById('editQuotaFeatureValue').value = '';
        document.getElementById('editQuotaUnit').value = '';
        document.getElementById('editQuotaUnitPrice').value = '';
        document.getElementById('editQuotaLaborCost').value = '';
        document.getElementById('editQuotaMaterialCost').value = '';
        document.getElementById('editQuotaMachineCost').value = '';
        document.getElementById('editQuotaRemark').value = '';
    }
    
    modal.style.display = 'block';
}

// 关闭定额编辑模态框
function closeQuotaEditModal() {
    document.getElementById('quotaEditModal').style.display = 'none';
    currentEditQuotaId = null;
}

// 保存定额
async function saveQuota() {
    const quotaName = document.getElementById('editQuotaName').value.trim();
    
    if (!quotaName) {
        alert('定额名称不能为空');
        return;
    }
    
    const quotaData = {
        quotaCode: document.getElementById('editQuotaCode').value.trim(),
        quotaName: quotaName,
        featureValue: document.getElementById('editQuotaFeatureValue').value.trim(),
        unit: document.getElementById('editQuotaUnit').value.trim(),
        unitPrice: parseFloat(document.getElementById('editQuotaUnitPrice').value) || null,
        laborCost: parseFloat(document.getElementById('editQuotaLaborCost').value) || null,
        materialCost: parseFloat(document.getElementById('editQuotaMaterialCost').value) || null,
        machineCost: parseFloat(document.getElementById('editQuotaMachineCost').value) || null,
        remark: document.getElementById('editQuotaRemark').value.trim()
    };
    
    try {
        const url = currentEditQuotaId 
            ? API_BASE + `/quotas/${currentEditQuotaId}`
            : API_BASE + '/quotas';
        const method = currentEditQuotaId ? 'PUT' : 'POST';
        
        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(quotaData)
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('保存成功！');
            closeQuotaEditModal();
            loadQuotas();
        } else {
            alert('保存失败：' + result.message);
        }
    } catch (error) {
        alert('保存失败：' + error.message);
    }
}

// 删除定额
async function deleteQuota(quotaId) {
    if (!confirm('确定要删除这条定额吗？')) {
        return;
    }
    
    try {
        const response = await fetch(API_BASE + `/quotas/${quotaId}`, {
            method: 'DELETE'
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('删除成功！');
            loadQuotas();
        } else {
            alert('删除失败：' + result.message);
        }
    } catch (error) {
        alert('删除失败：' + error.message);
    }
}

// 批量删除定额
async function batchDeleteQuotas() {
    if (selectedQuotaIds.size === 0) {
        alert('请先选择要删除的定额');
        return;
    }
    
    if (!confirm(`确定要删除选中的 ${selectedQuotaIds.size} 条定额吗？`)) {
        return;
    }
    
    try {
        const response = await fetch(API_BASE + '/quotas/batch', {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(Array.from(selectedQuotaIds))
        });
        
        const result = await response.json();
        
            if (result.success) {
                alert(result.message);
                selectedQuotaIds.clear();
                updateBatchActions();
                loadQuotas();
            } else {
                alert('批量删除失败：' + result.message);
            }
        } catch (error) {
            alert('批量删除失败：' + error.message);
        }
    }

// 导出定额
async function exportQuotas() {
    try {
        const response = await fetch(API_BASE + '/quotas/export');
        
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = '企业定额数据.xlsx';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        } else {
            alert('导出失败');
        }
    } catch (error) {
        alert('导出失败：' + error.message);
    }
}

// 从文件导入定额
function importQuotasFromFile() {
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = '.xlsx,.xls';
    fileInput.onchange = async function() {
        if (!this.files[0]) return;
        
        const formData = new FormData();
        formData.append('file', this.files[0]);
        
        try {
            const response = await fetch(API_BASE + '/import-quotas', {
                method: 'POST',
                body: formData
            });
            
            const result = await response.json();
            
            if (result.success) {
                alert(result.message);
                loadQuotas();
            } else {
                alert('导入失败：' + result.message);
            }
        } catch (error) {
            alert('导入失败：' + error.message);
        }
    };
    fileInput.click();
}

// 过滤定额
function filterQuotas() {
    const searchInput = document.getElementById('quotaManagementSearchInput');
    if (!searchInput) return;
    const keyword = searchInput.value.toLowerCase();
    const rows = document.querySelectorAll('#quotasTableBody tr');
    
    rows.forEach(row => {
        const text = row.textContent.toLowerCase();
        row.style.display = text.includes(keyword) ? '' : 'none';
    });
}

// 注意：已移除自动调整列宽功能，现在内容会换行适应列宽

// 初始化列宽调整功能（清单表格）
function initResizableColumns() {
    const table = document.getElementById('itemsTable');
    if (!table) return;
    
    const headers = table.querySelectorAll('thead th');
    
    headers.forEach((header, index) => {
        // 跳过最后一列（操作列），不需要调整
        if (index === headers.length - 1) return;
        
        // 检查是否已有调整器
        if (header.querySelector('.resizer')) return;
        
        // 创建调整器
        const resizer = document.createElement('div');
        resizer.className = 'resizer';
        header.appendChild(resizer);
        
        let startX, startWidth, isResizing = false;
        
        resizer.addEventListener('mousedown', (e) => {
            e.preventDefault();
            isResizing = true;
            startX = e.pageX;
            startWidth = header.offsetWidth;
            header.classList.add('resizing');
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
        });
        
        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            
            const width = startWidth + (e.pageX - startX);
            if (width > 50) { // 最小宽度50px
                header.style.width = width + 'px';
                header.style.minWidth = width + 'px';
                
                // 同步调整同一列的所有单元格
                const columnIndex = Array.from(headers).indexOf(header);
                const rows = table.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    const cell = row.cells[columnIndex];
                    if (cell) {
                        cell.style.width = width + 'px';
                        cell.style.minWidth = width + 'px';
                    }
                });
            }
        });
        
        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                header.classList.remove('resizing');
                document.body.style.cursor = '';
                document.body.style.userSelect = '';
            }
        });
    });
}

// 初始化列宽调整功能（定额表格）
function initResizableQuotaColumns() {
    const table = document.getElementById('quotasTable');
    if (!table) return;
    
    const headers = table.querySelectorAll('thead th');
    
    headers.forEach((header, index) => {
        // 跳过第一列（复选框）和最后一列（操作列），不需要调整
        if (index === 0 || index === headers.length - 1) return;
        
        // 检查是否已有调整器
        if (header.querySelector('.resizer')) return;
        
        // 创建调整器
        const resizer = document.createElement('div');
        resizer.className = 'resizer';
        header.appendChild(resizer);
        
        let startX, startWidth, isResizing = false;
        
        resizer.addEventListener('mousedown', (e) => {
            e.preventDefault();
            isResizing = true;
            startX = e.pageX;
            startWidth = header.offsetWidth;
            header.classList.add('resizing');
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
        });
        
        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            
            const width = startWidth + (e.pageX - startX);
            if (width > 50) { // 最小宽度50px
                header.style.width = width + 'px';
                header.style.minWidth = width + 'px';
                
                // 同步调整同一列的所有单元格
                const columnIndex = Array.from(headers).indexOf(header);
                const rows = table.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    const cell = row.cells[columnIndex];
                    if (cell) {
                        cell.style.width = width + 'px';
                        cell.style.minWidth = width + 'px';
                    }
                });
            }
        });
        
        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                header.classList.remove('resizing');
                document.body.style.cursor = '';
                document.body.style.userSelect = '';
            }
        });
    });
}

// 初始化列宽调整功能（版本表格）
function initResizableVersionColumns() {
    const table = document.getElementById('versionsTable');
    if (!table) return;
    
    const headers = table.querySelectorAll('thead th');
    
    headers.forEach((header, index) => {
        // 跳过第一列（复选框）和最后一列（操作列），不需要调整
        if (index === 0 || index === headers.length - 1) return;
        
        // 检查是否已有调整器
        if (header.querySelector('.resizer')) return;
        
        // 创建调整器
        const resizer = document.createElement('div');
        resizer.className = 'resizer';
        header.appendChild(resizer);
        
        let startX, startWidth, isResizing = false;
        
        resizer.addEventListener('mousedown', (e) => {
            e.preventDefault();
            e.stopPropagation(); // 阻止触发行的点击事件
            isResizing = true;
            startX = e.pageX;
            startWidth = header.offsetWidth;
            header.classList.add('resizing');
            document.body.style.cursor = 'col-resize';
            document.body.style.userSelect = 'none';
        });
        
        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;
            
            const width = startWidth + (e.pageX - startX);
            if (width > 50) { // 最小宽度50px
                header.style.width = width + 'px';
                header.style.minWidth = width + 'px';
                
                // 同步调整同一列的所有单元格
                const columnIndex = Array.from(headers).indexOf(header);
                const rows = table.querySelectorAll('tbody tr');
                rows.forEach(row => {
                    const cell = row.cells[columnIndex];
                    if (cell) {
                        cell.style.width = width + 'px';
                        cell.style.minWidth = width + 'px';
                    }
                });
            }
        });
        
        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                header.classList.remove('resizing');
                document.body.style.cursor = '';
                document.body.style.userSelect = '';
            }
        });
    });
}

// 初始化表格滚动条
function initTableScrollbar(containerId, scrollbarId, thumbId) {
    const container = document.getElementById(containerId);
    const scrollbar = document.getElementById(scrollbarId);
    const thumb = document.getElementById(thumbId);
    
    if (!container || !scrollbar || !thumb) {
        console.warn('滚动条元素未找到:', {containerId, scrollbarId, thumbId});
        return;
    }
    
    // 确保容器可以滚动
    container.style.overflowY = 'auto';
    container.style.overflowX = 'auto';
    
    function updateScrollbar() {
        const containerHeight = container.clientHeight;
        const containerScrollHeight = container.scrollHeight;
        const scrollTop = container.scrollTop;
        
        // 如果容器高度为0或未初始化，延迟重试
        if (containerHeight === 0 || containerScrollHeight === 0) {
            setTimeout(updateScrollbar, 100);
            return;
        }
        
        // 设置滚动条高度，确保与容器一致
        scrollbar.style.height = containerHeight + 'px';
        
        // 如果内容不需要滚动，隐藏滚动条
        const needsScroll = containerScrollHeight > containerHeight + 2; // 加2是为了避免浮点数误差和边框
        
        if (!needsScroll) {
            scrollbar.style.display = 'none';
            return;
        }
        
        // 显示滚动条
        scrollbar.style.display = 'block';
        
        // 计算滑块高度和位置
        const scrollbarHeight = scrollbar.clientHeight || containerHeight;
        if (scrollbarHeight === 0) {
            setTimeout(updateScrollbar, 100);
            return;
        }
        
        const thumbHeight = Math.max((containerHeight / containerScrollHeight) * scrollbarHeight, 30);
        const maxScroll = containerScrollHeight - containerHeight;
        const thumbTop = maxScroll > 0 ? (scrollTop / maxScroll) * (scrollbarHeight - thumbHeight) : 0;
        
        thumb.style.height = thumbHeight + 'px';
        thumb.style.top = Math.max(0, Math.min(thumbTop, scrollbarHeight - thumbHeight)) + 'px';
    }
    
    // 监听容器滚动
    container.addEventListener('scroll', updateScrollbar);
    
    // 确保鼠标滚轮可以滚动（使用默认行为即可）
    
    // 监听容器大小变化
    if (typeof ResizeObserver !== 'undefined') {
        const resizeObserver = new ResizeObserver(() => {
            setTimeout(updateScrollbar, 10);
        });
        resizeObserver.observe(container);
        resizeObserver.observe(scrollbar);
    } else {
        // 降级方案
        window.addEventListener('resize', updateScrollbar);
    }
    
    // 点击滚动条跳转
    scrollbar.addEventListener('click', (e) => {
        if (e.target === thumb) return; // 如果点击的是滑块本身，不处理
        
        const rect = scrollbar.getBoundingClientRect();
        const clickY = e.clientY - rect.top;
        const scrollbarHeight = scrollbar.clientHeight;
        const percentage = clickY / scrollbarHeight;
        const scrollHeight = container.scrollHeight - container.clientHeight;
        container.scrollTop = percentage * scrollHeight;
        updateScrollbar();
    });
    
    // 拖拽滚动条
    let isDragging = false;
    let startY = 0;
    let startScrollTop = 0;
    
    thumb.addEventListener('mousedown', (e) => {
        e.preventDefault();
        isDragging = true;
        startY = e.clientY;
        startScrollTop = container.scrollTop;
        document.body.style.cursor = 'grabbing';
        document.body.style.userSelect = 'none';
    });
    
    document.addEventListener('mousemove', (e) => {
        if (!isDragging) return;
        
        const deltaY = e.clientY - startY;
        const scrollbarHeight = scrollbar.clientHeight;
        const scrollHeight = container.scrollHeight - container.clientHeight;
        const scrollRatio = scrollHeight / scrollbarHeight;
        
        container.scrollTop = startScrollTop + (deltaY * scrollRatio);
    });
    
    document.addEventListener('mouseup', () => {
        if (isDragging) {
            isDragging = false;
            document.body.style.cursor = '';
            document.body.style.userSelect = '';
        }
    });
    
    // 监听表格内容变化（新增/删除行时更新滚动条）
    const table = container.querySelector('table');
    if (table && table.querySelector('tbody')) {
        const observer = new MutationObserver(() => {
            setTimeout(updateScrollbar, 50);
        });
        observer.observe(table.querySelector('tbody'), {
            childList: true,
            subtree: true
        });
    }
    
    // 初始化 - 多次尝试确保正确显示
    const initScrollbar = () => {
        // 立即执行一次
        updateScrollbar();
        // 延迟执行，确保DOM完全渲染
        setTimeout(updateScrollbar, 10);
        setTimeout(updateScrollbar, 50);
        setTimeout(updateScrollbar, 100);
        setTimeout(updateScrollbar, 200);
        setTimeout(updateScrollbar, 500);
    };
    
    initScrollbar();
    
    // 定期检查更新（防止动态内容加载后滚动条未更新）
    let checkCount = 0;
    const maxChecks = 30; // 最多检查30次（30秒）
    const intervalId = setInterval(() => {
        checkCount++;
        const oldScrollHeight = container.scrollHeight;
        const oldClientHeight = container.clientHeight;
        updateScrollbar();
        
        // 如果内容高度稳定且大于容器高度，停止定期检查
        if (container.scrollHeight === oldScrollHeight && 
            container.clientHeight === oldClientHeight &&
            container.scrollHeight > container.clientHeight) {
            clearInterval(intervalId);
        } else if (checkCount >= maxChecks) {
            clearInterval(intervalId);
        }
    }, 1000);
}

// ==================== 版本管理模块 ====================

let currentEditVersionId = null;

// 加载版本列表
async function loadVersions() {
    try {
        const response = await fetch(API_BASE + '/versions');
        
        // 检查响应状态
        if (!response.ok) {
            console.error('加载版本数据失败，状态码:', response.status);
            // 即使API返回错误，也要确保表格显示空状态而不是空白
            renderVersionsTable([]);
            return;
        }
        
        const versions = await response.json();
        
        // 验证返回的数据格式
        if (!Array.isArray(versions)) {
            console.error('返回的版本数据格式错误:', versions);
            renderVersionsTable([]);
            return;
        }
        
        renderVersionsTable(versions);
    } catch (error) {
        console.error('加载版本数据失败：', error);
        // 发生错误时也应确保显示空状态
        renderVersionsTable([]);
    }
}

// 渲染版本表格
function renderVersionsTable(versions) {
    const tbody = document.getElementById('versionsTableBody');
    if (!tbody) return;
    
    // 根据经验教训，在渲染前显式设置tbody的显示属性
    if (tbody) {
        tbody.style.display = '';
        tbody.style.visibility = 'visible';
    }
    
    if (versions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="empty-message">暂无数据，请先新增版本</td></tr>';
        selectedVersionIds.clear();
        updateVersionBatchActions();
        return;
    }
    
    tbody.innerHTML = versions.map((version, index) => {
        const isSelected = selectedVersionIds.has(version.id);
        const createTime = version.createTime ? new Date(version.createTime).toLocaleString('zh-CN') : '';
        const updateTime = version.updateTime ? new Date(version.updateTime).toLocaleString('zh-CN') : '';
        return `
            <tr style="cursor: pointer;" onclick="viewVersionDetail(${version.id}, '${(version.versionName || '').replace(/'/g, "\\'")}')">
                <td onclick="event.stopPropagation();">
                    <input type="checkbox" ${isSelected ? 'checked' : ''} 
                           onchange="toggleVersionSelection(${version.id}, this.checked)">
                </td>
                <td style="text-align: center; font-weight: bold;">${index + 1}</td>
                <td>${version.versionName || ''}</td>
                <td>${version.description || ''}</td>
                <td>${createTime}</td>
                <td>${updateTime}</td>
                <td onclick="event.stopPropagation();">
                    <button onclick="event.stopPropagation(); openVersionEditModal(${version.id})" class="btn-primary" style="padding: 5px 10px; margin-right: 5px;">编辑</button>
                    <button onclick="event.stopPropagation(); deleteVersion(${version.id})" class="btn-danger" style="padding: 5px 10px;">删除</button>
                </td>
            </tr>
        `;
    }).join('');
    
    // 初始化列宽调整功能
    setTimeout(() => {
        initResizableVersionColumns();
        updateVersionBatchActions();
        
        // 根据经验教训，强制重排确保表格正确渲染
        if (tbody) {
            tbody.offsetHeight; // 触发重排
            tbody.style.transform = 'translateZ(0)'; // 启用硬件加速
            setTimeout(() => {
                tbody.style.transform = ''; // 移除临时样式
            }, 10);
        }
    }, 100);
}

// 查看版本明细
function viewVersionDetail(versionId, versionName) {
    currentViewingVersionId = versionId;
    var titleEl = document.getElementById('versionDetailTitle');
    if (titleEl) titleEl.textContent = versionName + ' - 定额明细';
    var versionsTab = document.getElementById('versionsTab');
    var versionDetailTab = document.getElementById('versionDetailTab');
    if (versionsTab) versionsTab.classList.remove('active');
    if (versionDetailTab) versionDetailTab.classList.add('active');
    // 更新面包屑
    var breadcrumb = document.getElementById('breadcrumbCurrent');
    if (breadcrumb) breadcrumb.textContent = versionName + ' - 定额明细';
    loadQuotas();
}

// 返回版本列表
function backToVersionList() {
    currentViewingVersionId = null;
    var versionDetailTab = document.getElementById('versionDetailTab');
    var versionsTab = document.getElementById('versionsTab');
    if (versionDetailTab) versionDetailTab.classList.remove('active');
    if (versionsTab) versionsTab.classList.add('active');
    // 恢复面包屑
    var breadcrumb = document.getElementById('breadcrumbCurrent');
    if (breadcrumb) breadcrumb.textContent = '定额管理';
    loadVersions();
}

// 打开版本编辑模态框
function openVersionEditModal(versionId) {
    const modal = document.getElementById('versionEditModal');
    if (!modal) return;
    
    const title = document.getElementById('versionEditModalTitle');
    const nameInput = document.getElementById('editVersionName');
    const descInput = document.getElementById('editVersionDescription');
    const importSection = document.getElementById('versionImportSection');
    
    if (versionId) {
        if (title) title.textContent = '编辑版本';
        if (importSection) importSection.style.display = 'none';
        // 加载版本数据
        fetch(API_BASE + '/versions/' + versionId)
            .then(response => response.json())
            .then(version => {
                if (nameInput) nameInput.value = version.versionName || '';
                if (descInput) descInput.value = version.description || '';
                currentEditVersionId = versionId;
            })
            .catch(error => {
                console.error('加载版本数据失败：', error);
                alert('加载版本数据失败');
            });
    } else {
        if (title) title.textContent = '新增版本';
        if (importSection) importSection.style.display = 'block';
        if (nameInput) nameInput.value = '';
        if (descInput) descInput.value = '';
        const fileInput = document.getElementById('versionQuotaFile');
        if (fileInput) fileInput.value = '';
        currentEditVersionId = null;
    }
    
    modal.style.display = 'block';
}

// 关闭版本编辑模态框
function closeVersionEditModal() {
    const modal = document.getElementById('versionEditModal');
    if (modal) modal.style.display = 'none';
    currentEditVersionId = null;
}

// 保存版本
async function saveVersion() {
    const nameInput = document.getElementById('editVersionName');
    const descInput = document.getElementById('editVersionDescription');
    const fileInput = document.getElementById('versionQuotaFile');
    
    if (!nameInput || !nameInput.value.trim()) {
        alert('版本名称不能为空');
        return;
    }
    
    try {
        let versionId = currentEditVersionId;
        
        // 保存版本信息
        const versionData = {
            versionName: nameInput.value.trim(),
            description: descInput ? descInput.value.trim() : ''
        };
        
        let response;
        if (versionId) {
            response = await fetch(API_BASE + '/versions/' + versionId, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(versionData)
            });
        } else {
            response = await fetch(API_BASE + '/versions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(versionData)
            });
        }
        
        const result = await response.json();
        
        if (result.success) {
            if (!versionId) {
                versionId = result.version.id;
            }
            
            // 如果有文件，导入定额
            if (fileInput && fileInput.files[0]) {
                const formData = new FormData();
                formData.append('file', fileInput.files[0]);
                
                const importResponse = await fetch(API_BASE + '/versions/' + versionId + '/import-quotas', {
                    method: 'POST',
                    body: formData
                });
                
                const importResult = await importResponse.json();
                if (importResult.success) {
                    alert('版本保存成功，并导入了 ' + importResult.count + ' 条定额数据');
                } else {
                    alert('版本保存成功，但导入定额失败：' + importResult.message);
                }
            } else {
                alert('版本保存成功');
            }
            
            closeVersionEditModal();
            loadVersions();
            loadVersionOptions();
        } else {
            alert('保存失败：' + result.message);
        }
    } catch (error) {
        console.error('保存版本失败：', error);
        alert('保存失败：' + error.message);
    }
}

// 删除版本
async function deleteVersion(versionId) {
    if (!confirm('确定要删除此版本吗？')) {
        return;
    }
    
    try {
        const response = await fetch(API_BASE + '/versions/' + versionId, {
            method: 'DELETE'
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('删除成功');
            loadVersions();
            loadVersionOptions();
            if (currentViewingVersionId === versionId) {
                backToVersionList();
            }
        } else {
            alert('删除失败：' + result.message);
        }
    } catch (error) {
        console.error('删除版本失败：', error);
        alert('删除失败：' + error.message);
    }
}

// 批量删除版本
async function batchDeleteVersions() {
    if (selectedVersionIds.size === 0) {
        alert('请先选择要删除的版本');
        return;
    }
    
    if (!confirm('确定要删除选中的 ' + selectedVersionIds.size + ' 个版本吗？')) {
        return;
    }
    
    try {
        const response = await fetch(API_BASE + '/versions/batch', {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(Array.from(selectedVersionIds))
        });
        
        const result = await response.json();
        
        if (result.success) {
            alert('批量删除成功');
            selectedVersionIds.clear();
            loadVersions();
            loadVersionOptions();
        } else {
            alert('批量删除失败：' + result.message);
        }
    } catch (error) {
        console.error('批量删除版本失败：', error);
        alert('批量删除失败：' + error.message);
    }
}

// 切换版本选择
function toggleVersionSelection(versionId, checked) {
    if (checked) {
        selectedVersionIds.add(versionId);
    } else {
        selectedVersionIds.delete(versionId);
    }
    updateVersionBatchActions();
}

// 全选/取消全选版本
function toggleSelectAllVersions() {
    const checkbox = document.getElementById('selectAllVersions');
    if (!checkbox) return;
    
    const checkboxes = document.querySelectorAll('#versionsTableBody input[type="checkbox"]');
    
    checkboxes.forEach(cb => {
        const match = cb.getAttribute('onchange').match(/\d+/);
        if (match) {
            const versionId = parseInt(match[0]);
            cb.checked = checkbox.checked;
            if (checkbox.checked) {
                selectedVersionIds.add(versionId);
            } else {
                selectedVersionIds.delete(versionId);
            }
        }
    });
    
    updateVersionBatchActions();
}

// 更新版本批量操作按钮
function updateVersionBatchActions() {
    const btn = document.getElementById('toolbarBatchDeleteVersionsBtn');
    if (btn) {
        if (selectedVersionIds.size > 0) {
            btn.style.display = 'inline-block';
        } else {
            btn.style.display = 'none';
        }
    }
}

// 加载版本选项（用于匹配界面的下拉框）
async function loadVersionOptions() {
    try {
        const response = await fetch(API_BASE + '/versions');
        
        // 检查响应状态
        if (!response.ok) {
            console.error('加载版本选项失败，状态码:', response.status);
            // 即使API返回错误，也要确保下拉框显示默认选项
            const select = document.getElementById('versionSelect');
            if (select) {
                select.innerHTML = '<option value="">暂无版本</option>';
            }
            return;
        }
        
        const versions = await response.json();
        
        // 验证返回的数据格式
        if (!Array.isArray(versions)) {
            console.error('返回的版本选项数据格式错误:', versions);
            const select = document.getElementById('versionSelect');
            if (select) {
                select.innerHTML = '<option value="">暂无版本</option>';
            }
            return;
        }
        
        const select = document.getElementById('versionSelect');
        if (select) {
            if (versions.length === 0) {
                select.innerHTML = '<option value="">暂无版本</option>';
            } else {
                // 删除"全部版本"选项，只显示具体版本
                select.innerHTML = versions.map(v => `<option value="${v.id}">${v.versionName}</option>`).join('');
                // 默认选择第一个版本
                if (versions.length > 0) {
                    select.value = versions[0].id;
                }
            }
        }
    } catch (error) {
        console.error('加载版本选项失败：', error);
        // 发生错误时也应确保下拉框显示默认选项
        const select = document.getElementById('versionSelect');
        if (select) {
            select.innerHTML = '<option value="">暂无版本</option>';
        }
    }
}

// 导入定额到版本
async function importQuotasToVersion() {
    if (!currentViewingVersionId) {
        alert('请先选择版本');
        return;
    }
    
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = '.xlsx,.xls';
    fileInput.onchange = async function() {
        if (!fileInput.files[0]) return;
        
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        
        try {
            const response = await fetch(API_BASE + '/versions/' + currentViewingVersionId + '/import-quotas', {
                method: 'POST',
                body: formData
            });
            
            const result = await response.json();
            
            if (result.success) {
                alert('导入成功，共导入 ' + result.count + ' 条企业定额数据');
                loadQuotas();
            } else {
                alert('导入失败：' + result.message);
            }
        } catch (error) {
            console.error('导入失败：', error);
            alert('导入失败：' + error.message);
        }
    };
    
    fileInput.click();
}

// 确保所有函数都在全局作用域（防止作用域问题导致按钮无法点击）
if (typeof window !== 'undefined') {
    window.switchNav = switchNav;
    window.loadVersions = loadVersions;
    window.openVersionEditModal = openVersionEditModal;
    window.closeVersionEditModal = closeVersionEditModal;
    window.saveVersion = saveVersion;
    window.deleteVersion = deleteVersion;
    window.batchDeleteVersions = batchDeleteVersions;
    window.toggleVersionSelection = toggleVersionSelection;
    window.toggleSelectAllVersions = toggleSelectAllVersions;
    window.viewVersionDetail = viewVersionDetail;
    window.backToVersionList = backToVersionList;
    window.importQuotasToVersion = importQuotasToVersion;
    window.loadVersionOptions = loadVersionOptions;
    
    // 用户管理相关函数
    window.loadUsers = loadUsers;
    window.openUserEditModal = openUserEditModal;
    window.closeUserEditModal = closeUserEditModal;
    window.saveUser = saveUser;
    window.deleteUser = deleteUser;
    window.updateUserStatus = updateUserStatus;
    window.openChangePasswordModal = openChangePasswordModal;
    window.closeChangePasswordModal = closeChangePasswordModal;
    window.savePassword = savePassword;
}

// ==================== 用户管理相关函数 ====================

let currentEditUserId = null;
let currentUserRole = null; // 当前用户角色

// 获取当前用户信息并检查角色
async function checkCurrentUserRole() {
    try {
        const response = await fetch('/api/auth/current');
        const result = await response.json();
        
        if (result.success) {
            currentUserRole = result.user.role || 'user';
            window.currentUserId = result.user.id; // 保存当前用户ID
            // 根据角色控制界面元素
            controlUserInterfaceByRole();
        }
    } catch (error) {
        console.error('获取用户信息失败：', error);
    }
}

// 根据用户角色控制界面元素
function controlUserInterfaceByRole() {
    // 控制用户管理页面的显示 - 所有用户都可以看到用户管理（但功能不同）
    const userManagementNav = document.querySelector('.nav-item[onclick*="switchNav(\'users\')"]');
    if (userManagementNav) {
        userManagementNav.style.display = 'flex'; // 所有用户都可以看到用户管理
    }
    
    // 控制用户管理页面的按钮
    const addNewUserBtn = document.querySelector('#usersTab .toolbar .btn-primary[onclick*="openUserEditModal"]');
    if (addNewUserBtn) {
        if (currentUserRole === 'admin') {
            addNewUserBtn.style.display = 'inline-block';
        } else {
            addNewUserBtn.style.display = 'none'; // 普通用户看不到新增用户按钮
        }
    }
    
    // 控制刷新列表按钮 - 所有用户都可以刷新
    const refreshBtn = document.querySelector('#usersTab .toolbar .btn-primary[onclick*="loadUsers"]');
    if (refreshBtn) {
        refreshBtn.style.display = 'inline-block';
    }
    
    // 控制修改密码按钮 - 所有用户都可以修改密码
    const changePasswordBtn = document.querySelector('#usersTab .toolbar .btn-primary[onclick*="openChangePasswordModal"]');
    if (changePasswordBtn) {
        changePasswordBtn.style.display = 'inline-block';
    }
}

// 加载用户列表
async function loadUsers() {
    try {
        // 确保用户角色已加载
        await checkCurrentUserRole();
        
        const response = await fetch('/api/user/list');
        
        // 检查响应状态
        if (!response.ok) {
            console.error('加载用户列表失败，状态码:', response.status);
            // 即使API返回错误，也要确保表格显示空状态而不是空白
            renderUsersTable([]);
            return;
        }
        
        const result = await response.json();
        
        if (result.success) {
            // 验证返回的数据格式
            if (!Array.isArray(result.users)) {
                console.error('返回的用户数据格式错误:', result.users);
                renderUsersTable([]);
                return;
            }
            renderUsersTable(result.users);
        } else {
            console.error('加载用户列表失败：', result.message);
            // 即使后端返回错误，也要确保表格显示空状态而不是空白
            renderUsersTable([]);
        }
    } catch (error) {
        console.error('加载用户列表失败：', error);
        // 发生错误时也应确保显示空状态
        renderUsersTable([]);
    }
}



// 渲染用户表格
function renderUsersTable(users) {
    const tbody = document.getElementById('usersTableBody');
    if (!tbody) return;
    
    // 根据经验教训，在渲染前显式设置tbody的显示属性
    if (tbody) {
        tbody.style.display = '';
        tbody.style.visibility = 'visible';
    }
    
    if (users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="empty-message">暂无用户数据</td></tr>';
        return;
    }
    
    tbody.innerHTML = users.map((user, index) => {
        const statusClass = user.status === 1 ? 'status-matched' : 'status-unmatched';
        const statusText = user.status === 1 ? '启用' : '禁用';
        const createTime = user.createTime ? new Date(user.createTime).toLocaleString('zh-CN') : '';
        
        // 根据当前用户角色和被显示用户的角色来决定操作按钮
        let actionButtons = '';
        if (currentUserRole === 'admin') {
            // 管理员可以看到所有操作按钮
            actionButtons = `
                <button onclick="openUserEditModal(${user.id})" class="btn-primary" style="padding: 5px 10px; margin-right: 5px;">编辑</button>
                <button onclick="updateUserStatus(${user.id}, ${user.status === 1 ? 0 : 1})" class="btn-primary" style="padding: 5px 10px; margin-right: 5px; background: ${user.status === 1 ? '#ff9800' : '#4caf50'};">
                    ${user.status === 1 ? '禁用' : '启用'}
                </button>
                <button onclick="deleteUser(${user.id})" class="btn-danger" style="padding: 5px 10px;">删除</button>
            `;
        } else {
            // 普通用户没有任何编辑功能
            actionButtons = `<span style="color: #999; font-size: 12px;">无权限</span>`;
        }
        
        return `
            <tr>
                <td style="text-align: center; font-weight: bold;">${index + 1}</td>
                <td>${user.username || ''}</td>
                <td>${user.realName || ''}</td>
                <td>${user.email || ''}</td>
                <td><span class="status-badge ${statusClass}">${statusText}</span></td>
                <td>${createTime}</td>
                <td>
                    ${actionButtons}
                </td>
            </tr>
        `;
    }).join('');
    
    // 根据经验教训，强制重排确保表格正确渲染
    setTimeout(() => {
        if (tbody) {
            tbody.offsetHeight; // 触发重排
            tbody.style.transform = 'translateZ(0)'; // 启用硬件加速
            setTimeout(() => {
                tbody.style.transform = ''; // 移除临时样式
            }, 10);
        }
    }, 100);
}

// 获取当前登录用户ID
function getCurrentUserId() {
    // 从全局变量或存储中获取当前用户ID
    // 如果没有全局变量，可以通过检查当前用户列表中的信息来确定
    if (window.currentUserId) {
        return window.currentUserId;
    }
    
    // 尝试从用户列表中获取当前用户ID
    const usersTableBody = document.getElementById('usersTableBody');
    if (usersTableBody) {
        // 如果用户列表只有一条记录且是当前登录用户，可以尝试获取
        const rows = usersTableBody.querySelectorAll('tr');
        if (rows.length === 1) {
            // 这种情况下无法准确判断，需要后端提供专门的接口
            return null;
        }
    }
    
    return null;
}

// 从后端获取当前用户信息
async function fetchCurrentUserDetails() {
    try {
        const response = await fetch('/api/auth/current');
        const result = await response.json();
        
        if (result.success && result.user) {
            window.currentUserId = result.user.id;
            return result.user;
        }
    } catch (error) {
        console.error('获取当前用户信息失败：', error);
    }
    
    return null;
}

// 打开用户编辑模态框
function openUserEditModal(userId) {
    // 检查权限 - 只有管理员可以编辑用户
    if (currentUserRole !== 'admin') {
        alert('无权限编辑用户信息');
        return;
    }
    
    currentEditUserId = userId;
    const modal = document.getElementById('userEditModal');
    const title = document.getElementById('userEditModalTitle');
    const passwordGroup = document.getElementById('passwordGroup');
    
    if (userId) {
        title.textContent = '编辑用户';
        // 管理员编辑其他用户时隐藏密码字段（密码通过重置密码功能修改）
        passwordGroup.style.display = 'none';
        // 加载用户信息
        loadUserInfo(userId);
    } else {
        // 只有管理员可以新增用户
        if (currentUserRole !== 'admin') {
            alert('无权限新增用户');
            return;
        }
        title.textContent = '新增用户';
        passwordGroup.style.display = 'block';
        document.getElementById('editUsername').value = '';
        document.getElementById('editPassword').value = '';
        document.getElementById('editRealName').value = '';
        document.getElementById('editEmail').value = '';
    }
    
    modal.style.display = 'block';
}

// 加载用户信息
async function loadUserInfo(userId) {
    try {
        // 只有管理员可以加载用户信息
        if (currentUserRole !== 'admin') {
            console.error('普通用户无权限加载用户信息');
            return;
        }
        
        // 直接获取指定用户的信息，而不是从列表中查找
        const response = await fetch(`/api/user/list`);
        const result = await response.json();
        
        if (result.success) {
            // 管理员可以查看所有用户信息
            if (currentUserRole === 'admin') {
                const user = result.users.find(u => u.id === userId);
                if (user) {
                    document.getElementById('editUsername').value = user.username || '';
                    document.getElementById('editRealName').value = user.realName || '';
                    document.getElementById('editEmail').value = user.email || '';
                }
            }
        } else {
            console.error('获取用户列表失败：', result.message);
        }
    } catch (error) {
        console.error('加载用户信息失败：', error);
    }
}

// 关闭用户编辑模态框
function closeUserEditModal() {
    document.getElementById('userEditModal').style.display = 'none';
    currentEditUserId = null;
}

// 保存用户
async function saveUser() {
    // 检查权限 - 只有管理员可以保存用户
    if (currentUserRole !== 'admin') {
        alert('无权限保存用户信息');
        return;
    }
    
    const username = document.getElementById('editUsername').value.trim();
    const password = document.getElementById('editPassword').value;
    const realName = document.getElementById('editRealName').value.trim();
    const email = document.getElementById('editEmail').value.trim();
    
    if (!username) {
        alert('用户名不能为空');
        return;
    }
    
    if (!currentEditUserId && !password) {
        alert('密码不能为空');
        return;
    }
    
    if (password && password.length < 6) {
        alert('密码长度不能少于6位');
        return;
    }
    
    try {
        if (currentEditUserId) {
            // 更新用户 - 管理员可以更新任意用户
            const formData = new URLSearchParams();
            
            // 管理员可以更新用户名
            if (username) {
                formData.append('username', username);
            }
            if (realName !== null && realName !== undefined) {
                formData.append('realName', realName);
            }
            if (email !== null && email !== undefined) {
                formData.append('email', email);
            }
            
            const response = await fetch(`/api/user/${currentEditUserId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: formData.toString()
            });
            
            const result = await response.json();
            if (result.success) {
                alert('更新成功！');
                closeUserEditModal();
                loadUsers();
            } else {
                alert('更新失败：' + result.message);
            }
        } else {
            // 新增用户 - 只有管理员可以新增
            const formData = new URLSearchParams();
            formData.append('username', username);
            formData.append('password', password);
            if (realName) formData.append('realName', realName);
            if (email) formData.append('email', email);
            
            const response = await fetch('/api/user/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: formData.toString()
            });
            
            const result = await response.json();
            if (result.success) {
                alert('新增成功！');
                closeUserEditModal();
                loadUsers();
            } else {
                alert('新增失败：' + result.message);
            }
        }
    } catch (error) {
        console.error('保存用户失败：', error);
        alert('保存失败：' + error.message);
    }
}

// 删除用户
async function deleteUser(userId) {
    // 检查权限
    if (currentUserRole !== 'admin') {
        alert('无权限删除用户');
        return;
    }
    
    if (!confirm('确定要删除这个用户吗？')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/user/${userId}`, {
            method: 'DELETE'
        });
        
        const result = await response.json();
        if (result.success) {
            alert('删除成功！');
            loadUsers();
        } else {
            alert('删除失败：' + result.message);
        }
    } catch (error) {
        console.error('删除用户失败：', error);
        alert('删除失败：' + error.message);
    }
}

// 更新用户状态
async function updateUserStatus(userId, status) {
    // 检查权限
    if (currentUserRole !== 'admin') {
        alert('无权限操作用户状态');
        return;
    }
    
    const statusText = status === 1 ? '启用' : '禁用';
    if (!confirm(`确定要${statusText}这个用户吗？`)) {
        return;
    }
    
    try {
        const formData = new URLSearchParams();
        formData.append('status', status);
        
        const response = await fetch(`/api/user/${userId}/status`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: formData.toString()
        });
        
        const result = await response.json();
        if (result.success) {
            alert(`${statusText}成功！`);
            loadUsers();
        } else {
            alert(`${statusText}失败：` + result.message);
        }
    } catch (error) {
        console.error('更新用户状态失败：', error);
        alert('操作失败：' + error.message);
    }
}

// 打开修改密码模态框
function openChangePasswordModal() {
    document.getElementById('changePasswordModal').style.display = 'block';
    document.getElementById('oldPassword').value = '';
    document.getElementById('newPassword').value = '';
    document.getElementById('confirmPassword').value = '';
    document.getElementById('passwordErrorMessage').textContent = '';
}

// 关闭修改密码模态框
function closeChangePasswordModal() {
    document.getElementById('changePasswordModal').style.display = 'none';
}

// 保存密码
async function savePassword() {
    const oldPassword = document.getElementById('oldPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    const errorMessage = document.getElementById('passwordErrorMessage');
    
    if (!oldPassword || !newPassword || !confirmPassword) {
        errorMessage.textContent = '请填写所有字段';
        return;
    }
    
    if (newPassword.length < 6) {
        errorMessage.textContent = '新密码长度不能少于6位';
        return;
    }
    
    if (newPassword !== confirmPassword) {
        errorMessage.textContent = '两次输入的密码不一致';
        return;
    }
    
    try {
        const formData = new URLSearchParams();
        formData.append('oldPassword', oldPassword);
        formData.append('newPassword', newPassword);
        
        const response = await fetch('/api/user/change-password', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: formData.toString()
        });
        
        const result = await response.json();
        if (result.success) {
            alert('密码修改成功！');
            closeChangePasswordModal();
        } else {
            errorMessage.textContent = result.message || '修改密码失败';
        }
    } catch (error) {
        console.error('修改密码失败：', error);
        errorMessage.textContent = '修改密码失败：' + error.message;
    }
}

// ==================== 文档生成模块 ====================

// 生成文档
// 添加占位符行
function addPlaceholderRow() {
    const container = document.getElementById('placeholdersContainer');
    const row = document.createElement('div');
    row.className = 'placeholder-row';
    row.style.cssText = 'display: flex; align-items: center; gap: 10px; margin-bottom: 12px; padding: 10px; background: white; border-radius: 4px; border: 1px solid #e0e0e0;';
    row.innerHTML = `
        <span style="color: #1976d2; font-weight: 500; white-space: nowrap;">\${</span>
        <input type="text" class="placeholder-name" placeholder="占位符名称" style="flex: 1; padding: 8px; border: 1px solid #bbdefb; border-radius: 4px; font-size: 14px;">
        <span style="color: #1976d2; font-weight: 500; white-space: nowrap;">}=</span>
        <input type="text" class="placeholder-value" placeholder="替换内容" style="flex: 2; padding: 8px; border: 1px solid #bbdefb; border-radius: 4px; font-size: 14px;">
        <button onclick="removePlaceholderRow(this)" class="btn-danger" style="padding: 6px 12px; font-size: 12px; min-width: 60px;">删除</button>
    `;
    container.appendChild(row);
}

// 删除占位符行
function removePlaceholderRow(button) {
    const row = button.closest('.placeholder-row');
    if (row) {
        row.remove();
    }
}

// 从输入框收集替换内容并转换为文本格式
function collectReplacements() {
    const rows = document.querySelectorAll('.placeholder-row');
    const replacements = [];
    
    rows.forEach(row => {
        const nameInput = row.querySelector('.placeholder-name');
        const valueInput = row.querySelector('.placeholder-value');
        const name = nameInput ? nameInput.value.trim() : '';
        const value = valueInput ? valueInput.value.trim() : '';
        
        if (name && value) {
            replacements.push(`\${${name}}=${value}`);
        }
    });
    
    return replacements.join('\n');
}

async function generateDocument() {
    const templateFile = document.getElementById('templateFile').files[0];
    const replacementsText = collectReplacements();
    const statusSpan = document.getElementById('documentStatus');

    // 验证模板文件
    if (!templateFile) {
        statusSpan.textContent = '请选择模板文件';
        statusSpan.style.color = '#f44336';
        return;
    }

    // 验证替换内容
    if (!replacementsText) {
        statusSpan.textContent = '请至少添加一个占位符和替换内容';
        statusSpan.style.color = '#f44336';
        return;
    }

    statusSpan.textContent = '正在生成文档...';
    statusSpan.style.color = '#2196F3';

    try {
        const formData = new FormData();
        formData.append('template', templateFile);
        formData.append('replacements', replacementsText);

        const response = await fetch('/api/document/generate', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            // 获取文件blob
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            
            // 从响应头获取文件名，如果没有则使用默认名称
            const contentDisposition = response.headers.get('Content-Disposition');
            let fileName = 'generated_document.docx';
            if (contentDisposition) {
                // 尝试多种格式解析文件名
                let fileNameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                if (fileNameMatch && fileNameMatch[1]) {
                    fileName = fileNameMatch[1].replace(/['"]/g, ''); // 移除引号
                } else {
                    // 备用解析方式
                    fileNameMatch = contentDisposition.match(/filename="?([^"]+)"?/);
                    if (fileNameMatch && fileNameMatch[1]) {
                        fileName = fileNameMatch[1];
                    }
                }
            }
            
            // 确保文件名以.docx结尾
            if (!fileName.toLowerCase().endsWith('.docx')) {
                // 如果文件名不包含扩展名或扩展名不对，添加.docx
                const nameWithoutExt = fileName.replace(/\.[^.]*$/, '');
                fileName = nameWithoutExt + '.docx';
            }
            
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            statusSpan.textContent = '文档生成成功！';
            statusSpan.style.color = '#4CAF50';
        } else {
            const result = await response.json();
            statusSpan.textContent = result.message || '文档生成失败';
            statusSpan.style.color = '#f44336';
        }
    } catch (error) {
        console.error('生成文档失败：', error);
        statusSpan.textContent = '生成文档失败：' + error.message;
        statusSpan.style.color = '#f44336';
    }
}

// 填充默认替换内容
function fillDefaultReplacements() {
    const defaultData = [
        { name: '采购方式', value: '公开选择' },
        { name: '评审办法', value: '综合评价法（价格分93分，商务分2分，技术分5分）' },
        { name: '施工范围', value: '本项目2025年度通车路段劳务施工' },
        { name: '项目名称', value: '仙居至庆元公路松阳县水南至枫坪段抽蓄影响改线工程第JD1标段机电施工' },
        { name: '公开选择日期', value: '2025年09月' },
        { name: '议题日期', value: '2025年09月26日' },
        { name: '项目概况', value: '本项目起点接原仙居至庆元公路松阳县水南至枫坪段工程，起点桩号K13+686.586，终点与原仙居至庆元公路松阳县水南至枫坪段工程相接，终点桩号 K20+388.378，全长 6.702 公里，主要施工内容为项目实施范围内包括周岭根1号隧道、周岭根2号隧道的通信系统、监控系统、专用软件、通风、消防系统、供配电照明系统、防雷接地系统、管道工程等设施的设备安装、调试、施工等' },
        { name: '建设地点', value: '浙江省松阳县' },
        { name: '选择内容和范围', value: '项目施工图设计所含范围内的全部施工内容（详见工程量清单），包含设备安装及调试、基础制作及施工、封道费（不包含第三方封道措施费）、材料现场验收、转运、装卸、二次装运、吊装、保管及看护（通车前巡查）、电缆沟垃圾清理、辅材、维护修复、防腐、擦拭等工作、技术资料和税金等内容。特殊说明，安装内容中已经包括施工前期勘察、可能发生的垃圾清扫、渣土外运、电缆沟盖板揭盖（反复揭盖两次）、钢筋及混凝土试块检测费、设备单机及联网调试、安全文明措施及施工配合等内容，上述各类工作内容已经包括在响应报价总价中，不再单独计量' },
        { name: '标段数', value: '1' },
        { name: '标段1名称', value: '仙居至庆元公路松阳县水南至枫坪段抽蓄影响改线工程第JD1标段设备安装、调试等劳务施工工作' },
        { name: '标段1金额', value: '151.13' },
        { name: '控制价', value: '151.13' },
        { name: '合同工期', value: '施工工期365个日历天，含试运行期180个日历天，缺陷责任期12个月，保修期12个月，LED灯5年' },
        { name: '业绩起始时间', value: '2022年7月1日' },
        { name: '完成业绩数量', value: '1' },
        { name: '业绩', value: '120' },
        { name: '允许中标数', value: '1' },
        { name: '已完成项目数', value: '1' },
        { name: '选择文件下载开始时间', value: '2025年10月09日09时30分' },
        { name: '选择文件下载截至时间', value: '2025年10月14日9时30分' },
        { name: '提交疑问截止日', value: '2025年10月15日9时30分' },
        { name: '答疑截止日', value: '2025年10月16日9时30分' },
        { name: '响应文件递交的截止时间', value: '2025年10月17日14时30分' },
        { name: '联系人', value: '蔡工' },
        { name: '类似项目', value: '公路机电工程劳务施工' },
        { name: '保证金金额', value: '20000.00' }
    ];

    // 清空现有行
    const container = document.getElementById('placeholdersContainer');
    container.innerHTML = '';

    // 添加默认数据行
    defaultData.forEach(item => {
        const row = document.createElement('div');
        row.className = 'placeholder-row';
        row.style.cssText = 'display: flex; align-items: center; gap: 10px; margin-bottom: 12px; padding: 10px; background: white; border-radius: 4px; border: 1px solid #e0e0e0;';
        row.innerHTML = `
            <span style="color: #1976d2; font-weight: 500; white-space: nowrap;">\${</span>
            <input type="text" class="placeholder-name" placeholder="占位符名称" value="${item.name}" style="flex: 1; padding: 8px; border: 1px solid #bbdefb; border-radius: 4px; font-size: 14px;">
            <span style="color: #1976d2; font-weight: 500; white-space: nowrap;">}=</span>
            <input type="text" class="placeholder-value" placeholder="替换内容" value="${item.value}" style="flex: 2; padding: 8px; border: 1px solid #bbdefb; border-radius: 4px; font-size: 14px;">
            <button onclick="removePlaceholderRow(this)" class="btn-danger" style="padding: 6px 12px; font-size: 12px; min-width: 60px;">删除</button>
        `;
        container.appendChild(row);
    });
}

// ==================== 模板管理功能 ====================

// 加载文档模板列表
async function loadDocumentTemplates() {
    try {
        const response = await fetch('/api/document/template/list');
        
        // 检查响应状态
        if (!response.ok) {
            console.error('加载文档模板失败，状态码:', response.status);
            // 即使API返回错误，也要确保下拉框显示默认选项
            const select = document.getElementById('documentTemplateSelect');
            if (select) {
                select.innerHTML = '<option value="">请选择服务器模板</option>';
            }
            return;
        }
        
        const templates = await response.json();
        
        // 验证返回的数据格式
        if (!Array.isArray(templates)) {
            console.error('返回的文档模板数据格式错误:', templates);
            const select = document.getElementById('documentTemplateSelect');
            if (select) {
                select.innerHTML = '<option value="">请选择服务器模板</option>';
            }
            return;
        }
        
        const select = document.getElementById('documentTemplateSelect');
        if (select) {
            select.innerHTML = '<option value="">请选择服务器模板</option>';
            templates.forEach(template => {
                const option = document.createElement('option');
                option.value = template.id;
                option.textContent = template.templateName + (template.description ? ' - ' + template.description : '');
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('加载文档模板失败：', error);
        // 发生错误时也应确保下拉框显示默认选项
        const select = document.getElementById('documentTemplateSelect');
        if (select) {
            select.innerHTML = '<option value="">请选择服务器模板</option>';
        }
    }
}

// 文档模板选择变化
function onDocumentTemplateSelect() {
    const select = document.getElementById('documentTemplateSelect');
    const fileInput = document.getElementById('templateFile');
    if (select.value) {
        fileInput.value = ''; // 清空文件选择
    }
}

// 打开文档模板管理模态框
function openDocumentTemplateModal() {
    document.getElementById('documentTemplateModal').style.display = 'block';
    loadDocumentTemplateList();
}

// 关闭文档模板管理模态框
function closeDocumentTemplateModal() {
    document.getElementById('documentTemplateModal').style.display = 'none';
}

// 加载文档模板列表
async function loadDocumentTemplateList() {
    try {
        const response = await fetch('/api/document/template/list');
        if (response.ok) {
            const templates = await response.json();
            const tbody = document.getElementById('documentTemplateList');
            if (templates.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="empty-message">暂无模板，请先上传</td></tr>';
            } else {
                tbody.innerHTML = templates.map(template => {
                    const fileSize = template.fileSize ? (template.fileSize / 1024).toFixed(2) + ' KB' : '-';
                    const createdAt = template.createdAt ? new Date(template.createdAt).toLocaleString('zh-CN') : '-';
                    return `
                        <tr>
                            <td>${template.templateName}</td>
                            <td>${template.fileName}</td>
                            <td>${fileSize}</td>
                            <td>${createdAt}</td>
                            <td>
                                <button onclick="selectDocumentTemplate(${template.id})" class="btn-primary" style="padding: 4px 8px; font-size: 12px; margin-right: 5px;">选择</button>
                                <button onclick="deleteDocumentTemplate(${template.id})" class="btn-danger" style="padding: 4px 8px; font-size: 12px;">删除</button>
                            </td>
                        </tr>
                    `;
                }).join('');
            }
        }
    } catch (error) {
        console.error('加载文档模板列表失败：', error);
    }
}

// 上传文档模板
async function uploadDocumentTemplate() {
    const templateName = document.getElementById('newDocumentTemplateName').value.trim();
    const description = document.getElementById('newDocumentTemplateDescription').value.trim();
    const fileInput = document.getElementById('newDocumentTemplateFile');
    const file = fileInput.files[0];
    
    if (!templateName) {
        alert('请输入模板名称');
        return;
    }
    
    if (!file) {
        alert('请选择文件');
        return;
    }
    
    const formData = new FormData();
    formData.append('file', file);
    formData.append('templateName', templateName);
    if (description) {
        formData.append('description', description);
    }
    
    try {
        const response = await fetch('/api/document/template/upload', {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        if (result.success) {
            alert('上传成功');
            document.getElementById('newDocumentTemplateName').value = '';
            document.getElementById('newDocumentTemplateDescription').value = '';
            fileInput.value = '';
            loadDocumentTemplateList();
            loadDocumentTemplates();
        } else {
            alert('上传失败：' + result.message);
        }
    } catch (error) {
        console.error('上传文档模板失败：', error);
        alert('上传失败：' + error.message);
    }
}

// 选择文档模板
function selectDocumentTemplate(templateId) {
    const select = document.getElementById('documentTemplateSelect');
    select.value = templateId;
    onDocumentTemplateSelect();
    closeDocumentTemplateModal();
}

// 删除文档模板
async function deleteDocumentTemplate(templateId) {
    if (!confirm('确定要删除这个模板吗？')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/document/template/${templateId}`, {
            method: 'DELETE'
        });
        
        const result = await response.json();
        if (result.success) {
            alert('删除成功');
            loadDocumentTemplateList();
            loadDocumentTemplates();
        } else {
            alert('删除失败：' + result.message);
        }
    } catch (error) {
        console.error('删除文档模板失败：', error);
        alert('删除失败：' + error.message);
    }
}

// 加载替换内容模板列表
async function loadReplacementTemplates() {
    try {
        const response = await fetch('/api/document/replacement-template/list');
        
        // 检查响应状态
        if (!response.ok) {
            console.error('加载替换内容模板失败，状态码:', response.status);
            // 即使API返回错误，也要确保下拉框显示默认选项
            const select = document.getElementById('replacementTemplateSelect');
            if (select) {
                select.innerHTML = '<option value="">选择填充模板</option>';
            }
            return;
        }
        
        const templates = await response.json();
        
        // 验证返回的数据格式
        if (!Array.isArray(templates)) {
            console.error('返回的替换内容模板数据格式错误:', templates);
            const select = document.getElementById('replacementTemplateSelect');
            if (select) {
                select.innerHTML = '<option value="">选择填充模板</option>';
            }
            return;
        }
        
        const select = document.getElementById('replacementTemplateSelect');
        if (select) {
            select.innerHTML = '<option value="">选择填充模板</option>';
            templates.forEach(template => {
                const option = document.createElement('option');
                option.value = template.id;
                option.textContent = template.templateName + (template.description ? ' - ' + template.description : '');
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('加载替换内容模板失败：', error);
        // 发生错误时也应确保下拉框显示默认选项
        const select = document.getElementById('replacementTemplateSelect');
        if (select) {
            select.innerHTML = '<option value="">选择填充模板</option>';
        }
    }
}

// 替换内容模板选择变化
function onReplacementTemplateSelect() {
    const select = document.getElementById('replacementTemplateSelect');
    const deleteBtn = document.getElementById('deleteReplacementTemplateBtn');
    if (select.value) {
        deleteBtn.style.display = 'inline-block';
        loadReplacementTemplate();
    } else {
        deleteBtn.style.display = 'none';
    }
}

// 加载替换内容模板
async function loadReplacementTemplate() {
    const select = document.getElementById('replacementTemplateSelect');
    const templateId = select.value;
    if (!templateId) {
        return;
    }
    
    try {
        const response = await fetch(`/api/document/replacement-template/${templateId}`);
        if (response.ok) {
            const template = await response.json();
            // 解析replacements（JSON格式）
            try {
                const replacements = JSON.parse(template.replacements);
                // 清空现有行
                const container = document.getElementById('placeholdersContainer');
                container.innerHTML = '';
                
                // 添加模板数据行
                Object.keys(replacements).forEach(name => {
                    const row = document.createElement('div');
                    row.className = 'placeholder-row';
                    row.style.cssText = 'display: flex; align-items: center; gap: 10px; margin-bottom: 12px; padding: 10px; background: white; border-radius: 4px; border: 1px solid #e0e0e0;';
                    
                    // 转义HTML特殊字符
                    const escapedName = name.replace(/"/g, '&quot;').replace(/'/g, '&#39;');
                    const escapedValue = replacements[name].replace(/"/g, '&quot;').replace(/'/g, '&#39;');
                    
                    row.innerHTML = `
                        <span style="color: #1976d2; font-weight: 500; white-space: nowrap;">\${</span>
                        <input type="text" class="placeholder-name" placeholder="占位符名称" value="${escapedName}" style="flex: 1; padding: 8px; border: 1px solid #bbdefb; border-radius: 4px; font-size: 14px;">
                        <span style="color: #1976d2; font-weight: 500; white-space: nowrap;">}=</span>
                        <input type="text" class="placeholder-value" placeholder="替换内容" value="${escapedValue}" style="flex: 2; padding: 8px; border: 1px solid #bbdefb; border-radius: 4px; font-size: 14px;">
                        <button onclick="removePlaceholderRow(this)" class="btn-danger" style="padding: 6px 12px; font-size: 12px; min-width: 60px;">删除</button>
                    `;
                    container.appendChild(row);
                });
            } catch (e) {
                console.error('解析模板数据失败：', e);
                alert('模板数据格式错误');
            }
        }
    } catch (error) {
        console.error('加载替换内容模板失败：', error);
        alert('加载模板失败：' + error.message);
    }
}

// 保存替换内容模板
function saveReplacementTemplate() {
    document.getElementById('saveReplacementTemplateModal').style.display = 'block';
    document.getElementById('replacementTemplateName').value = '';
    document.getElementById('replacementTemplateDescription').value = '';
}

// 关闭保存替换内容模板模态框
function closeSaveReplacementTemplateModal() {
    document.getElementById('saveReplacementTemplateModal').style.display = 'none';
}

// 确认保存替换内容模板
async function confirmSaveReplacementTemplate() {
    const templateName = document.getElementById('replacementTemplateName').value.trim();
    const description = document.getElementById('replacementTemplateDescription').value.trim();
    
    if (!templateName) {
        alert('请输入模板名称');
        return;
    }
    
    // 收集当前替换内容
    const rows = document.querySelectorAll('.placeholder-row');
    const replacements = {};
    rows.forEach(row => {
        const nameInput = row.querySelector('.placeholder-name');
        const valueInput = row.querySelector('.placeholder-value');
        const name = nameInput ? nameInput.value.trim() : '';
        const value = valueInput ? valueInput.value.trim() : '';
        if (name && value) {
            replacements[name] = value;
        }
    });
    
    if (Object.keys(replacements).length === 0) {
        alert('请至少添加一个占位符和替换内容');
        return;
    }
    
    const formData = new FormData();
    formData.append('templateName', templateName);
    formData.append('replacements', JSON.stringify(replacements));
    if (description) {
        formData.append('description', description);
    }
    
    try {
        const response = await fetch('/api/document/replacement-template/save', {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        if (result.success) {
            alert('保存成功');
            closeSaveReplacementTemplateModal();
            loadReplacementTemplates();
        } else {
            alert('保存失败：' + result.message);
        }
    } catch (error) {
        console.error('保存替换内容模板失败：', error);
        alert('保存失败：' + error.message);
    }
}

// 修改generateDocument函数以支持服务器模板
async function generateDocument() {
    const templateFileInput = document.getElementById('templateFile');
    const templateSelect = document.getElementById('documentTemplateSelect');
    const templateId = templateSelect.value;
    const templateFile = templateFileInput.files[0];
    const replacementsText = collectReplacements();
    const statusSpan = document.getElementById('documentStatus');

    // 验证模板文件
    if (!templateId && !templateFile) {
        statusSpan.textContent = '请选择模板文件或从服务器选择模板';
        statusSpan.style.color = '#f44336';
        return;
    }

    // 验证替换内容
    if (!replacementsText) {
        statusSpan.textContent = '请至少添加一个占位符和替换内容';
        statusSpan.style.color = '#f44336';
        return;
    }

    statusSpan.textContent = '正在生成文档...';
    statusSpan.style.color = '#2196F3';

    try {
        const formData = new FormData();
        formData.append('replacements', replacementsText);
        
        let response;
        if (templateId) {
            // 使用服务器模板
            formData.append('templateId', templateId);
            response = await fetch('/api/document/generate-from-template', {
                method: 'POST',
                body: formData
            });
        } else {
            // 使用上传的文件
            formData.append('template', templateFile);
            response = await fetch('/api/document/generate', {
                method: 'POST',
                body: formData
            });
        }

        if (response.ok) {
            // 获取文件blob
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            
            // 从响应头获取文件名
            const contentDisposition = response.headers.get('Content-Disposition');
            let fileName = 'generated_document.docx';
            if (contentDisposition) {
                let fileNameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                if (fileNameMatch && fileNameMatch[1]) {
                    fileName = fileNameMatch[1].replace(/['"]/g, '');
                } else {
                    fileNameMatch = contentDisposition.match(/filename="?([^"]+)"?/);
                    if (fileNameMatch && fileNameMatch[1]) {
                        fileName = fileNameMatch[1];
                    }
                }
            }
            
            // 确保文件名以.docx结尾
            if (!fileName.toLowerCase().endsWith('.docx')) {
                const nameWithoutExt = fileName.replace(/\.[^.]*$/, '');
                fileName = nameWithoutExt + '.docx';
            }
            
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            statusSpan.textContent = '文档生成成功！';
            statusSpan.style.color = '#4CAF50';
        } else {
            const result = await response.json();
            statusSpan.textContent = result.message || '文档生成失败';
            statusSpan.style.color = '#f44336';
        }
    } catch (error) {
        console.error('生成文档失败：', error);
        statusSpan.textContent = '生成文档失败：' + error.message;
        statusSpan.style.color = '#f44336';
    }
}

// 确保文档生成函数在全局作用域
if (typeof window !== 'undefined') {
    window.generateDocument = generateDocument;
    window.fillDefaultReplacements = fillDefaultReplacements;
    window.addPlaceholderRow = addPlaceholderRow;
    window.removePlaceholderRow = removePlaceholderRow;
    window.loadDocumentTemplates = loadDocumentTemplates;
    window.onDocumentTemplateSelect = onDocumentTemplateSelect;
    window.openDocumentTemplateModal = openDocumentTemplateModal;
    window.closeDocumentTemplateModal = closeDocumentTemplateModal;
    window.uploadDocumentTemplate = uploadDocumentTemplate;
    window.selectDocumentTemplate = selectDocumentTemplate;
    window.deleteDocumentTemplate = deleteDocumentTemplate;
    window.loadReplacementTemplates = loadReplacementTemplates;
    window.loadReplacementTemplate = loadReplacementTemplate;
    window.onReplacementTemplateSelect = onReplacementTemplateSelect;
    window.deleteReplacementTemplate = deleteReplacementTemplate;
    window.saveReplacementTemplate = saveReplacementTemplate;
    window.closeSaveReplacementTemplateModal = closeSaveReplacementTemplateModal;
    window.confirmSaveReplacementTemplate = confirmSaveReplacementTemplate;
    window.updateTemplateFileLabel = updateTemplateFileLabel;
}

// 修复表格显示问题（确保 overflow 样式正确应用，新版CSS已从根本上解决）
// 保留供外部调用兼容
function fixTableVisibility() {
    var containers = document.querySelectorAll('.table-container');
    for (var i = 0; i < containers.length; i++) {
        containers[i].style.overflowY = 'auto';
        containers[i].style.overflowX = 'auto';
    }
}

// 将函数添加到全局作用域（向后兼容）
if (typeof window !== 'undefined') {
    window.fixTableVisibility = fixTableVisibility;
    // forceRefreshTableDisplay 已移除：需要刷新时直接调用对应 tab 的 load 函数
}

// 更新文件选择框标签
function updateTemplateFileLabel() {
    const fileInput = document.getElementById('templateFile');
    const label = document.getElementById('templateFileLabel');
    if (fileInput && label) {
        if (fileInput.files && fileInput.files.length > 0) {
            label.textContent = fileInput.files[0].name;
            label.style.color = '#1976d2';
        } else {
            label.textContent = '请选择本地模板';
            label.style.color = '#999';
        }
    }
}

// 删除替换内容模板
async function deleteReplacementTemplate() {
    const select = document.getElementById('replacementTemplateSelect');
    const templateId = select.value;
    if (!templateId) {
        alert('请先选择要删除的模板');
        return;
    }
    
    if (!confirm('确定要删除这个模板吗？')) {
        return;
    }
    
    try {
        const response = await fetch(`/api/document/replacement-template/${templateId}`, {
            method: 'DELETE'
        });
        
        const result = await response.json();
        if (result.success) {
            alert('删除成功');
            select.value = '';
            document.getElementById('deleteReplacementTemplateBtn').style.display = 'none';
            loadReplacementTemplates();
            // 清空当前填充的内容
            const container = document.getElementById('placeholdersContainer');
            container.innerHTML = '';
            // 添加一个空行
            addPlaceholderRow();
        } else {
            alert('删除失败：' + result.message);
        }
    } catch (error) {
        console.error('删除替换内容模板失败：', error);
        alert('删除失败：' + error.message);
    }
}

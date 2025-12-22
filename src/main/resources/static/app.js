const API_BASE = '/api/quota';
let currentEditItemId = null;
let currentItemQuotas = [];
let currentBasicEditItemId = null;
let selectedItemIds = new Set();

window.onload = function() {
    loadItems();
    // 确保表格容器可以正常滚动
    ensureTableScrolling();
};

// 确保表格容器可以正常滚动
function ensureTableScrolling() {
    const containers = ['itemsTableContainer', 'quotasTableContainer'];
    
    containers.forEach(containerId => {
        const container = document.getElementById(containerId);
        if (!container) return;
        
        // 确保容器可以滚动
        container.style.overflowY = 'auto';
        container.style.overflowX = 'auto';
        
        // 确保父容器链都有正确的高度设置
        let parent = container.parentElement;
        while (parent && parent !== document.body) {
            const style = window.getComputedStyle(parent);
            if (style.display === 'flex' && style.flexDirection === 'column') {
                parent.style.minHeight = '0';
                if (parent.classList.contains('data-panel') || parent.classList.contains('quota-management-panel')) {
                    parent.style.flex = '1';
                }
            }
            parent = parent.parentElement;
        }
    });
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
    
    statusSpan.textContent = '匹配中...';
    statusSpan.className = 'status-message';
    
    try {
        const response = await fetch(API_BASE + '/match', {
            method: 'POST'
        });
        
        const result = await response.json();
        
        if (result.success) {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-success';
            loadItems();
        } else {
            statusSpan.textContent = result.message;
            statusSpan.className = 'status-message status-error';
        }
    } catch (error) {
        statusSpan.textContent = '匹配失败：' + error.message;
        statusSpan.className = 'status-message status-error';
    }
}

async function loadItems() {
    try {
        const response = await fetch(API_BASE + '/items');
        const items = await response.json();
        
        // 对于多定额匹配的项目，加载其关联的定额列表
        const itemsWithQuotas = await Promise.all(items.map(async (item) => {
            if (item.matchStatus === 3) {
                try {
                    const quotasResponse = await fetch(API_BASE + `/items/${item.id}/quotas`);
                    item.quotas = await quotasResponse.json();
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
    }
}

function renderItemsTable(items) {
    const tbody = document.getElementById('itemsTableBody');
    
    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="15" class="empty-message">暂无数据，请先导入项目清单</td></tr>';
        selectedItemIds.clear();
        updateItemBatchActions();
        return;
    }
    
    tbody.innerHTML = items.map((item, index) => {
        const statusClass = item.matchStatus === 1 ? 'status-matched' : 
                           item.matchStatus === 2 ? 'status-manual' : 
                           item.matchStatus === 3 ? 'status-multi' : 'status-unmatched';
        const statusText = item.matchStatus === 1 ? '已匹配' : 
                          item.matchStatus === 2 ? '手动修改' : 
                          item.matchStatus === 3 ? '多定额匹配' : '未匹配';
        
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
        
        const isSelected = selectedItemIds.has(item.id);
        return `
            <tr data-item-id="${item.id}">
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
                <td style="vertical-align: top;">${quotaDisplay}</td>
                <td style="vertical-align: top;">${quotaNameDisplay}</td>
                <td style="vertical-align: top;">${quotaFeatureDisplay}</td>
                <td>${item.matchedUnitPrice || 0}</td>
                <td>${item.totalPrice || 0}</td>
                <td class="editable-cell" data-field="remark" data-item-id="${item.id}" title="双击编辑">${item.remark || ''}</td>
                <td><span class="status-badge ${statusClass}">${statusText}</span></td>
                <td>
                    <button class="action-btn" onclick="openEditModal(${item.id}, '${(item.itemName || '').replace(/'/g, "\\'")}')">
                        匹配定额
                    </button>
                    <button class="action-btn" onclick="openItemEditModal(${item.id})" style="background: linear-gradient(135deg, #4caf50 0%, #388e3c 100%);">编辑</button>
                </td>
            </tr>
        `;
    }).join('');
    
    // 初始化列宽调整功能、批量操作和滚动
    setTimeout(() => {
        initResizableColumns();
        updateItemBatchActions();
        ensureTableScrolling();
    }, 100);
}

// 增加新行到表格
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
    } catch (error) {
        alert('增加行失败：' + error.message);
    }
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

async function openEditModal(itemId, itemName) {
    currentEditItemId = itemId;
    document.getElementById('currentItemName').textContent = itemName;
    document.getElementById('editModal').style.display = 'block';
    document.getElementById('quotaList').innerHTML = '<p>请输入关键词搜索企业定额</p>';
    document.getElementById('quotaSearchInput').value = '';
    document.getElementById('manualPrice').value = '';
    
    // 加载已添加的定额
    await loadItemQuotas(itemId);
}

function closeEditModal() {
    document.getElementById('editModal').style.display = 'none';
    currentEditItemId = null;
    currentItemQuotas = [];
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
    const keyword = document.getElementById('quotaSearchInput').value.trim();
    const quotaList = document.getElementById('quotaList');
    
    if (!keyword) {
        quotaList.innerHTML = '<p>请输入关键词搜索企业定额</p>';
        return;
    }
    
    quotaList.innerHTML = '<p>搜索中...</p>';
    
    try {
        const response = await fetch(API_BASE + '/quotas/search?keyword=' + encodeURIComponent(keyword));
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
    
    if (event.target === editModal) {
        closeEditModal();
    }
    if (event.target === itemEditModal) {
        closeItemEditModal();
    }
    if (event.target === quotaEditModal) {
        closeQuotaEditModal();
    }
}

// ==================== 标签页切换 ====================
function switchTab(tabName) {
    // 隐藏所有标签页内容
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // 移除所有标签按钮的active状态
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    // 显示选中的标签页
    if (tabName === 'items') {
        document.getElementById('itemsTab').classList.add('active');
        document.querySelectorAll('.tab-btn')[0].classList.add('active');
        loadItems();
    } else if (tabName === 'quotas') {
        document.getElementById('quotasTab').classList.add('active');
        document.querySelectorAll('.tab-btn')[1].classList.add('active');
        loadQuotas();
    }
}

// ==================== 定额管理模块 ====================
let currentEditQuotaId = null;
let selectedQuotaIds = new Set();

// 加载定额列表
async function loadQuotas() {
    try {
        const response = await fetch(API_BASE + '/quotas');
        const quotas = await response.json();
        renderQuotasTable(quotas);
    } catch (error) {
        console.error('加载定额数据失败：', error);
    }
}

// 渲染定额表格
function renderQuotasTable(quotas) {
    const tbody = document.getElementById('quotasTableBody');
    
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
    }, 100);
    
    // 显示/隐藏批量操作
    document.getElementById('batchActions').style.display = selectedQuotaIds.size > 0 ? 'block' : 'none';
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


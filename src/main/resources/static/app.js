const API_BASE = '/api/quota';
let currentEditItemId = null;
let currentItemQuotas = [];

window.onload = function() {
    loadItems();
};

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
    } catch (error) {
        console.error('加载数据失败：', error);
    }
}

function renderItemsTable(items) {
    const tbody = document.getElementById('itemsTableBody');
    
    if (items.length === 0) {
        tbody.innerHTML = '<tr><td colspan="13" class="empty-message">暂无数据，请先导入项目清单</td></tr>';
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
        
        return `
            <tr>
                <td style="text-align: center; font-weight: bold;">${index + 1}</td>
                <td>${item.itemCode || ''}</td>
                <td>${item.itemName || ''}</td>
                <td>${item.featureValue || ''}</td>
                <td>${item.unit || ''}</td>
                <td>${item.quantity || 0}</td>
                <td style="vertical-align: top;">${quotaDisplay}</td>
                <td style="vertical-align: top;">${quotaNameDisplay}</td>
                <td style="vertical-align: top;">${quotaFeatureDisplay}</td>
                <td>${item.matchedUnitPrice || 0}</td>
                <td>${item.totalPrice || 0}</td>
                <td><span class="status-badge ${statusClass}">${statusText}</span></td>
                <td>
                    <button class="action-btn" onclick="openEditModal(${item.id}, '${(item.itemName || '').replace(/'/g, "\\'")}')">
                        编辑
                    </button>
                </td>
            </tr>
        `;
    }).join('');
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
    const modal = document.getElementById('editModal');
    if (event.target === modal) {
        closeEditModal();
    }
}


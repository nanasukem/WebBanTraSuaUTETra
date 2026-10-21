/* ============================================================
   profile-address.js - Sổ địa chỉ trên trang Profile
   Gọi /api/addresses (giống trang Checkout), thêm / sửa bằng modal AddressBook (address-book.js)
   ============================================================ */
(() => {
	const section = document.getElementById('addressSection');
	if (!section || !window.AddressBook) return;
	const box = document.getElementById('addressList');
	const errBox = document.getElementById('addressError');
	const TYPE_LABEL = { HOME: 'Nhà riêng', OFFICE: 'Văn phòng' };
	const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
		(c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
	let addresses = [];

	async function api(url, options = {}) {
		const res = await fetch(url, { headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' }, ...options });
		if (res.status === 401 || (res.redirected && res.url.includes('/auth/login'))) {
			location.href = '/auth/login';
			throw new Error('Vui lòng đăng nhập');
		}
		const data = await res.json().catch(() => ({}));
		if (!res.ok) throw new Error(data.message || 'Có lỗi xảy ra');
		return data;
	}

	function showError(msg) {
		errBox.textContent = msg || '';
		errBox.classList.toggle('d-none', !msg);
	}

	function render() {
		if (!addresses.length) {
			box.innerHTML = '<p class="text-muted mb-0">Bạn chưa có địa chỉ nào. Bấm <b>Thêm địa chỉ</b> để lưu địa chỉ nhận hàng.</p>';
			return;
		}
		box.innerHTML = addresses.map((a) => `
			<div class="border rounded-3 p-3 mb-2 ${a.isDefault ? 'border-success' : ''}">
				<div class="d-flex justify-content-between align-items-start gap-2">
					<div class="min-w-0">
						<div class="fw-semibold">${esc(a.receiverName)} <span class="text-muted fw-normal">| ${esc(a.receiverPhone)}</span></div>
						<div class="small">${esc(a.addressLine)}</div>
						${a.note ? `<div class="small text-muted">Ghi chú: ${esc(a.note)}</div>` : ''}
						<div class="mt-1">
							${a.isDefault ? '<span class="badge text-bg-success me-1">Mặc định</span>' : ''}
							${a.addressType ? `<span class="badge text-bg-light border fw-normal me-1">${TYPE_LABEL[a.addressType] || ''}</span>` : ''}
							${a.latitude == null ? '<span class="small text-warning"><i class="bi bi-geo"></i> Chưa ghim vị trí</span>' : ''}
						</div>
					</div>
					<div class="d-flex flex-column align-items-end gap-1 flex-shrink-0">
						<button type="button" class="btn btn-sm btn-link text-success text-decoration-none p-0" data-edit="${a.id}">
							<i class="bi bi-pencil-square"></i> Sửa</button>
						${a.isDefault ? '' : `
						<button type="button" class="btn btn-sm btn-outline-success py-0" data-default="${a.id}">Đặt mặc định</button>
						<button type="button" class="btn btn-sm btn-link text-danger text-decoration-none p-0" data-delete="${a.id}">
							<i class="bi bi-trash"></i> Xoá</button>`}
					</div>
				</div>
			</div>`).join('');
	}

	async function reload() {
		addresses = await api('/api/addresses');
		render();
	}

	box.addEventListener('click', async (e) => {
		const edit = e.target.closest('[data-edit]');
		const def = e.target.closest('[data-default]');
		const del = e.target.closest('[data-delete]');
		showError('');
		try {
			if (edit) {
				AddressBook.open({
					address: addresses.find((x) => x.id === Number(edit.dataset.edit)),
					onSaved: reload,
					onDeleted: (list) => { addresses = list; render(); },
				});
			} else if (def) {
				addresses = await api('/api/addresses/' + def.dataset.default + '/default', { method: 'PUT' });
				render();
			} else if (del) {
				if (!confirm('Xoá địa chỉ này khỏi sổ địa chỉ?')) return;
				addresses = await api('/api/addresses/' + del.dataset.delete, { method: 'DELETE' });
				render();
			}
		} catch (err) {
			showError(err.message);
		}
	});

	document.getElementById('btnAddAddress').addEventListener('click', () => AddressBook.open({
		defaults: { name: section.dataset.name || '', phone: section.dataset.phone || '' },
		isFirst: addresses.length === 0,
		onSaved: reload,
	}));

	reload().catch((err) => showError(err.message));
})();

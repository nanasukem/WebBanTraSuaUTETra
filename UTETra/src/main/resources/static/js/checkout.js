/* ============================================================
   checkout.js - Trang thanh toán UTETra
   Mọi tiền đều do server tính (POST /api/checkout/preview), JS chỉ hiển thị.
   Thêm/sửa địa chỉ: dùng AddressBook (static/js/address-book.js)
   ============================================================ */
(() => {
	const $ = (id) => document.getElementById(id);
	if (!$('checkout-content')) return;

	const state = {
		deliveryType: 'DELIVERY',
		addresses: [], carriers: [], branches: [],
		addressId: null, carrierId: null, branchId: null,
		branchManual: false,   // khách đã tự chọn chi nhánh -> không tự đổi nữa
		userLocation: null,    // vị trí của khách {lat, lng, approx}
		askedGps: false,
		productCode: null, shippingCode: null,
		paymentMethod: 'COD',
		canOrder: false,
	};

	// ================= TIỆN ÍCH =================
	const fmt = (n) => (Number(n) || 0).toLocaleString('vi-VN') + 'đ';
	const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
		(c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

	async function api(url, options = {}) {
		const res = await fetch(url, {
			headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' }, ...options,
		});
		if (res.status === 401 || (res.redirected && res.url.includes('/auth/login'))) {
			location.href = '/auth/login';
			throw new Error('Vui lòng đăng nhập');
		}
		let data = null;
		try { data = await res.json(); } catch (e) { /* không phải JSON */ }
		if (!res.ok) throw new Error((data && data.message) || 'Có lỗi xảy ra, vui lòng thử lại');
		return data;
	}

	function buildReq() {
		return {
			branchId: state.branchId,
			deliveryType: state.deliveryType,
			addressId: state.deliveryType === 'DELIVERY' ? state.addressId : null,
			carrierId: state.deliveryType === 'DELIVERY' ? state.carrierId : null,
			receiverName: $('pickup-name').value.trim(),
			receiverPhone: $('pickup-phone').value.trim(),
			productVoucherCode: state.productCode,
			shippingVoucherCode: state.shippingCode,
			paymentMethod: state.paymentMethod,
			note: $('order-note').value.trim(),
		};
	}

	// ================= KHỞI TẠO =================
	async function init() {
		try {
			const data = await api('/api/checkout/init');
			state.addresses = data.addresses || [];
			state.carriers = data.carriers || [];
			$('pickup-name').value = data.receiverName || '';
			$('pickup-phone').value = data.receiverPhone || '';
			renderTier(data.tier);

			const def = state.addresses.find((a) => a.isDefault) || state.addresses[0];
			state.addressId = def ? def.id : null;
			state.carrierId = state.carriers.length ? state.carriers[0].id : null;
			renderAddresses();
			renderCarriers();

			$('co-loading').classList.add('d-none');
			$('co-page').classList.remove('d-none');
			await loadBranches();
			refresh();
		} catch (e) {
			$('co-loading').innerHTML = `<div class="alert alert-danger">${esc(e.message)}</div>`;
		}
	}

	// ================= HẠNG THÀNH VIÊN =================
	function renderTier(t) {
		if (!t) return;
		const colors = { 'Thành viên': '#6c757d', 'Bạc': '#8a8f98', 'Vàng': '#c9a227', 'Kim cương': '#2b9fd9' };
		const color = colors[t.tierName] || '#198754';
		let html = `<div class="d-flex align-items-center gap-2 mb-1 flex-wrap">
			<span class="tier-badge" style="background:${color}"><i class="bi bi-award"></i> ${esc(t.tierName)}</span>`;
		html += Number(t.discountPercent) > 0
			? `<span class="small">Giảm ${Number(t.discountPercent)}%${t.maxDiscount ? ' (tối đa ' + fmt(t.maxDiscount) + ')' : ''} mỗi đơn</span>`
			: '<span class="small text-muted">Chưa có ưu đãi hạng</span>';
		html += '</div>';
		if (t.nextTierName) {
			const spent = Number(t.spentLast12Months), need = Number(t.amountToNextTier);
			const pct = spent + need > 0 ? Math.min(100, Math.round(spent * 100 / (spent + need))) : 0;
			html += `<div class="progress my-1" style="height:6px"><div class="progress-bar" style="width:${pct}%;background:${color}"></div></div>
				<div class="small text-muted">Chi thêm <b>${fmt(need)}</b> để lên hạng <b>${esc(t.nextTierName)}</b>
				<span class="d-block">(tính các đơn đã giao trong 12 tháng gần nhất)</span></div>`;
		} else {
			html += '<div class="small text-muted">Bạn đang ở hạng cao nhất 🎉</div>';
		}
		$('tier-box').innerHTML = html;
	}

	// ================= ĐỊA CHỈ (thêm/sửa bằng AddressBook) =================
	const TYPE_LABEL = { HOME: 'Nhà riêng', OFFICE: 'Văn phòng' };

	function renderAddresses() {
		const box = $('address-list');
		if (!state.addresses.length) {
			box.innerHTML = '<div class="alert alert-light border small mb-0">Bạn chưa có địa chỉ nào. Bấm <b>Thêm địa chỉ mới</b> để tiếp tục.</div>';
			return;
		}
		box.innerHTML = state.addresses.map((a) => `
			<label class="option-card ${a.id === state.addressId ? 'active' : ''}">
				<input type="radio" name="address" class="form-check-input mt-1" value="${a.id}" ${a.id === state.addressId ? 'checked' : ''}>
				<div class="flex-grow-1 min-w-0">
					<div class="fw-semibold">${esc(a.receiverName)} · ${esc(a.receiverPhone)}</div>
					<div class="small text-muted">${esc(a.addressLine)}</div>
					${a.note ? `<div class="small fst-italic text-secondary"><i class="bi bi-sticky me-1"></i>${esc(a.note)}</div>` : ''}
					<div class="mt-1">
						${a.isDefault ? '<span class="badge text-bg-success me-1">Mặc định</span>' : ''}
						${a.addressType ? `<span class="badge text-bg-light border fw-normal me-1">${TYPE_LABEL[a.addressType] || ''}</span>` : ''}
						${a.latitude == null ? '<span class="small text-warning"><i class="bi bi-geo"></i> Chưa ghim vị trí</span>' : ''}
					</div>
				</div>
				<button type="button" class="btn btn-sm btn-link text-success text-decoration-none p-0 flex-shrink-0"
					data-edit="${a.id}"><i class="bi bi-pencil-square"></i> Sửa</button>
			</label>`).join('');
		box.querySelectorAll('input[name=address]').forEach((r) => r.addEventListener('change', async () => {
			state.addressId = Number(r.value);
			renderAddresses();
			await loadBranches(); // giao tận nơi: chi nhánh gần địa chỉ giao nhất
			refresh();
		}));
		box.querySelectorAll('[data-edit]').forEach((b) => b.addEventListener('click', (e) => {
			e.preventDefault();  // không để click "Sửa" chọn luôn địa chỉ đó
			e.stopPropagation();
			AddressBook.open({
				address: state.addresses.find((x) => x.id === Number(b.dataset.edit)),
				onSaved: afterAddressSaved,
				onDeleted: afterAddressDeleted,
			});
		}));
	}

	$('btn-add-address').addEventListener('click', () => AddressBook.open({
		defaults: { name: $('pickup-name').value, phone: $('pickup-phone').value }, // điền sẵn từ hồ sơ
		isFirst: state.addresses.length === 0,
		onSaved: afterAddressSaved,
	}));

	async function afterAddressSaved(saved) {
		state.addresses = await api('/api/addresses');
		state.addressId = saved.id;
		state.branchManual = false; // toạ độ có thể đã đổi -> chọn lại chi nhánh gần nhất
		renderAddresses();
		await loadBranches();
		refresh();
	}

	async function afterAddressDeleted(list, deletedId) {
		state.addresses = list;
		if (state.addressId === deletedId) {
			const def = state.addresses.find((x) => x.isDefault) || state.addresses[0];
			state.addressId = def ? def.id : null;
		}
		state.branchManual = false;
		renderAddresses();
		await loadBranches();
		refresh();
	}

	function renderCarriers() {
		const box = $('carrier-list');
		if (!state.carriers.length) {
			box.innerHTML = '<div class="small text-muted">Hiện chưa có nhà vận chuyển nào hoạt động</div>';
			return;
		}
		box.innerHTML = state.carriers.map((c) => `
			<label class="option-card ${c.id === state.carrierId ? 'active' : ''}">
				<input type="radio" name="carrier" class="form-check-input mt-1" value="${c.id}" ${c.id === state.carrierId ? 'checked' : ''}>
				<div class="flex-grow-1 fw-semibold">${esc(c.name)}</div>
				<span class="fw-semibold">${fmt(c.fee)}</span>
			</label>`).join('');
		box.querySelectorAll('input[name=carrier]').forEach((r) => r.addEventListener('change', () => {
			state.carrierId = Number(r.value);
			renderCarriers();
			refresh();
		}));
	}

	// ================= CHI NHÁNH (gần nhất chọn sẵn) =================
	function currentLocation() {
		if (state.deliveryType === 'DELIVERY') {
			const a = state.addresses.find((x) => x.id === state.addressId);
			return a && a.latitude != null ? { lat: a.latitude, lng: a.longitude, label: 'địa chỉ giao hàng' } : null;
		}
		if (state.userLocation) {
			return { ...state.userLocation,
				label: state.userLocation.approx ? 'vị trí gần đúng của bạn (theo mạng)' : 'vị trí hiện tại của bạn' };
		}
		const def = state.addresses.find((a) => a.isDefault && a.latitude != null);
		return def ? { lat: def.latitude, lng: def.longitude, label: 'địa chỉ mặc định của bạn' } : null;
	}

	async function loadBranches() {
		const loc = currentLocation();
		const q = loc ? `?lat=${loc.lat}&lng=${loc.lng}` : '';
		try {
			state.branches = await api('/api/branches/nearby' + q);
		} catch (e) {
			state.branches = [];
		}
		$('location-hint').textContent = loc
			? `Sắp xếp theo khoảng cách tới ${loc.label}`
			: (state.deliveryType === 'PICKUP'
				? 'Bấm "Dùng vị trí hiện tại" để tìm chi nhánh gần bạn'
				: 'Ghim vị trí cho địa chỉ (bấm "Sửa") để tự chọn chi nhánh gần nhất');
		const stillThere = state.branches.some((b) => b.id === state.branchId);
		if (!state.branchManual || !stillThere) {
			state.branchId = state.branches.length ? state.branches[0].id : null;
			state.branchManual = false;
		}
		renderBranches();
	}

	function renderBranches() {
		const box = $('branch-list');
		if (!state.branches.length) {
			box.innerHTML = '<div class="small text-muted">Hiện chưa có chi nhánh nào nhận đơn</div>';
			return;
		}
		box.innerHTML = state.branches.map((b, i) => `
			<label class="option-card ${b.id === state.branchId ? 'active' : ''}">
				<input type="radio" name="branch" class="form-check-input mt-1" value="${b.id}" ${b.id === state.branchId ? 'checked' : ''}>
				<div class="flex-grow-1 min-w-0">
					<div class="fw-semibold">${esc(b.name)}
						${i === 0 && b.distanceKm != null ? '<span class="badge bg-success-subtle text-success border border-success-subtle ms-1">Gần bạn nhất</span>' : ''}</div>
					<div class="small text-muted">${esc(b.address)}</div>
				</div>
				${b.distanceKm != null ? `<span class="small fw-semibold text-nowrap">${Number(b.distanceKm).toLocaleString('vi-VN')} km</span>` : ''}
			</label>`).join('');
		box.querySelectorAll('input[name=branch]').forEach((r) => r.addEventListener('change', () => {
			state.branchId = Number(r.value);
			state.branchManual = true;
			renderBranches();
			refresh();
		}));
	}

	/**
	 * Nút "Dùng vị trí hiện tại" (đến lấy):
	 * sắp xếp ngay theo vị trí gần đúng của mạng, GPS có kết quả thì sắp lại cho chính xác.
	 */
	async function locate(showError) {
		const btn = $('btn-locate');
		btn.disabled = true;
		btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Đang lấy vị trí...';
		let gotGps = false;

		const approxTask = AddressBook.approxPosition().then(async (p) => {
			if (!gotGps) {
				state.userLocation = { lat: p.lat, lng: p.lng, approx: true };
				state.branchManual = false;
				if (state.deliveryType === 'PICKUP') { await loadBranches(); refresh(); }
			}
			return p;
		}).catch(() => null);

		try {
			const pos = await AddressBook.getPosition();
			gotGps = true;
			state.userLocation = { lat: pos.lat, lng: pos.lng, approx: false };
			state.branchManual = false; // vừa có vị trí -> chọn lại chi nhánh gần nhất
			if (state.deliveryType === 'PICKUP') { await loadBranches(); refresh(); }
		} catch (e) {
			const p = await approxTask;
			if (!p && showError) $('location-hint').textContent = e.message + '. Bạn vẫn có thể tự chọn chi nhánh bên dưới.';
		} finally {
			btn.disabled = false;
			btn.innerHTML = '<i class="bi bi-crosshair"></i> Dùng vị trí hiện tại';
		}
	}

	// ================= MÃ GIẢM GIÁ =================
	function discountText(v) {
		if (v.discountType === 'PERCENT')
			return `Giảm ${Number(v.discountValue)}%` + (v.maxDiscount ? ` tối đa ${fmt(v.maxDiscount)}` : '');
		return v.applyTo === 'SHIPPING' ? `Giảm ${fmt(v.discountValue)} phí ship` : `Giảm ${fmt(v.discountValue)}`;
	}

	function renderVouchers(list) {
		const box = $('voucher-list');
		if (!list || !list.length) {
			box.innerHTML = '<div class="small text-muted">Chưa có mã nào cho đơn này</div>';
			return;
		}
		box.innerHTML = list.map((v) => {
			const selected = (v.applyTo === 'PRODUCT' ? state.productCode : state.shippingCode) === v.code;
			const isShip = v.applyTo === 'SHIPPING';
			return `
			<div class="voucher ${v.usable ? '' : 'disabled'} ${selected ? 'selected' : ''}">
				<div class="voucher-icon ${isShip ? 'ship' : ''}"><i class="bi ${isShip ? 'bi-truck' : 'bi-ticket-perforated'}"></i></div>
				<div class="flex-grow-1 min-w-0">
					<div class="fw-semibold">${esc(v.code)} <span class="fw-normal">· ${discountText(v)}</span></div>
					<div class="small text-muted">${Number(v.minOrderValue) > 0 ? 'Đơn từ ' + fmt(v.minOrderValue) + ' · ' : ''}HSD ${esc(v.endDate)}</div>
					<div>${(v.tags || []).map((t) => `<span class="badge text-bg-light border fw-normal me-1">${esc(t)}</span>`).join('')}</div>
					${v.usable ? '' : `<div class="small text-danger mt-1"><i class="bi bi-exclamation-circle"></i> ${esc(v.reason)}</div>`}
				</div>
				<button type="button" class="btn btn-sm ${selected ? 'btn-outline-danger' : 'btn-success'}"
					data-code="${esc(v.code)}" data-apply="${v.applyTo}" ${v.usable || selected ? '' : 'disabled'}>
					${selected ? 'Bỏ' : 'Dùng'}</button>
			</div>`;
		}).join('');
		box.querySelectorAll('button[data-code]').forEach((btn) =>
			btn.addEventListener('click', () => toggleVoucher(btn.dataset.code, btn.dataset.apply)));
	}

	function toggleVoucher(code, applyTo) {
		if (applyTo === 'PRODUCT') state.productCode = state.productCode === code ? null : code;
		else state.shippingCode = state.shippingCode === code ? null : code;
		refresh();
	}

	function renderSelectedVouchers(p) {
		const chips = [];
		if (state.productCode) chips.push(chip(state.productCode, 'PRODUCT', p.productVoucherError, p.productDiscount));
		if (state.shippingCode) chips.push(chip(state.shippingCode, 'SHIPPING', p.shippingVoucherError, p.shippingDiscount));
		$('selected-vouchers').innerHTML = chips.join('');
		$('selected-vouchers').querySelectorAll('[data-remove]').forEach((b) => b.addEventListener('click', () => {
			if (b.dataset.remove === 'PRODUCT') state.productCode = null; else state.shippingCode = null;
			refresh();
		}));
	}

	function chip(code, applyTo, error, discount) {
		return `
		<div class="selected-chip ${error ? 'error' : ''}">
			<i class="bi ${applyTo === 'SHIPPING' ? 'bi-truck' : 'bi-ticket-perforated'}"></i>
			<div class="flex-grow-1 min-w-0"><b>${esc(code)}</b>
				${error ? `<div class="small text-danger">${esc(error)}</div>` : `<span class="small text-success">−${fmt(discount)}</span>`}
			</div>
			<button type="button" class="btn-close" style="font-size:.7rem" data-remove="${applyTo}" aria-label="Bỏ mã"></button>
		</div>`;
	}

	$('btn-apply-voucher').addEventListener('click', applyTypedCode);
	$('voucher-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); applyTypedCode(); } });

	async function applyTypedCode() {
		const code = $('voucher-input').value.trim().toUpperCase();
		$('voucher-error').textContent = '';
		if (!code) return;
		try {
			const v = await api('/api/checkout/voucher-type?code=' + encodeURIComponent(code));
			if (v.applyTo === 'SHIPPING') state.shippingCode = v.code; else state.productCode = v.code;
			$('voucher-input').value = '';
			refresh();
		} catch (e) {
			$('voucher-error').textContent = e.message;
		}
	}

	// ================= TÍNH LẠI TIỀN (gọi server) =================
	let timer = null;
	let seq = 0;
	function refresh() {
		clearTimeout(timer);
		timer = setTimeout(doRefresh, 200); // gom nhiều thay đổi liên tiếp thành 1 lần gọi
	}

	async function doRefresh() {
		const my = ++seq;
		try {
			const data = await api('/api/checkout/preview', { method: 'POST', body: JSON.stringify(buildReq()) });
			if (my !== seq) return; // đã có lần gọi mới hơn
			renderPreview(data.preview);
			renderSelectedVouchers(data.preview);
			renderVouchers(data.vouchers);
		} catch (e) {
			showErrors([e.message]);
		}
	}

	function renderPreview(p) {
		if (!p.items.length) {
			$('co-page').classList.add('d-none');
			$('co-empty').classList.remove('d-none');
			return;
		}
		const unavailable = p.unavailableItems || [];
		$('sum-qty').textContent = p.totalQty;
		$('sum-items').innerHTML = p.items.map((i) => {
			const het = unavailable.includes(i.productName);
			const toppings = (i.toppings || []).map((t) => esc(t.name)).filter(Boolean).join(', ');
			return `
			<div class="d-flex justify-content-between small mb-2 ${het ? 'text-danger' : ''}">
				<div class="me-2"><b>${i.quantity}×</b> ${esc(i.productName)}
					<span class="text-muted">(${esc(i.size)}${toppings ? ', ' + toppings : ''})</span>
					${i.note ? `<div class="text-muted"><i class="bi bi-pencil-square me-1"></i>${esc(i.note)}</div>` : ''}
					${het ? '<div><i class="bi bi-x-circle"></i> Hết tại chi nhánh này</div>' : ''}</div>
				<div class="text-nowrap">${fmt(i.lineTotal)}</div>
			</div>`;
		}).join('');

		$('sum-items-total').textContent = fmt(p.itemsTotal);
		setDiscountRow('row-product-discount', 'sum-product-discount', p.productDiscount);
		setDiscountRow('row-member-discount', 'sum-member-discount', p.memberDiscount);
		$('sum-member-label').textContent = 'Ưu đãi hạng ' + (p.memberTierName || '');
		$('row-shipping').classList.toggle('d-none', state.deliveryType !== 'DELIVERY');
		$('sum-shipping-fee').textContent = fmt(p.shippingFee);
		setDiscountRow('row-shipping-discount', 'sum-shipping-discount', p.shippingDiscount);
		$('sum-total').textContent = fmt(p.totalAmount);

		const saved = Number(p.productDiscount) + Number(p.memberDiscount) + Number(p.shippingDiscount);
		$('sum-saved').textContent = saved > 0 ? `Bạn tiết kiệm được ${fmt(saved)}` : '';

		showErrors(p.errors || []);
		state.canOrder = p.canOrder;
		$('btn-place-order').disabled = !p.canOrder;
	}

	function setDiscountRow(rowId, valueId, amount) {
		$(rowId).classList.toggle('d-none', !(Number(amount) > 0));
		$(valueId).textContent = '−' + fmt(amount);
	}

	function showErrors(list) {
		$('sum-errors').innerHTML = list.map((m) =>
			`<div class="alert alert-warning small py-2 mb-2"><i class="bi bi-exclamation-triangle me-1"></i>${esc(m)}</div>`).join('');
	}

	// ================= ĐỔI HÌNH THỨC NHẬN / THANH TOÁN / NGƯỜI LẤY =================
	document.querySelectorAll('input[name=deliveryType]').forEach((r) => r.addEventListener('change', async () => {
		state.deliveryType = r.value;
		const pickup = state.deliveryType === 'PICKUP';
		$('sec-delivery').classList.toggle('d-none', pickup);
		$('sec-pickup').classList.toggle('d-none', !pickup);
		$('btn-locate').classList.toggle('d-none', !pickup);
		$('branch-title').textContent = pickup ? 'Chọn chi nhánh đến lấy' : 'Chi nhánh làm & giao món';
		if (pickup) state.shippingCode = null; // đến lấy không có phí ship
		await loadBranches();
		refresh();
		if (pickup && !state.userLocation && !state.askedGps) {
			state.askedGps = true;
			locate(false); // hỏi vị trí 1 lần, không được thì dùng địa chỉ mặc định
		}
	}));

	document.querySelectorAll('input[name=paymentMethod]').forEach((r) => r.addEventListener('change', () => {
		state.paymentMethod = r.value;
		document.querySelectorAll('input[name=paymentMethod]').forEach((x) =>
			x.closest('.option-card').classList.toggle('active', x.checked));
		refresh();
	}));

	$('btn-locate').addEventListener('click', () => locate(true));
	$('pickup-name').addEventListener('input', refresh);
	$('pickup-phone').addEventListener('input', refresh);

	// ================= ĐẶT HÀNG =================
	$('btn-place-order').addEventListener('click', async () => {
		if (!state.canOrder) return;
		const btn = $('btn-place-order');
		btn.disabled = true;
		btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang đặt hàng...';
		try {
			const res = await api('/api/checkout', {
			    method: 'POST',
			    body: JSON.stringify(buildReq())
			});

			location.href = res.paymentUrl
			    || ('/checkout/success/' + res.orderId);
		} catch (e) {
			showErrors([e.message]);
			btn.innerHTML = '<i class="bi bi-bag-check me-1"></i> Đặt hàng';
			refresh();
		}
	});

	init();
})();
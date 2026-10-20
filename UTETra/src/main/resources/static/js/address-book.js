/* ============================================================
   address-book.js - Thêm / sửa địa chỉ nhận hàng kiểu Shopee
   Dùng chung cho trang Checkout (và sau này trang Hồ sơ).
   Màn hình: Form -> Chọn Tỉnh/Phường -> Nhập Tên đường -> Bản đồ xác nhận -> Form
   - "Dùng vị trí hiện tại": lấy vị trí máy, tự điền tên đường/số nhà đọc được từ bản đồ
   - Ghim lệch xa Phường/Xã đã chọn -> cảnh báo (so bằng khoảng cách, vì bản đồ OSM còn dùng tên phường cũ)
   Cách dùng:
     AddressBook.open({ address, defaults: {name, phone}, isFirst, onSaved(saved), onDeleted(list) })
   ============================================================ */
window.AddressBook = (() => {
	const $ = (id) => document.getElementById(id);
	const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
		(c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
	/** Bỏ dấu để tìm kiếm: "Phường Đông Hòa" -> "phuong dong hoa" */
	const plain = (s) => String(s || '').replace(/[đĐ]/g, 'd').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
	const PHONE_RE = /^0\d{9}$/;
	const HCM = [10.7769, 106.7009];
	const NOMINATIM = 'https://nominatim.openstreetmap.org';
	const HCM_VIEWBOX = '106.30,11.20,107.10,10.30';
	const SKIP_TYPES = ['bus_stop', 'platform', 'stop_position', 'stop', 'station', 'halt', 'tram_stop', 'bus_station'];

	// ================= GỌI SERVER =================
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

	// ================= VỊ TRÍ (dùng chung, checkout.js cũng gọi) =================

	/** Vị trí chính xác của máy (GPS / Wi-Fi). Tối đa 8 giây */
	function getPosition() {
		return new Promise((resolve, reject) => {
			if (!window.isSecureContext)
				return reject(new Error('Trình duyệt chỉ cho lấy vị trí khi mở web bằng localhost hoặc https'));
			if (!navigator.geolocation) return reject(new Error('Trình duyệt không hỗ trợ lấy vị trí'));
			const explain = (code) => code === 1
				? 'Bạn đã chặn quyền vị trí. Bấm biểu tượng bên trái thanh địa chỉ → Vị trí → Cho phép, rồi tải lại trang'
				: 'Máy không xác định được vị trí chính xác (không có Wi-Fi/GPS hoặc chưa bật định vị)';
			let done = false;
			const finish = (fn, v) => { if (!done) { done = true; clearTimeout(guard); fn(v); } };
			const guard = setTimeout(() => finish(reject, new Error(explain(3))), 8000);
			if (navigator.permissions && navigator.permissions.query) {
				navigator.permissions.query({ name: 'geolocation' })
					.then((p) => { if (p.state === 'denied') finish(reject, new Error(explain(1))); })
					.catch(() => { /* không hỗ trợ */ });
			}
			const ok = (pos) => finish(resolve, { lat: pos.coords.latitude, lng: pos.coords.longitude });
			navigator.geolocation.getCurrentPosition(ok, (err) => {
				if (err.code === 1) return finish(reject, new Error(explain(1)));
				navigator.geolocation.getCurrentPosition(ok, (e2) => finish(reject, new Error(explain(e2.code))),
					{ enableHighAccuracy: true, timeout: 6000, maximumAge: 300000 });
			}, { enableHighAccuracy: false, timeout: 5000, maximumAge: 300000 });
		});
	}

	/** Vị trí GẦN ĐÚNG theo mạng (cỡ quận/thành phố) */
	async function approxPosition() {
		const res = await fetch('https://get.geojs.io/v1/ip/geo.json');
		if (!res.ok) throw new Error('Không lấy được vị trí gần đúng');
		const d = await res.json();
		const lat = Number(d.latitude), lng = Number(d.longitude);
		if (!isFinite(lat) || !isFinite(lng)) throw new Error('Không lấy được vị trí gần đúng');
		return { lat, lng, city: d.city || '' };
	}

	// ================= DỮ LIỆU 34 TỈNH / 3321 PHƯỜNG XÃ (sau sáp nhập 01/07/2025) =================
	let areas = null; // [{n: "Thành phố Hồ Chí Minh", w: ["Phường An Lạc", ...]}, ...]
	async function loadAreas() {
		if (!areas) {
			const res = await fetch('/js/vn-areas.json');
			if (!res.ok) throw new Error('Không tải được danh sách Tỉnh/Phường');
			areas = await res.json();
		}
		return areas;
	}

	// ================= TÌM ĐỊA ĐIỂM TRÊN BẢN ĐỒ (OpenStreetMap) =================

	/** Phần "Tên đường, Toà nhà, Số nhà" lấy từ kết quả bản đồ */
	function streetPart(r) {
		const a = r.address || {};
		const road = [a.house_number, a.road].filter(Boolean).join(' ');
		const place = r.name && r.name !== a.road && !/^\d/.test(r.name) ? r.name : '';
		const s = [place, road].filter(Boolean).join(', ');
		return s || (r.display_name || '').split(',')[0];
	}

	/** Phần còn lại (phường cũ/quận/thành phố theo bản đồ) chỉ để hiển thị gợi ý */
	function areaPart(r) {
		const a = r.address || {};
		return [a.quarter || a.suburb || a.neighbourhood || a.village, a.city_district || a.district || a.county,
			a.city || a.state].filter(Boolean).filter((p, i, arr) => arr.indexOf(p) === i).join(', ');
	}

	async function nominatim(q) {
		const url = `${NOMINATIM}/search?format=json&limit=8&addressdetails=1&countrycodes=vn&accept-language=vi`
			+ `&viewbox=${HCM_VIEWBOX}&q=` + encodeURIComponent(q);
		const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
		if (!res.ok) return [];
		return (await res.json()).filter((r) => !SKIP_TYPES.includes(r.type) && r.class !== 'public_transport');
	}

	/** Gợi ý địa điểm cho chữ khách gõ, ưu tiên trong Tỉnh/Phường đã chọn */
	async function suggest(text, ward, province) {
		const shortWard = ward.replace(/^(Phường|Xã|Đặc khu)\s+/, '');
		const queries = [`${text}, ${shortWard}, ${province}`, `${text}, ${province}`, text];
		for (const q of queries) {
			const rows = await nominatim(q);
			if (rows.length) {
				const seen = new Set();
				return rows.map((r) => ({ lat: Number(r.lat), lng: Number(r.lon), street: streetPart(r), area: areaPart(r) }))
					.filter((s) => { const k = s.street + '|' + s.area; return !seen.has(k) && seen.add(k); })
					.slice(0, 5);
			}
		}
		return [];
	}

	/** Đọc địa chỉ từ 1 điểm trên bản đồ (dùng cho "Dùng vị trí hiện tại") */
	async function reverse(lat, lng) {
		const url = `${NOMINATIM}/reverse?format=json&zoom=18&addressdetails=1&accept-language=vi&lat=${lat}&lon=${lng}`;
		const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
		if (!res.ok) return null;
		const r = await res.json();
		return r && !r.error ? r : null;
	}

	/** Khoảng cách 2 điểm (km), công thức Haversine */
	function distanceKm(lat1, lng1, lat2, lng2) {
		const rad = (d) => d * Math.PI / 180;
		const a = Math.sin(rad(lat2 - lat1) / 2) ** 2
			+ Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lng2 - lng1) / 2) ** 2;
		return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	/** Trung tâm của Phường/Xã đã chọn. Không tìm thấy (tên phường mới chưa có trên bản đồ) -> null */
	const wardCenterCache = {};
	async function wardCenter(ward, province) {
		const key = ward + '|' + province;
		if (key in wardCenterCache) return wardCenterCache[key];
		const rows = await nominatim(`${ward.replace(/^(Phường|Xã|Đặc khu)\s+/, '')}, ${province}`);
		// Chỉ tin kết quả là khu vực hành chính / địa danh, bỏ qua tên đường hay cửa hàng trùng tên
		const area = rows.find((r) => r.class === 'boundary' || r.class === 'place');
		const c = area ? [Number(area.lat), Number(area.lon)] : null;
		wardCenterCache[key] = c;
		return c;
	}

	/** Phường thường nhỏ, xã/đặc khu rộng hơn nhiều -> ngưỡng cảnh báo khác nhau */
	function warnLimitKm(ward) {
		return /^Phường/.test(ward) ? 4 : 10;
	}

	/** Toạ độ trung tâm của Phường/Tỉnh (để mở bản đồ đúng khu vực) */
	async function areaCenter(ward, province) {
		for (const q of [`${ward.replace(/^(Phường|Xã|Đặc khu)\s+/, '')}, ${province}`, province]) {
			const rows = await nominatim(q);
			if (rows.length) return [Number(rows[0].lat), Number(rows[0].lon)];
		}
		return HCM;
	}

	// ================= TRẠNG THÁI =================
	const st = {
		id: null, province: '', ward: '', street: '', lat: null, lng: null, type: 'HOME', isDefault: false,
		lockDefault: false, onSaved: null, onDeleted: null,
		screen: 'form',
		pickProvince: '', pickStep: 'province', // đang chọn trong màn Tỉnh/Phường
	};
	let modal = null, bigMap = null, miniMap = null, miniMarker = null;

	// ================= CHUYỂN MÀN HÌNH =================
	const SCREENS = ['form', 'area', 'street', 'map'];
	function show(screen) {
		st.screen = screen;
		SCREENS.forEach((s) => $('ab-s-' + s).classList.toggle('d-none', s !== screen));
		$('ab-footer-form').classList.toggle('d-none', screen !== 'form');
		$('ab-footer-map').classList.toggle('d-none', screen !== 'map');
		$('ab-title').textContent = screen === 'area' ? 'Chọn khu vực'
			: (st.id ? 'Sửa địa chỉ' : 'Địa chỉ mới');
		$('ab-body').scrollTop = 0;
		if (screen === 'area') { $('ab-area-search').value = ''; renderArea(); setTimeout(() => $('ab-area-search').focus(), 50); }
		if (screen === 'street') openStreet();
		if (screen === 'map') setTimeout(() => bigMap && bigMap.invalidateSize(), 60);
		if (screen === 'form') renderForm();
	}

	function back() {
		if (st.screen === 'form') modal.hide();
		else if (st.screen === 'map') show('street');
		else show('form');
	}

	// ================= MÀN 1: FORM =================
	function renderForm() {
		$('ab-area-text').innerHTML = st.province && st.ward
			? `<div>${esc(st.province)}</div><div>${esc(st.ward)}</div>`
			: '<span class="text-muted">Chọn Tỉnh/Thành Phố, Phường/Xã</span>';
		const streetBtn = $('ab-open-street');
		streetBtn.disabled = !(st.province && st.ward);
		$('ab-street-text').innerHTML = st.street
			? esc(st.street)
			: `<span class="text-muted">${st.province && st.ward ? 'Nhập tên đường, toà nhà, số nhà' : 'Chọn Tỉnh/Phường trước'}</span>`;
		document.querySelectorAll('#abModal .ab-chip').forEach((c) => c.classList.toggle('active', c.dataset.type === st.type));
		$('ab-default').checked = st.isDefault;
		$('ab-default').disabled = st.lockDefault;
		renderMiniMap();
		updateSubmit();
	}

	function renderMiniMap() {
		const box = $('ab-mini-map');
		const has = st.lat != null && st.lng != null && typeof L !== 'undefined';
		box.classList.toggle('d-none', !has);
		if (!has) return;
		if (!miniMap) {
			miniMap = L.map(box, { zoomControl: false, dragging: false, scrollWheelZoom: false, doubleClickZoom: false,
				boxZoom: false, keyboard: false, touchZoom: false, attributionControl: false });
			L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(miniMap);
			miniMap.on('click', () => { if (st.street) openMap([st.lat, st.lng]); });
		}
		setTimeout(() => {
			miniMap.invalidateSize();
			miniMap.setView([st.lat, st.lng], 17);
			if (miniMarker) miniMarker.setLatLng([st.lat, st.lng]);
			else miniMarker = L.marker([st.lat, st.lng]).addTo(miniMap);
		}, 60);
	}

	function phoneError() {
		const v = $('ab-phone').value.trim();
		if (!v) return 'Vui lòng nhập Số điện thoại';
		if (!PHONE_RE.test(v)) return 'Số điện thoại phải có 10 số, bắt đầu bằng 0';
		return '';
	}

	function formReady() {
		return $('ab-name').value.trim() && !phoneError() && st.province && st.ward && st.street;
	}

	function updateSubmit() {
		$('ab-submit').disabled = !formReady();
	}

	// ================= MÀN 2: CHỌN TỈNH / PHƯỜNG =================
	function renderArea() {
		const q = plain($('ab-area-search').value);
		const step = st.pickStep;

		// "Khu vực được chọn"
		$('ab-area-crumbs').innerHTML = `
			<button type="button" class="ab-crumb ${step === 'province' ? 'active' : ''}" data-crumb="province">
				<span class="ab-dot"></span>${st.pickProvince ? esc(st.pickProvince) : 'Chọn Tỉnh/Thành Phố'}</button>
			${st.pickProvince ? `<button type="button" class="ab-crumb ${step === 'ward' ? 'active' : ''}" data-crumb="ward">
				<span class="ab-dot"></span>${st.ward && st.province === st.pickProvince ? esc(st.ward) : 'Chọn Phường/Xã'}</button>` : ''}`;
		$('ab-area-crumbs').querySelectorAll('[data-crumb]').forEach((b) => b.addEventListener('click', () => {
			st.pickStep = b.dataset.crumb;
			$('ab-area-search').value = '';
			renderArea();
		}));

		let items = []; // {label, sub, province, ward, selected}
		if (step === 'ward') {
			const p = areas.find((x) => x.n === st.pickProvince);
			items = (p ? p.w : []).filter((w) => !q || plain(w).includes(q))
				.map((w) => ({ label: w, province: st.pickProvince, ward: w,
					selected: st.province === st.pickProvince && st.ward === w }));
			$('ab-area-header').textContent = 'Phường/Xã';
		} else if (!q) {
			items = areas.map((p) => ({ label: p.n, province: p.n, selected: p.n === st.pickProvince }));
			$('ab-area-header').textContent = 'Tỉnh/Thành Phố';
		} else {
			// Tìm cả tỉnh lẫn phường (VD gõ "an lac" -> "Phường An Lạc, Thành phố Hồ Chí Minh")
			items = areas.filter((p) => plain(p.n).includes(q)).map((p) => ({ label: p.n, province: p.n }));
			areas.forEach((p) => p.w.forEach((w) => {
				if (items.length < 60 && plain(w).includes(q)) items.push({ label: w, sub: p.n, province: p.n, ward: w });
			}));
			$('ab-area-header').textContent = 'Kết quả tìm kiếm';
		}

		$('ab-area-list').innerHTML = items.length
			? items.map((it, i) => `
				<button type="button" class="ab-item ${it.selected ? 'selected' : ''}" data-i="${i}">
					<span>${esc(it.label)}${it.sub ? `<span class="d-block small text-muted">${esc(it.sub)}</span>` : ''}</span>
					${it.selected ? '<i class="bi bi-check-lg"></i>' : ''}
				</button>`).join('')
			: '<div class="p-3 small text-muted">Không tìm thấy</div>';
		$('ab-area-list').querySelectorAll('[data-i]').forEach((b) => b.addEventListener('click', () => {
			const it = items[Number(b.dataset.i)];
			if (it.ward) {
				pickWard(it.province, it.ward);
			} else {
				st.pickProvince = it.province; // chọn tỉnh -> sang danh sách phường
				st.pickStep = 'ward';
				$('ab-area-search').value = '';
				renderArea();
				$('ab-body').scrollTop = 0;
			}
		}));
	}

	function pickWard(province, ward) {
		const changed = province !== st.province || ward !== st.ward;
		st.province = province;
		st.ward = ward;
		st.pickProvince = province;
		if (changed) { st.lat = null; st.lng = null; } // đổi khu vực -> phải ghim lại trên bản đồ
		show('form');
		if (changed && st.street) $('ab-form-err').textContent = 'Bạn vừa đổi khu vực, hãy bấm "Tên đường, Toà nhà, Số nhà" để kiểm tra lại vị trí';
	}

	// ================= MÀN 3: NHẬP TÊN ĐƯỜNG + GỢI Ý =================
	function openStreet() {
		$('ab-street-input').value = st.street;
		$('ab-street-area').textContent = `${st.ward}, ${st.province}`;
		renderStreetList([]);
		setTimeout(() => $('ab-street-input').focus(), 50);
	}

	function renderStreetList(list, message) {
		const typed = $('ab-street-input').value.trim();
		let html = `<button type="button" class="ab-suggest" data-here="1">
				<i class="bi bi-crosshair text-success"></i>
				<span><b>Dùng vị trí hiện tại</b>
				<span class="d-block small text-muted">Lấy vị trí của máy, tự điền tên đường (bạn kiểm tra lại số nhà)</span></span></button>`;
		if (typed) {
			html += `<button type="button" class="ab-suggest" data-typed="1">
				<i class="bi bi-pencil-square"></i>
				<span><b>Dùng địa chỉ bạn vừa nhập:</b> ${esc(typed)}
				<span class="d-block small text-muted">Ghim vị trí trên bản đồ ở bước tiếp theo</span></span></button>`;
		}
		if (message) html += `<div class="px-3 py-2 small text-muted">${esc(message)}</div>`;
		html += list.map((s, i) => `
			<button type="button" class="ab-suggest" data-i="${i}">
				<i class="bi bi-geo-alt-fill"></i>
				<span><b>${esc(s.street)}</b>${s.area ? ', ' + esc(s.area) : ''}</span></button>`).join('');
		$('ab-street-list').innerHTML = html;
		$('ab-street-list').querySelectorAll('[data-i]').forEach((b) => b.addEventListener('click', () => {
			const s = list[Number(b.dataset.i)];
			st.street = keepHouseNumber(typed, s.street);
			openMap([s.lat, s.lng]);
		}));
		const here = $('ab-street-list').querySelector('[data-here]');
		if (here) here.addEventListener('click', () => useCurrentLocation(here));
		const t = $('ab-street-list').querySelector('[data-typed]');
		if (t) t.addEventListener('click', async () => {
			st.street = typed;
			t.disabled = true;
			const center = st.lat != null ? [st.lat, st.lng] : await areaCenter(st.ward, st.province);
			openMap(center, st.lat == null ? 15 : 17);
		});
	}

	/** Khách gõ "76 Đường 1B" mà gợi ý không có số nhà -> giữ số 76 */
	function keepHouseNumber(typed, found) {
		const m = typed.match(/^(\d+[\w\/\-]*)\s/);
		return m && !found.includes(m[1]) ? `${m[1]} ${found}` : found;
	}

	async function searchStreet() {
		const text = $('ab-street-input').value.trim();
		if (text.length < 2) return;
		const btn = $('ab-street-search');
		btn.disabled = true;
		renderStreetList([], 'Đang tìm gợi ý...');
		try {
			const list = await suggest(text, st.ward, st.province);
			renderStreetList(list, list.length ? 'Các địa điểm được đề xuất dựa trên địa chỉ bạn nhập vào'
				: 'Không có gợi ý phù hợp. Bấm "Dùng địa chỉ bạn vừa nhập" rồi ghim trên bản đồ');
		} catch (e) {
			renderStreetList([], 'Không tìm được gợi ý (lỗi mạng). Bạn vẫn dùng địa chỉ vừa nhập được');
		} finally {
			btn.disabled = false;
		}
	}

	// ================= MÀN 4: BẢN ĐỒ XÁC NHẬN (ghim cố định giữa màn hình) =================
	function openMap(center, zoom = 17) {
		show('map');
		$('ab-map-address').textContent = `${st.street || '(chưa có tên đường)'}, ${st.ward}, ${st.province}`;
		$('ab-map-msg').textContent = '';
		$('ab-map-warn').classList.add('d-none');
		if (typeof L === 'undefined') {
			$('ab-big-map').innerHTML = '<div class="p-3 small text-muted">Không tải được bản đồ. Bấm Xác nhận để lưu không có vị trí</div>';
			return;
		}
		if (!bigMap) {
			bigMap = L.map('ab-big-map', { zoomControl: true });
			L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
				maxZoom: 19, attribution: '&copy; OpenStreetMap',
			}).addTo(bigMap);
			bigMap.on('movestart', () => $('abModal').classList.add('ab-moving'));
			bigMap.on('moveend', () => $('abModal').classList.remove('ab-moving'));
		}
		setTimeout(() => { bigMap.invalidateSize(); bigMap.setView(center, zoom); }, 80);
	}

	async function locateOnMap() {
		const btn = $('ab-map-locate');
		btn.disabled = true;
		$('ab-map-msg').textContent = 'Đang lấy vị trí của bạn...';
		let gotGps = false;
		const approxTask = approxPosition().then((p) => { if (!gotGps) bigMap.setView([p.lat, p.lng], 15); return p; })
			.catch(() => null);
		try {
			const pos = await getPosition();
			gotGps = true;
			bigMap.setView([pos.lat, pos.lng], 18);
			$('ab-map-msg').textContent = '';
		} catch (e) {
			const p = await approxTask;
			$('ab-map-msg').textContent = p
				? 'Máy không xác định được vị trí chính xác. Bản đồ đã tới khu vực của bạn, hãy kéo bản đồ cho ghim nằm đúng nhà'
				: e.message;
		} finally {
			btn.disabled = false;
		}
	}

	/** "Dùng vị trí hiện tại": GPS (không được thì vị trí gần đúng theo mạng) -> tự điền tên đường -> mở bản đồ */
	async function useCurrentLocation(btn) {
		btn.disabled = true;
		const label = btn.querySelector('b');
		label.textContent = 'Đang lấy vị trí...';
		let pos = null;
		let approx = false;
		try {
			pos = await getPosition();
		} catch (e) {
			try {
				const p = await approxPosition();
				pos = { lat: p.lat, lng: p.lng };
				approx = true;
			} catch (e2) {
				renderStreetList([], e.message);
				return;
			}
		}

		// Vị trí chính xác: đọc tên đường / số nhà từ bản đồ (nếu khách chưa gõ gì)
		const typed = $('ab-street-input').value.trim();
		let found = '';
		if (!approx) {
			try {
				const r = await reverse(pos.lat, pos.lng);
				if (r) found = streetPart(r);
			} catch (e) { /* lỗi mạng: bỏ qua, khách tự gõ */ }
		}
		st.street = typed || found;
		$('ab-street-input').value = st.street;

		openMap([pos.lat, pos.lng], approx ? 15 : 18);
		if (approx) {
			$('ab-map-msg').textContent = 'Máy không xác định được vị trí chính xác. Bản đồ đã tới khu vực gần bạn, hãy kéo cho ghim nằm đúng nhà';
		} else if (!st.street) {
			$('ab-map-msg').textContent = 'Chưa đọc được tên đường ở vị trí này. Xác nhận ghim xong, bạn nhập Tên đường, Số nhà nhé';
		}
	}

	/** Bấm Xác nhận trên bản đồ: ghim xa Phường/Xã đã chọn thì cảnh báo trước khi lưu */
	async function confirmMap() {
		if (!bigMap) { finishMap(); return; }
		const c = bigMap.getCenter();
		const btn = $('ab-map-confirm');
		btn.disabled = true;
		btn.textContent = 'Đang kiểm tra vị trí...';
		let warn = null;
		try {
			const center = await wardCenter(st.ward, st.province);
			if (center) {
				const km = distanceKm(c.lat, c.lng, center[0], center[1]);
				if (km > warnLimitKm(st.ward)) warn = { km };
			}
		} catch (e) { /* không kiểm tra được thì cho qua */ }
		btn.disabled = false;
		btn.textContent = 'Xác Nhận';

		if (warn) {
			$('ab-warn-text').textContent = `Vị trí ghim cách trung tâm ${st.ward} khoảng ${warn.km.toFixed(1)} km, có thể không thuộc khu vực bạn đã chọn.`;
			$('ab-map-warn').classList.remove('d-none');
			return;
		}
		finishMap();
	}

	/** Lưu toạ độ ghim và quay về form */
	function finishMap() {
		if (bigMap) {
			const c = bigMap.getCenter();
			st.lat = Number(c.lat.toFixed(6));
			st.lng = Number(c.lng.toFixed(6));
		}
		$('ab-map-warn').classList.add('d-none');
		$('ab-form-err').textContent = '';
		show('form');
	}

	// ================= LƯU / XOÁ =================
	async function submit() {
		$('ab-form-err').textContent = '';
		$('ab-phone-err').textContent = phoneError();
		$('ab-name-err').textContent = $('ab-name').value.trim() ? '' : 'Vui lòng nhập Họ và tên';
		if (!formReady()) return;
		const btn = $('ab-submit');
		btn.disabled = true;
		try {
			const body = JSON.stringify({
				receiverName: $('ab-name').value.trim(),
				receiverPhone: $('ab-phone').value.trim(),
				province: st.province,
				ward: st.ward,
				street: st.street,
				addressLine: `${st.street}, ${st.ward}, ${st.province}`,
				latitude: st.lat,
				longitude: st.lng,
				addressType: st.type,
				note: $('ab-note').value.trim(),
				isDefault: $('ab-default').checked,
			});
			const saved = st.id
				? await api('/api/addresses/' + st.id, { method: 'PUT', body })
				: await api('/api/addresses', { method: 'POST', body });
			modal.hide();
			if (st.onSaved) st.onSaved(saved);
		} catch (e) {
			$('ab-form-err').textContent = e.message;
		} finally {
			updateSubmit();
		}
	}

	async function remove() {
		if (!st.id || !confirm('Xoá địa chỉ này khỏi sổ địa chỉ?')) return;
		try {
			const list = await api('/api/addresses/' + st.id, { method: 'DELETE' });
			const deletedId = st.id;
			modal.hide();
			if (st.onDeleted) st.onDeleted(list, deletedId);
		} catch (e) {
			$('ab-form-err').textContent = e.message;
		}
	}

	// ================= KHỞI TẠO (1 lần) =================
	let wired = false;
	function wire() {
		if (wired) return;
		wired = true;
		modal = new bootstrap.Modal($('abModal'));
		$('ab-back').addEventListener('click', back);
		$('ab-open-area').addEventListener('click', async () => {
			try {
				await loadAreas();
				st.pickProvince = st.province;
				st.pickStep = st.province ? 'ward' : 'province';
				show('area');
			} catch (e) {
				$('ab-form-err').textContent = e.message;
			}
		});
		$('ab-open-street').addEventListener('click', () => show('street'));
		$('ab-area-search').addEventListener('input', renderArea);
		$('ab-area-reset').addEventListener('click', () => {
			st.pickProvince = '';
			st.pickStep = 'province';
			$('ab-area-search').value = '';
			renderArea();
		});
		$('ab-street-input').addEventListener('input', () => renderStreetList([]));
		$('ab-street-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); searchStreet(); } });
		$('ab-street-clear').addEventListener('click', () => { $('ab-street-input').value = ''; renderStreetList([]); $('ab-street-input').focus(); });
		$('ab-street-search').addEventListener('click', searchStreet);
		$('ab-map-change').addEventListener('click', () => show('street'));
		$('ab-map-locate').addEventListener('click', locateOnMap);
		$('ab-map-confirm').addEventListener('click', confirmMap);
		$('ab-warn-keep').addEventListener('click', finishMap);                  // vẫn dùng vị trí ghim này
		$('ab-warn-fix').addEventListener('click', () => $('ab-map-warn').classList.add('d-none')); // kéo lại ghim
		$('ab-warn-area').addEventListener('click', () => {                    // chọn lại Phường/Xã
			$('ab-map-warn').classList.add('d-none');
			$('ab-open-area').click();
		});
		$('ab-submit').addEventListener('click', submit);
		$('ab-delete').addEventListener('click', remove);
		$('ab-name').addEventListener('input', () => { $('ab-name-err').textContent = ''; updateSubmit(); });
		$('ab-phone').addEventListener('input', () => { if ($('ab-phone-err').textContent) $('ab-phone-err').textContent = phoneError(); updateSubmit(); });
		$('ab-phone').addEventListener('blur', () => { $('ab-phone-err').textContent = phoneError(); });
		document.querySelectorAll('#abModal .ab-chip').forEach((c) => c.addEventListener('click', () => {
			st.type = c.dataset.type;
			renderForm();
		}));
		$('ab-default').addEventListener('change', () => { st.isDefault = $('ab-default').checked; });
		$('ab-note').addEventListener('input', () => { $('ab-note-count').textContent = $('ab-note').value.length; });
	}

	/** Mở modal. address = null: thêm mới */
	function open({ address = null, defaults = {}, isFirst = false, onSaved = null, onDeleted = null } = {}) {
		wire();
		const a = address;
		st.id = a ? a.id : null;
		st.province = (a && a.province) || '';
		st.ward = (a && a.ward) || '';
		// Địa chỉ cũ (chưa tách Tỉnh/Phường): đưa cả dòng vào ô tên đường, khách chọn lại khu vực
		st.street = a ? (a.street || (a.province ? '' : a.addressLine) || '') : '';
		st.lat = a && a.latitude != null ? Number(a.latitude) : null;
		st.lng = a && a.longitude != null ? Number(a.longitude) : null;
		st.type = (a && a.addressType) || 'HOME';
		st.isDefault = a ? !!a.isDefault : isFirst;
		st.lockDefault = !!(a && a.isDefault); // đang là mặc định thì không bỏ được (luôn cần 1 mặc định)
		st.onSaved = onSaved;
		st.onDeleted = onDeleted;
		$('ab-name').value = a ? a.receiverName : (defaults.name || '');
		$('ab-phone').value = a ? a.receiverPhone : (defaults.phone || '');
		$('ab-note').value = (a && a.note) || '';
		$('ab-note-count').textContent = $('ab-note').value.length;
		$('ab-name-err').textContent = '';
		$('ab-phone-err').textContent = '';
		$('ab-form-err').textContent = a && !a.province
			? 'Địa chỉ này lưu theo kiểu cũ, hãy chọn lại Tỉnh/Thành Phố và Phường/Xã' : '';
		$('ab-delete').classList.toggle('d-none', !a);
		show('form');
		modal.show();
		$('abModal').addEventListener('shown.bs.modal', () => renderMiniMap(), { once: true });
	}

	return { open, getPosition, approxPosition };
})();
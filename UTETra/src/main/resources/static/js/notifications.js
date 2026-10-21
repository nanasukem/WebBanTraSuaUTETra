/* ============================================================
   notifications.js - Chuông thông báo (User, Manager, Admin)
   - Mở trang: GET /api/notifications -> số chưa đọc + 20 thông báo mới nhất
   - Thông báo mới tới qua WebSocket /user/queue/notifications -> hiện lên ĐẦU danh sách, chuông rung, popup
   - Bấm 1 thông báo: đánh dấu đã đọc rồi mở trang liên quan; "Đánh dấu đã đọc hết"
   Cần: Bootstrap JS, live-socket.js nạp trước.
   ============================================================ */
(() => {
	const toggle = document.getElementById('nb-toggle');
	if (!toggle) return;
	const list = document.getElementById('nb-list');
	const count = document.getElementById('nb-count');
	const ICON = {
		ORDER: 'bi-receipt text-success', REVIEW: 'bi-star-fill text-warning',
		VOUCHER: 'bi-ticket-perforated text-danger', USER: 'bi-person-plus text-primary',
	};
	const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
		(c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

	function setCount(n) {
		count.textContent = n > 99 ? '99+' : n;
		count.classList.toggle('d-none', !(n > 0));
	}

	function itemHtml(n, isNew) {
		return `<a class="nb-item ${n.read ? '' : 'unread'} ${isNew ? 'nb-new' : ''}" data-id="${n.id}" href="${esc(n.link || '#')}">
			<span class="nb-icon"><i class="bi ${ICON[n.type] || 'bi-bell text-secondary'}"></i></span>
			<span class="nb-text">
				<span class="d-block nb-title small">${esc(n.title)}</span>
				${n.content ? `<span class="d-block small text-muted text-truncate">${esc(n.content)}</span>` : ''}
				<span class="d-block small text-muted" style="font-size:.75rem">${esc(n.time)}</span>
			</span></a>`;
	}

	function emptyIfNone() {
		if (!list.querySelector('.nb-item')) {
			list.innerHTML = '<div class="text-center text-muted small py-4"><i class="bi bi-bell-slash d-block fs-3 mb-1"></i>Chưa có thông báo</div>';
		}
	}

	async function load() {
		try {
			const res = await fetch('/api/notifications', { headers: { Accept: 'application/json' } });
			if (!res.ok || (res.redirected && res.url.includes('/auth/login'))) return;
			const data = await res.json();
			setCount(data.unread);
			list.innerHTML = (data.items || []).map((n) => itemHtml(n, false)).join('');
			emptyIfNone();
		} catch (e) { /* mạng lỗi: thử lại khi mở chuông */ }
	}

	// Bấm 1 thông báo: đánh dấu đã đọc rồi mới mở trang
	list.addEventListener('click', async (e) => {
		const a = e.target.closest('.nb-item');
		if (!a) return;
		e.preventDefault();
		const href = a.getAttribute('href');
		try {
			const res = await fetch('/api/notifications/' + a.dataset.id + '/read', { method: 'POST' });
			if (res.ok) setCount((await res.json()).unread);
		} catch (err) { /* vẫn mở trang */ }
		a.classList.remove('unread');
		if (href && href !== '#') location.href = href;
	});

	document.getElementById('nb-read-all').addEventListener('click', async () => {
		try {
			await fetch('/api/notifications/read-all', { method: 'POST' });
			setCount(0);
			list.querySelectorAll('.nb-item.unread').forEach((a) => a.classList.remove('unread'));
		} catch (e) { /* bỏ qua */ }
	});

	// Mở chuông thì tải lại cho mới
	toggle.addEventListener('show.bs.dropdown', load);

	function popup(n) {
		let wrap = document.getElementById('nb-toasts');
		if (!wrap) {
			wrap = document.createElement('div');
			wrap.id = 'nb-toasts';
			wrap.style.cssText = 'position:fixed;top:76px;right:16px;z-index:1100;width:min(340px,calc(100vw - 32px))';
			document.body.appendChild(wrap);
		}
		const el = document.createElement('div');
		el.className = 'toast border-0 shadow mb-2 w-100';
		el.innerHTML = `<div class="toast-body d-flex gap-2 align-items-start">
			<i class="bi ${ICON[n.type] || 'bi-bell'} fs-5"></i>
			<div class="flex-grow-1"><div class="fw-bold small">${esc(n.title)}</div>
			<div class="small text-muted">${esc(n.content || '')}</div></div>
			<button type="button" class="btn-close btn-sm" data-bs-dismiss="toast" aria-label="Đóng"></button></div>`;
		if (n.link) {
			el.style.cursor = 'pointer';
			el.addEventListener('click', (ev) => { if (!ev.target.closest('.btn-close')) location.href = n.link; });
		}
		wrap.prepend(el);
		if (window.bootstrap) new bootstrap.Toast(el, { delay: 6000 }).show(); else el.classList.add('show');
	}

	function ring() {
		const bell = toggle.querySelector('.bi-bell');
		bell.classList.remove('ring');
		void bell.offsetWidth; // chạy lại hiệu ứng
		bell.classList.add('ring');
	}

	load();
	if (window.LiveSocket) {
		LiveSocket.subscribe('/user/queue/notifications', (ev) => {
			if (!ev || !ev.notification) return;
			list.querySelector('.text-center')?.remove();
			list.insertAdjacentHTML('afterbegin', itemHtml(ev.notification, true)); // mới nhất lên đầu
			setCount(ev.unread);
			ring();
			popup(ev.notification);
		});
	}
})();

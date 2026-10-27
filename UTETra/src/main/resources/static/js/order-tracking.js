(() => {
	const box = document.getElementById('trkBox');
	if (!box) return;

	const orderId = Number(box.dataset.orderId);
	const flow = box.dataset.flow.split(',');
	const steps = Array.from(box.querySelectorAll('.trk-step'));
	const fill = document.getElementById('trkFill');
	const badge = document.getElementById('trkBadge');
	const log = document.getElementById('trkLog');
	const stopped = document.getElementById('trkStopped');
	const bar = document.getElementById('trkBar');
	const live = document.getElementById('trkLive');
	const liveText = document.getElementById('trkLiveText');

	const BADGE = {
		PENDING: 'text-bg-warning', CONFIRMED: 'text-bg-info', READY: 'text-bg-warning', PICKED_UP: 'text-bg-primary', DELIVERING: 'text-bg-primary',
		DELIVERED: 'text-bg-success', CANCELLED: 'text-bg-secondary', RETURNED: 'text-bg-danger',
	};
	const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
		(c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

	function paint(status) {
		const idx = flow.indexOf(status);
		const stop = status === 'CANCELLED' || status === 'RETURNED';
		bar.classList.toggle('opacity-50', stop);
		steps.forEach((el, i) => {
			el.classList.toggle('done', idx >= 0 && i <= idx);
			el.classList.toggle('current', idx >= 0 && i === idx);
		});
		const pct = idx <= 0 ? 0 : (idx / (flow.length - 1)) * 100;
		fill.style.width = pct + '%';
		fill.parentElement.style.left = (50 / flow.length) + '%';
		fill.parentElement.style.right = (50 / flow.length) + '%';
		box.dataset.status = status;
	}

	function addLog(title, sub) {
		const time = new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
		const li = document.createElement('li');
		li.className = 'd-flex gap-2 mb-2';
		li.innerHTML = `<i class="bi bi-circle-fill text-success" style="font-size:.6rem;margin-top:.45rem"></i>
			<div><div class="fw-semibold">${esc(title)}</div><div class="small text-muted">${esc(time)}${sub ? ' · ' + esc(sub) : ''}</div></div>`;
		log.prepend(li);
	}

	async function sync() {
		try {
			const res = await fetch('/orders/' + orderId + '/status', { headers: { 'Accept': 'application/json' } });
			if (!res.ok || (res.redirected && res.url.includes('/auth/login'))) return;
			const s = await res.json();
			update({ status: s.status, statusLabel: s.statusLabel, message: s.message });
		} catch (e) {  }
	}

	function setLive(on) {
		const wasOn = live.classList.contains('on');
		live.classList.toggle('on', on);
		liveText.textContent = on ? 'Đang cập nhật trực tiếp' : 'Mất kết nối, đang thử lại...';
		if (on && !wasOn) sync();
	}

	paint(box.dataset.status);
	setInterval(sync, 60000);
	if (!window.LiveSocket) { setLive(false); return; }

	LiveSocket.subscribe('/user/queue/orders', (ev) => {
		if (!ev || ev.orderId !== orderId || ev.type !== 'STATUS') return;
		update(ev);
	}, setLive);

	function update(ev) {
		if (!ev.status || ev.status === box.dataset.status) return;
		paint(ev.status);
		badge.className = 'badge ' + (BADGE[ev.status] || 'text-bg-secondary');
		const cancelBtn = document.getElementById('trkCancel');
		if (cancelBtn && ev.status !== 'PENDING') cancelBtn.remove();
		badge.textContent = ev.statusLabel;
		addLog(ev.statusLabel, ev.message);

		if (ev.status === 'CANCELLED' || ev.status === 'RETURNED') {
			stopped.className = 'alert mb-3 ' + (ev.status === 'CANCELLED' ? 'alert-secondary' : 'alert-danger');
			stopped.textContent = ev.status === 'CANCELLED'
				? (ev.statusLabel || 'Đơn đã huỷ') + '. ' + (ev.message || '') : (ev.title || 'Giao không thành công') + '. ' + (ev.message || '');
		}
		if (ev.status === 'READY' && flow.length === 4) {
			document.getElementById('trkHint').innerHTML = '<b class="text-success">Món đã xong! Mời bạn đến quán lấy.</b>';
		}
		if (ev.status === 'DELIVERED') {
			document.getElementById('trkHint').innerHTML =
				'Đơn đã hoàn tất. <a class="link-success fw-semibold" href="/orders/' + orderId + '/review">Đánh giá món và quán</a>';
		}
		if (navigator.vibrate) navigator.vibrate(120);
	}
})();

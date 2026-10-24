(() => {
	const root = document.getElementById('manager-orders');
	if (!root || !root.dataset.branchId) return;
	const branchId = Number(root.dataset.branchId);
	const $ = (id) => document.getElementById(id);

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
		try { data = await res.json(); } catch (e) {  }
		if (!res.ok) throw new Error((data && data.message) || 'Có lỗi xảy ra, vui lòng thử lại');
		return data;
	}

	let audioCtx = null;
	let soundOn = true;
	try { soundOn = localStorage.getItem('vo-sound') !== '0'; } catch (e) {  }

	function ensureAudio() {
		if (!audioCtx) {
			const Ctx = window.AudioContext || window.webkitAudioContext;
			if (Ctx) audioCtx = new Ctx();
		}
		if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
	}

	function ring() {
		if (!soundOn || !audioCtx || audioCtx.state !== 'running') return;
		[0, 0.22, 0.44].forEach((t, i) => {
			const osc = audioCtx.createOscillator();
			const gain = audioCtx.createGain();
			const start = audioCtx.currentTime + t;
			osc.type = 'sine';
			osc.frequency.value = i === 1 ? 1046 : 880;
			gain.gain.setValueAtTime(0.0001, start);
			gain.gain.exponentialRampToValueAtTime(0.4, start + 0.02);
			gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.2);
			osc.connect(gain).connect(audioCtx.destination);
			osc.start(start);
			osc.stop(start + 0.22);
		});
	}

	function renderSoundBtn() {
		const ready = audioCtx && audioCtx.state === 'running';
		const btn = $('vo-sound');
		btn.className = 'btn btn-sm ms-auto ' + (soundOn ? 'btn-success' : 'btn-outline-secondary');
		btn.innerHTML = soundOn
			? (ready ? '<i class="bi bi-volume-up-fill"></i> Chuông: Bật'
				: '<i class="bi bi-volume-up"></i> Bấm để bật chuông')
			: '<i class="bi bi-volume-mute"></i> Chuông: Tắt';
	}

	$('vo-sound').addEventListener('click', () => {
		const ready = audioCtx && audioCtx.state === 'running';
		if (soundOn && !ready) {
			ensureAudio();
		} else {
			soundOn = !soundOn;
			if (soundOn) ensureAudio();
		}
		try { localStorage.setItem('vo-sound', soundOn ? '1' : '0'); } catch (e) {  }
		setTimeout(() => { renderSoundBtn(); ring(); }, 100);
	});
	document.addEventListener('click', () => { if (soundOn) { ensureAudio(); setTimeout(renderSoundBtn, 100); } }, { once: true });
	renderSoundBtn();

	const baseTitle = document.title;
	let unseen = 0;
	function markUnseen() {
		if (!document.hidden) return;
		unseen++;
		document.title = `(${unseen}) Đơn mới - ${baseTitle}`;
	}
	document.addEventListener('visibilitychange', () => {
		if (!document.hidden) { unseen = 0; document.title = baseTitle; }
	});

	function popup(ev) {
		const el = document.createElement('div');
		el.className = 'toast text-bg-success border-0 mb-2';
		el.setAttribute('role', 'alert');
		el.innerHTML = `
			<div class="d-flex">
				<div class="toast-body">
					<div class="fw-bold"><i class="bi bi-bell-fill me-1"></i>${esc(ev.title)}</div>
					<div class="small">${esc(ev.message)}</div>
				</div>
				<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Đóng"></button>
			</div>`;
		$('vo-toasts').prepend(el);
		el.addEventListener('click', (e) => {
			if (e.target.closest('.btn-close')) return;
			const card = document.querySelector(`[data-order="${ev.orderId}"]`);
			if (card) card.scrollIntoView({ behavior: 'smooth', block: 'center' });
		});
		const toast = new bootstrap.Toast(el, { delay: 12000 });
		el.addEventListener('hidden.bs.toast', () => el.remove());
		toast.show();
	}

	const highlight = new Set();
	let loadSeq = 0;

	async function load() {
		const my = ++loadSeq;
		try {
			const board = await api('/api/manager/orders?branchId=' + branchId);
			if (my !== loadSeq) return;
			column('new', board.newOrders, 'Chưa có đơn mới');
			column('making', board.making, 'Không có đơn đang pha');
			column('shipping', board.shipping, 'Không có đơn đang giao');
			column('recent', board.recent, 'Chưa có đơn hoàn tất');
			$('vo-error').textContent = '';
		} catch (e) {
			$('vo-error').textContent = e.message;
		}
	}

	function column(key, list, emptyText) {
		$('cnt-' + key).textContent = list.length;
		$('col-' + key).innerHTML = list.length
			? list.map(card).join('')
			: `<div class="small text-muted text-center py-4">${emptyText}</div>`;
	}

	function card(o) {
		const buttons = o.actions.map((a) => `
			<button type="button" class="btn btn-sm ${a.style} flex-grow-1 fw-semibold" data-id="${o.id}" data-to="${a.to}">
				${esc(a.label)}</button>`).join('');
		const cancel = o.canCancel
			? `<button type="button" class="btn btn-sm btn-outline-danger" data-cancel="${o.id}">Huỷ</button>` : '';
		return `
		<div class="card vo-card mb-2 ${highlight.has(o.id) ? 'vo-new' : ''}" data-order="${o.id}">
			<div class="card-body p-2">
				<div class="d-flex justify-content-between align-items-center">
					<div class="fw-bold">#${o.id} <span class="small text-muted fw-normal">${esc(o.time)}</span></div>
					<span class="badge ${o.pickup ? 'text-bg-dark' : 'text-bg-light border'}">
						${o.pickup ? '<i class="bi bi-shop"></i> Đến lấy' : '<i class="bi bi-scooter"></i> Giao'}</span>
				</div>
				<ul class="small ps-3">${o.items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul>
				${o.note ? `<div class="small text-danger"><i class="bi bi-chat-left-text"></i> ${esc(o.note)}</div>` : ''}
				<div class="small text-muted"><i class="bi bi-person"></i> ${esc(o.receiverName)} · ${esc(o.receiverPhone)}</div>
				${o.deliveryAddress ? `<div class="small text-muted"><i class="bi bi-geo-alt"></i> ${esc(o.deliveryAddress)}</div>` : ''}
				${o.deliveryNote ? `<div class="small fst-italic text-secondary"><i class="bi bi-sticky"></i> ${esc(o.deliveryNote)}</div>` : ''}
				${o.pickup ? '' : (o.shipperLabel
					? `<div class="small text-success fw-semibold"><i class="bi bi-scooter"></i> Shipper: ${esc(o.shipperLabel)}</div>`
					: (o.status === 'READY' ? '<div class="small text-warning"><i class="bi bi-scooter"></i> Chờ shipper nhận</div>' : ''))}
				<div class="d-flex justify-content-between align-items-center mt-1">
					<span class="badge ${o.badgeClass}">${esc(o.statusLabel)}</span>
					<b class="text-danger">${fmt(o.totalAmount)}</b>
				</div>
				<div class="small text-muted">${o.totalQty} ly ·
					<span class="badge rounded-pill ${o.prepaid ? 'text-bg-success' : 'text-bg-light border text-dark'}">
					<i class="bi ${o.prepaid ? 'bi-check-circle-fill' : 'bi-cash-coin'} me-1"></i>${esc(o.paymentLabel)}</span></div>
				${o.hint ? `<div class="small text-primary mt-1"><i class="bi bi-info-circle"></i> ${esc(o.hint)}</div>` : ''}
				${buttons || cancel ? `<div class="d-flex gap-1 mt-2">${buttons}${cancel}</div>` : ''}
			</div>
		</div>`;
	}

	async function changeStatus(orderId, to, reason) {
		await api(`/api/manager/orders/${orderId}/status`, {
			method: 'POST', body: JSON.stringify({ status: to, reason: reason || null }),
		});
		highlight.delete(orderId);
		await load();
	}

	root.addEventListener('click', async (e) => {
		const btn = e.target.closest('button[data-to]');
		if (btn) {
			btn.disabled = true;
			try {
				await changeStatus(Number(btn.dataset.id), btn.dataset.to);
			} catch (err) {
				$('vo-error').textContent = err.message;
				btn.disabled = false;
			}
			return;
		}
		const cancelBtn = e.target.closest('button[data-cancel]');
		if (cancelBtn) openCancel(Number(cancelBtn.dataset.cancel));
	});

	let cancelId = null;
	let cancelModal = null;
	function openCancel(orderId) {
		cancelId = orderId;
		$('vo-cancel-id').textContent = orderId;
		$('vo-cancel-error').textContent = '';
		$('vo-reason-text').value = '';
		document.querySelectorAll('input[name=vo-reason]').forEach((r) => { r.checked = false; });
		if (!cancelModal) cancelModal = new bootstrap.Modal($('vo-cancel-modal'));
		cancelModal.show();
	}

	$('vo-cancel-confirm').addEventListener('click', async () => {
		const picked = document.querySelector('input[name=vo-reason]:checked');
		let reason = picked ? picked.value : '';
		if (reason === 'OTHER') reason = $('vo-reason-text').value.trim();
		if (!reason) {
			$('vo-cancel-error').textContent = 'Vui lòng chọn hoặc ghi lý do huỷ';
			return;
		}
		const btn = $('vo-cancel-confirm');
		btn.disabled = true;
		try {
			await changeStatus(cancelId, 'CANCELLED', reason);
			cancelModal.hide();
		} catch (err) {
			$('vo-cancel-error').textContent = err.message;
		} finally {
			btn.disabled = false;
		}
	});

	function setLive(online) {
		const el = $('vo-live');
		el.className = 'badge ' + (online ? 'text-bg-success' : 'text-bg-secondary');
		el.innerHTML = online
			? '<i class="bi bi-broadcast"></i> Đang nhận đơn trực tiếp'
			: '<i class="bi bi-wifi-off"></i> Mất kết nối, đang thử lại...';
		if (online) load();
	}

	LiveSocket.subscribe('/user/queue/manager-orders', (ev) => {
		if (ev.branchId !== branchId) return;
		if (ev.type === 'NEW') {
			highlight.add(ev.orderId);
			ring();
			popup(ev);
			markUnseen();
		}
		load();
	}, setLive);

	setInterval(load, 60000);
	load();
})();
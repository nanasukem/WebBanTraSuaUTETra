(function () {
	const main = document.getElementById('pr-main');
	if (!main) return;
	const orderId = main.dataset.orderId;
	const state = main.dataset.state;
	const deadline = Number(main.dataset.deadline || 0);

	const counters = document.querySelectorAll('[data-countdown]');
	if (deadline > 0 && counters.length) {
		const tick = () => {
			const left = Math.max(0, deadline - Date.now());
			const m = String(Math.floor(left / 60000)).padStart(2, '0');
			const s = String(Math.floor((left % 60000) / 1000)).padStart(2, '0');
			counters.forEach((c) => (c.textContent = `${m}:${s}`));
			if (left <= 0) {
				clearInterval(timer);
				setTimeout(() => location.reload(), 70000);
			}
		};
		const timer = setInterval(tick, 1000);
		tick();
	}

	if (state === 'PROCESSING' || state === 'FAILED') {
		let n = 0;
		const poll = async () => {
			n++;
			try {
				const refresh = state === 'PROCESSING' && n % 3 === 0;
				const res = await fetch(`/payment/${orderId}/status?refresh=${refresh}`, { headers: { Accept: 'application/json' } });
				if (res.ok) {
					const d = await res.json();
					const now = d.orderStatus !== 'AWAITING_PAYMENT' ? 'DONE'
						: d.transactionStatus === 'PENDING' ? 'PROCESSING' : 'FAILED';
					if (now === 'DONE' || now !== state) return location.reload();
				}
			} catch (e) {  }
			setTimeout(poll, state === 'PROCESSING' ? 4000 : 15000);
		};
		setTimeout(poll, 3000);
	}

	const back = document.getElementById('pr-back-edit');
	if (back) back.addEventListener('click', async (e) => {
		e.preventDefault();
		if (!confirm('Huỷ đơn chưa thanh toán và quay lại giỏ hàng để sửa?')) return;
		try {
			const res = await fetch(`/api/checkout/${orderId}/return-to-checkout`, { method: 'POST' });
			const d = await res.json();
			if (!res.ok) throw new Error(d.message || 'Không quay lại được');
			location.href = d.redirectUrl || '/checkout';
		} catch (err) {
			alert(err.message);
		}
	});
})();

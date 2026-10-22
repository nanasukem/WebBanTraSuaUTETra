/*
 * Trang kết quả thanh toán:
 * - Đếm ngược hạn giữ đơn (quá hạn hệ thống tự huỷ)
 * - Đang chờ cổng xác nhận: hỏi /payment/{id}/status mỗi 4 giây (3 lần hỏi thì nhờ server hỏi lại VNPAY/MoMo 1 lần),
 *   trạng thái đổi thì tải lại trang
 * - "Quay lại sửa đơn": huỷ đơn chưa trả, cho món lại vào giỏ rồi về trang thanh toán
 */
(function () {
	const main = document.getElementById('pr-main');
	if (!main) return;
	const orderId = main.dataset.orderId;
	const state = main.dataset.state;
	const deadline = Number(main.dataset.deadline || 0);

	// Đếm ngược
	const counters = document.querySelectorAll('[data-countdown]');
	if (deadline > 0 && counters.length) {
		const tick = () => {
			const left = Math.max(0, deadline - Date.now());
			const m = String(Math.floor(left / 60000)).padStart(2, '0');
			const s = String(Math.floor((left % 60000) / 1000)).padStart(2, '0');
			counters.forEach((c) => (c.textContent = `${m}:${s}`));
			if (left <= 0) {
				clearInterval(timer);
				setTimeout(() => location.reload(), 70000); // hệ thống huỷ đơn quá hạn mỗi phút
			}
		};
		const timer = setInterval(tick, 1000);
		tick();
	}

	// Hỏi trạng thái
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
			} catch (e) { /* mất mạng: thử lại lần sau */ }
			setTimeout(poll, state === 'PROCESSING' ? 4000 : 15000);
		};
		setTimeout(poll, 3000);
	}

	// Quay lại sửa đơn
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

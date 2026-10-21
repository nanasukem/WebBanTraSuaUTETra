/* ============================================================
   order-review.js - Trang "Đánh giá đơn hàng" (orders/review.html), như app Katinat / Highlands
   - Chọn sao (mặc định 5) cho từng món và cho quán; "Để sau" để bỏ qua 1 món
   - Gợi ý nhanh: bấm để tô sáng / bỏ chọn (checkbox kiểu nút, gửi lên thành nhãn); xem trước ảnh/video
   (Server - ReviewService - kiểm tra lại toàn bộ)
   ============================================================ */
(() => {
	const form = document.getElementById('orderReviewForm');
	if (!form) return;

	const MAX_FILES = 5, MAX_IMG = 5 * 1024 * 1024, MAX_VIDEO = 30 * 1024 * 1024;
	const TEXTS = ['', 'Tệ', 'Không hài lòng', 'Bình thường', 'Hài lòng', 'Tuyệt vời'];

	// ===== Sao (dùng chung cho món và quán) =====
	form.querySelectorAll('.rv-stars').forEach((box) => {
		const wrap = box.parentElement;
		// ô lưu số sao + chữ mô tả nằm cùng hàng với các ngôi sao
		const input = wrap.querySelector('.rv-rating') || box.closest('.rv-form')?.querySelector('.rv-rating');
		const text = wrap.querySelector('.rv-star-text');
		if (!input) return;
		const paint = (v) => {
			box.querySelectorAll('.rv-star').forEach((b) => {
				b.querySelector('i').className = Number(b.dataset.value) <= v ? 'bi bi-star-fill' : 'bi bi-star';
			});
			if (text) text.textContent = TEXTS[v] || '';
		};
		box.addEventListener('click', (e) => {
			const b = e.target.closest('.rv-star');
			if (!b) return;
			input.value = b.dataset.value;
			const item = b.closest('.rv-item');
			if (item) setSkipped(item, false);
			paint(Number(b.dataset.value));
		});
		paint(Number(input.value) || 5);
	});

	// ===== Từng món =====
	const items = Array.from(form.querySelectorAll('.rv-item')).filter((it) => it.querySelector('.rv-form'));

	function setSkipped(item, skip) {
		item.dataset.skip = skip ? '1' : '';
		item.querySelector('.rv-rating').disabled = skip; // ô bị tắt thì không gửi lên -> server bỏ qua món này
		item.querySelector('.rv-body').classList.toggle('d-none', skip);
		item.querySelector('.rv-stars').classList.toggle('opacity-50', skip);
		item.querySelector('.rv-skip').textContent = skip ? 'Đánh giá món này' : 'Để sau';
		item.querySelectorAll('.rv-body input, .rv-body textarea').forEach((el) => { el.disabled = skip; });
	}

	function filesOk(item) {
		const files = Array.from(item.querySelector('.rv-media').files || []);
		let msg = '';
		if (files.length > MAX_FILES) msg = 'Chỉ chọn tối đa ' + MAX_FILES + ' file';
		files.forEach((f) => {
			const v = f.type.startsWith('video/'), img = f.type.startsWith('image/');
			if (!v && !img) msg = msg || ('"' + f.name + '" không phải ảnh/video');
			else if (f.size > (v ? MAX_VIDEO : MAX_IMG)) msg = msg || ('"' + f.name + '" quá lớn');
		});
		item.querySelector('.rv-media-error').textContent = msg;
		return !msg;
	}

	items.forEach((item) => {
		item.querySelector('.rv-skip').addEventListener('click', () => setSkipped(item, !item.dataset.skip));
		const media = item.querySelector('.rv-media');
		media.addEventListener('change', () => {
			const preview = item.querySelector('.rv-preview');
			preview.innerHTML = '';
			Array.from(media.files || []).forEach((f) => {
				const v = f.type.startsWith('video/');
				const el = document.createElement(v ? 'video' : 'img');
				el.src = URL.createObjectURL(f);
				el.className = 'rounded-3 border';
				el.style.cssText = 'width:72px;height:72px;object-fit:cover';
				if (v) el.muted = true;
				preview.appendChild(el);
			});
			filesOk(item);
		});
	});

	// ===== Gửi =====
	form.addEventListener('submit', (e) => {
		const bad = items.filter((item) => !item.dataset.skip && !filesOk(item));
		if (bad.length) {
			e.preventDefault();
			bad[0].scrollIntoView({ behavior: 'smooth', block: 'center' });
			return;
		}
		const btn = document.getElementById('rvSubmit');
		if (btn) {
			btn.disabled = true;
			btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang gửi...';
		}
	});
})();

/* ============================================================
   review-form.js - Form "Viết đánh giá" trên trang chi tiết món
   - Chọn sao (1..5), đếm ký tự bình luận (tối thiểu 50), xem trước ảnh/video
   - Kiểm tra trước khi gửi: đủ 50 ký tự, tối đa 5 file, ảnh <= 5MB, video <= 30MB
   (Server - ReviewService - vẫn kiểm tra lại toàn bộ)
   ============================================================ */
(() => {
	const form = document.getElementById('reviewForm');
	if (!form) return;

	const MIN = 50, MAX_FILES = 5, MAX_IMG = 5 * 1024 * 1024, MAX_VIDEO = 30 * 1024 * 1024;
	const TEXTS = ['', 'Tệ', 'Không hài lòng', 'Bình thường', 'Hài lòng', 'Tuyệt vời'];

	// ===== Sao =====
	const ratingInput = document.getElementById('ratingInput');
	const stars = Array.from(form.querySelectorAll('.star-btn'));
	const ratingText = document.getElementById('ratingText');
	function paint(v) {
		stars.forEach((b) => {
			const on = Number(b.dataset.value) <= v;
			b.querySelector('i').className = on ? 'bi bi-star-fill' : 'bi bi-star';
		});
		ratingText.textContent = TEXTS[v] || '';
	}
	stars.forEach((b) => b.addEventListener('click', () => {
		ratingInput.value = b.dataset.value;
		paint(Number(b.dataset.value));
	}));
	paint(Number(ratingInput.value) || 5);

	// ===== Đếm ký tự =====
	const content = document.getElementById('reviewContent');
	const count = document.getElementById('contentCount');
	const hint = document.getElementById('contentHint');
	function updateCount() {
		const n = content.value.trim().length;
		count.textContent = n;
		if (n >= MIN) {
			hint.textContent = 'Đủ độ dài';
			hint.className = 'text-success';
		} else {
			hint.textContent = 'Cần thêm ' + (MIN - n) + ' ký tự';
			hint.className = 'text-danger';
		}
	}
	content.addEventListener('input', updateCount);
	updateCount();

	// ===== Ảnh / video =====
	const input = document.getElementById('reviewMedia');
	const preview = document.getElementById('mediaPreview');
	const mediaError = document.getElementById('mediaError');
	function checkFiles() {
		const files = Array.from(input.files || []);
		preview.innerHTML = '';
		let err = '';
		if (files.length > MAX_FILES) err = 'Chỉ chọn tối đa ' + MAX_FILES + ' file';
		files.forEach((f) => {
			const isVideo = f.type.startsWith('video/');
			const isImage = f.type.startsWith('image/');
			if (!isVideo && !isImage) err = err || ('"' + f.name + '" không phải ảnh/video');
			else if (f.size > (isVideo ? MAX_VIDEO : MAX_IMG)) err = err || ('"' + f.name + '" quá lớn');
			const url = URL.createObjectURL(f);
			const el = document.createElement(isVideo ? 'video' : 'img');
			el.src = url;
			el.className = 'rounded-3 border';
			el.style.cssText = 'width:96px;height:96px;object-fit:cover';
			if (isVideo) el.muted = true;
			preview.appendChild(el);
		});
		mediaError.textContent = err;
		return !err;
	}
	input.addEventListener('change', checkFiles);

	// ===== Gửi =====
	form.addEventListener('submit', (e) => {
		updateCount();
		const okText = content.value.trim().length >= MIN;
		const okFiles = checkFiles();
		if (!okText || !okFiles) {
			e.preventDefault();
			if (!okText) content.focus();
			return;
		}
		const btn = document.getElementById('reviewSubmit');
		btn.disabled = true;
		btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang gửi...';
	});
})();

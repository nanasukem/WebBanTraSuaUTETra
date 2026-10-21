/* ============================================================
   favorite.js - Nút tim "Thích sản phẩm"
   Nút: <button class="js-favorite" data-id="12">  (trang chi tiết món, trang Yêu thích)
   - Bấm: POST /api/favorites/{id}/toggle -> đổi màu tim + cập nhật [data-fav-count]
   - Trang Yêu thích: nút có data-remove-on-unfav -> bỏ thích thì ẩn thẻ món (.fav-item)
   - Chưa đăng nhập / hết phiên -> chuyển sang trang đăng nhập
   ============================================================ */
(() => {
	document.addEventListener('click', async (e) => {
		const btn = e.target.closest('.js-favorite');
		if (!btn || btn.disabled) return;
		e.preventDefault();
		btn.disabled = true;
		try {
			const res = await fetch('/api/favorites/' + encodeURIComponent(btn.dataset.id) + '/toggle', {
				method: 'POST', headers: { 'Accept': 'application/json' },
			});
			if (res.status === 401 || res.status === 403 || (res.redirected && res.url.includes('/auth/login'))) {
				location.href = '/auth/login';
				return;
			}
			if (!res.ok) throw new Error(res.status);
			const data = await res.json();
			const on = !!data.favorite;

			btn.setAttribute('aria-pressed', on);
			btn.classList.toggle('btn-danger', on);
			btn.classList.toggle('btn-outline-danger', !on);
			const icon = btn.querySelector('i.bi');
			if (icon) {
				icon.classList.toggle('bi-heart-fill', on);
				icon.classList.toggle('bi-heart', !on);
			}
			const label = btn.querySelector('.fav-label');
			if (label) label.textContent = on ? 'Đã thích' : 'Thích';
			document.querySelectorAll('[data-fav-count]').forEach((el) => { el.textContent = data.count; });

			if (!on && btn.hasAttribute('data-remove-on-unfav')) {
				const item = btn.closest('.fav-item');
				if (item) item.remove();
				const left = document.querySelectorAll('.fav-item').length;
				const total = document.getElementById('favTotal');
				if (total) total.textContent = left;
				if (!left) document.getElementById('favEmpty')?.classList.remove('d-none');
			}
		} catch (err) {
			alert('Không thực hiện được, vui lòng thử lại.');
		} finally {
			btn.disabled = false;
		}
	});
})();

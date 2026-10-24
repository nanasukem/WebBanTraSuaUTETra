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

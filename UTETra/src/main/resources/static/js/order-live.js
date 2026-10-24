(() => {
    const box = document.querySelector('[data-live-orders]');
    if (!box || !window.LiveSocket) return;
    const orderId = Number(box.dataset.orderId || 0);
    const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
        (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    function toast(ev) {
        let wrap = document.getElementById('live-toasts');
        if (!wrap) {
            wrap = document.createElement('div');
            wrap.id = 'live-toasts';
            wrap.style.cssText = 'position:fixed;top:80px;right:16px;z-index:1100;width:min(340px,calc(100vw - 32px))';
            document.body.appendChild(wrap);
        }
        const el = document.createElement('div');
        el.className = 'toast text-bg-success border-0 mb-2 w-100';
        el.innerHTML = `<div class="toast-body"><div class="fw-bold"><i class="bi bi-bell-fill me-1"></i>${esc(ev.title)}</div>
			<div class="small">${esc(ev.message)}</div></div>`;
        wrap.prepend(el);
        new bootstrap.Toast(el, { delay: 6000 }).show();
    }

    LiveSocket.subscribe('/user/queue/orders', (ev) => {
        toast(ev);
        if (!orderId || ev.orderId === orderId) setTimeout(() => location.reload(), 1500);
    });
})();
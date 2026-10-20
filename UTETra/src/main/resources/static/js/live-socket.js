/* ============================================================
   live-socket.js - Kết nối WebSocket (STOMP) dùng chung
   - Tự nạp thư viện @stomp/stompjs, mạng lỗi thì trang vẫn chạy bình thường (chỉ mất cập nhật trực tiếp)
   - Server gửi theo tài khoản: /user/queue/... (người khác không nghe được)
   Cách dùng:
     LiveSocket.subscribe('/user/queue/orders', (ev) => {...}, (online) => {...});
   ============================================================ */
window.LiveSocket = (() => {
	const STOMP_URL = 'https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.0.0/bundles/stomp.umd.min.js';
	let loading = null;

	function loadStomp() {
		if (window.StompJs) return Promise.resolve();
		if (!loading) {
			loading = new Promise((resolve, reject) => {
				const s = document.createElement('script');
				s.src = STOMP_URL;
				s.async = true;
				s.onload = () => resolve();
				s.onerror = () => reject(new Error('Không tải được thư viện WebSocket'));
				document.head.appendChild(s);
			});
		}
		return loading;
	}

	/** onMessage(ev): ev là JSON server gửi; onStatus(true/false): đang kết nối hay mất kết nối */
	function subscribe(destination, onMessage, onStatus = () => {}) {
		loadStomp().then(() => {
			const client = new StompJs.Client({
				brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
				reconnectDelay: 5000, // rớt mạng thì 5 giây thử lại
				onConnect: () => {
					onStatus(true);
					client.subscribe(destination, (msg) => {
						let ev;
						try { ev = JSON.parse(msg.body); } catch (e) { return; }
						onMessage(ev);
					});
				},
				onWebSocketClose: () => onStatus(false),
				onStompError: () => onStatus(false),
			});
			client.activate();
		}).catch(() => onStatus(false));
	}

	return { subscribe };
})();
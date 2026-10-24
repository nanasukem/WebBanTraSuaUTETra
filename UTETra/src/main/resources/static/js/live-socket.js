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

	function subscribe(destination, onMessage, onStatus = () => {}) {
		loadStomp().then(() => {
			const client = new StompJs.Client({
				brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
				reconnectDelay: 5000,
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
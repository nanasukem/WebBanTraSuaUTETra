package vn.utetra.controller.api;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.utetra.service.CurrentUserService;
import vn.utetra.service.NotificationService;

/**
 * Chuông thông báo (mọi tài khoản đã đăng nhập):
 * GET  /api/notifications            -> {unread, items: 20 thông báo mới nhất}
 * POST /api/notifications/{id}/read  -> {link} (đánh dấu đã đọc, trả về trang cần mở)
 * POST /api/notifications/read-all   -> {updated}
 * Thông báo mới tới trực tiếp qua WebSocket /user/queue/notifications (NotificationService).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

	private final NotificationService notificationService;
	private final CurrentUserService currentUserService;

	public NotificationApiController(NotificationService notificationService, CurrentUserService currentUserService) {
		this.notificationService = notificationService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	public Map<String, Object> list(Principal principal, HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		return Map.of("unread", notificationService.unreadCount(userId), "items", notificationService.latest(userId));
	}

	@PostMapping("/{id}/read")
	public Map<String, Object> read(@PathVariable("id") Integer id, Principal principal, HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		Map<String, Object> res = new HashMap<>();
		res.put("link", notificationService.markRead(userId, id));
		res.put("unread", notificationService.unreadCount(userId));
		return res;
	}

	@PostMapping("/read-all")
	public Map<String, Object> readAll(Principal principal, HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		return Map.of("updated", notificationService.markAllRead(userId), "unread", 0);
	}
}

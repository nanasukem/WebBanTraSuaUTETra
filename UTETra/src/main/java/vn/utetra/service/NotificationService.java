package vn.utetra.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vn.utetra.dto.response.NotificationRes;
import vn.utetra.entity.Notification;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.repository.NotificationRepository;
import vn.utetra.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class NotificationService {

	public static final String QUEUE = "/queue/notifications";
	public static final int LIST_SIZE = 20;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final SimpMessagingTemplate messagingTemplate;

	public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
			SimpMessagingTemplate messagingTemplate) {
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
		this.messagingTemplate = messagingTemplate;
	}

	@Transactional
	public Notification create(User to, Order order, String type, String title, String content, String link) {
		return create(to, order, type, title, content, link, null);
	}

	@Transactional
	public Notification create(User to, Order order, String type, String title, String content, String link,
			String refKey) {
		if (to == null) return null;
		to = userRepository.findById(to.getId()).orElse(to);
		if (refKey != null && notificationRepository.existsByUserIdAndRefKey(to.getId(), refKey)) return null;
		Notification n = new Notification();
		n.setUser(to);
		n.setOrder(order);
		n.setType(type == null ? "ORDER" : type);
		n.setTitle(cut(title, 150));
		n.setContent(cut(content, 500));
		n.setLink(link);
		n.setRefKey(refKey);
		n.setCreatedAt(LocalDateTime.now());
		notificationRepository.save(n);
		long unread = notificationRepository.countByUserIdAndIsReadFalse(to.getId());
		push(to.getUsername(), Map.of("notification", toRes(n), "unread", unread));
		return n;
	}

	@Transactional
	public int notifyAdmins(String type, String title, String content, String link, String refKey) {
		int sent = 0;
		for (User admin : userRepository.findByRole_Name("ADMIN")) {
			if (create(admin, null, type, title, content, link, refKey) != null) sent++;
		}
		return sent;
	}

	public List<NotificationRes> latest(Integer userId) {
		return notificationRepository.findTop20ByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(this::toRes).toList();
	}

	public long unreadCount(Integer userId) {
		return notificationRepository.countByUserIdAndIsReadFalse(userId);
	}

	@Transactional
	public String markRead(Integer userId, Integer id) {
		return notificationRepository.findByIdAndUserId(id, userId).map(n -> {
			n.setIsRead(true);
			return n.getLink();
		}).orElse(null);
	}

	@Transactional
	public int markAllRead(Integer userId) {
		return notificationRepository.markAllRead(userId);
	}

	private NotificationRes toRes(Notification n) {
		return new NotificationRes(n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getLink(),
				ago(n.getCreatedAt()), Boolean.TRUE.equals(n.getIsRead()));
	}

	static String ago(LocalDateTime t) {
		if (t == null) return "";
		Duration d = Duration.between(t, LocalDateTime.now());
		if (d.toMinutes() < 1) return "Vừa xong";
		if (d.toMinutes() < 60) return d.toMinutes() + " phút trước";
		if (d.toHours() < 24) return d.toHours() + " giờ trước";
		return t.format(DAY);
	}

	private static String cut(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}

	private void push(String username, Object payload) {
		Runnable send = () -> {
			try {
				messagingTemplate.convertAndSendToUser(username, QUEUE, payload);
			} catch (Exception e) {
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					send.run();
				}
			});
		} else {
			send.run();
		}
	}
}

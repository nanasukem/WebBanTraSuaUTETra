package vn.utetra.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utetra.dto.response.OrderEventRes;
import vn.utetra.entity.Notification;
import vn.utetra.entity.Order;
import vn.utetra.entity.OrderDetail;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.NotificationRepository;
import vn.utetra.service.impl.OrderFlowServiceImpl;

/**
 * Báo tin về đơn hàng:
 * 1. Lưu vào bảng Notifications (cùng transaction với đơn: đơn lỗi thì thông báo cũng không lưu).
 * 2. Đẩy WebSocket SAU KHI lưu đơn thành công (afterCommit), tới đúng người:
 *    - Quán (chủ chi nhánh): /user/queue/manager-orders
 *    - Khách:                /user/queue/orders
 *    Gửi theo tài khoản (convertAndSendToUser) nên người khác không nghe lén được.
 */
@Component
public class OrderNotifier {

	public static final String MANAGER_QUEUE = "/queue/manager-orders";
	public static final String CUSTOMER_QUEUE = "/queue/orders";

	private final NotificationRepository notificationRepository;
	private final SimpMessagingTemplate messagingTemplate;

	public OrderNotifier(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate) {
		this.notificationRepository = notificationRepository;
		this.messagingTemplate = messagingTemplate;
	}

	/** Khách vừa đặt đơn -> báo chủ chi nhánh (chuông + popup ở Quầy đơn hàng) */
	public void onNewOrder(Order o) {
		User owner = o.getBranch().getOwner();
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		int qty = o.getDetails().stream().mapToInt(OrderDetail::getQuantity).sum();
		String title = "Có đơn hàng mới #" + o.getId();
		String message = (pickup ? "Khách đến lấy" : "Giao tận nơi") + " · " + qty + " ly · "
				+ String.format("%,d", o.getTotalAmount().longValue()).replace(',', '.') + "đ";

		save(owner, o, title, message);
		push(owner.getUsername(), MANAGER_QUEUE, new OrderEventRes("NEW", o.getId(), o.getBranch().getId(),
				o.getStatus().name(), OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup), title, message));
	}

	/**
	 * Đơn đổi trạng thái.
	 * byCustomer = true: khách tự huỷ -> lưu thông báo cho quán.
	 * byCustomer = false: quán/shipper đổi -> lưu thông báo cho khách.
	 * WebSocket đẩy cho cả hai để trang đang mở tự cập nhật.
	 */
	public void onStatusChanged(Order o, boolean byCustomer) {
		User customer = o.getUser();
		User owner = o.getBranch().getOwner();
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		String label = OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup);

		String title = byCustomer ? "Khách đã huỷ đơn #" + o.getId() : "Đơn #" + o.getId() + ": " + label;
		String message = o.getStatus() == OrderStatus.CANCELLED && o.getCancelReason() != null
				? "Lý do: " + o.getCancelReason()
				: o.getBranch().getName();

		save(byCustomer ? owner : customer, o, title, message);

		OrderEventRes event = new OrderEventRes("STATUS", o.getId(), o.getBranch().getId(), o.getStatus().name(),
				label, title, message);
		push(customer.getUsername(), CUSTOMER_QUEUE, event);
		push(owner.getUsername(), MANAGER_QUEUE, event);
	}

	private void save(User to, Order o, String title, String content) {
		Notification n = new Notification();
		n.setUser(to);
		n.setOrder(o);
		n.setTitle(title);
		n.setContent(content);
		notificationRepository.save(n);
	}

	/** Đẩy WebSocket sau khi transaction commit (chưa lưu xong thì chưa báo) */
	private void push(String username, String destination, OrderEventRes payload) {
		Runnable send = () -> {
			try {
				messagingTemplate.convertAndSendToUser(username, destination, payload);
			} catch (Exception e) {
				// Lỗi WebSocket không được làm hỏng đơn hàng; trang quán có tự tải lại mỗi phút
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
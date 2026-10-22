package vn.utetra.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utetra.dto.response.OrderEventRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.OrderDetail;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.CancelledBy;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.service.impl.OrderFlowServiceImpl;

/**
 * Báo tin về đơn hàng:
 * 1. Chuông thông báo (NotificationService): lưu bảng Notifications + đẩy /user/queue/notifications
 * 2. Trang đơn hàng đang mở tự cập nhật: đẩy OrderEventRes SAU KHI lưu đơn thành công (afterCommit)
 *    - Quán (chủ chi nhánh): /user/queue/manager-orders
 *    - Khách:                /user/queue/orders
 *    Gửi theo tài khoản (convertAndSendToUser) nên người khác không nghe lén được.
 */
@Component
public class OrderNotifier {

	public static final String MANAGER_QUEUE = "/queue/manager-orders";
	public static final String CUSTOMER_QUEUE = "/queue/orders";

	private final NotificationService notificationService;
	private final SimpMessagingTemplate messagingTemplate;

	public OrderNotifier(NotificationService notificationService, SimpMessagingTemplate messagingTemplate) {
		this.notificationService = notificationService;
		this.messagingTemplate = messagingTemplate;
	}

	/** Khách vừa đặt đơn -> báo chủ chi nhánh (chuông + popup ở Quầy đơn hàng) */
	public void onNewOrder(Order o) {
		User owner = o.getBranch().getOwner();
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		int qty = o.getDetails().stream().mapToInt(OrderDetail::getQuantity).sum();
		String title = "Có đơn hàng mới #" + o.getId();
		String message = (pickup ? "Khách đến lấy" : "Giao tận nơi") + " · " + qty + " ly · " + money(o) + "đ";
		notificationService.create(owner, o, "ORDER", title, message, "/manager/orders/" + o.getId());
		push(owner.getUsername(), MANAGER_QUEUE, new OrderEventRes("NEW", o.getId(), o.getBranch().getId(),
				o.getStatus().name(), OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup), title, message));
	}

	/**
	 * Đơn đổi trạng thái.
	 * byCustomer = true: khách tự huỷ -> báo cho quán.
	 * byCustomer = false: quán / shipper đổi -> báo cho khách.
	 * Trang đơn hàng của cả hai bên đang mở đều tự cập nhật.
	 */
	public void onStatusChanged(Order o, boolean byCustomer) {
		onStatusChanged(o, byCustomer ? CancelledBy.CUSTOMER : CancelledBy.STORE, true);
	}

	/**
	 * actor: CUSTOMER (khách huỷ) / STORE (quán, shipper, Admin) / SYSTEM (hệ thống tự huỷ: quá hạn thanh toán).
	 * storeKnew = false: đơn huỷ khi còn Chờ thanh toán -> quán chưa từng thấy đơn, không báo quán.
	 */
	public void onStatusChanged(Order o, CancelledBy actor, boolean storeKnew) {
		boolean byCustomer = actor == CancelledBy.CUSTOMER;
		User customer = o.getUser();
		User owner = o.getBranch().getOwner();
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		String label = OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup);
		String reason = o.getCancelReason() == null ? "" : "Lý do: " + o.getCancelReason();
		String branch = o.getBranch().getName();
		int id = o.getId();

		String title;
		String message;
		String type = "ORDER";
		String link = "/orders/" + id;
		if (byCustomer) {
			title = "Khách đã huỷ đơn #" + id;
			message = reason;
			if (storeKnew) notificationService.create(owner, o, type, title, message, "/manager/orders/" + id);
		} else if (actor == CancelledBy.SYSTEM) {
			title = "Đơn #" + id + " đã tự huỷ";
			message = (o.getCancelReason() == null ? "Đơn quá hạn thanh toán" : o.getCancelReason())
					+ ". Mã giảm giá (nếu có) đã được hoàn lại";
			notificationService.create(customer, o, type, title, message, link);
		} else {
			switch (o.getStatus()) {
				case CONFIRMED -> { title = "Quán đã nhận đơn #" + id; message = "Đang pha chế tại " + branch; }
				case READY -> {
					if (pickup) {
						title = "Đơn #" + id + " đã xong, mời bạn đến lấy";
						message = branch + " · " + o.getBranch().getAddress();
					} else {
						title = "Đơn #" + id + " đã pha xong";
						message = "Đang chờ shipper đến lấy hàng";
					}
				}
				case PICKED_UP -> { title = "Shipper đã lấy đơn #" + id; message = "Đơn đang chuẩn bị giao tới bạn"; }
				case DELIVERING -> { title = "Đơn #" + id + " đang được giao"; message = "Shipper đang trên đường tới"; }
				case DELIVERED -> {
					title = pickup ? "Bạn đã nhận đơn #" + id : "Đơn #" + id + " đã giao thành công";
					message = "Bạn thấy món và quán thế nào? Đánh giá ngay nhé";
					type = "REVIEW";
					link = "/orders/" + id + "/review";
				}
				case CANCELLED -> {
					title = "Cửa hàng đã huỷ đơn #" + id;
					message = reason;
				}
				default -> { title = "Đơn #" + id + ": " + label; message = branch; }
			}
			notificationService.create(customer, o, type, title, message, link);
		}

		OrderEventRes event = new OrderEventRes("STATUS", id, o.getBranch().getId(), o.getStatus().name(),
				o.getStatus() == OrderStatus.CANCELLED
						? OrderFlowServiceImpl.cancelledByLabel(o.getCancelledBy(), true)
						: label,
				title, message);
		push(customer.getUsername(), CUSTOMER_QUEUE, event);
		if (storeKnew) push(owner.getUsername(), MANAGER_QUEUE, event);
	}

	private static String money(Order o) {
		return String.format("%,d", o.getTotalAmount().longValue()).replace(',', '.');
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

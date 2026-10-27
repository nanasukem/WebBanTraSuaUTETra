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

@Component
public class OrderNotifier {

	public static final String MANAGER_QUEUE = "/queue/manager-orders";
	public static final String CUSTOMER_QUEUE = "/queue/orders";
	public static final String SHIPPER_QUEUE = "/queue/shipper-orders";

	private final NotificationService notificationService;
	private final SimpMessagingTemplate messagingTemplate;
	private final vn.utetra.repository.UserRepository userRepository;

	public OrderNotifier(NotificationService notificationService, SimpMessagingTemplate messagingTemplate,
			vn.utetra.repository.UserRepository userRepository) {
		this.notificationService = notificationService;
		this.messagingTemplate = messagingTemplate;
		this.userRepository = userRepository;
	}

	private User fresh(User u) {
		return u == null ? null : userRepository.findById(u.getId()).orElse(u);
	}

	public void onNewOrder(Order o) {
		User owner = fresh(o.getBranch().getOwner());
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		int qty = o.getDetails().stream().mapToInt(OrderDetail::getQuantity).sum();
		String title = "Có đơn hàng mới #" + o.getId();
		String message = (pickup ? "Khách đến lấy" : "Giao tận nơi") + " · " + qty + " ly · " + money(o) + "đ";
		notificationService.create(owner, o, "ORDER", title, message, "/manager/orders/" + o.getId());
		push(owner.getUsername(), MANAGER_QUEUE, new OrderEventRes("NEW", o.getId(), o.getBranch().getId(),
				o.getStatus().name(), OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup), title, message));
	}

	public void onStatusChanged(Order o, boolean byCustomer) {
		onStatusChanged(o, byCustomer ? CancelledBy.CUSTOMER : CancelledBy.STORE, true);
	}

	public void onStatusChanged(Order o, CancelledBy actor, boolean storeKnew) {
		boolean byCustomer = actor == CancelledBy.CUSTOMER;
		User customer = fresh(o.getUser());
		User owner = fresh(o.getBranch().getOwner());
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
				case RETURNED -> {
					if (pickup) {
						title = "Bạn không đến lấy đơn #" + id;
						message = "Quán đã pha xong nhưng bạn không đến lấy"
								+ (o.getPaymentMethod() == vn.utetra.entity.enums.PaymentMethod.COD
										? ". Lưu ý: không nhận đơn từ " + OrderFlowServiceImpl.COD_BLOCK_AFTER_FAILED
												+ " lần trở lên sẽ không dùng được thanh toán khi nhận hàng"
										: ". Đơn đã thanh toán trước, liên hệ quán nếu bạn cần hỗ trợ");
						notificationService.notifyAdmins(type, "Khách không đến lấy đơn #" + id, branch,
								"/admin/orders/" + id, "NO_SHOW:" + id);
						break;
					}
					title = "Đơn #" + id + " giao không thành công";
					message = reason + (o.getPaymentMethod() == vn.utetra.entity.enums.PaymentMethod.COD
							? ". Lưu ý: giao thất bại từ " + OrderFlowServiceImpl.COD_BLOCK_AFTER_FAILED
									+ " lần trở lên sẽ không dùng được thanh toán khi nhận hàng (COD)"
							: ". Đơn đã thanh toán trước, liên hệ quán nếu bạn cần hỗ trợ");
					String shipperName = o.getShipper() == null ? "Shipper" : "Shipper " + o.getShipper().getFullName();
					notificationService.create(owner, o, type, "Giao thất bại đơn #" + id,
							shipperName + " báo: " + o.getCancelReason(), "/manager/orders/" + id);
					notificationService.notifyAdmins(type, "Giao thất bại đơn #" + id,
							branch + " · " + shipperName + " báo: " + o.getCancelReason(), "/admin/orders/" + id,
							"FAILED_DELIVERY:" + id);
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
		if (!pickup && storeKnew) pushShippers(o, event);
	}

	public void onShipperChanged(Order o) {
		String label = OrderFlowServiceImpl.statusLabel(o.getStatus(), false);
		String title = o.getShipper() == null ? "Đơn #" + o.getId() + " chờ shipper nhận"
				: "Quán giao đơn #" + o.getId() + " cho " + o.getShipper().getFullName();
		pushShippers(o, new OrderEventRes("SHIPPER", o.getId(), o.getBranch().getId(), o.getStatus().name(), label,
				title, ""));
	}

	private void pushShippers(Order o, OrderEventRes event) {
		for (User s : userRepository.findShippersOfBranch(o.getBranch().getId())) {
			if (Boolean.TRUE.equals(s.getIsActive())) push(s.getUsername(), SHIPPER_QUEUE, event);
		}
	}

	private static String money(Order o) {
		return String.format("%,d", o.getTotalAmount().longValue()).replace(',', '.');
	}

	private void push(String username, String destination, OrderEventRes payload) {
		Runnable send = () -> {
			try {
				messagingTemplate.convertAndSendToUser(username, destination, payload);
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

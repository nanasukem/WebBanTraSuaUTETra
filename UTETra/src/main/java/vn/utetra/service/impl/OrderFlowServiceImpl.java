package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.OrderListRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.entity.Order;
import vn.utetra.entity.OrderDetail;
import vn.utetra.entity.VoucherUsage;
import vn.utetra.entity.enums.CancelledBy;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.VoucherRepository;
import vn.utetra.repository.VoucherUsageRepository;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.OrderNotifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderFlowServiceImpl implements OrderFlowService {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
	private static final int REASON_MAX = 255;
	/** Hạn đánh giá sau khi nhận hàng (giống Shopee) */
	public static final int REVIEW_DAYS = 30;

	/**
	 * Luồng giao tận nơi: Đơn mới -> Đang pha chế -> Pha xong (chờ shipper) -> Shipper đã lấy -> Đang giao -> Đã giao.
	 * Quán huỷ được tới lúc pha xong (vd hết nguyên liệu); khách chỉ tự huỷ khi quán chưa nhận đơn.
	 */
	private static final Map<OrderStatus, Set<OrderStatus>> DELIVERY_FLOW = Map.of(
			OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
			OrderStatus.CONFIRMED, Set.of(OrderStatus.READY, OrderStatus.CANCELLED),
			OrderStatus.READY, Set.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED),
			OrderStatus.PICKED_UP, Set.of(OrderStatus.DELIVERING),
			OrderStatus.DELIVERING, Set.of(OrderStatus.DELIVERED));

	/** Luồng đến lấy: Đơn mới -> Đang pha chế -> Đã xong, chờ khách đến lấy -> Khách đã nhận (không qua shipper) */
	private static final Map<OrderStatus, Set<OrderStatus>> PICKUP_FLOW = Map.of(
			OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
			OrderStatus.CONFIRMED, Set.of(OrderStatus.READY, OrderStatus.CANCELLED),
			OrderStatus.READY, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED));

	private final OrderRepository orderRepository;
	private final VoucherRepository voucherRepository;
	private final VoucherUsageRepository voucherUsageRepository;
	private final ProductRepository productRepository;
	private final OrderNotifier orderNotifier;
	private final vn.utetra.repository.ReviewRepository reviewRepository;

	public OrderFlowServiceImpl(OrderRepository orderRepository, VoucherRepository voucherRepository,
			VoucherUsageRepository voucherUsageRepository, ProductRepository productRepository,
			OrderNotifier orderNotifier, vn.utetra.repository.ReviewRepository reviewRepository) {
		this.reviewRepository = reviewRepository;
		this.orderRepository = orderRepository;
		this.voucherRepository = voucherRepository;
		this.voucherUsageRepository = voucherUsageRepository;
		this.productRepository = productRepository;
		this.orderNotifier = orderNotifier;
	}

	// ================= KHÁCH XEM ĐƠN =================

	@Override
	public List<OrderListRes> myOrders(Integer userId, OrderTab tab) {
		return orderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId, tab.getStatuses())
				.stream().map(this::toListRes).toList();
	}

	@Override
	public Map<String, Long> myOrderCounts(Integer userId) {
		Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
		for (Object[] row : orderRepository.countByStatusOfUser(userId)) {
			byStatus.put((OrderStatus) row[0], ((Number) row[1]).longValue());
		}
		Map<String, Long> res = new LinkedHashMap<>();
		for (OrderTab t : OrderTab.values()) {
			res.put(t.name(), t.getStatuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum());
		}
		return res;
	}

	@Override
	public OrderDetailRes myOrder(Integer userId, Integer orderId) {
		return toDetailRes(findOwned(userId, orderId));
	}
	
	@Override
	public OrderDetailRes orderDetail(Integer orderId) {
		return toDetailRes(orderRepository.findById(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId)));
	}

	// ================= HUỶ / ĐỔI TRẠNG THÁI =================

	@Override
	@Transactional
	public void cancelByCustomer(Integer userId, Integer orderId, String reason) {
		Order o = findOwned(userId, orderId);
		if (o.getStatus() != OrderStatus.PENDING) {
			throw new BusinessException("Chỉ huỷ được đơn đang Chờ xác nhận. Đơn #" + orderId + " hiện ở trạng thái \""
					+ statusLabel(o.getStatus(), isPickup(o)) + "\", vui lòng liên hệ chi nhánh");
		}
		changeStatus(o, OrderStatus.CANCELLED, reason, true);
	}

	@Override
	@Transactional
	public void updateStatus(Integer orderId, OrderStatus to, String cancelReason) {
		Order o = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
		changeStatus(o, to, cancelReason, false);
	}

	/** byCustomer = true: khách tự huỷ (báo cho quán); false: quán/shipper đổi (báo cho khách) */
	private void changeStatus(Order o, OrderStatus to, String reason, boolean byCustomer) {
		boolean pickup = isPickup(o);
		Map<OrderStatus, Set<OrderStatus>> flow = pickup ? PICKUP_FLOW : DELIVERY_FLOW;
		if (to == null || !flow.getOrDefault(o.getStatus(), Set.of()).contains(to)) {
			throw new BusinessException("Không thể chuyển đơn #" + o.getId() + " từ \"" + statusLabel(o.getStatus(), pickup)
					+ "\" sang \"" + (to == null ? "?" : statusLabel(to, pickup)) + "\"");
		}

		String cleanReason = null;
		if (to == OrderStatus.CANCELLED) {
			if (reason == null || reason.isBlank()) throw new BusinessException("Vui lòng chọn lý do huỷ đơn");
			cleanReason = reason.trim();
			if (cleanReason.length() > REASON_MAX)
				throw new BusinessException("Lý do huỷ tối đa " + REASON_MAX + " ký tự");
		}

		// Gom số ly theo món TRƯỚC khi lưu (dùng để tăng "đã bán" khi giao xong)
		Map<Integer, Integer> qtyByProduct = new HashMap<>();
		if (to == OrderStatus.DELIVERED) {
			for (OrderDetail d : o.getDetails()) qtyByProduct.merge(d.getProduct().getId(), d.getQuantity(), Integer::sum);
		}

		LocalDateTime now = LocalDateTime.now();
		o.setStatus(to);
		o.setUpdatedAt(now);
		if (to == OrderStatus.CANCELLED) {
			o.setCancelReason(cleanReason);
			o.setCancelledBy(byCustomer ? CancelledBy.CUSTOMER : CancelledBy.STORE);
			// Đơn đã trả online thì chuyển sang Đã hoàn tiền (tuần 3 gọi API hoàn tiền VNPAY/MoMo)
		}
		if (to == OrderStatus.DELIVERED) {
			o.setDeliveredAt(now);
			// COD: shipper / quầy đã thu tiền khi giao
			if (o.getPaymentMethod() == PaymentMethod.COD) o.setPaymentStatus(PaymentStatus.PAID);
		}
		Integer orderId = o.getId();
		orderRepository.saveAndFlush(o);

		// Lưu thông báo + đẩy WebSocket (đẩy sau khi commit). Gọi TRƯỚC các câu UPDATE bên dưới
		orderNotifier.onStatusChanged(o, byCustomer);

		// Các câu UPDATE bên dưới có clearAutomatically -> phải lưu đơn xong ở trên trước
		if (to == OrderStatus.CANCELLED) refundVouchers(orderId);
		qtyByProduct.forEach(productRepository::increaseSoldCount);
	}

	/** Huỷ đơn: xoá lịch sử dùng mã của đơn + trả lại 1 lượt cho mỗi mã */
	private void refundVouchers(Integer orderId) {
		List<VoucherUsage> usages = voucherUsageRepository.findByOrderId(orderId);
		if (usages.isEmpty()) return;
		List<Integer> voucherIds = usages.stream().map(u -> u.getVoucher().getId()).toList();
		voucherUsageRepository.deleteAll(usages);
		voucherIds.forEach(voucherRepository::decreaseUsedCount);
	}

	// ================= HÀM PHỤ =================

	private Order findOwned(Integer userId, Integer orderId) {
		return orderRepository.findById(orderId)
				.filter(o -> o.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
	}

	private boolean isPickup(Order o) {
		return o.getDeliveryType() == DeliveryType.PICKUP;
	}

	/** Chữ hiển thị theo trạng thái (đơn đến lấy dùng chữ khác cho dễ hiểu) */
	public static String statusLabel(OrderStatus s, boolean pickup) {
		return switch (s) {
			case PENDING -> "Chờ xác nhận";
			case CONFIRMED -> "Đang pha chế";
			case READY -> pickup ? "Đã xong, chờ khách đến lấy" : "Pha xong, chờ shipper";
			case PICKED_UP -> "Shipper đã lấy hàng";
			case DELIVERING -> "Đang giao";
			case DELIVERED -> pickup ? "Đã nhận món" : "Đã giao";
			case CANCELLED -> "Đã huỷ";
			case RETURNED -> "Trả hàng";
		};
	}

	public static String badgeClass(OrderStatus s) {
		return switch (s) {
			case PENDING -> "text-bg-warning";
			case CONFIRMED -> "text-bg-info";
			case READY -> "text-bg-warning";
			case PICKED_UP, DELIVERING -> "text-bg-primary";
			case DELIVERED -> "text-bg-success";
			case CANCELLED -> "text-bg-secondary";
			case RETURNED -> "text-bg-danger";
		};
	}

	public static String paymentMethodLabel(PaymentMethod m) {
		if (m == null) return "";
		return switch (m) {
			case COD -> "Thanh toán khi nhận hàng (COD)";
			case VNPAY -> "VNPAY";
			case MOMO -> "Ví MoMo";
		};
	}

	private static String paymentStatusLabel(PaymentStatus s) {
		if (s == null) return "";
		return switch (s) {
			case UNPAID -> "Chưa thanh toán";
			case PAID -> "Đã thanh toán";
			case REFUNDED -> "Đã hoàn tiền";
		};
	}

	private static String time(LocalDateTime t) {
		return t == null ? null : t.format(TIME);
	}

	private static BigDecimal money(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}

	private OrderListRes toListRes(Order o) {
		boolean pickup = isPickup(o);
		List<OrderDetail> details = o.getDetails();
		int totalQty = details.stream().mapToInt(OrderDetail::getQuantity).sum();
		String first = "";
		if (!details.isEmpty()) {
			OrderDetail d = details.get(0);
			first = d.getQuantity() + "× " + d.getProduct().getName() + " (" + d.getSize() + ")";
		}
		return new OrderListRes(o.getId(), o.getBranch().getName(), pickup, o.getStatus().name(),
				statusLabel(o.getStatus(), pickup), badgeClass(o.getStatus()), totalQty, first,
				Math.max(0, details.size() - 1), money(o.getTotalAmount()), paymentMethodLabel(o.getPaymentMethod()),
				time(o.getCreatedAt()), o.getStatus() == OrderStatus.PENDING,
				o.getCancelledBy() == null ? null : o.getCancelledBy().name(), toReviewCount(o));
	}

	/** Đơn đã giao còn trong hạn đánh giá (30 ngày): số ly chưa đánh giá. Khác -> 0 */
	private int toReviewCount(Order o) {
		if (o.getStatus() != OrderStatus.DELIVERED || o.getDeliveredAt() == null
				|| o.getDeliveredAt().plusDays(REVIEW_DAYS).isBefore(LocalDateTime.now())) return 0;
		List<Integer> ids = o.getDetails().stream().map(OrderDetail::getId).toList();
		return ids.isEmpty() ? 0 : (int) (ids.size() - reviewRepository.countByOrderDetailIdIn(ids));
	}

	private OrderDetailRes toDetailRes(Order o) {
		boolean pickup = isPickup(o);
		List<OrderDetailRes.Item> items = o.getDetails().stream().map(d -> new OrderDetailRes.Item(
				d.getProduct().getName(),
				String.valueOf(d.getSize()),
				d.getSugarLevel() + "% đường · " + d.getIceLevel() + "% đá",
				d.getToppings().stream().map(t -> t.getTopping().getName()).collect(Collectors.joining(", ")),
				d.getQuantity(),
				money(d.getUnitPrice()),
				money(d.getSubtotal()),
				d.getNote())).toList();
		int totalQty = items.stream().mapToInt(OrderDetailRes.Item::quantity).sum();

		return new OrderDetailRes(o.getId(), o.getStatus().name(), statusLabel(o.getStatus(), pickup),
				badgeClass(o.getStatus()), pickup, steps(o.getStatus(), pickup),
				o.getBranch().getName(), o.getBranch().getAddress(), o.getBranch().getPhone(),
				o.getReceiverName(), o.getReceiverPhone(), o.getDeliveryAddress(), o.getDeliveryNote(),
				o.getCarrier() == null ? null : o.getCarrier().getName(), o.getNote(),
				items, totalQty, money(o.getItemsTotal()),
				money(o.getProductDiscount()), o.getProductVoucher() == null ? null : o.getProductVoucher().getCode(),
				money(o.getMemberDiscount()), o.getMemberTierName(),
				money(o.getShippingFee()), money(o.getShippingDiscount()),
				o.getShippingVoucher() == null ? null : o.getShippingVoucher().getCode(),
				money(o.getTotalAmount()), paymentMethodLabel(o.getPaymentMethod()),
				paymentStatusLabel(o.getPaymentStatus()), o.getCancelReason(),
				time(o.getCreatedAt()), time(o.getDeliveredAt()), o.getStatus() == OrderStatus.PENDING,
				o.getCancelledBy() == null ? null : o.getCancelledBy().name());
	}

	/** Trạng thái được chuyển tiếp từ trạng thái hiện tại (theo kiểu đơn) */
	public static List<OrderStatus> nextStatuses(OrderStatus from, boolean pickup) {
		Set<OrderStatus> next = (pickup ? PICKUP_FLOW : DELIVERY_FLOW).getOrDefault(from, Set.of());
		return Arrays.stream(OrderStatus.values()).filter(next::contains).toList();
	}

	/** Các bước của thanh tiến trình theo kiểu đơn */
	public static List<OrderStatus> flowOf(boolean pickup) {
		return pickup
				? List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.READY, OrderStatus.DELIVERED)
				: List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.READY, OrderStatus.PICKED_UP,
						OrderStatus.DELIVERING, OrderStatus.DELIVERED);
	}

	/** Chữ hiển thị "ai huỷ" - phía khách: Bạn đã huỷ / Cửa hàng đã huỷ; phía quán: Khách tự huỷ / Quán đã huỷ */
	public static String cancelledByLabel(CancelledBy by, boolean forCustomer) {
		if (by == null) return forCustomer ? "Đơn đã huỷ" : "Đã huỷ";
		return switch (by) {
			case CUSTOMER -> forCustomer ? "Bạn đã huỷ đơn" : "Khách tự huỷ";
			case STORE -> forCustomer ? "Cửa hàng đã huỷ đơn" : "Quán đã huỷ";
			case SYSTEM -> forCustomer ? "Hệ thống tự huỷ đơn" : "Hệ thống tự huỷ";
		};
	}

	/** Thanh tiến trình. Đơn đã huỷ / trả hàng: không vẽ (trang hiện thông báo riêng) */
	private static List<OrderDetailRes.Step> steps(OrderStatus status, boolean pickup) {
		List<OrderStatus> flow = flowOf(pickup);
		List<String> labels = pickup
				? List.of("Đơn mới", "Đang pha chế", "Chờ bạn đến lấy", "Đã nhận món")
				: List.of("Đơn mới", "Đang pha chế", "Pha xong", "Shipper lấy hàng", "Đang giao", "Đã giao");
		int current = flow.indexOf(status);
		if (current < 0) return List.of();
		List<OrderDetailRes.Step> res = new ArrayList<>();
		for (int i = 0; i < flow.size(); i++) res.add(new OrderDetailRes.Step(labels.get(i), i <= current, i == current));
		return res;
	}
}
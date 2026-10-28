package vn.utetra.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.dto.DateRange;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;

@Service
@Transactional(readOnly = true)
public class ShipperService {

	public static final List<String> TABS = List.of("READY", "SHIPPING", "DELIVERED", "FAILED");

	private final OrderRepository orderRepository;
	private final OrderFlowService orderFlowService;

	public ShipperService(OrderRepository orderRepository, OrderFlowService orderFlowService) {
		this.orderRepository = orderRepository;
		this.orderFlowService = orderFlowService;
	}

	public record Board(String tab, List<Order> orders, int readyCount, int shippingCount, int deliveredCount,
			long doneToday, long doneMonth, long doneTotal, int failedCount) {
	}

	public record Stats(List<Order> orders, long countToday, long countMonth, long countYear, long countAll,
			BigDecimal codTotal, BigDecimal shipTotal, List<Order> failed) {
	}

	public static String normalizeTab(String tab) {
		String t = tab == null ? "READY" : tab.trim().toUpperCase();
		return TABS.contains(t) ? t : "READY";
	}

	public Board board(User shipper, String tab) {
		String currentTab = normalizeTab(tab);
		Integer branchId = shipper.getBranch() == null ? null : shipper.getBranch().getId();
		List<Order> allDelivery = branchId == null ? List.of()
				: orderRepository.findAll().stream().filter(o -> o.getDeliveryType() == DeliveryType.DELIVERY)
						.filter(o -> o.getBranch() != null && o.getBranch().getId().equals(branchId)).toList();

		List<Order> ready = allDelivery.stream()
				.filter(o -> o.getStatus() == OrderStatus.READY && openFor(o, shipper))
				.sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		List<Order> shipping = allDelivery.stream()
				.filter(o -> (o.getStatus() == OrderStatus.PICKED_UP || o.getStatus() == OrderStatus.DELIVERING)
						&& openFor(o, shipper))
				.sorted(Comparator.comparing(Order::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
		List<Order> delivered = allDelivery.stream()
				.filter(o -> o.getStatus() == OrderStatus.DELIVERED && openFor(o, shipper))
				.sorted(Comparator.comparing(Order::getDeliveredAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<Order> failed = allDelivery.stream()
				.filter(o -> o.getStatus() == OrderStatus.RETURNED && o.getShipper() != null
						&& o.getShipper().getId().equals(shipper.getId()))
				.sorted(Comparator.comparing(Order::getDeliveredAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
		List<Order> mineDone = delivered.stream()
				.filter(o -> o.getShipper() != null && o.getShipper().getId().equals(shipper.getId())).toList();

		List<Order> display = switch (currentTab) {
		case "SHIPPING" -> shipping;
		case "DELIVERED" -> delivered;
		case "FAILED" -> failed;
		default -> ready;
		};
		return new Board(currentTab, display, ready.size(), shipping.size(), delivered.size(),
				countSince(mineDone, todayStart), countSince(mineDone, monthStart), mineDone.size(), failed.size());
	}

	public Stats stats(User shipper, DateRange range) {
		LocalDate today = LocalDate.now();
		List<Order> all = orderRepository.findByShipper_IdAndStatusOrderByDeliveredAtDesc(shipper.getId(),
				OrderStatus.DELIVERED);
		List<Order> orders = all.stream().filter(o -> range.contains(o.getDeliveredAt())).toList();
		BigDecimal cod = orders.stream().filter(o -> o.getPaymentMethod() == PaymentMethod.COD)
				.map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal ship = orders.stream().map(Order::getShippingFee).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Stats(orders, countSince(all, today.atStartOfDay()),
				countSince(all, today.withDayOfMonth(1).atStartOfDay()),
				countSince(all, today.withDayOfYear(1).atStartOfDay()), all.size(), cod, ship,
				orderRepository.findByShipper_IdAndStatusOrderByDeliveredAtDesc(shipper.getId(), OrderStatus.RETURNED)
						.stream().filter(o -> range.contains(o.getDeliveredAt())).toList());
	}

	public List<Order> delivering(Integer shipperId) {
		List<Integer> ids = orderRepository.findOpenOrderIdsOfShipper(shipperId,
				List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING));
		return ids.isEmpty() ? List.of()
				: orderRepository.findAllById(ids).stream().sorted(Comparator.comparing(Order::getId)).toList();
	}

	@Transactional
	public void updateStatus(User shipper, Integer id, OrderStatus status) {
		updateStatus(shipper, id, status, null);
	}

	@Transactional
	public void updateStatus(User shipper, Integer id, OrderStatus status, String reason) {
		Order order = orderRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + id));
		if (order.getDeliveryType() != DeliveryType.DELIVERY) {
			throw new BusinessException("Đơn #" + id + " là đơn khách tự đến lấy tại quán");
		}
		if (status != OrderStatus.PICKED_UP && status != OrderStatus.DELIVERING && status != OrderStatus.DELIVERED
				&& status != OrderStatus.RETURNED) {
			throw new BusinessException("Trạng thái cập nhật của Shipper không hợp lệ");
		}
		if (shipper.getBranch() == null) {
			throw new BusinessException("Bạn chưa được phân công chi nhánh, liên hệ Admin để nhận đơn");
		}
		if (order.getBranch() == null || !order.getBranch().getId().equals(shipper.getBranch().getId())) {
			throw new BusinessException("Đơn #" + id + " thuộc chi nhánh khác, bạn không nhận được");
		}
		if (order.getShipper() != null && !order.getShipper().getId().equals(shipper.getId())) {
			throw new BusinessException("Đơn #" + id + " đã do shipper " + order.getShipper().getFullName() + " nhận");
		}
		if (status == OrderStatus.RETURNED && order.getShipper() == null) {
			throw new BusinessException("Đơn #" + id + " chưa có shipper nhận");
		}
		if (order.getShipper() == null) {
			order.setShipper(shipper);
			orderRepository.saveAndFlush(order);
		}
		orderFlowService.updateStatus(id, status, status == OrderStatus.RETURNED ? reason : null);
	}

	private static boolean openFor(Order o, User shipper) {
		return o.getShipper() == null || o.getShipper().getId().equals(shipper.getId());
	}

	private static long countSince(List<Order> orders, LocalDateTime since) {
		return orders.stream().filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(since)).count();
	}
}

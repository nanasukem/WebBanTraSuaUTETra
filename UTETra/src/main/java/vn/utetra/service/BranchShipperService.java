package vn.utetra.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class BranchShipperService {

	public static final List<OrderStatus> ASSIGNABLE = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
			OrderStatus.READY);
	public static final List<OrderStatus> ON_THE_ROAD = List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING);

	private final OrderRepository orderRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;
	private final OrderNotifier orderNotifier;

	public BranchShipperService(OrderRepository orderRepository, UserRepository userRepository,
			NotificationService notificationService, OrderNotifier orderNotifier) {
		this.orderRepository = orderRepository;
		this.userRepository = userRepository;
		this.notificationService = notificationService;
		this.orderNotifier = orderNotifier;
	}

	public record ShipperRow(Integer id, String name, String username, String phone, boolean active,
			List<Integer> deliveringIds, long deliveredToday, long deliveredTotal, LocalDateTime lastDeliveredAt) {
	}

	public record ShipperDetail(User shipper, boolean inBranch, List<Order> delivering, List<Order> waiting,
			List<Order> delivered, long deliveredToday, long deliveredMonth) {
	}

	public record ShipperOption(Integer id, String name, String phone, int busy) {
	}

	public record ShipperBox(boolean show, User shipper, boolean canAssign, List<ShipperOption> options) {
	}

	private List<Order> ordersOf(Integer branchId) {
		return orderRepository.findByBranchIdAndStatusInOrderByCreatedAtAsc(branchId, EnumSet.allOf(OrderStatus.class));
	}

	public List<ShipperRow> rows(Integer branchId) {
		List<Order> orders = ordersOf(branchId);
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		List<ShipperRow> rows = new ArrayList<>();
		for (User s : userRepository.findShippersOfBranch(branchId)) {
			List<Order> mine = orders.stream()
					.filter(o -> o.getShipper() != null && o.getShipper().getId().equals(s.getId())).toList();
			List<Integer> delivering = mine.stream().filter(o -> ON_THE_ROAD.contains(o.getStatus())).map(Order::getId)
					.sorted().toList();
			List<Order> delivered = mine.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).toList();
			long today = delivered.stream()
					.filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(todayStart)).count();
			LocalDateTime last = delivered.stream().map(Order::getDeliveredAt).filter(Objects::nonNull)
					.max(Comparator.naturalOrder()).orElse(null);
			rows.add(new ShipperRow(s.getId(), s.getFullName(), s.getUsername(), s.getPhone(),
					Boolean.TRUE.equals(s.getIsActive()), delivering, today, delivered.size(), last));
		}
		return rows;
	}

	public Optional<ShipperDetail> detail(Integer branchId, Integer shipperId) {
		User s = shipperId == null ? null : userRepository.findById(shipperId).orElse(null);
		List<Order> mine = s == null ? List.of()
				: ordersOf(branchId).stream()
						.filter(o -> o.getShipper() != null && o.getShipper().getId().equals(shipperId)).toList();
		boolean inBranch = s != null && s.getBranch() != null && s.getBranch().getId().equals(branchId);
		if (s == null || !inBranch && mine.isEmpty()) {
			return Optional.empty();
		}
		List<Order> delivering = mine.stream().filter(o -> ON_THE_ROAD.contains(o.getStatus()))
				.sorted(Comparator.comparing(Order::getId)).toList();
		List<Order> waiting = mine.stream().filter(o -> ASSIGNABLE.contains(o.getStatus()))
				.sorted(Comparator.comparing(Order::getId)).toList();
		List<Order> delivered = mine.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED)
				.sorted(Comparator.comparing(Order::getDeliveredAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
		return Optional.of(new ShipperDetail(s, inBranch, delivering, waiting, delivered,
				delivered.stream().filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(todayStart))
						.count(),
				delivered.stream().filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(monthStart))
						.count()));
	}

	public ShipperBox box(Integer orderId) {
		Order o = orderId == null ? null : orderRepository.findById(orderId).orElse(null);
		if (o == null || o.getDeliveryType() != DeliveryType.DELIVERY) {
			return new ShipperBox(false, null, false, List.of());
		}
		List<ShipperOption> options = new ArrayList<>();
		if (o.getBranch() != null) {
			for (User s : userRepository.findShippersOfBranch(o.getBranch().getId())) {
				if (!Boolean.TRUE.equals(s.getIsActive())) continue;
				int busy = orderRepository.findOpenOrderIdsOfShipper(s.getId(), ON_THE_ROAD).size();
				options.add(new ShipperOption(s.getId(), s.getFullName(), s.getPhone(), busy));
			}
		}
		return new ShipperBox(true, o.getShipper(), ASSIGNABLE.contains(o.getStatus()), options);
	}

	@Transactional
	public User assign(Integer orderId, Integer shipperId) {
		Order o = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
		if (o.getDeliveryType() != DeliveryType.DELIVERY) {
			throw new BusinessException("Đơn #" + orderId + " là đơn khách tự đến lấy, không cần shipper");
		}
		if (!ASSIGNABLE.contains(o.getStatus())) {
			throw new BusinessException("Shipper đã lấy hàng hoặc đơn đã kết thúc, không đổi shipper được nữa");
		}
		User old = o.getShipper();
		User s = null;
		if (shipperId != null) {
			s = userRepository.findById(shipperId).orElse(null);
			if (s == null || s.getRole() == null || !"SHIPPER".equals(s.getRole().getName()) || s.getBranch() == null
					|| o.getBranch() == null || !s.getBranch().getId().equals(o.getBranch().getId())) {
				throw new BusinessException("Shipper này không thuộc chi nhánh của đơn");
			}
			if (!Boolean.TRUE.equals(s.getIsActive())) {
				throw new BusinessException("Tài khoản shipper " + s.getFullName() + " đang bị khoá");
			}
		}
		if (old == null && s == null || old != null && s != null && old.getId().equals(s.getId())) {
			throw new BusinessException("Đơn #" + orderId + " không có gì thay đổi");
		}
		o.setShipper(s);
		orderRepository.save(o);
		orderNotifier.onShipperChanged(o);
		if (s != null) {
			notificationService.create(s, o, "ORDER", "Quán giao đơn #" + orderId + " cho bạn",
					"Đơn giao tới " + o.getDeliveryAddress() + ". Pha xong là đơn hiện ở mục Chờ lấy tại quán.",
					"/shipper/orders");
		}
		if (old != null) {
			notificationService.create(old, o, "ORDER", "Quán đã chuyển đơn #" + orderId + " cho shipper khác",
					"Bạn không cần giao đơn này nữa.", "/shipper/orders");
		}
		return s;
	}
}

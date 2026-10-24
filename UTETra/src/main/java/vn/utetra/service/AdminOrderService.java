package vn.utetra.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Order;
import vn.utetra.entity.OrderIssue;
import vn.utetra.entity.Payment;
import vn.utetra.entity.enums.IssueStatus;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.OrderIssueRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.PaymentRepository;

@Service
@Transactional(readOnly = true)
public class AdminOrderService {

	private final OrderRepository orderRepository;
	private final OrderIssueRepository orderIssueRepository;
	private final PaymentRepository paymentRepository;
	private final VnPayService vnPayService;
	private final MomoService momoService;

	public AdminOrderService(OrderRepository orderRepository, OrderIssueRepository orderIssueRepository,
			PaymentRepository paymentRepository, VnPayService vnPayService, MomoService momoService) {
		this.orderRepository = orderRepository;
		this.orderIssueRepository = orderIssueRepository;
		this.paymentRepository = paymentRepository;
		this.vnPayService = vnPayService;
		this.momoService = momoService;
	}

	public record IssueMarks(Map<Integer, String> labels, Set<Integer> pending) {
	}

	public record OrderCounts(long total, long pending, long delivered, long cancelled) {
	}

	public static final List<OrderStatus> OPEN_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
			OrderStatus.READY, OrderStatus.PICKED_UP, OrderStatus.DELIVERING);

	public record ManagerOrders(List<Order> open, List<Order> inRange, long countAll, long countDelivered,
			long countCancelled, java.math.BigDecimal revenue) {
	}

	public ManagerOrders managerOrders(Integer managerId, vn.utetra.dto.DateRange range) {
		List<Order> all = orderRepository.findByManager_IdOrderByCreatedAtDesc(managerId).stream()
				.filter(o -> o.getStatus() != OrderStatus.AWAITING_PAYMENT).toList();
		List<Order> open = all.stream().filter(o -> OPEN_STATUSES.contains(o.getStatus())).toList();
		List<Order> inRange = all.stream().filter(o -> range.contains(o.getCreatedAt())).toList();
		java.math.BigDecimal revenue = inRange.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED)
				.map(Order::getSalesAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
		return new ManagerOrders(open, inRange, all.size(),
				all.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count(),
				all.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count(), revenue);
	}

	public List<Order> search(String keyword, String branchId, String status, String paymentStatus,
			String paymentMethod, LocalDate from, LocalDate to) {
		return orderRepository.findAll().stream().filter(o -> {
			if (keyword == null || keyword.isBlank()) {
				return true;
			}
			String key = keyword.trim().toLowerCase(Locale.ROOT);
			String orderId = String.valueOf(o.getId());
			String customerName = o.getUser() != null && o.getUser().getFullName() != null
					? o.getUser().getFullName().toLowerCase(Locale.ROOT)
					: "";
			String username = o.getUser() != null && o.getUser().getUsername() != null
					? o.getUser().getUsername().toLowerCase(Locale.ROOT)
					: "";
			String branchName = o.getBranch() != null && o.getBranch().getName() != null
					? o.getBranch().getName().toLowerCase(Locale.ROOT)
					: "";
			return orderId.contains(key) || customerName.contains(key) || username.contains(key)
					|| branchName.contains(key);
		}).filter(o -> {
			if (branchId == null || branchId.isBlank()) {
				return true;
			}
			try {
				return o.getBranch() != null && o.getBranch().getId().equals(Integer.valueOf(branchId));
			} catch (NumberFormatException e) {
				return false;
			}
		}).filter(o -> status == null || status.isBlank() || o.getStatus().name().equals(status))
				.filter(o -> paymentStatus == null || paymentStatus.isBlank()
						|| o.getPaymentStatus().name().equals(paymentStatus))
				.filter(o -> paymentMethod == null || paymentMethod.isBlank()
						|| o.getPaymentMethod().name().equals(paymentMethod))
				.filter(o -> {
					LocalDateTime t = o.getStatus() == OrderStatus.DELIVERED && o.getDeliveredAt() != null
							? o.getDeliveredAt()
							: o.getCreatedAt();
					if (t == null) return from == null && to == null;
					return (from == null || !t.toLocalDate().isBefore(from))
							&& (to == null || !t.toLocalDate().isAfter(to));
				}).sorted((a, b) -> {
					if (a.getCreatedAt() == null) return 1;
					if (b.getCreatedAt() == null) return -1;
					return b.getCreatedAt().compareTo(a.getCreatedAt());
				}).toList();
	}

	public OrderCounts counts() {
		List<Order> all = orderRepository.findAll();
		return new OrderCounts(all.size(), all.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count(),
				all.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count(),
				all.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count());
	}

	public IssueMarks issueMarks() {
		Map<Integer, String> labels = new HashMap<>();
		Set<Integer> pending = new HashSet<>();
		for (OrderIssue i : orderIssueRepository.findAll()) {
			labels.put(i.getOrder().getId(), i.getStatus().getLabel());
			if (i.getStatus() == IssueStatus.PENDING) pending.add(i.getOrder().getId());
		}
		return new IssueMarks(labels, pending);
	}

	public List<Order> recent(int limit) {
		return orderRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
	}

	public Optional<Order> find(Integer id) {
		return id == null ? Optional.empty() : orderRepository.findById(id);
	}

	public List<Payment> payments(Integer orderId) {
		return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
	}

	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	public List<String> gatewayCheck(Integer orderId) {
		List<String> lines = new ArrayList<>();
		for (Payment p : paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId)) {
			if (p.getTxnRef() == null) continue;
			String r = "VNPAY".equals(p.getMethod()) ? vnPayService.lookup(p) : momoService.lookup(p);
			lines.add(p.getTxnRef() + " → " + r);
		}
		if (lines.isEmpty()) lines.add("Đơn không có giao dịch online để tra cứu");
		return lines;
	}
}

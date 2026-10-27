package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.response.ManagerCustomerDetailRes;
import vn.utetra.dto.response.ManagerCustomerRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.MemberTier;
import vn.utetra.entity.Order;
import vn.utetra.entity.OrderDetail;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderDetailRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.service.ManagerCustomerService;
import vn.utetra.service.ManagerOrderService;
import vn.utetra.service.MemberService;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ManagerCustomerServiceImpl implements ManagerCustomerService {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final ManagerOrderService managerOrderService;
	private final OrderRepository orderRepository;
	private final OrderDetailRepository orderDetailRepository;
	private final BranchRepository branchRepository;
	private final MemberService memberService;

	public ManagerCustomerServiceImpl(ManagerOrderService managerOrderService, OrderRepository orderRepository,
			OrderDetailRepository orderDetailRepository, BranchRepository branchRepository,
			MemberService memberService) {
		this.managerOrderService = managerOrderService;
		this.orderRepository = orderRepository;
		this.orderDetailRepository = orderDetailRepository;
		this.branchRepository = branchRepository;
		this.memberService = memberService;
	}

	@Override
	public List<ManagerCustomerRes> customers(Integer managerId, Integer branchId, String keyword) {
		Integer id = managerOrderService.resolveBranchId(managerId, branchId);

		Map<Integer, List<Order>> byUser = new LinkedHashMap<>();
		for (Order o : branchOrders(id)) {
			byUser.computeIfAbsent(o.getUser().getId(), k -> new ArrayList<>()).add(o);
		}

		String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
		return byUser.values().stream()
				.map(this::toRes)
				.filter(c -> kw.isEmpty() || contains(c.fullName(), kw) || contains(c.username(), kw)
						|| contains(c.phone(), kw))
				.sorted(Comparator.comparing((ManagerCustomerRes c) -> lastOrderTime(byUser.get(c.userId())))
						.reversed())
				.toList();
	}

	@Override
	public ManagerCustomerDetailRes customer(Integer managerId, Integer branchId, Integer userId) {
		Integer id = managerOrderService.resolveBranchId(managerId, branchId);
		Branch branch = branchRepository.findById(id).orElseThrow();

		List<Order> orders = branchOrders(id).stream()
				.filter(o -> o.getUser().getId().equals(userId))
				.sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
						.reversed())
				.toList();
		if (orders.isEmpty()) {
			throw new ResourceNotFoundException("Khách #" + userId + " chưa đặt đơn nào ở chi nhánh của bạn");
		}

		List<ManagerCustomerDetailRes.OrderRow> rows = orders.stream().map(this::toRow).toList();
		return new ManagerCustomerDetailRes(branch.getId(), branch.getName(), toRes(orders), rows);
	}

	private List<Order> branchOrders(Integer branchId) {
		return orderRepository.findByBranchIdAndStatusInOrderByCreatedAtAsc(branchId, EnumSet.allOf(OrderStatus.class));
	}

	private ManagerCustomerRes toRes(List<Order> orders) {
		User u = orders.get(0).getUser();
		Order last = orders.stream()
				.max(Comparator.comparing(Order::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
				.orElse(orders.get(0));

		long delivered = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
		long cancelled = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
		BigDecimal spent = orders.stream().filter(Order::countsAsRevenue)
				.map(Order::getSalesAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

		MemberTier tier = memberService.getCurrentTier(u.getId());
		String phone = u.getPhone() != null && !u.getPhone().isBlank() ? u.getPhone() : last.getReceiverPhone();
		String fullName = u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();

		return new ManagerCustomerRes(u.getId(), fullName, u.getUsername(), phone,
				tier != null ? tier.getName() : "", orders.size(), delivered, cancelled, spent,
				last.getCreatedAt() != null ? last.getCreatedAt().format(TIME) : "");
	}

	private ManagerCustomerDetailRes.OrderRow toRow(Order o) {
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		int qty = orderDetailRepository.findByOrderId(o.getId()).stream()
				.map(OrderDetail::getQuantity).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
		return new ManagerCustomerDetailRes.OrderRow(o.getId(),
				o.getCreatedAt() != null ? o.getCreatedAt().format(TIME) : "", pickup, o.getStatus().name(),
				OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup), OrderFlowServiceImpl.badgeClass(o.getStatus()),
				qty, o.getTotalAmount());
	}

	private static java.time.LocalDateTime lastOrderTime(List<Order> orders) {
		return orders.stream().map(Order::getCreatedAt).filter(Objects::nonNull)
				.max(Comparator.naturalOrder()).orElse(java.time.LocalDateTime.MIN);
	}

	private static boolean contains(String value, String kw) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
	}
}

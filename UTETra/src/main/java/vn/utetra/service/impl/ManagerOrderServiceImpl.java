package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.ManagerBoardRes;
import vn.utetra.dto.response.ManagerBranchRes;
import vn.utetra.dto.response.ManagerHistoryRes;
import vn.utetra.dto.response.ManagerOrderRes;
import vn.utetra.dto.response.ManagerTabRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Order;
import vn.utetra.entity.OrderDetail;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.ManagerOrderService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ManagerOrderServiceImpl implements ManagerOrderService {

	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY_HOUR = DateTimeFormatter.ofPattern("HH:mm dd/MM");
	private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
			OrderStatus.READY, OrderStatus.PICKED_UP, OrderStatus.DELIVERING);
	private static final List<OrderStatus> FINISHED = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED,
			OrderStatus.RETURNED);

	private static final int PAGE_SIZE = 10;

	/**
	 * Tab "Đơn hàng của chi nhánh" giống app quán thật: Đơn mới -> Đang chuẩn bị -> Pha xong / chờ lấy (khách đến lấy
	 * hoặc chờ shipper) -> Đã giao shipper / Đang giao -> Hoàn thành -> Đã huỷ (+ Trả hàng khi có).
	 * Đơn Chờ thanh toán (VNPAY/MoMo chưa trả xong) KHÔNG hiện cho quán.
	 */
	private record Tab(String label, List<OrderStatus> statuses) {
	}

	private static final Map<String, Tab> TABS = new LinkedHashMap<>();
	static {
		TABS.put("PENDING", new Tab("Đơn mới", List.of(OrderStatus.PENDING)));
		TABS.put("CONFIRMED", new Tab("Đang chuẩn bị", List.of(OrderStatus.CONFIRMED)));
		TABS.put("READY", new Tab("Pha xong / chờ lấy", List.of(OrderStatus.READY)));
		TABS.put("SHIPPING", new Tab("Đã giao shipper / Đang giao", List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING)));
		TABS.put("DELIVERED", new Tab("Hoàn thành", List.of(OrderStatus.DELIVERED)));
		TABS.put("CANCELLED", new Tab("Đã huỷ", List.of(OrderStatus.CANCELLED)));
		// Đồ uống không trả hàng: tab "Khách báo vấn đề" (đơn đã giao mà khách báo sai món, đổ vỡ...)
		TABS.put("ISSUE", new Tab("Khách báo vấn đề", List.of()));
	}

	private final BranchRepository branchRepository;
	private final OrderRepository orderRepository;
	private final OrderFlowService orderFlowService;
	private final vn.utetra.service.PaymentInfoBuilder paymentInfoBuilder;
	private final vn.utetra.repository.OrderIssueRepository orderIssueRepository;

	public ManagerOrderServiceImpl(BranchRepository branchRepository, OrderRepository orderRepository,
			OrderFlowService orderFlowService, vn.utetra.service.PaymentInfoBuilder paymentInfoBuilder,
			vn.utetra.repository.OrderIssueRepository orderIssueRepository) {
		this.orderIssueRepository = orderIssueRepository;
		this.paymentInfoBuilder = paymentInfoBuilder;
		this.branchRepository = branchRepository;
		this.orderRepository = orderRepository;
		this.orderFlowService = orderFlowService;
	}

	@Override
	public List<ManagerBranchRes> myBranches(Integer managerId) {
		return branchRepository.findByOwnerIdOrderByIdAsc(managerId).stream()
				.map(b -> new ManagerBranchRes(b.getId(), b.getName(), b.getStatus().name())).toList();
	}

	@Override
	public Integer resolveBranchId(Integer managerId, Integer branchId) {
		List<Branch> mine = branchRepository.findByOwnerIdOrderByIdAsc(managerId);
		if (mine.isEmpty()) throw new BusinessException("Tài khoản của bạn chưa được giao quản lý chi nhánh nào");
		if (branchId == null) return mine.get(0).getId();
		return mine.stream().filter(b -> b.getId().equals(branchId)).findFirst().map(Branch::getId)
				.orElseThrow(() -> new ResourceNotFoundException("Bạn không quản lý chi nhánh #" + branchId));
	}

	@Override
	public ManagerBoardRes board(Integer managerId, Integer branchId) {
		Integer id = resolveBranchId(managerId, branchId);
		Branch branch = branchRepository.findById(id).orElseThrow();

		// Đơn đang làm: cũ nhất lên đầu (đặt trước làm trước)
		List<Order> active = orderRepository.findByBranchIdAndStatusInOrderByCreatedAtAsc(id, ACTIVE);
		List<ManagerOrderRes> newOrders = new ArrayList<>();
		List<ManagerOrderRes> making = new ArrayList<>();
		List<ManagerOrderRes> shipping = new ArrayList<>();
		for (Order o : active) {
			ManagerOrderRes card = toCard(o, HOUR);
			switch (o.getStatus()) {
				case PENDING -> newOrders.add(card);
				case CONFIRMED -> making.add(card);
				default -> shipping.add(card); // pha xong (chờ khách lấy / chờ shipper), shipper đang giao
			}
		}
		List<ManagerOrderRes> recent = orderRepository.findTop20ByBranchIdAndStatusInOrderByUpdatedAtDesc(id, FINISHED)
				.stream().map(o -> toCard(o, DAY_HOUR)).toList();
		return new ManagerBoardRes(id, branch.getName(), newOrders, making, shipping, recent);
	}

	@Override
	@Transactional
	public void changeStatus(Integer managerId, Integer orderId, OrderStatus to, String reason) {
		Order o = findOwned(managerId, orderId);

		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		boolean shipperStep = to == OrderStatus.PICKED_UP || to == OrderStatus.DELIVERING
				|| (to == OrderStatus.DELIVERED && !pickup);
		if (shipperStep) {
			throw new BusinessException("Đơn #" + orderId + " giao tận nơi: các bước Lấy hàng, Đang giao, Đã giao do Shipper cập nhật");
		}
		// Luồng (PENDING -> CONFIRMED...) và lý do huỷ do OrderFlowService kiểm tra
		orderFlowService.updateStatus(orderId, to, reason);
	}

	// ================= ĐƠN HÀNG CỦA CHI NHÁNH =================

	@Override
	public ManagerHistoryRes history(Integer managerId, Integer branchId, String tab, int page) {
		Integer id = resolveBranchId(managerId, branchId);
		Branch branch = branchRepository.findById(id).orElseThrow();
		String current = tab == null ? "PENDING" : tab.trim().toUpperCase();
		// Link cũ ?status=PICKED_UP / DELIVERING -> tab Đang giao; RETURNED (trả hàng cũ) -> Khách báo vấn đề
		if (current.equals("PICKED_UP") || current.equals("DELIVERING")) current = "SHIPPING";
		if (current.equals("RETURNED")) current = "ISSUE";
		if (!TABS.containsKey(current)) current = "PENDING";

		// Số đơn từng tab
		Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
		for (Object[] row : orderRepository.countByStatusOfBranch(id)) {
			counts.put((OrderStatus) row[0], ((Number) row[1]).longValue());
		}
		List<ManagerTabRes> tabs = new ArrayList<>();
		long choXuLy = orderIssueRepository.countByOrderBranchIdAndStatus(id, vn.utetra.entity.enums.IssueStatus.PENDING);
		TABS.forEach((key, t) -> {
			long n = t.statuses().stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum();
			if ("ISSUE".equals(key)) n = choXuLy;
			if ("DELIVERED".equals(key)) n -= choXuLy; // đơn khách báo vấn đề chưa xử lý xong: chưa Hoàn thành
			tabs.add(new ManagerTabRes(key, t.label(), n));
		});

		PageRequest pr = PageRequest.of(Math.max(0, page), PAGE_SIZE);
		Page<Order> result = "ISSUE".equals(current)
				? orderIssueRepository.findByOrderBranchIdOrderByCreatedAtDesc(id, pr).map(vn.utetra.entity.OrderIssue::getOrder)
				: "DELIVERED".equals(current)
						? orderRepository.findPageWithoutPendingIssue(id, TABS.get(current).statuses(), pr)
						: orderRepository.findPageByBranchIdAndStatusInOrderByCreatedAtDesc(id, TABS.get(current).statuses(), pr);
		List<ManagerOrderRes> orders = result.getContent().stream().map(o -> toCard(o, DAY_HOUR)).toList();

		return new ManagerHistoryRes(id, branch.getName(), current, TABS.get(current).label(), tabs, orders,
				result.getNumber(), result.getTotalPages(), result.getTotalElements());
	}

	@Override
	public OrderDetailRes orderDetail(Integer managerId, Integer orderId) {
		findOwned(managerId, orderId); // kiểm tra đơn thuộc chi nhánh của manager
		return orderFlowService.orderDetail(orderId);
	}

	@Override
	public ManagerOrderRes orderCard(Integer managerId, Integer orderId) {
		return toCard(findOwned(managerId, orderId), DAY_HOUR);
	}

	// ================= HÀM PHỤ =================

	private Order findOwned(Integer managerId, Integer orderId) {
		return orderRepository.findById(orderId)
				.filter(x -> x.getBranch().getOwner().getId().equals(managerId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn #" + orderId + " ở chi nhánh của bạn"));
	}

	private ManagerOrderRes toCard(Order o, DateTimeFormatter timeFormat) {
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		List<String> items = o.getDetails().stream().map(this::itemLine).toList();
		int qty = o.getDetails().stream().mapToInt(OrderDetail::getQuantity).sum();

		List<ManagerOrderRes.Action> actions = new ArrayList<>();
		String hint = null;
		vn.utetra.entity.OrderIssue issue = o.getStatus() == OrderStatus.DELIVERED
				? orderIssueRepository.findByOrderId(o.getId()).orElse(null) : null;
		switch (o.getStatus()) {
			case PENDING -> actions.add(new ManagerOrderRes.Action("CONFIRMED", "Nhận đơn & pha chế", "btn-success"));
			case CONFIRMED -> {
				actions.add(new ManagerOrderRes.Action("READY",
						pickup ? "Pha xong, báo khách đến lấy" : "Pha xong, gọi shipper", "btn-warning"));
				hint = pickup ? "Khách đến lấy tại quán" : "Pha xong thì bấm để shipper đến lấy hàng";
			}
			case READY -> {
				if (pickup) {
					actions.add(new ManagerOrderRes.Action("DELIVERED", "Khách đã nhận món", "btn-primary"));
					hint = "Đã báo khách đến lấy. Gọi khách nếu lâu chưa tới: " + o.getReceiverPhone();
				} else {
					hint = "Chờ shipper đến lấy hàng";
				}
			}
			case CANCELLED -> hint = OrderFlowServiceImpl.cancelledByLabel(o.getCancelledBy(), false)
					+ (o.getCancelReason() == null ? "" : ": " + o.getCancelReason());
			case DELIVERED -> hint = issue == null ? null
					: "Khách báo vấn đề: " + issue.getReason().getLabel() + " · " + issue.getStatus().getLabel();
			default -> { }
		}
		// Quán huỷ được tới khi pha xong; đơn đến lấy: huỷ được cả khi khách không tới lấy
		boolean canCancel = o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.CONFIRMED
				|| (o.getStatus() == OrderStatus.READY && pickup);
		String label = o.getStatus() == OrderStatus.CANCELLED
				? OrderFlowServiceImpl.cancelledByLabel(o.getCancelledBy(), false)
				: OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup);

		// Đơn đang làm: hiện giờ đặt; đơn đã xong/huỷ: hiện giờ xong
		LocalDateTime t = FINISHED.contains(o.getStatus()) && o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getCreatedAt();
		String time = t == null ? "" : t.format(timeFormat);

		return new ManagerOrderRes(o.getId(), time, pickup, o.getStatus().name(), label,
				OrderFlowServiceImpl.badgeClass(o.getStatus()), o.getReceiverName(), o.getReceiverPhone(),
				o.getDeliveryAddress(), o.getDeliveryNote(), o.getNote(), items, qty, o.getTotalAmount(),
				paymentInfoBuilder.storeLabel(o), hint, actions, canCancel,
				o.getPaymentStatus() == vn.utetra.entity.enums.PaymentStatus.PAID
						&& o.getPaymentMethod() != vn.utetra.entity.enums.PaymentMethod.COD,
				issue == null ? null : "Có vấn đề · " + issue.getStatus().getLabel(),
				issue != null && issue.getStatus() == vn.utetra.entity.enums.IssueStatus.PENDING);
	}

	/** "2× Cà phê muối · L · 70% đường · 50% đá + Kem cheese" */
	private String itemLine(OrderDetail d) {
		String toppings = d.getToppings().stream().map(t -> t.getTopping().getName()).collect(Collectors.joining(", "));
		String options = d.getProduct().isToppingItem() ? "topping mua riêng"
				: d.getSize() + " · " + d.getSugarLevel() + "% đường · " + d.getIceLevel() + "% đá";
		return d.getQuantity() + "× " + d.getProduct().getName() + " · " + options
				+ (toppings.isEmpty() ? "" : " + " + toppings)
				+ (d.getNote() == null ? "" : " · Ghi chú: " + d.getNote());
	}
}
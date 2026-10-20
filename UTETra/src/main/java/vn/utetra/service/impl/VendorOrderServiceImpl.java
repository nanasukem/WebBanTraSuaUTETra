package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.VendorBoardRes;
import vn.utetra.dto.response.VendorBranchRes;
import vn.utetra.dto.response.VendorHistoryRes;
import vn.utetra.dto.response.VendorOrderRes;
import vn.utetra.dto.response.VendorTabRes;
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
import vn.utetra.service.VendorOrderService;

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
public class VendorOrderServiceImpl implements VendorOrderService {

	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY_HOUR = DateTimeFormatter.ofPattern("HH:mm dd/MM");
	private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
			OrderStatus.PICKED_UP, OrderStatus.DELIVERING);
	private static final List<OrderStatus> FINISHED = List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED,
			OrderStatus.RETURNED);

	private static final int PAGE_SIZE = 10;

	/** 7 tab đúng chữ trong đề: mới, đã xác nhận, đã lấy hàng, đang giao, đã giao, huỷ, trả hàng - hoàn tiền */
	private static final Map<OrderStatus, String> TAB_LABELS = new LinkedHashMap<>();
	static {
		TAB_LABELS.put(OrderStatus.PENDING, "Đơn mới");
		TAB_LABELS.put(OrderStatus.CONFIRMED, "Đã xác nhận");
		TAB_LABELS.put(OrderStatus.PICKED_UP, "Đã lấy hàng");
		TAB_LABELS.put(OrderStatus.DELIVERING, "Đang giao");
		TAB_LABELS.put(OrderStatus.DELIVERED, "Đã giao");
		TAB_LABELS.put(OrderStatus.CANCELLED, "Đã huỷ");
		TAB_LABELS.put(OrderStatus.RETURNED, "Trả hàng - hoàn tiền");
	}

	private final BranchRepository branchRepository;
	private final OrderRepository orderRepository;
	private final OrderFlowService orderFlowService;

	public VendorOrderServiceImpl(BranchRepository branchRepository, OrderRepository orderRepository,
			OrderFlowService orderFlowService) {
		this.branchRepository = branchRepository;
		this.orderRepository = orderRepository;
		this.orderFlowService = orderFlowService;
	}

	@Override
	public List<VendorBranchRes> myBranches(Integer vendorId) {
		return branchRepository.findByOwnerIdOrderByIdAsc(vendorId).stream()
				.map(b -> new VendorBranchRes(b.getId(), b.getName(), b.getStatus().name())).toList();
	}

	@Override
	public Integer resolveBranchId(Integer vendorId, Integer branchId) {
		List<Branch> mine = branchRepository.findByOwnerIdOrderByIdAsc(vendorId);
		if (mine.isEmpty()) throw new BusinessException("Tài khoản của bạn chưa sở hữu chi nhánh nào");
		if (branchId == null) return mine.get(0).getId();
		return mine.stream().filter(b -> b.getId().equals(branchId)).findFirst().map(Branch::getId)
				.orElseThrow(() -> new ResourceNotFoundException("Bạn không quản lý chi nhánh #" + branchId));
	}

	@Override
	public VendorBoardRes board(Integer vendorId, Integer branchId) {
		Integer id = resolveBranchId(vendorId, branchId);
		Branch branch = branchRepository.findById(id).orElseThrow();

		// Đơn đang làm: cũ nhất lên đầu (đặt trước làm trước)
		List<Order> active = orderRepository.findByBranchIdAndStatusInOrderByCreatedAtAsc(id, ACTIVE);
		List<VendorOrderRes> newOrders = new ArrayList<>();
		List<VendorOrderRes> making = new ArrayList<>();
		List<VendorOrderRes> shipping = new ArrayList<>();
		for (Order o : active) {
			VendorOrderRes card = toCard(o, HOUR);
			switch (o.getStatus()) {
				case PENDING -> newOrders.add(card);
				case CONFIRMED -> making.add(card);
				default -> shipping.add(card);
			}
		}
		List<VendorOrderRes> recent = orderRepository.findTop20ByBranchIdAndStatusInOrderByUpdatedAtDesc(id, FINISHED)
				.stream().map(o -> toCard(o, DAY_HOUR)).toList();
		return new VendorBoardRes(id, branch.getName(), newOrders, making, shipping, recent);
	}

	@Override
	@Transactional
	public void changeStatus(Integer vendorId, Integer orderId, OrderStatus to, String reason) {
		Order o = findOwned(vendorId, orderId);

		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		boolean shipperStep = to == OrderStatus.PICKED_UP || to == OrderStatus.DELIVERING
				|| (to == OrderStatus.DELIVERED && !pickup);
		if (shipperStep) {
			throw new BusinessException("Đơn #" + orderId + " giao tận nơi: các bước Lấy hàng, Đang giao, Đã giao do Shipper cập nhật");
		}
		// Luồng (PENDING -> CONFIRMED...) và lý do huỷ do OrderFlowService kiểm tra
		orderFlowService.updateStatus(orderId, to, reason);
	}

	// ================= ĐƠN HÀNG CỦA SHOP (7 TAB) =================

	@Override
	public VendorHistoryRes history(Integer vendorId, Integer branchId, OrderStatus status, int page) {
		Integer id = resolveBranchId(vendorId, branchId);
		Branch branch = branchRepository.findById(id).orElseThrow();
		OrderStatus current = status == null ? OrderStatus.PENDING : status;

		// Số đơn từng tab
		Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
		for (Object[] row : orderRepository.countByStatusOfBranch(id)) {
			counts.put((OrderStatus) row[0], ((Number) row[1]).longValue());
		}
		List<VendorTabRes> tabs = new ArrayList<>();
		TAB_LABELS.forEach((s, label) -> tabs.add(new VendorTabRes(s.name(), label, counts.getOrDefault(s, 0L))));

		Page<Order> result = orderRepository.findPageByBranchIdAndStatusOrderByCreatedAtDesc(id, current,
				PageRequest.of(Math.max(0, page), PAGE_SIZE));
		List<VendorOrderRes> orders = result.getContent().stream().map(o -> toCard(o, DAY_HOUR)).toList();

		return new VendorHistoryRes(id, branch.getName(), current.name(), TAB_LABELS.get(current), tabs, orders,
				result.getNumber(), result.getTotalPages(), result.getTotalElements());
	}

	@Override
	public OrderDetailRes orderDetail(Integer vendorId, Integer orderId) {
		findOwned(vendorId, orderId); // kiểm tra đơn thuộc chi nhánh của vendor
		return orderFlowService.orderDetail(orderId);
	}

	@Override
	public VendorOrderRes orderCard(Integer vendorId, Integer orderId) {
		return toCard(findOwned(vendorId, orderId), DAY_HOUR);
	}

	// ================= HÀM PHỤ =================

	private Order findOwned(Integer vendorId, Integer orderId) {
		return orderRepository.findById(orderId)
				.filter(x -> x.getBranch().getOwner().getId().equals(vendorId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn #" + orderId + " ở chi nhánh của bạn"));
	}

	private VendorOrderRes toCard(Order o, DateTimeFormatter timeFormat) {
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		List<String> items = o.getDetails().stream().map(this::itemLine).toList();
		int qty = o.getDetails().stream().mapToInt(OrderDetail::getQuantity).sum();

		List<VendorOrderRes.Action> actions = new ArrayList<>();
		String hint = null;
		if (o.getStatus() == OrderStatus.PENDING) {
			actions.add(new VendorOrderRes.Action("CONFIRMED", "Xác nhận & pha chế", "btn-success"));
		} else if (o.getStatus() == OrderStatus.CONFIRMED && pickup) {
			actions.add(new VendorOrderRes.Action("DELIVERED", "Khách đã nhận món", "btn-primary"));
			hint = "Pha xong thì gọi khách: " + o.getReceiverPhone();
		} else if (o.getStatus() == OrderStatus.CONFIRMED) {
			hint = "Pha xong, chờ shipper đến lấy hàng";
		}

		// Đơn đang làm: hiện giờ đặt; đơn đã xong/huỷ: hiện giờ xong
		LocalDateTime t = FINISHED.contains(o.getStatus()) && o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getCreatedAt();
		String time = t == null ? "" : t.format(timeFormat);

		return new VendorOrderRes(o.getId(), time, pickup, o.getStatus().name(), OrderFlowServiceImpl.statusLabel(o.getStatus(), pickup),
				OrderFlowServiceImpl.badgeClass(o.getStatus()), o.getReceiverName(), o.getReceiverPhone(),
				o.getDeliveryAddress(), o.getDeliveryNote(), o.getNote(), items, qty, o.getTotalAmount(),
				OrderFlowServiceImpl.paymentMethodLabel(o.getPaymentMethod()), hint, actions,
				o.getStatus() == OrderStatus.PENDING);
	}

	/** "2× Cà phê muối · L · 70% đường · 50% đá + Kem cheese" */
	private String itemLine(OrderDetail d) {
		String toppings = d.getToppings().stream().map(t -> t.getTopping().getName()).collect(Collectors.joining(", "));
		return d.getQuantity() + "× " + d.getProduct().getName() + " · " + d.getSize() + " · "
				+ d.getSugarLevel() + "% đường · " + d.getIceLevel() + "% đá"
				+ (toppings.isEmpty() ? "" : " + " + toppings);
	}
}
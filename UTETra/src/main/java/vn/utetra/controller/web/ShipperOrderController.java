package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.OrderFlowService;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/shipper")
@Transactional(readOnly = true)
public class ShipperOrderController {

	private final OrderRepository orderRepository;
	private final OrderFlowService orderFlowService;
	private final CurrentUserService currentUserService;

	public ShipperOrderController(OrderRepository orderRepository, OrderFlowService orderFlowService,
			CurrentUserService currentUserService) {
		this.orderRepository = orderRepository;
		this.orderFlowService = orderFlowService;
		this.currentUserService = currentUserService;
	}

	@GetMapping({ "", "/", "/orders" })
	public String orders(@RequestParam(defaultValue = "READY") String tab, Principal principal, HttpSession session,
			Model model) {
		User shipper = currentUserService.requireUser(principal, session);
		String currentTab = tab == null ? "READY" : tab.trim().toUpperCase();
		if (!List.of("READY", "SHIPPING", "DELIVERED").contains(currentTab)) {
			currentTab = "READY";
		}

		Integer branchId = shipper.getBranch() == null ? null : shipper.getBranch().getId();
		List<Order> allDelivery = branchId == null ? List.of() : orderRepository.findAll().stream()
				.filter(o -> o.getDeliveryType() == DeliveryType.DELIVERY)
				.filter(o -> o.getBranch() != null && o.getBranch().getId().equals(branchId))
				.toList();

		List<Order> readyOrders = allDelivery.stream()
				.filter(o -> o.getStatus() == OrderStatus.READY
						&& (o.getShipper() == null || o.getShipper().getId().equals(shipper.getId())))
				.sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();

		List<Order> shippingOrders = allDelivery.stream()
				.filter(o -> (o.getStatus() == OrderStatus.PICKED_UP || o.getStatus() == OrderStatus.DELIVERING)
						&& (o.getShipper() == null || o.getShipper().getId().equals(shipper.getId())))
				.sorted(Comparator.comparing(Order::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<Order> deliveredOrders = allDelivery.stream()
				.filter(o -> o.getStatus() == OrderStatus.DELIVERED
						&& (o.getShipper() == null || o.getShipper().getId().equals(shipper.getId())))
				.sorted(Comparator.comparing(Order::getDeliveredAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<Order> displayOrders = switch (currentTab) {
			case "SHIPPING" -> shippingOrders;
			case "DELIVERED" -> deliveredOrders;
			default -> readyOrders;
		};

		model.addAttribute("shipper", shipper);
		model.addAttribute("branch", shipper.getBranch());
		model.addAttribute("currentTab", currentTab);
		model.addAttribute("readyCount", readyOrders.size());
		model.addAttribute("shippingCount", shippingOrders.size());
		model.addAttribute("deliveredCount", deliveredOrders.size());
		java.time.LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
		java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
		List<Order> mineDone = deliveredOrders.stream()
				.filter(o -> o.getShipper() != null && o.getShipper().getId().equals(shipper.getId())).toList();
		model.addAttribute("doneToday", mineDone.stream()
				.filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(todayStart)).count());
		model.addAttribute("doneMonth", mineDone.stream()
				.filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(monthStart)).count());
		model.addAttribute("doneTotal", mineDone.size());
		model.addAttribute("orders", displayOrders);
		return "shipper/orders";
	}

	@GetMapping("/stats")
	public String stats(@RequestParam(defaultValue = "TODAY") String period, Principal principal,
			HttpSession session, Model model) {
		User shipper = currentUserService.requireUser(principal, session);
		String p = List.of("TODAY", "MONTH", "ALL").contains(period.toUpperCase()) ? period.toUpperCase() : "TODAY";
		java.time.LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
		java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();

		List<Order> all = orderRepository.findByShipper_IdAndStatusOrderByDeliveredAtDesc(shipper.getId(),
				OrderStatus.DELIVERED);
		java.util.function.Predicate<Order> since = o -> {
			java.time.LocalDateTime from = "TODAY".equals(p) ? todayStart : "MONTH".equals(p) ? monthStart : null;
			return from == null || o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(from);
		};
		List<Order> orders = all.stream().filter(since).toList();

		model.addAttribute("shipper", shipper);
		model.addAttribute("branch", shipper.getBranch());
		model.addAttribute("period", p);
		model.addAttribute("orders", orders);
		model.addAttribute("countToday", all.stream()
				.filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(todayStart)).count());
		model.addAttribute("countMonth", all.stream()
				.filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(monthStart)).count());
		model.addAttribute("countAll", all.size());
		model.addAttribute("codTotal", orders.stream()
				.filter(o -> o.getPaymentMethod() == vn.utetra.entity.enums.PaymentMethod.COD)
				.map(Order::getTotalAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
		model.addAttribute("shipTotal", orders.stream().map(Order::getShippingFee)
				.filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
		return "shipper/stats";
	}

	@PostMapping("/orders/{id}/status")
	@Transactional
	public String updateStatus(@PathVariable Integer id, @RequestParam OrderStatus status,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		User shipper = currentUserService.requireUser(principal, session);
		try {
			Order order = orderRepository.findById(id)
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + id));
			if (order.getDeliveryType() != DeliveryType.DELIVERY) {
				throw new BusinessException("Đơn #" + id + " là đơn khách tự đến lấy tại quán");
			}
			if (status != OrderStatus.PICKED_UP && status != OrderStatus.DELIVERING && status != OrderStatus.DELIVERED) {
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
			if (order.getShipper() == null) {
				order.setShipper(shipper);
				orderRepository.saveAndFlush(order);
			}
			orderFlowService.updateStatus(id, status, null);
			ra.addFlashAttribute("successMessage", OrderStatus.actionText(status, id, null));
			String nextTab = status == OrderStatus.DELIVERED ? "DELIVERED" : "SHIPPING";
			return "redirect:/shipper/orders?tab=" + nextTab;
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/shipper/orders";
		}
	}
}

package vn.utetra.controller.web;

import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.impl.OrderFlowServiceImpl;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.entity.Branch;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin/orders")
@Transactional(readOnly = true)
public class AdminOrderController {

	private final OrderRepository orderRepository;
	private final BranchRepository branchRepository;
	private final OrderFlowService orderFlowService;
	private final vn.utetra.service.PaymentInfoBuilder paymentInfoBuilder;
	private final vn.utetra.repository.PaymentRepository paymentRepository;
	private final vn.utetra.service.RefundService refundService;
	private final vn.utetra.service.VnPayService vnPayService;
	private final vn.utetra.service.MomoService momoService;

	public AdminOrderController(OrderRepository orderRepository, BranchRepository branchRepository,
			OrderFlowService orderFlowService, vn.utetra.service.PaymentInfoBuilder paymentInfoBuilder,
			vn.utetra.repository.PaymentRepository paymentRepository, vn.utetra.service.RefundService refundService,
			vn.utetra.service.VnPayService vnPayService, vn.utetra.service.MomoService momoService) {
		this.vnPayService = vnPayService;
		this.momoService = momoService;
		this.paymentInfoBuilder = paymentInfoBuilder;
		this.paymentRepository = paymentRepository;
		this.refundService = refundService;
		this.orderRepository = orderRepository;
		this.branchRepository = branchRepository;
		this.orderFlowService = orderFlowService;
	}

	// Danh sách đơn hàng
	@GetMapping
	public String index(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "") String branchId, @RequestParam(defaultValue = "") String status,
			@RequestParam(defaultValue = "") String paymentStatus,
			@RequestParam(defaultValue = "") String paymentMethod, Model model) {

		List<Order> allOrders = orderRepository.findAll();

		List<Order> orders = allOrders.stream().filter(o -> {
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
				.sorted((a, b) -> {
					if (a.getCreatedAt() == null)
						return 1;
					if (b.getCreatedAt() == null)
						return -1;
					return b.getCreatedAt().compareTo(a.getCreatedAt());
				}).toList();

		model.addAttribute("orders", orders);
		model.addAttribute("branches", branchRepository.findAll());
		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("paymentStatuses", PaymentStatus.values());
		model.addAttribute("paymentMethods", PaymentMethod.values());

		model.addAttribute("keyword", keyword);
		model.addAttribute("selectedBranch", branchId);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("selectedPaymentStatus", paymentStatus);
		model.addAttribute("selectedPaymentMethod", paymentMethod);

		model.addAttribute("totalOrders", allOrders.size());
		model.addAttribute("pendingOrders",
				allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count());
		model.addAttribute("deliveredOrders",
				allOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count());
		model.addAttribute("cancelledOrders",
				allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count());

		return "admin/orders/index";
	}

	// Chi tiết đơn hàng
	@GetMapping("/{id}")
	public String detail(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {

		Order order = orderRepository.findById(id).orElse(null);

		if (order == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng #" + id);
			return "redirect:/admin/orders";
		}

		model.addAttribute("order", order);
		model.addAttribute("nextStatuses", getNextStatuses(order));
		model.addAttribute("pay", paymentInfoBuilder.build(order));
		model.addAttribute("payments", paymentRepository.findByOrderIdOrderByCreatedAtDesc(id));

		return "admin/orders/detail";
	}

	/** Tra cứu tại cổng: hỏi thẳng VNPAY / MoMo trạng thái các giao dịch của đơn (bằng chứng đã trả / đã hoàn) */
	@PostMapping("/{id}/gateway-check")
	public String gatewayCheck(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
		List<String> lines = new java.util.ArrayList<>();
		for (vn.utetra.entity.Payment p : paymentRepository.findByOrderIdOrderByCreatedAtDesc(id)) {
			if (p.getTxnRef() == null) continue;
			String r = "VNPAY".equals(p.getMethod()) ? vnPayService.lookup(p) : momoService.lookup(p);
			lines.add(p.getTxnRef() + " → " + r);
		}
		if (lines.isEmpty()) lines.add("Đơn không có giao dịch online để tra cứu");
		redirectAttributes.addFlashAttribute("gatewayCheck", lines);
		return "redirect:/admin/orders/" + id;
	}

	/** Hoàn tiền lại (lần trước cổng báo lỗi / mất mạng) */
	@PostMapping("/{id}/refund")
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	public String retryRefund(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
		if (refundService.retry(id)) {
			redirectAttributes.addFlashAttribute("successMessage", "Cổng thanh toán đã nhận lệnh hoàn tiền đơn #" + id + ".");
		} else {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Chưa hoàn tiền được đơn #" + id + ". Xem lỗi ở mục Giao dịch thanh toán.");
		}
		return "redirect:/admin/orders/" + id;
	}

	// Cập nhật trạng thái đơn hàng: đi qua luồng chung (OrderFlowService) để đúng thứ tự trạng thái,
	// huỷ bắt buộc lý do, hoàn mã giảm giá + xu, tăng "đã bán" khi giao xong và báo cho khách / quán
	@PostMapping("/{id}/status")
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	public String updateStatus(@PathVariable Integer id, @RequestParam OrderStatus status,
			@RequestParam(required = false) String reason, RedirectAttributes redirectAttributes) {
		try {
			orderFlowService.updateStatus(id, status,
					status == OrderStatus.CANCELLED && reason != null && !reason.isBlank()
							? "UTETra huỷ: " + reason.trim() : reason);
			redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật đơn hàng #" + id + ".");
		} catch (BusinessException | ResourceNotFoundException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/orders/" + id;
	}

	/** Các trạng thái tiếp theo hợp lệ (cùng luồng với quán) */
	private List<OrderStatus> getNextStatuses(Order order) {
		return OrderFlowServiceImpl.nextStatuses(order.getStatus(), order.getDeliveryType() == DeliveryType.PICKUP);
	}
}

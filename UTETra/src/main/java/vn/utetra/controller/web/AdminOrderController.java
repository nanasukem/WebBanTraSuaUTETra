package vn.utetra.controller.web;

import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.impl.OrderFlowServiceImpl;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;

import java.util.List;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

	private final vn.utetra.service.AdminOrderService adminOrderService;
	private final vn.utetra.service.OrderIssueService orderIssueService;
	private final vn.utetra.service.BranchService branchService;
	private final OrderFlowService orderFlowService;
	private final vn.utetra.service.PaymentInfoBuilder paymentInfoBuilder;
	private final vn.utetra.service.RefundService refundService;

	public AdminOrderController(vn.utetra.service.AdminOrderService adminOrderService,
			vn.utetra.service.OrderIssueService orderIssueService, vn.utetra.service.BranchService branchService,
			OrderFlowService orderFlowService, vn.utetra.service.PaymentInfoBuilder paymentInfoBuilder,
			vn.utetra.service.RefundService refundService) {
		this.adminOrderService = adminOrderService;
		this.orderIssueService = orderIssueService;
		this.branchService = branchService;
		this.orderFlowService = orderFlowService;
		this.paymentInfoBuilder = paymentInfoBuilder;
		this.refundService = refundService;
	}

	@GetMapping
	public String index(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "") String branchId, @RequestParam(defaultValue = "") String status,
			@RequestParam(defaultValue = "") String paymentStatus,
			@RequestParam(defaultValue = "") String paymentMethod,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
			Model model) {

		List<Order> orders = adminOrderService.search(keyword, branchId, status, paymentStatus, paymentMethod, from, to);

		model.addAttribute("orders", orders);
		vn.utetra.service.AdminOrderService.IssueMarks marks = adminOrderService.issueMarks();
		model.addAttribute("issueLabels", marks.labels());
		model.addAttribute("issuePending", marks.pending());
		model.addAttribute("branches", branchService.findAll());
		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("paymentStatuses", PaymentStatus.values());
		model.addAttribute("paymentMethods", PaymentMethod.values());

		model.addAttribute("keyword", keyword);
		model.addAttribute("selectedBranch", branchId);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("selectedPaymentStatus", paymentStatus);
		model.addAttribute("selectedPaymentMethod", paymentMethod);
		model.addAttribute("from", from);
		model.addAttribute("to", to);

		vn.utetra.service.AdminOrderService.OrderCounts counts = adminOrderService.counts();
		model.addAttribute("totalOrders", counts.total());
		model.addAttribute("pendingOrders", counts.pending());
		model.addAttribute("deliveredOrders", counts.delivered());
		model.addAttribute("cancelledOrders", counts.cancelled());

		return "admin/orders/index";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {

		Order order = adminOrderService.find(id).orElse(null);

		if (order == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy đơn hàng #" + id);
			return "redirect:/admin/orders";
		}

		model.addAttribute("order", order);
		model.addAttribute("nextStatuses", getNextStatuses(order));
		model.addAttribute("pay", paymentInfoBuilder.build(order));
		model.addAttribute("issue", orderIssueService.forOrder(id));
		model.addAttribute("payments", adminOrderService.payments(id));

		return "admin/orders/detail";
	}

	@PostMapping("/{id}/gateway-check")
	public String gatewayCheck(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("gatewayCheck", adminOrderService.gatewayCheck(id));
		return "redirect:/admin/orders/" + id;
	}

	@PostMapping("/{id}/refund")
	public String retryRefund(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
		if (refundService.retry(id)) {
			redirectAttributes.addFlashAttribute("successMessage", "Cổng thanh toán đã nhận lệnh hoàn tiền đơn #" + id + ".");
		} else {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Chưa hoàn tiền được đơn #" + id + ". Xem lỗi ở mục Giao dịch thanh toán.");
		}
		return "redirect:/admin/orders/" + id;
	}

	@PostMapping("/{id}/status")
	public String updateStatus(@PathVariable Integer id, @RequestParam OrderStatus status,
			@RequestParam(required = false) String reason, RedirectAttributes redirectAttributes) {
		try {
			Order current = adminOrderService.find(id).orElse(null);
			String finalReason = status == OrderStatus.RETURNED && current != null
					&& current.getDeliveryType() == DeliveryType.PICKUP ? OrderFlowServiceImpl.NO_SHOW_REASON
							: status == OrderStatus.CANCELLED && reason != null && !reason.isBlank()
									? "UTETra huỷ: " + reason.trim() : reason;
			orderFlowService.updateStatus(id, status, finalReason);
			redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật đơn hàng #" + id + ".");
		} catch (BusinessException | ResourceNotFoundException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/orders/" + id;
	}

	private List<OrderStatus> getNextStatuses(Order order) {
		return OrderFlowServiceImpl.nextStatuses(order.getStatus(), order.getDeliveryType() == DeliveryType.PICKUP);
	}
}

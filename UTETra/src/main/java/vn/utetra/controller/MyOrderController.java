package vn.utetra.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.OrderFlowService;

import java.security.Principal;
import java.util.List;

/** Trang "Đơn hàng của tôi" của khách: /orders?tab=..., /orders/{id}, huỷ đơn */
@Controller
@RequestMapping("/orders")
public class MyOrderController {

	/** Lý do huỷ có sẵn (giống app thật), chọn "Lý do khác" thì khách tự ghi */
	private static final List<String> CANCEL_REASONS = List.of(
			"Muốn thay đổi địa chỉ / số điện thoại nhận hàng",
			"Muốn thay đổi món, size hoặc topping",
			"Muốn dùng mã giảm giá khác",
			"Đặt nhầm, không còn nhu cầu");
	private static final String OTHER = "OTHER";

	private final OrderFlowService orderFlowService;
	private final CurrentUserService currentUserService;

	public MyOrderController(OrderFlowService orderFlowService, CurrentUserService currentUserService) {
		this.orderFlowService = orderFlowService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	public String list(@RequestParam(required = false) String tab, Principal principal, HttpSession session, Model model) {
		Integer userId = currentUserService.requireUserId(principal, session);
		OrderTab current = OrderTab.parse(tab);
		model.addAttribute("tabs", OrderTab.values());
		model.addAttribute("tab", current);
		model.addAttribute("counts", orderFlowService.myOrderCounts(userId));
		model.addAttribute("orders", orderFlowService.myOrders(userId, current));
		return "order/list";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			model.addAttribute("order", orderFlowService.myOrder(userId, id));
			model.addAttribute("cancelReasons", CANCEL_REASONS);
			return "order/detail";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders";
		}
	}

	@PostMapping("/{id}/cancel")
	public String cancel(@PathVariable Integer id, @RequestParam(required = false) String reason,
			@RequestParam(required = false) String otherReason, Principal principal, HttpSession session,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		String finalReason = reason;
		if (OTHER.equals(reason)) {
			if (otherReason == null || otherReason.isBlank()) {
				ra.addFlashAttribute("error", "Bạn chọn \"Lý do khác\", vui lòng ghi rõ lý do");
				return "redirect:/orders/" + id;
			}
			finalReason = "Khác: " + otherReason.trim();
		}
		try {
			orderFlowService.cancelByCustomer(userId, id, finalReason);
			ra.addFlashAttribute("success", "Đã huỷ đơn #" + id + ". Mã giảm giá (nếu có) đã được hoàn lại.");
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/orders/" + id;
	}
}
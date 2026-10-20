package vn.utetra.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.VendorOrderService;

import java.security.Principal;
import java.util.List;

/**
 * Vendor quản lý đơn của shop (SecurityConfig: /vendor/** chỉ ROLE_VENDOR):
 * - /vendor/orders            : Quầy đơn hàng (realtime, chuông)
 * - /vendor/orders/history    : Đơn hàng của shop theo 7 trạng thái của đề
 * - /vendor/orders/{id}       : Chi tiết 1 đơn + nút đổi trạng thái
 */
@Controller
public class VendorOrderController {

	/** Lý do quán huỷ đơn */
	private static final List<String> CANCEL_REASONS = List.of(
			"Quán hết nguyên liệu / hết món",
			"Quán đang quá tải, không kịp làm",
			"Ngoài giờ phục vụ",
			"Không liên lạc được với khách");
	private static final String OTHER = "OTHER";

	private final VendorOrderService vendorOrderService;
	private final CurrentUserService currentUserService;

	public VendorOrderController(VendorOrderService vendorOrderService, CurrentUserService currentUserService) {
		this.vendorOrderService = vendorOrderService;
		this.currentUserService = currentUserService;
	}

	/** Quầy đơn hàng */
	@GetMapping("/vendor/orders")
	public String page(@RequestParam(required = false) Integer branchId, Principal principal, HttpSession session,
			Model model) {
		Integer vendorId = currentUserService.requireUserId(principal, session);
		model.addAttribute("branches", vendorOrderService.myBranches(vendorId));
		model.addAttribute("cancelReasons", CANCEL_REASONS);
		try {
			model.addAttribute("branchId", vendorOrderService.resolveBranchId(vendorId, branchId));
		} catch (BusinessException | ResourceNotFoundException e) {
			model.addAttribute("branchId", null);
			model.addAttribute("error", e.getMessage());
		}
		return "vendor/orders";
	}

	/** Đơn hàng của shop theo 7 trạng thái */
	@GetMapping("/vendor/orders/history")
	public String history(@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			Principal principal, HttpSession session, Model model) {
		Integer vendorId = currentUserService.requireUserId(principal, session);
		model.addAttribute("branches", vendorOrderService.myBranches(vendorId));
		try {
			model.addAttribute("data", vendorOrderService.history(vendorId, branchId, parseStatus(status), page));
		} catch (BusinessException | ResourceNotFoundException e) {
			model.addAttribute("data", null);
			model.addAttribute("error", e.getMessage());
		}
		return "vendor/history";
	}

	/** Chi tiết 1 đơn của shop */
	@GetMapping("/vendor/orders/{id}")
	public String detail(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer vendorId = currentUserService.requireUserId(principal, session);
		try {
			model.addAttribute("order", vendorOrderService.orderDetail(vendorId, id));
			model.addAttribute("card", vendorOrderService.orderCard(vendorId, id));
			model.addAttribute("cancelReasons", CANCEL_REASONS);
			return "vendor/order-detail";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/vendor/orders/history";
		}
	}

	/** Đổi trạng thái từ trang chi tiết (form) */
	@PostMapping("/vendor/orders/{id}/status")
	public String changeStatus(@PathVariable Integer id, @RequestParam String status,
			@RequestParam(required = false) String reason, @RequestParam(required = false) String otherReason,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer vendorId = currentUserService.requireUserId(principal, session);
		String finalReason = OTHER.equals(reason) ? (otherReason == null ? null : otherReason.trim()) : reason;
		try {
			vendorOrderService.changeStatus(vendorId, id, parseStatus(status), finalReason);
			ra.addFlashAttribute("success", "Đã cập nhật đơn #" + id);
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/vendor/orders/" + id;
	}

	/** "CONFIRMED" -> OrderStatus.CONFIRMED; sai hoặc trống -> null */
	private OrderStatus parseStatus(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return OrderStatus.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
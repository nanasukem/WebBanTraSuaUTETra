package vn.utetra.controller.web;

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
import vn.utetra.service.ManagerOrderService;

import java.security.Principal;
import java.util.List;

/**
 * Manager quản lý đơn của chi nhánh (SecurityConfig: /manager/** chỉ ROLE_MANAGER):
 * - /manager/orders            : Quầy đơn hàng (realtime, chuông)
 * - /manager/orders/history    : Đơn hàng của chi nhánh theo tab trạng thái
 * - /manager/orders/{id}       : Chi tiết 1 đơn + nút đổi trạng thái
 */
@Controller
public class ManagerOrderController {

	/** Lý do quán huỷ đơn */
	private static final List<String> CANCEL_REASONS = List.of(
			"Quán hết nguyên liệu / hết món",
			"Quán đang quá tải, không kịp làm",
			"Ngoài giờ phục vụ",
			"Không liên lạc được với khách",
			"Khách không đến lấy món");
	private static final String OTHER = "OTHER";

	private final ManagerOrderService managerOrderService;
	private final CurrentUserService currentUserService;

	private final vn.utetra.service.OrderIssueService orderIssueService;

	public ManagerOrderController(ManagerOrderService managerOrderService, CurrentUserService currentUserService,
			vn.utetra.service.OrderIssueService orderIssueService) {
		this.managerOrderService = managerOrderService;
		this.currentUserService = currentUserService;
		this.orderIssueService = orderIssueService;
	}

	/** Quầy đơn hàng */
	@GetMapping("/manager/orders")
	public String page(@RequestParam(required = false) Integer branchId, Principal principal, HttpSession session,
			Model model) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		model.addAttribute("branches", managerOrderService.myBranches(managerId));
		model.addAttribute("cancelReasons", CANCEL_REASONS);
		try {
			model.addAttribute("branchId", managerOrderService.resolveBranchId(managerId, branchId));
		} catch (BusinessException | ResourceNotFoundException e) {
			model.addAttribute("branchId", null);
			model.addAttribute("error", e.getMessage());
		}
		return "manager/orders";
	}

	/** Đơn hàng của chi nhánh theo tab trạng thái */
	@GetMapping("/manager/orders/history")
	public String history(@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			Principal principal, HttpSession session, Model model) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		model.addAttribute("branches", managerOrderService.myBranches(managerId));
		try {
			model.addAttribute("data", managerOrderService.history(managerId, branchId, status, page));
		} catch (BusinessException | ResourceNotFoundException e) {
			model.addAttribute("data", null);
			model.addAttribute("error", e.getMessage());
		}
		return "manager/history";
	}

	/** Chi tiết 1 đơn của chi nhánh */
	@GetMapping("/manager/orders/{id}")
	public String detail(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		try {
			model.addAttribute("order", managerOrderService.orderDetail(managerId, id));
			model.addAttribute("card", managerOrderService.orderCard(managerId, id));
			model.addAttribute("cancelReasons", CANCEL_REASONS);
			return "manager/order-detail";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/manager/orders/history";
		}
	}

	/** Đổi trạng thái từ trang chi tiết (form) */
	@PostMapping("/manager/orders/{id}/status")
	public String changeStatus(@PathVariable Integer id, @RequestParam String status,
			@RequestParam(required = false) String reason, @RequestParam(required = false) String otherReason,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		String finalReason = OTHER.equals(reason) ? (otherReason == null ? null : otherReason.trim()) : reason;
		try {
			managerOrderService.changeStatus(managerId, id, parseStatus(status), finalReason);
			ra.addFlashAttribute("success", "Đã cập nhật đơn #" + id);
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/manager/orders/" + id;
	}

	/** Xử lý khách báo vấn đề với đơn: hoàn tiền (một phần / toàn bộ), làm lại món, từ chối */
	@PostMapping("/manager/orders/{id}/issue")
	public String resolveIssue(@PathVariable Integer id, @RequestParam String action,
			@RequestParam(required = false) java.math.BigDecimal amount, @RequestParam(required = false) String note,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		try {
			managerOrderService.orderCard(managerId, id); // kiểm tra đơn thuộc chi nhánh của mình
			orderIssueService.resolve(id, action, amount, note);
			ra.addFlashAttribute("success", "Đã xử lý báo cáo của khách cho đơn #" + id);
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/manager/orders/" + id;
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